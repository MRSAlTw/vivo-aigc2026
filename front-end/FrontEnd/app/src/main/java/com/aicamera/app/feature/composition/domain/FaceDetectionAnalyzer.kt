package com.aicamera.app.feature.composition.domain

import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * CameraX 帧分析器：融合 YOLOv8n（人体检测）+ ML Kit（人脸检测）。
 *
 * 流程：
 * ImageProxy → 提取 Bitmap（YOLO）+ InputImage（ML Kit）
 * → 后台协程依次执行 YOLO + ML Kit → 合并结果回调 ViewModel。
 *
 * F-CG-04 + F-CG-05 双引擎。
 */
class FaceDetectionAnalyzer(
    private val yoloDetector: YoloSubjectDetector,
    private val onSubjectsDetected: (
        faces: List<Face>,
        personBoxes: List<YoloSubjectDetector.Detection>,
        frameWidth: Int,
        frameHeight: Int,
    ) -> Unit,
    private val analysisScope: CoroutineScope,
) : ImageAnalysis.Analyzer {

    /** ML Kit 人脸检测器 */
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    private var lastAnalyzedMs: Long = 0

    /** 节流间隔：YOLO 在后台协程运行，250ms ≈ 4 FPS */
    private val throttleMs = 250L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedMs < throttleMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // ── 提取数据（在 analyzer 线程完成，与 imageProxy 解耦）──
        val bitmap = YoloSubjectDetector.imageProxyToBitmap(imageProxy)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // ── 后台协程：YOLO → ML Kit 顺序执行（不阻塞相机管线）──
        analysisScope.launch(Dispatchers.Default) {
            // 路径 1：YOLO 人体检测（CPU 密集，后台线程）
            val personBoxes = if (bitmap != null) {
                yoloDetector.detect(bitmap)
            } else {
                emptyList<YoloSubjectDetector.Detection>()
            }

            // 路径 2：ML Kit 人脸检测（异步回调桥接为挂起函数）
            val faces = suspendCancellableCoroutine<List<Face>> { cont ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }

            // 合并结果回主线程
            withContext(Dispatchers.Main) {
                onSubjectsDetected(faces, personBoxes, width, height)
            }
        }
    }

    companion object {
        fun faceCenterNormalized(face: Face, frameW: Int, frameH: Int): PointF {
            val box = face.boundingBox
            return PointF(
                (box.centerX().toFloat() / frameW).coerceIn(0f, 1f),
                (box.centerY().toFloat() / frameH).coerceIn(0f, 1f),
            )
        }

        fun faceSizeNormalized(face: Face, frameW: Int, frameH: Int): Pair<Float, Float> {
            val box = face.boundingBox
            return Pair(
                box.width().toFloat() / frameW,
                box.height().toFloat() / frameH,
            )
        }
    }
}
