package com.aicamera.app.feature.composition.domain

import com.aicamera.app.core.ai.DetectedObject
import com.aicamera.app.feature.composition.state.CompositionMode

/**
 * 构图规则引擎 — 传统 CV 规则运算。
 * 输入：主体检测结果 → 输出：构图评分 + 调整方向文字。
 *
 * 当前 3.2 阶段的规则运算内联在 CompositionViewModel 中，
 * 后续可将其提取为实现此接口的独立类，便于自学习功能（3.4 节）插拔规则。
 */
interface CompositionRuleEngine {
    fun evaluate(objects: List<DetectedObject>, mode: CompositionMode): CompositionResult
}

data class CompositionResult(
    val score: Float,
    val directionText: String?,
    val suggestion: String,
)
