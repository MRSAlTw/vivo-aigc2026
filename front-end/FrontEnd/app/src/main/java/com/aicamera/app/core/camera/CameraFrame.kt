package com.aicamera.app.core.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * 相机帧封装�? * CameraX ImageAnalysis 每帧回调 �?转为统一格式�?AI / 预览消费�? * 重对象（Bitmap / ImageProxy）不进入 UiState，通过�?data class 传递�? */
data class CameraFrame(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
    val timestamp: Long,
    val lensFacing: Int,      // CameraSelector.LENS_FACING_BACK / FRONT
    /** 原始 ImageProxy，使用完后必�?close() */
    val imageProxy: ImageProxy? = null,
)

