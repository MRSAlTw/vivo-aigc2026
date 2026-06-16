package com.aicamera.app.feature.composition.domain

import com.aicamera.app.core.ai.DetectedObject
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.GuidanceDirection

/**
 * 构图规则引擎 �?传统 CV 规则运算�? * 输入：主体检测结�?�?输出：构图评�?+ 调整方向�? */
interface CompositionRuleEngine {
    fun evaluate(objects: List<DetectedObject>, mode: CompositionMode): CompositionResult
}

data class CompositionResult(
    val score: Float,
    val direction: GuidanceDirection?,
    val suggestion: String,
)

