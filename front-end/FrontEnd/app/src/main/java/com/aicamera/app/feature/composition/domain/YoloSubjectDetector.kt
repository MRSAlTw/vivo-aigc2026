package com.aicamera.app.feature.composition.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.RectF
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** 置信度阈值：低于此值的检测结果丢弃 */
private const val CONFIDENCE_THRESHOLD = 0.4f

/** NMS IoU 阈值：重叠超过此值的框合并 */
private const val NMS_IOU_THRESHOLD = 0.5f

/** YOLOv8n 输入尺寸 */
private const val INPUT_SIZE = 640

/** COCO 类别数 */
private const val NUM_CLASSES = 80

/** 输出预测数 */
private const val NUM_PREDICTIONS = 8400

/**
 * 基于 TFLite 的 YOLOv8n 主体检测器。
 * 检测画面中的人物（person），返回归一化包围框。
 *
 * 使用方式：
 * 1. 将 yolov8n.tflite 放入 model/ 目录（自动打包进 APK assets）
 * 2. val detector = YoloSubjectDetector(context)
 * 3. detector.detect(bitmap) → List<Detection>
 *
 * 参考需求文档 F-CG-04：主体检测使用 YOLOv8n。
 */
class YoloSubjectDetector(context: Context) {

    data class Detection(
        /** 归一化坐标 [0,1] 的包围框 */
        val boundingBox: RectF,
        val confidence: Float,
        /** COCO 类别索引（0=person） */
        val classId: Int,
    )

    private val interpreter: Interpreter

    /** 用于保存本次推理的输出 — 必须匹配模型输出形状 [1, 84, 8400] */
    private val outputArray = Array(1) { Array(4 + NUM_CLASSES) { FloatArray(NUM_PREDICTIONS) } }

    /** Letterbox 参数：preprocess 计算，postprocess 消费（detect() 同步调用，线程安全） */
    private var lbPadLeft = 0f
    private var lbPadTop = 0f
    private var lbContentW = 0f
    private var lbContentH = 0f

    init {
        val model = loadModelFile(context, "yolov8n.tflite")
        interpreter = Interpreter(model, Interpreter.Options().apply {
            setNumThreads(2)  // 2 线程足够，避免与相机管线争抢 CPU
        })
    }

    // ═══════════════════════════════════════════
    // 公开 API
    // ═══════════════════════════════════════════

    /** 对一张 Bitmap 做推理，返回检测到的主体（仅 person，classId=0） */
    fun detect(bitmap: Bitmap): List<Detection> {
        val input = preprocess(bitmap)
        interpreter.run(input, outputArray)
        return postprocess(outputArray[0], bitmap.width, bitmap.height)
            .filter { it.classId == 0 } // 只保留 person
    }

    fun close() {
        interpreter.close()
    }

    // ═══════════════════════════════════════════
    // 预处理：Bitmap → letterbox 640×640 float32 归一化 [0,1]
    //
    // 使用 letterbox（保持宽高比 + 灰边填充）替代直接拉伸，
    // 避免画面变形导致 YOLO 检测框不稳定。
    // ═══════════════════════════════════════════

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        // 计算缩放比例（保持宽高比，取较小者）
        val scale = minOf(INPUT_SIZE / imgW, INPUT_SIZE / imgH)
        val newW = (imgW * scale).toInt()
        val newH = (imgH * scale).toInt()

        // 记录 letterbox 参数，供 postprocess 坐标还原
        lbPadLeft = ((INPUT_SIZE - newW) / 2f)
        lbPadTop = ((INPUT_SIZE - newH) / 2f)
        lbContentW = newW.toFloat()
        lbContentH = newH.toFloat()

        // 创建 640×640 画布，YOLO 标准灰边填充 (114,114,114)
        val letterbox = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterbox)
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))

        // 缩放原图并居中绘制
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(resized, lbPadLeft, lbPadTop, null)
        resized.recycle()

        // 转换为 float32 归一化输入
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        letterbox.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        letterbox.recycle()

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
        return buffer
    }

    // ═══════════════════════════════════════════
    // 后处理：解析 [1, 84, 8400] → 筛选 → NMS
    // 同时将 letterbox 归一化坐标还原为原始图像归一化坐标
    // ═══════════════════════════════════════════

    private fun postprocess(output: Array<FloatArray>, imageW: Int, imageH: Int): List<Detection> {
        val rawBoxes = mutableListOf<Detection>()

        for (i in 0 until NUM_PREDICTIONS) {
            var maxScore = 0f
            var maxClass = -1
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = c
                }
            }
            if (maxScore < CONFIDENCE_THRESHOLD) continue

            val rawCx = output[0][i]
            val rawCy = output[1][i]
            val rawW = output[2][i]
            val rawH = output[3][i]

            // 将 letterbox 640×640 归一化坐标 → 原始图像归一化坐标
            val cx = if (lbContentW > 0f) (rawCx * INPUT_SIZE - lbPadLeft) / lbContentW else rawCx
            val cy = if (lbContentH > 0f) (rawCy * INPUT_SIZE - lbPadTop) / lbContentH else rawCy
            val w = if (lbContentW > 0f) rawW * INPUT_SIZE / lbContentW else rawW
            val h = if (lbContentH > 0f) rawH * INPUT_SIZE / lbContentH else rawH

            // clamp 到 [0,1]（letterbox 坐标还原后可能略微超出）
            val left = (cx - w / 2).coerceIn(0f, 1f)
            val top = (cy - h / 2).coerceIn(0f, 1f)
            val right = (cx + w / 2).coerceIn(0f, 1f)
            val bottom = (cy + h / 2).coerceIn(0f, 1f)

            rawBoxes.add(Detection(RectF(left, top, right, bottom), maxScore, maxClass))
        }

        return nonMaxSuppression(rawBoxes)
    }

    /** 非极大值抑制：去除高度重叠的低置信度框 */
    private fun nonMaxSuppression(boxes: List<Detection>): List<Detection> {
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)

            val iter = sorted.iterator()
            while (iter.hasNext()) {
                val other = iter.next()
                if (iou(best.boundingBox, other.boundingBox) > NMS_IOU_THRESHOLD) {
                    iter.remove()
                }
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val areaA = a.width() * a.height()
        val areaB = b.width() * b.height()
        return intersection / (areaA + areaB - intersection + 1e-6f)
    }

    // ═══════════════════════════════════════════
    // 工具：从 assets 加载模型 + ImageProxy→Bitmap
    // ═══════════════════════════════════════════

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        context.assets.openFd(filename).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(
                    FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
                )
            }
        }
    }

    companion object {
        /**
         * 将 CameraX [ImageProxy]（YUV_420_888）转换为 [Bitmap]。
         *
         * 优化点：
         * - 使用 [BitmapFactory.Options.inSampleSize] 降采样解码
         * - RGB_565 格式降低 50% Bitmap 内存，YOLO 对此精度不敏感
         * - JPEG 质量 85 对模型友好
         */
        fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            val planes = imageProxy.planes
            if (planes.isEmpty()) return null

            val yBuffer = planes[0].buffer
            val uBuffer = planes.getOrNull(1)?.buffer
            val vBuffer = planes.getOrNull(2)?.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer?.remaining() ?: 0
            val vSize = vBuffer?.remaining() ?: 0

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer?.get(nv21, ySize, vSize)
            uBuffer?.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21,
                imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0,
                imageProxy.width, imageProxy.height), 85, out)
            val jpegBytes = out.toByteArray()

            // 动态降采样：长边 > 1280 时取 inSampleSize=2（典型 1920→960）
            val maxDim = maxOf(imageProxy.width, imageProxy.height)
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = if (maxDim > 1280) 2 else 1
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
        }
    }
}
