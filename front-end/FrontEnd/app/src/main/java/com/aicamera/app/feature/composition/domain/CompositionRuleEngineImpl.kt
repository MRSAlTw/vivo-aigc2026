package com.aicamera.app.feature.composition.domain

import android.graphics.PointF
import com.aicamera.app.core.ai.DetectedObject
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.GuidanceDirection
import kotlin.math.sqrt

/**
 * 构图规则引擎实现 — 传统 CV 几何规则运算。
 *
 * 将 ViewModel 中的核心构图逻辑（idealFacePosition、buildDirectionText、autoRecommend）
 * 抽取为独立实现类，便于自学习功能（3.4 节）插拔规则。
 *
 * 实现 [CompositionRuleEngine] 接口。
 */
class CompositionRuleEngineImpl : CompositionRuleEngine {

    private val alignThreshold = 0.055f
    private val faceTooSmallRatio = 0.008f
    private val faceTooLargeRatio = 0.35f

    override fun evaluate(objects: List<DetectedObject>, mode: CompositionMode): CompositionResult {
        // 当有检测对象时使用对象中心；否则默认画面中心
        val mainObject = objects.firstOrNull()
        val cx = if (mainObject != null) {
            val box = mainObject.boundingBox  // [left, top, right, bottom]
            (box[0] + box[2]) / 2f
        } else 0.5f
        val cy = if (mainObject != null) {
            val box = mainObject.boundingBox
            (box[1] + box[3]) / 2f
        } else 0.5f

        val target = idealFacePosition(cx, cy, mode)
        val dx = target.x - cx
        val dy = target.y - cy
        val distance = sqrt(dx * dx + dy * dy)
        val aligned = distance < alignThreshold

        val direction = computeDirection(dx, dy, aligned)
        val suggestion = buildDirectionText(dx, dy, aligned = aligned)

        return CompositionResult(
            score = (1f - distance).coerceIn(0f, 1f),
            direction = direction,
            suggestion = suggestion,
        )
    }

    /**
     * 根据当前主体位置，计算给定构图方式下的理想目标位置。
     */
    fun idealFacePosition(currentX: Float, currentY: Float, mode: CompositionMode): PointF {
        return when (mode) {
            CompositionMode.AUTO -> {
                // AUTO 下委托给 autoRecommend 选择最佳构图
                val best = CompositionMode.entries
                    .filter { it != CompositionMode.AUTO }
                    .minByOrNull { m ->
                        val p = idealFacePosition(currentX, currentY, m)
                        dist(p.x, p.y, currentX, currentY)
                    } ?: CompositionMode.RULE_OF_THIRDS
                idealFacePosition(currentX, currentY, best)
            }
            CompositionMode.RULE_OF_THIRDS -> {
                val cands = listOf(
                    PointF(1f / 3, 1f / 3), PointF(2f / 3, 1f / 3),
                    PointF(1f / 3, 2f / 3), PointF(2f / 3, 2f / 3),
                )
                cands.minByOrNull { dist(it.x, it.y, currentX, currentY) }!!
            }
            CompositionMode.CENTER -> PointF(0.5f, 0.5f)
            CompositionMode.DIAGONAL -> {
                val d1 = projectToLine(currentX, currentY, 0f, 0f, 1f, 1f)
                val d2 = projectToLine(currentX, currentY, 0f, 1f, 1f, 0f)
                val dist1 = dist(d1.x, d1.y, currentX, currentY)
                val dist2 = dist(d2.x, d2.y, currentX, currentY)
                val p = if (dist1 < dist2) d1 else d2
                PointF(p.x.coerceIn(0.1f, 0.9f), p.y.coerceIn(0.1f, 0.9f))
            }
            CompositionMode.FRAME -> PointF(0.5f, 0.5f)
            CompositionMode.NEGATIVE_SPACE -> PointF(0.28f, 0.5f)
        }
    }

    /**
     * 自动推荐：遍历所有非 AUTO 构图方式，选择距离最近者。
     */
    fun autoRecommend(cx: Float, cy: Float): CompositionMode {
        return CompositionMode.entries
            .filter { it != CompositionMode.AUTO }
            .minByOrNull { mode ->
                val i = idealFacePosition(cx, cy, mode)
                dist(i.x, i.y, cx, cy)
            } ?: CompositionMode.RULE_OF_THIRDS
    }

    /**
     * 构建引导方向文字（中文）。
     */
    fun buildDirectionText(
        dx: Float, dy: Float,
        effectiveW: Float = 0f, effectiveH: Float = 0f,
        aligned: Boolean,
    ): String {
        // ── 优先：未对齐时给出方向引导 ──
        if (!aligned) {
            val distance = sqrt(dx * dx + dy * dy)
            val magnitude = when {
                distance < 0.12f -> "一点"
                distance < 0.30f -> ""
                else -> "大幅"
            }

            val h = when {
                dx < -alignThreshold -> "向右移$magnitude"
                dx > alignThreshold -> "向左移$magnitude"
                else -> ""
            }
            val v = when {
                dy < -alignThreshold -> "向下移$magnitude"
                dy > alignThreshold -> "向上移$magnitude"
                else -> ""
            }

            val dirText = when {
                h.isNotEmpty() && v.isNotEmpty() -> "↗ $h · $v"
                h.isNotEmpty() -> "↔ $h"
                v.isNotEmpty() -> "↕ $v"
                else -> null
            }
            if (dirText != null) return dirText
        }

        // ── 已对齐 → 检查主体尺寸是否合适 ──
        val size = effectiveW * effectiveH
        if (size < faceTooSmallRatio && size > 0f) return "📷 靠近一点"
        if (size > faceTooLargeRatio) return "📷 离远一点"

        return "✅ 位置合适"
    }

    /**
     * 根据 dx/dy 计算 GuidanceDirection 枚举。
     */
    fun computeDirection(dx: Float, dy: Float, aligned: Boolean): GuidanceDirection? {
        if (aligned) return null
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)
        return if (absDx > absDy) {
            if (dx > 0) GuidanceDirection.MOVE_LEFT else GuidanceDirection.MOVE_RIGHT
        } else {
            if (dy > 0) GuidanceDirection.MOVE_UP else GuidanceDirection.MOVE_DOWN
        }
    }

    // ═══════════════════════════════════════════
    // 几何工具
    // ═══════════════════════════════════════════

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun projectToLine(
        px: Float, py: Float,
        ax: Float, ay: Float, bx: Float, by: Float,
    ): PointF {
        val abx = bx - ax; val aby = by - ay
        val t = ((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby)
        return PointF(ax + t * abx, ay + t * aby)
    }
}
