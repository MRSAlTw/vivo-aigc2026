package com.aicamera.app.core.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CameraX 实现类。
 * 管理相机生命周期、预览、拍照和照片保存。
 */
@Singleton
class CameraControllerImpl @Inject constructor(
    private val context: Context
) : CameraController {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: androidx.camera.core.Camera? = null

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var _currentLensFacing = CameraSelector.LENS_FACING_BACK
    override val currentLensFacing: Int get() = _currentLensFacing

    private val _isCameraReady = MutableStateFlow(false)
    override val isCameraReady: Boolean get() = _isCameraReady.value

    private var _lastPhotoUri: Uri? = null
    override val lastPhotoUri: Uri? get() = _lastPhotoUri

    // ────────── 变焦相关 ──────────
    private val _zoomRatioFlow = MutableStateFlow(1.0f)
    override val zoomRatioFlow: Flow<Float> = _zoomRatioFlow.asStateFlow()
    override val zoomRatio: Float get() = _zoomRatioFlow.value

    private var _minZoomRatio = 1.0f
    override val minZoomRatio: Float get() = _minZoomRatio

    private var _maxZoomRatio = 8.0f
    override val maxZoomRatio: Float get() = _maxZoomRatio

    // 预览帧流：从 ImageAnalysis 产出 CameraFrame
    private val _frameFlow = MutableSharedFlow<CameraFrame>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frameFlow: Flow<CameraFrame> = _frameFlow.asSharedFlow().conflate()

    // 拍照结果流
    private val _photoCaptureResult = MutableSharedFlow<Bitmap>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val photoCaptureResult: SharedFlow<Bitmap> = _photoCaptureResult.asSharedFlow()

    // 后台线程
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun bindToLifecycle(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        startCamera()
    }

    private fun startCamera() {
        val pv = previewView ?: return
        val owner = lifecycleOwner ?: return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = try {
                val provider = cameraProviderFuture.get()
                bindUseCases(provider, pv, owner)
                provider
            } catch (e: Exception) {
                _isCameraReady.value = false
                null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        pv: PreviewView,
        owner: LifecycleOwner
    ) {
        // 解绑旧用例
        provider.unbindAll()

        // Preview
        val preview = Preview.Builder()
            .setTargetResolution(Size(1080, 1920))
            .build()
            .also { it.setSurfaceProvider(pv.surfaceProvider) }

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetResolution(Size(1080, 1920))
            .setTargetRotation(pv.display?.rotation ?: android.view.Surface.ROTATION_0)
            .build()

        // ImageAnalysis（预览帧流）
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val frame = imageProxyToCameraFrame(imageProxy)
                    if (frame != null) {
                        _frameFlow.tryEmit(frame)
                    }
                    imageProxy.close()
                }
            }

        // CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_currentLensFacing)
            .build()

        try {
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )

            // 观察变焦状态
            camera?.cameraInfo?.zoomState?.observe(owner) { zoomState ->
                _zoomRatioFlow.value = zoomState.zoomRatio
                _minZoomRatio = zoomState.minZoomRatio
                _maxZoomRatio = zoomState.maxZoomRatio
            }

            _isCameraReady.value = true
        } catch (e: Exception) {
            _isCameraReady.value = false
        }
    }

    private fun imageProxyToCameraFrame(imageProxy: ImageProxy): CameraFrame? {
        return try {
            val bitmap = imageProxyToBitmap(imageProxy) ?: return null
            CameraFrame(
                bitmap = bitmap,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                timestamp = imageProxy.imageInfo.timestamp ?: System.currentTimeMillis(),
                lensFacing = _currentLensFacing,
                imageProxy = imageProxy // 调用方需要 close()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        // 获取 buffer（处理 nullable planes[0].buffer）
        val plane0 = imageProxy.planes[0] ?: return null
        val rawBuffer: ByteBuffer = when (imageProxy.format) {
            ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888 -> {
                // YUV -> RGB conversion via YuvImage
                val yBuffer = plane0.buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    imageProxy.width, imageProxy.height, null
                )
                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                    80, out
                )
                ByteBuffer.wrap(out.toByteArray())
            }
            else -> plane0.buffer!!
        }

        val bytes = ByteArray(rawBuffer.remaining())
        rawBuffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // 根据旋转角度修正方向
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0 && bitmap != null) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    override suspend fun hasUltraWide(): Boolean {
        // CameraX doesn't provide direct focal length API, so we check camera availability
        // In real implementation, this would query device camera characteristics
        return try {
            camera?.cameraInfo != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun flipCamera() {
        _currentLensFacing = if (_currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        rebindUseCases()
    }

    override suspend fun switchToUltraWide(enable: Boolean) {
        if (enable) {
            // 切换到超广角（通过 CameraSelector 选择广角镜头）
            // 注意：CameraX 不直接支持在同一 LensFacing 下切换焦距
            // 实际实现依赖设备支持，这里作为预留接口
            _isUltraWideActive = true
        } else {
            _isUltraWideActive = false
        }
        rebindUseCases()
    }

    private var _isUltraWideActive = false

    private fun rebindUseCases() {
        val pv = previewView ?: return
        val owner = lifecycleOwner ?: return
        val provider = cameraProvider ?: return
        bindUseCases(provider, pv, owner)
    }

    override suspend fun setZoomRatio(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio)
        } catch (_: Exception) { }
    }

    /** 截取 PreviewView 当前画面（直接用屏幕渲染结果，无 YUV 转换问题） */
    override fun capturePreviewSnapshot(): Bitmap? {
        return try {
            previewView?.bitmap
        } catch (_: Exception) { null }
    }

    override suspend fun capturePhoto() {
        val capture = imageCapture ?: return
        val pv = previewView ?: return

        // 创建 MediaStore 条目（Android 10+ 作用域存储）
        val photoUri = withContext(Dispatchers.IO) {
            createPendingMediaStoreUri()
        }

        if (photoUri != null) {
            // 使用 OutputFileOptions 让 CameraX 直接写入 JPEG
            capturePhotoToUri(capture, photoUri)
        } else {
            // 降级：通过内存回调获取 JPEG buffer
            capturePhotoFallback(capture, pv)
        }
    }

    private fun capturePhotoToUri(capture: ImageCapture, photoUri: Uri) {
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            photoUri,
            ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
            }
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: photoUri
                    _lastPhotoUri = savedUri
                    loadThumbnail(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    // 保存失败，清理 MediaStore 条目
                    try { context.contentResolver.delete(photoUri, null, null) } catch (_: Exception) {}
                    // 降级到 fallback（使用本地 previewView 变量）
                    val pv = previewView
                    if (pv != null) capturePhotoFallback(capture, pv)
                }
            }
        )
    }

    private fun capturePhotoFallback(capture: ImageCapture, pv: PreviewView) {
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    if (bitmap != null) {
                        saveBitmapToMediaStore(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    // 拍照失败，不做处理
                }
            }
        )
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap) {
        val uri = saveBitmapToGallery(context, bitmap)
        if (uri != null) {
            _lastPhotoUri = uri
        }
        _photoCaptureResult.tryEmit(bitmap)
    }

    private fun loadThumbnail(uri: Uri) {
        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
            _photoCaptureResult.tryEmit(bitmap)
        } catch (e: Exception) {
            // 缩略图加载失败不影响主流程
        }
    }

    private fun createPendingMediaStoreUri(): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val displayName = "IMG_${timestamp}_${System.nanoTime().toString().takeLast(4)}"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AICamera")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val displayName = "IMG_${timestamp}_${System.nanoTime().toString().takeLast(4)}"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AICamera")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            _lastPhotoUri = uri
            uri
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun shutdown() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
            _isCameraReady.value = false
        } catch (_: Exception) {
        }
    }
}

/** 辅助扩展：取字符串末尾 N 个字符 */
private fun String.takeLast(n: Int): String = if (length <= n) this else substring(length - n)