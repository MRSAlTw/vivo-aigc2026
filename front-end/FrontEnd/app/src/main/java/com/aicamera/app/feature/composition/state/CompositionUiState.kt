package com.aicamera.app.feature.composition.state

/**
 * 构图指导页面 UI 状态�? */
data class CompositionUiState(
    val currentMode: CompositionMode = CompositionMode.AUTO,
    val availableModes: List<CompositionMode> = CompositionMode.entries,
    val guidanceText: String? = null,
    val guidanceDirection: GuidanceDirection? = null,
    val isReady: Boolean = false,
)

enum class CompositionMode(val displayName: String) {
    AUTO("自动推荐"),
    RULE_OF_THIRDS("三分构图"),
    CENTER("中心构图"),
    DIAGONAL("对角线构图"),
    FRAME("框架构图"),
    NEGATIVE_SPACE("留白构图"),
}

enum class GuidanceDirection {
    MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
    ZOOM_IN, ZOOM_OUT, STEADY,
}

