package com.aicamera.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.aicamera.app.core.camera.CameraFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * AI 推理引擎 TFLite 实现。
 *
 * 加载模型:
 *   - yolov8n.tflite: YOLOv8n 目标检测 (COCO 80 类)，输入 640×640
 *   - nima_mobilenet.tflite: NIMA 美学评分 (AVA 数据集)，输入 224×224
 *
 * 设计:
 *   - 单例，模型只加载一次
 *   - 推理在调用者线程运行（协程 Dispatchers.Default）
 */
@Singleton
class AiEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiEngine {

    // ──────────────────────────────────────
    // TFLite 运行时
    // ──────────────────────────────────────

    private var interpreter: Interpreter? = null

    // YOLOv8n 输入尺寸
    private val inputW = 640
    private val inputH = 640

    // 检测阈值
    private val confThreshold = 0.35f
    private val iouThreshold = 0.45f
    private val nmsTopK = 300

    // 模型是否加载成功
    private var isLoaded = false
    private var nimaLoaded = false

    // NIMA 输入尺寸
    private val nimaInputSize = 224

    private var nimaInterpreter: Interpreter? = null

    init {
        loadYoloModel()
        loadNimaModel()
    }

    /** 从 assets 拷贝 NIMA 模型，加载 TFLite */
    private fun loadNimaModel() {
        try {
            val modelFile = File(context.filesDir, "nima_mobilenet.tflite")
            if (!modelFile.exists()) {
                context.assets.open("nima_mobilenet.tflite").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "NIMA 模型已从 assets 复制到: ${modelFile.absolutePath}")
            }

            nimaInterpreter = Interpreter(modelFile)
            nimaLoaded = true
            Log.d(TAG, "NIMA TFLite 模型加载成功 (${modelFile.length() / 1024} KB)")
        } catch (e: Exception) {
            nimaLoaded = false
            Log.e(TAG, "NIMA 模型加载失败", e)
        }
    }

    /** 从 assets 拷贝模型到内部存储，加载 TFLite 模型 */
    private fun loadYoloModel() {
        try {
            val modelFile = File(context.filesDir, "yolov8n.tflite")
            if (!modelFile.exists()) {
                context.assets.open("yolov8n.tflite").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "模型已从 assets 复制到: ${modelFile.absolutePath}")
            }

            interpreter = Interpreter(modelFile)
            isLoaded = true
            Log.d(TAG, "YOLOv8n TFLite 模型加载成功 (${modelFile.length() / 1024} KB)")
        } catch (e: Exception) {
            isLoaded = false
            Log.e(TAG, "TFLite 模型加载失败", e)
        }
    }

    // ──────────────────────────────────────
    // AiEngine 接口实现
    // ──────────────────────────────────────

    override suspend fun detectObjects(frame: CameraFrame): DetectionResult {
        if (!isLoaded || interpreter == null) {
            return DetectionResult(emptyList(), frame.timestamp)
        }

        return try {
            // ── 1. 预处理 ──
            val inputData = preprocessBitmap(frame.bitmap, inputW, inputH)

            // ── 2. 准备输入/输出 ByteBuffer（避免 FP16 张量分配问题）──
            val inputBuf = ByteBuffer.allocateDirect(inputData.size * 4)
                .order(ByteOrder.nativeOrder())
            inputBuf.asFloatBuffer().put(inputData)

            val outputSize = 84 * 8400
            val outputBuf = ByteBuffer.allocateDirect(outputSize * 4)
                .order(ByteOrder.nativeOrder())

            // ── 3. 推理 ──
            interpreter!!.run(inputBuf, outputBuf)

            // ── 4. 读取输出 ──
            // YOLOv8n 输出形状: [1, 84, 8400]
            //   - 8400 = 3 个检测头 (80×80 + 40×40 + 20×20)
            //   - 84 = 4 (cx,cy,w,h) + 80 (COCO 类概率)
            val outputData = FloatArray(outputSize)
            outputBuf.rewind()
            outputBuf.asFloatBuffer().get(outputData)

            // 调试：获取实际输出张量形状
            val outShape = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "YOLO 输出形状: ${outShape?.joinToString("×")}, " +
                    "首10值=[${outputData.take(10).joinToString(",") { "%.3f".format(it) }}], " +
                    "末10值=[${outputData.takeLast(10).joinToString(",") { "%.3f".format(it) }}]")

            val numCandidates = 8400
            val numChannels = 84

            // ── 4. 后处理：解析检测框 + NMS ──
            val objects = postProcessYolov8(
                outputData, numCandidates, numChannels,
                inputW, inputH, frame.bitmap.width, frame.bitmap.height,
                confThreshold, iouThreshold
            )

            Log.d(TAG, "YOLO 检测到 ${objects.size} 个目标: ${
                objects.groupBy { it.label }.map { "${it.key}=${it.value.size}" }.joinToString(", ")
            }")

            DetectionResult(objects, frame.timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "YOLO 推理异常", e)
            DetectionResult(emptyList(), frame.timestamp)
        }
    }

    override suspend fun detectPose(frame: CameraFrame): PoseResult? {
        // 姿势指导阶段实现，当前返回 null
        return null
    }

    override suspend fun assessAesthetics(frame: CameraFrame): Float {
        if (!nimaLoaded || nimaInterpreter == null) {
            return 5.0f  // fallback
        }

        return try {
            // ── 1. 预处理：resize 224×224 → NHWC float[-1,1] ──
            val nimaData = preprocessForNima(frame.bitmap)

            // ── 2. TFLite 推理（用 ByteBuffer 避免 FP16 张量分配问题）──
            val inputBuf = ByteBuffer.allocateDirect(nimaData.size * 4)
                .order(ByteOrder.nativeOrder())
            inputBuf.asFloatBuffer().put(nimaData)

            // NIMA 输出: (1, 10) 概率分布
            val outputBuf = ByteBuffer.allocateDirect(10 * 4)
                .order(ByteOrder.nativeOrder())

            nimaInterpreter!!.run(inputBuf, outputBuf)

            // ── 3. 读取输出 + 计算平均美学分数 (1-10) ──
            val scores = FloatArray(10)
            outputBuf.rewind()
            outputBuf.asFloatBuffer().get(scores)

            var meanScore = 0f
            for (i in scores.indices) {
                meanScore += (i + 1) * scores[i]
            }

            Log.d(TAG, "NIMA 美学评分: %.1f".format(meanScore))
            meanScore
        } catch (e: Exception) {
            Log.e(TAG, "NIMA 推理异常", e)
            5.0f  // fallback
        }
    }

    // ──────────────────────────────────────
    // 预处理：Bitmap → YOLOv8 输入张量
    // ──────────────────────────────────────

    /**
     * 将 Bitmap 预处理为 YOLOv8n 输入格式。
     *
     * 流程:
     *   1. 保持宽高比 resize，较短的边缩放到 640，较长的边缩放后补灰边到 640
     *   2. 转为 RGB float 数组，NHWC 格式（通道紧邻）
     *   3. 归一化到 [0, 1]
     */
    private fun preprocessBitmap(bitmap: Bitmap, targetW: Int, targetH: Int): FloatArray {
        // 计算缩放比例（保持宽高比）
        val scale = min(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        val resizeW = (bitmap.width * scale).toInt()
        val resizeH = (bitmap.height * scale).toInt()

        // 缩放到目标尺寸
        val resized = Bitmap.createScaledBitmap(bitmap, resizeW, resizeH, true)

        // 创建 640×640 画布，灰色填充（114/255 = 0.447）
        val canvas = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvas)
        c.drawColor(Color.rgb(114, 114, 114))
        // 将缩放后的图片居中绘制
        val offsetX = (targetW - resizeW) / 2f
        val offsetY = (targetH - resizeH) / 2f
        c.drawBitmap(resized, offsetX, offsetY, null)

        // 释放中间 bitmap
        if (resized !== bitmap) resized.recycle()

        // 提取像素 → NHWC float 数组
        val pixels = IntArray(targetW * targetH)
        canvas.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        canvas.recycle()

        val inputData = FloatArray(3 * targetW * targetH)  // NHWC: H=640, W=640, C=3
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val offset = i * 3
            // 归一化到 [0, 1]，NHWC 通道排列: R, G, B 紧邻
            inputData[offset]     = ((pixel shr 16) and 0xFF) / 255f  // R
            inputData[offset + 1] = ((pixel shr 8) and 0xFF) / 255f   // G
            inputData[offset + 2] = (pixel and 0xFF) / 255f            // B
        }

        return inputData
    }

    // ──────────────────────────────────────
    // YOLOv8 后处理 (NMS)
    // ──────────────────────────────────────

    /**
     * YOLOv8 输出后处理：解析候选框 → NMS 滤重 → 返回 [left,top,right,bottom] 归一化坐标。
     *
     * @param outputData 原始输出 float 数组 [84, 8400] (行优先, shape[1]=84, shape[2]=8400)
     * @param numCandidates 候选框数量 8400
     * @param numChannels 通道数 84 (4 + 80)
     * @param modelW 模型输入宽 640
     * @param modelH 模型输入高 640
     * @param originalW 原始图片宽
     * @param originalH 原始图片高
     */
    private fun postProcessYolov8(
        outputData: FloatArray,
        numCandidates: Int,
        numChannels: Int,
        modelW: Int,
        modelH: Int,
        originalW: Int,
        originalH: Int,
        confThresh: Float,
        iouThresh: Float,
    ): List<DetectedObject> {
        // 计算原始图片在模型输入中的缩放/偏移
        val scale = min(modelW.toFloat() / originalW, modelH.toFloat() / originalH)
        val padX = (modelW - originalW * scale) / 2f
        val padY = (modelH - originalH * scale) / 2f

        // 收集超过置信度阈值的候选框
        val candidates = mutableListOf<YoloCandidate>()

        // TFLite 输出是行优先: [84][8400]，在 float[] 中是 (84 * 8400) 个元素
        // 第 j 列 = 第 j 个候选框
        for (j in 0 until numCandidates) {
            // 找出最大类概率及其索引
            var maxConf = 0f
            var maxId = -1
            for (c in 4 until numChannels) {  // 跳过前 4 个坐标
                val conf = outputData[c * numCandidates + j]
                if (conf > maxConf) {
                    maxConf = conf
                    maxId = c - 4  // COCO 类别索引
                }
            }

            if (maxConf < confThresh || maxId < 0) continue

            // 读取 bbox (cx, cy, w, h) — 归一化到 [0, 1]
            val cxN = outputData[j]                        // index 0, [0,1]
            val cyN = outputData[1 * numCandidates + j]    // index 1, [0,1]
            val wN  = outputData[2 * numCandidates + j]    // index 2, [0,1]
            val hN  = outputData[3 * numCandidates + j]    // index 3, [0,1]

            // 转换为模型像素坐标
            val cx = cxN * modelW
            val cy = cyN * modelH
            val w  = wN * modelW
            val h  = hN * modelH

            // 转换为 xyxy 格式，再从模型坐标空间映射回原始图片坐标
            val x1 = ((cx - w / 2f) - padX) / scale
            val y1 = ((cy - h / 2f) - padY) / scale
            val x2 = ((cx + w / 2f) - padX) / scale
            val y2 = ((cy + h / 2f) - padY) / scale

            candidates.add(
                YoloCandidate(
                    x1 = x1.coerceIn(0f, originalW.toFloat()),
                    y1 = y1.coerceIn(0f, originalH.toFloat()),
                    x2 = x2.coerceIn(0f, originalW.toFloat()),
                    y2 = y2.coerceIn(0f, originalH.toFloat()),
                    confidence = maxConf,
                    classId = maxId,
                )
            )
        }

        // NMS
        candidates.sortByDescending { it.confidence }
        val selected = mutableListOf<YoloCandidate>()

        for (i in candidates.indices) {
            val keep = selected.all { sel ->
                computeIoU(candidates[i], sel) < iouThresh
            }
            if (keep) {
                selected.add(candidates[i])
                if (selected.size >= nmsTopK) break
            }
        }

        // 映射到 DetectedObject，坐标归一化到 [0, 1]
        return selected.map { c ->
            DetectedObject(
                label = COCO_CLASSES.getOrElse(c.classId) { "unknown" },
                confidence = c.confidence,
                boundingBox = floatArrayOf(
                    c.x1 / originalW,  // left  归一化
                    c.y1 / originalH,  // top
                    c.x2 / originalW,  // right
                    c.y2 / originalH,  // bottom
                ),
            )
        }
    }

    // ──────────────────────────────────────
    // 预处理：Bitmap → NIMA 输入张量
    // ──────────────────────────────────────

    /**
     * 将 Bitmap 预处理为 NIMA 输入格式。
     *
     * 流程:
     *   1. 直接 resize 到 224×224
     *   2. 提取像素 → RGB → BGR + 减去 ImageNet 均值（caffe 模式）
     *   3. 输出 NHWC float
     *
     * 注意：titu1994 NIMA 模型使用 MobileNet 的 caffe 预处理：
     *   BGR 通道顺序 + 减去 [103.939, 116.779, 123.68]
     */
    private fun preprocessForNima(bitmap: Bitmap): FloatArray {
        val size = nimaInputSize
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)

        val pixels = IntArray(size * size)
        resized.getPixels(pixels, 0, size, 0, 0, size, size)
        if (resized !== bitmap) resized.recycle()

        val inputData = FloatArray(size * size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val offset = i * 3
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // caffe 模式: BGR + 均值减法
            inputData[offset]     = b.toFloat() - 103.939f   // B
            inputData[offset + 1] = g.toFloat() - 116.779f   // G
            inputData[offset + 2] = r.toFloat() - 123.68f    // R
        }
        return inputData
    }

    /** 候选框数据结构 */
    private data class YoloCandidate(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val confidence: Float,
        val classId: Int,
    )

    /** 计算两个框的 IoU */
    private fun computeIoU(a: YoloCandidate, b: YoloCandidate): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)

        val interW = max(0f, interX2 - interX1)
        val interH = max(0f, interY2 - interY1)
        val interArea = interW * interH

        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)

        return interArea / (areaA + areaB - interArea + 1e-6f)
    }

    // ──────────────────────────────────────
    // COCO 80 类名称
    // ──────────────────────────────────────

    companion object {
        private const val TAG = "AICamera.AiEngine"

        val COCO_CLASSES = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
        )
    }
}
