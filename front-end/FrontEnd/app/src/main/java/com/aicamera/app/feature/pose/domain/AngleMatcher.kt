package com.aicamera.app.feature.pose.domain

import com.aicamera.app.core.ai.KeyPoint
import com.aicamera.app.core.data.model.PoseTemplate
import com.aicamera.app.feature.pose.state.JointDeviation

/**
 * 角度匹配�?�?实时对比用户姿势与模板�? * 输入：MoveNet 关键�?+ 模板 �?输出：偏差最大的 1-2 个关节�? */
interface AngleMatcher {
    fun match(current: List<KeyPoint>, template: PoseTemplate): List<JointDeviation>
}

