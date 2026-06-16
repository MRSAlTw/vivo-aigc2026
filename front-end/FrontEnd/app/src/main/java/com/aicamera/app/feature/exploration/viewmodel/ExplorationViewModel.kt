package com.aicamera.app.feature.exploration.viewmodel

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.feature.exploration.state.CameraMode
import com.aicamera.app.feature.exploration.state.ExplorationUiEvent
import com.aicamera.app.feature.exploration.state.ExplorationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExplorationViewModel @Inject constructor(
    private val cameraController: CameraController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorationUiState())
    val uiState: StateFlow<ExplorationUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExplorationUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 暴露缩放倍率流供 UI 层观察 */
    val zoomRatioFlow: Flow<Float> = cameraController.zoomRatioFlow

    /** 拍照结果 Bitmap 流（供 UI 层播放捕获动画） */
    val photoCaptureFlow = cameraController.photoCaptureResult

    /** 按下快门时立即发出的预览帧 Bitmap（不等照片拍好） */
    private val _immediateAnimBitmap = MutableStateFlow<Bitmap?>(null)
    val immediateAnimBitmap: StateFlow<Bitmap?> = _immediateAnimBitmap.asStateFlow()

    init {
        checkPermissions()
        observePhotoResults()
        observeZoomState()
    }

    /**
     * 由 UI 层在 AndroidView factory 中调用，将 PreviewView 和 LifecycleOwner 绑定到 CameraX。
     */
    fun bindCameraToPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraController.bindToLifecycle(previewView, lifecycleOwner)
    }

    private fun checkPermissions() {
        val hasCamera = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = hasCamera,
            hasStoragePermission = true
        )
    }

    /** 权限被授予后调用 */
    fun onPermissionsGranted() {
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = true,
            hasStoragePermission = true
        )
        // 权限就绪后，加载手机最近的一张照片作为缩略图
        viewModelScope.launch {
            loadLatestPhotoFromGallery()
        }
    }

    /**
     * 从系统相册查询最近拍摄的一张照片 URI
     */
    private suspend fun loadLatestPhotoFromGallery() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            val cursor = appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    _uiState.value = _uiState.value.copy(lastPhotoUri = uri)
                }
            }
        } catch (_: Exception) { }
    }

    private fun observePhotoResults() {
        viewModelScope.launch {
            cameraController.photoCaptureResult.collect { _ ->
                val uri = cameraController.lastPhotoUri
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    lastPhotoUri = uri
                )
                _events.send(ExplorationUiEvent.ShutterTriggered)
            }
        }
    }

    private fun observeZoomState() {
        viewModelScope.launch {
            cameraController.zoomRatioFlow.collect { ratio ->
                _uiState.value = _uiState.value.copy(
                    currentZoomRatio = ratio,
                    minZoomRatio = cameraController.minZoomRatio,
                    maxZoomRatio = cameraController.maxZoomRatio
                )
            }
        }
    }

    // ────────── 缩放控制 ──────────

    fun onZoomChange(ratio: Float) {
        viewModelScope.launch {
            cameraController.setZoomRatio(ratio)
        }
    }

    // ────────── 寻景 ──────────

    fun onStartExplore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExploring = true)
        }
    }

    fun onStopExplore() {
        _uiState.value = _uiState.value.copy(isExploring = false)
    }

    // ────────── 拍照 ──────────

    fun onShutterClick() {
        viewModelScope.launch {
            if (!_uiState.value.hasCameraPermission) return@launch
            _uiState.value = _uiState.value.copy(isCapturing = true)

            // 立即截取 PreviewView 当前画面触发飞入动画（不等照片保存完成）
            cameraController.capturePreviewSnapshot()?.let { bitmap ->
                _immediateAnimBitmap.value = bitmap
            }

            // 拍照在后台并行进行
            cameraController.capturePhoto()
        }
    }

    /** UI 层播完动画后调用，清除预览帧引用 */
    fun onCaptureAnimDone() {
        _immediateAnimBitmap.value = null
    }

    fun onFlipCamera() {
        viewModelScope.launch {
            cameraController.flipCamera()
            _uiState.value = _uiState.value.copy(
                isFrontCamera = !_uiState.value.isFrontCamera
            )
        }
    }

    /** 从系统相机/相册返回后刷新缩略图 */
    fun refreshLastPhoto() {
        _uiState.value = _uiState.value.copy(
            lastPhotoUri = cameraController.lastPhotoUri
        )
    }

    // ────────── 模式切换 ──────────

    fun onModeChange(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
        // 切换到寻景模式 → 自动开始寻景；切走 → 停止
        if (mode == CameraMode.EXPLORE) {
            onStartExplore()
        } else if (_uiState.value.isExploring) {
            onStopExplore()
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            cameraController.shutdown()
        }
    }
}