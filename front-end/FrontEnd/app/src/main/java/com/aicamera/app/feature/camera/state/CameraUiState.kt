package com.aicamera.app.feature.camera.state

import android.net.Uri

/**
 * Standard camera mode UI state.
 *
 * Controls the base camera viewfinder — shutter, zoom, flip, permissions.
 * Each tab shares the same CameraController singleton; this state tracks
 * the UI-relevant subset for the standard (plain viewfinder) mode.
 */
data class CameraUiState(
    // 相机状态
    val isCameraReady: Boolean = false,
    val isFrontCamera: Boolean = false,

    // 变焦
    val currentZoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 8.0f,

    // 拍照相关
    val isCapturing: Boolean = false,
    val lastPhotoUri: Uri? = null,

    // 权限
    val hasCameraPermission: Boolean = false,

    // 错误
    val errorMessage: String? = null,
)
