package com.aicamera.app.feature.pose.state

import com.aicamera.app.core.data.model.PoseCategory
import com.aicamera.app.core.data.model.PoseTemplate

/**
 * 姿势指导页面 UI 状态 */
data class PoseUiState(
    val selectedTemplate: PoseTemplate? = null,
    val availableTemplates: List<PoseTemplate> = emptyList(),
    val currentCategory: PoseCategory = PoseCategory.PORTRAIT,
    val deviations: List<JointDeviation> = emptyList(),
    val isPoseMatched: Boolean = false,
    val isLoading: Boolean = false,
)

data class JointDeviation(
    val jointName: String,
    val currentAngle: Float,
    val targetAngle: Float,
    val deviation: Float,       // 偏差角度
    val suggestion: String,     // 文字建议（如"抬高左手"�?)

)