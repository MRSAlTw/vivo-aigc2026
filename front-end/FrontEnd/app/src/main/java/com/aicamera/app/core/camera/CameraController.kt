package com.aicamera.app.core.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * CameraX 封装层接口。
 * 隔离 CameraX API，ViewModel 不直接依赖 CameraX 类型。
 */
interface CameraController {

    /** 预览帧流（20+ FPS），conflated */
    val frameFlow: Flow<CameraFrame>

    /** 截取当前预览画面（供动画立即使用，无需等待拍照完成） */
    fun capturePreviewSnapshot(): Bitmap?

    /** 单次拍照结果（拍照完成后发射 Bitmap） */
    val photoCaptureResult: SharedFlow<Bitmap>

    /** 当前镜头方向（CameraSelector.LENS_FACING_BACK / FRONT） */
    val currentLensFacing: Int

    /** 相机是否已就绪 */
    val isCameraReady: Boolean

    /** 绑定 PreviewView 和 LifecycleOwner（由 UI 层在 AndroidView 中调用） */
    fun bindToLifecycle(previewView: PreviewView, lifecycleOwner: LifecycleOwner)

    /** 是否支持超广角 */
    suspend fun hasUltraWide(): Boolean

    /** 切换镜头方向（前后摄） */
    suspend fun flipCamera()

    /** 切换至超广角 / 返回主摄 */
    suspend fun switchToUltraWide(enable: Boolean)

    /** 单次拍照（照片自动保存到系统相册） */
    suspend fun capturePhoto()

    /** 最近一次保存的照片缩略图 URI（用于左下角预览） */
    val lastPhotoUri: Uri?

    // ────────────── 变焦相关 ──────────────

    /** 当前缩放倍率（1.0 = 1x） */
    val zoomRatio: Float

    /** 最小缩放倍率 */
    val minZoomRatio: Float

    /** 最大缩放倍率 */
    val maxZoomRatio: Float

    /** 缩放倍率的响应式流 */
    val zoomRatioFlow: Flow<Float>

    /** 设置缩放倍率 */
    suspend fun setZoomRatio(ratio: Float)

    /** 释放资源 */
    suspend fun shutdown()
}