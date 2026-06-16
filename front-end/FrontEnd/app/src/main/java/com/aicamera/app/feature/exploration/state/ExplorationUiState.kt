package com.aicamera.app.feature.exploration.state

import android.net.Uri

/**
 * 相机模式枚举
 */
enum class CameraMode(val label: String, val emoji: String) {
    STANDARD("标准", "📷"),
    EXPLORE("寻景", "🔍"),
    COMPOSITION("构图", "⊞"),
    POSE("姿势", "🧑"),
}

/**
 * Exploration / viewfinder preview page UI state
 */
data class ExplorationUiState(
    // 相机状态
    val isCameraReady: Boolean = false,
    val isFrontCamera: Boolean = false,
    val hasUltraWide: Boolean = false,
    val isUltraWideActive: Boolean = false,

    // 变焦
    val currentZoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 8.0f,

    // 寻景相关
    val isExploring: Boolean = false,
    val sceneScore: Float? = null,
    val guidanceAngle: Float? = null,
    val guidanceText: String? = null,

    // 拍照相关
    val isCapturing: Boolean = false,
    val lastPhotoUri: Uri? = null,

    // 相机模式
    val currentMode: CameraMode = CameraMode.STANDARD,

    // 权限
    val hasCameraPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,

    // 错误
    val errorMessage: String? = null,
)