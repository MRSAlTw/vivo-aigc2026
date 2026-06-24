package com.aicamera.app.feature.composition.state

/**
 * 5 种构图方式（对应需求文档 3.2.2）。
 */
enum class CompositionMode(val label: String) {
    RULE_OF_THIRDS("三分法"),
    CENTER("中心构图"),
    DIAGONAL("对角线"),
    FRAME("框架构图"),
    NEGATIVE_SPACE("留白"),
}

/**
 * 当前检测与引导数据。
 *
 * 双检测引擎（YOLO 人体 + ML Kit 人脸）：
 * - YOLO 检测完整人体 → [bodyCenterX/Y] 作为主要构图参照
 * - ML Kit 检测人脸 → [faceCenterX/Y] 作为辅助（人脸更精细）
 * - 引导计算优先使用人体中心，无人时才用人脸
 */
data class FaceGuideData(
    // ── 人脸（ML Kit）──
    val faceCenterX: Float = 0.5f,
    val faceCenterY: Float = 0.5f,
    val faceWidth: Float = 0f,
    val faceHeight: Float = 0f,
    // ── 人体（YOLO）──
    val bodyCenterX: Float = 0.5f,
    val bodyCenterY: Float = 0.5f,
    val bodyWidth: Float = 0f,
    val bodyHeight: Float = 0f,
    val hasBody: Boolean = false,
    // ── 引导结果 ──
    val targetX: Float = 0.5f,
    val targetY: Float = 0.5f,
    val directionText: String = "",
    val isAligned: Boolean = false,
    val hasFace: Boolean = false,
)

/**
 * 构图模块 UI 状态（MVVM 单一可信源）。
 */
data class CompositionUiState(
    val currentMode: CompositionMode = CompositionMode.RULE_OF_THIRDS,
    val recommendedMode: CompositionMode? = null,
    val faceGuide: FaceGuideData? = null,
    val isActive: Boolean = false,
)

sealed interface CompositionUiEvent {
    data class ShowToast(val msg: String) : CompositionUiEvent
}
