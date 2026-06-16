package com.aicamera.app.feature.camera.viewmodel

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.feature.camera.state.CameraUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the standard camera mode.
 *
 * Manages camera permissions, zoom, shutter, flip, and photo thumbnail.
 * Does NOT include exploration/composition/pose logic — those live in their own ViewModels.
 *
 * The CameraController singleton is shared across all tab ViewModels.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraCtrl: CameraController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** Expose CameraController for shared CameraPreview binding */
    val cameraController: CameraController get() = cameraCtrl

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Captured photo bitmap stream (for capture animation) */
    val photoCaptureResult = cameraCtrl.photoCaptureResult

    /** Snapshot bitmap taken immediately on shutter press (for instant animation) */
    private val _immediateAnimBitmap = MutableStateFlow<Bitmap?>(null)
    val immediateAnimBitmap: StateFlow<Bitmap?> = _immediateAnimBitmap.asStateFlow()

    init {
        checkPermissions()
        observePhotoResults()
        observeZoomState()
    }

    // ────────── 权限 ──────────

    private fun checkPermissions() {
        val hasCamera = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = hasCamera,
        )
    }

    fun onPermissionsGranted() {
        _uiState.value = _uiState.value.copy(hasCameraPermission = true)
        viewModelScope.launch { loadLatestPhotoFromGallery() }
    }

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

    // ────────── 观察 ──────────

    private fun observePhotoResults() {
        viewModelScope.launch {
            cameraCtrl.photoCaptureResult.collect { _ ->
                val uri = cameraCtrl.lastPhotoUri
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    lastPhotoUri = uri
                )
            }
        }
    }

    private fun observeZoomState() {
        viewModelScope.launch {
            cameraCtrl.zoomRatioFlow.collect { ratio ->
                _uiState.value = _uiState.value.copy(
                    currentZoomRatio = ratio,
                    minZoomRatio = cameraCtrl.minZoomRatio,
                    maxZoomRatio = cameraCtrl.maxZoomRatio
                )
            }
        }
    }

    // ────────── 变焦 ──────────

    fun onZoomChange(ratio: Float) {
        viewModelScope.launch {
            cameraCtrl.setZoomRatio(ratio)
        }
    }

    // ────────── 快门 ──────────

    fun onShutterClick() {
        viewModelScope.launch {
            if (!_uiState.value.hasCameraPermission) return@launch
            _uiState.value = _uiState.value.copy(isCapturing = true)

            cameraCtrl.capturePreviewSnapshot()?.let { bitmap ->
                _immediateAnimBitmap.value = bitmap
            }

            cameraCtrl.capturePhoto()
        }
    }

    /** Called after the capture animation finishes */
    fun onCaptureAnimDone() {
        _immediateAnimBitmap.value = null
    }

    // ────────── 翻转 ──────────

    fun onFlipCamera() {
        viewModelScope.launch {
            cameraCtrl.flipCamera()
            _uiState.value = _uiState.value.copy(
                isFrontCamera = !_uiState.value.isFrontCamera
            )
        }
    }

    // ────────── 缩略图刷新 ──────────

    fun refreshLastPhoto() {
        _uiState.value = _uiState.value.copy(
            lastPhotoUri = cameraCtrl.lastPhotoUri
        )
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            cameraCtrl.shutdown()
        }
    }
}
