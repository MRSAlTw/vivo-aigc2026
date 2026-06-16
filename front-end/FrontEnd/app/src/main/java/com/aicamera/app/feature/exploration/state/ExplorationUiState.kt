package com.aicamera.app.feature.exploration.state

/**
 * Exploration (寻景) tab UI state.
 *
 * Only contains exploration-specific fields.
 * Camera state (zoom, flip, shutter) is managed by CameraViewModel in the camera module.
 */
data class ExplorationUiState(
    // 寻景状态
    val isExploring: Boolean = false,
    val hasUltraWide: Boolean = false,
    val isUltraWideActive: Boolean = false,

    // 引导数据
    val sceneScore: Float? = null,
    val guidanceAngle: Float? = null,
    val guidanceText: String? = null,

    // 加载/错误
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
