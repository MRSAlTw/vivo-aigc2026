package com.aicamera.app.feature.exploration.domain

import com.aicamera.app.core.ai.DetectedObject
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2

private const val TAG = "AICamera.SceneScore"

/**
 * 场景评分引擎。
 *
 * 评分策略:
 *   - 美学分数：由 NIMA-MobileNet 模型给出 (1-10)
 *   - 方向引导：由 YOLOv8n 检测到的目标位置决定
 *
 * NIMA 未加载时降级为规则打分。
 */
object SceneScoreEngine {

    /**
     * 场景评分 + 方向引导
     *
     * @param objects YOLO 检测结果（用于方向引导）
     * @param nimaScore NIMA 美学评分 (1-10)，null 时降级为规则打分
     * @param imgWidth 原始图片宽（px）
     * @param imgHeight 原始图片高（px）
     */
    fun evaluate(
        objects: List<DetectedObject>,
        nimaScore: Float? = null,
        imgWidth: Int = 1080,
        imgHeight: Int = 1920,
    ): SceneAdvice {
        // ── 美学分数（缩放到 0-100）──
        val aestheticScore: Float
        if (nimaScore != null) {
            // NIMA 原始分数 4.0-6.0 映射到 0-100
            aestheticScore = scaleNimaScore(nimaScore)
        } else {
            // 降级：规则打分（也映射到 0-100）
            aestheticScore = ruleBasedScore(objects) * 10f  // 1-10 → 10-100
        }

        // ── 方向引导 ──
        if (objects.isEmpty()) {
            val text = when {
                aestheticScore >= 75f -> "风景优美，构图不错"
                aestheticScore >= 40f -> "画面还行，移动寻找主体"
                else -> "移动手机，寻找有景物的方向"
            }
            return SceneAdvice(
                score = aestheticScore,
                angle = null,
                text = text,
            )
        }

        // 计算方向（加权中心）
        val (angle, bestObject, dx, dy) = calculateGuidance(objects)

        if (bestObject == null) {
            return SceneAdvice(
                score = aestheticScore,
                angle = null,
                text = buildScoreText(aestheticScore),
            )
        }

        // 中文引导文字
        val labelCn = SCORE_MAP_CN[bestObject.label] ?: bestObject.label
        val text = when {
            angle == null -> {
                val prefix = if (aestheticScore >= 75f) "构图精美" else "构图不错"
                "$prefix！聚焦「$labelCn」"
            }
            else -> buildString {
                if (abs(dx) > 0.15f) append(if (dx > 0) "← 向右" else "向左 →")
                if (abs(dy) > 0.15f) {
                    if (isNotEmpty()) append("、")
                    append(if (dy > 0) "向下" else "向上")
                }
                append(" — 发现「$labelCn」")
            }
        }

        return SceneAdvice(
            score = aestheticScore,
            angle = angle,
            text = text,
        )
    }

    /** 降级：基于 YOLO 目标类别的规则打分 */
    private fun ruleBasedScore(objects: List<DetectedObject>): Float {
        if (objects.isEmpty()) return 3.0f
        var score = 5.0f
        for (obj in objects) {
            score += (obj.confidence * ((SCORE_MAP[obj.label] ?: 0f)))
        }
        return score.coerceIn(1.0f, 10.0f)
    }

    /** 基于目标位置和权重计算方向引导 */
    private fun calculateGuidance(
        objects: List<DetectedObject>,
    ): Guidance {
        var cx = 0f; var cy = 0f; var tw = 0f
        var bestObj: DetectedObject? = null; var bestW = 0f

        for (obj in objects) {
            val weight = (SCORE_MAP[obj.label] ?: 0f).coerceAtLeast(0.1f)  // 至少给 0.1 权重
            if (obj.confidence < 0.3f) continue

            Log.d(TAG, "  目标: ${obj.label} conf=${"%.2f".format(obj.confidence)} weight=$weight bbox=[${obj.boundingBox.joinToString(",")}]")
            val bb = obj.boundingBox
            val w = weight * obj.confidence
            cx += (bb[0] + bb[2]) / 2f * w
            cy += (bb[1] + bb[3]) / 2f * w
            tw += w

            if (w > bestW) { bestW = w; bestObj = obj }
        }

        if (tw <= 0f) return Guidance(null, null, 0f, 0f)

        val dx = cx / tw - 0.5f
        val dy = cy / tw - 0.5f

        val angle = when {
            abs(dx) < 0.08f && abs(dy) < 0.08f -> null
            else -> Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        return Guidance(angle, bestObj, dx, dy)
    }

    /** 方向引导中间结果 */
    private data class Guidance(
        val angle: Float?,
        val bestObject: DetectedObject?,
        val dx: Float,
        val dy: Float,
    )

    private fun buildScoreText(score: Float): String = when {
        score >= 80f -> "非常美！"
        score >= 60f -> "画面不错"
        score >= 40f -> "还可以，试试换个角度"
        else -> "一般，换个方向试试"
    }

    /**
     * 将 NIMA 原始分数 (1-10，实际集中在 4.5-5.5) 线性映射到 0-100。
     *
     * 映射:  raw ≤ 4.5 → 0,  raw 4.5-5.5 → 0-100,  raw ≥ 5.5 → 100
     * 公式:  (raw - 4.5) * 100
     */
    fun scaleNimaScore(raw: Float): Float {
        return ((raw - 4.5f) * 100f).coerceIn(0f, 100f)
    }

    /** 每个 COCO 类别的分数贡献（正值 = 好场景，负值 = 干扰，0 = 中性） */
    private val SCORE_MAP = mapOf(
        // 🌟 高分风景要素
        "person" to 1.5f,
        "bird" to 1.5f,
        "cat" to 1.5f,
        "dog" to 1.5f,
        "horse" to 1.5f,
        "sheep" to 1.5f,
        "cow" to 1.5f,
        "elephant" to 1.5f,
        "bear" to 1.5f,
        "zebra" to 1.5f,
        "giraffe" to 1.5f,
        // 自然元素
        "potted plant" to 2.0f,
        "vase" to 1.5f,
        // 户外/运动（常出现在好风景中）
        "boat" to 1.5f,
        "surfboard" to 1.0f,
        "kite" to 1.5f,
        "frisbee" to 1.0f,
        "skis" to 1.0f,
        "snowboard" to 1.0f,
        "tennis racket" to 1.0f,
        "baseball bat" to 0.5f,
        "baseball glove" to 0.5f,
        // 交通工具（动态感）
        "bicycle" to 1.0f,
        "motorcycle" to 1.0f,
        "car" to 0.5f,
        "airplane" to 1.5f,
        "train" to 1.0f,
        "truck" to 0.5f,
        "bus" to 0.5f,
        // 🟡 中性
        "backpack" to 0f,
        "umbrella" to 0.5f,
        "handbag" to 0f,
        "tie" to 0f,
        "suitcase" to 0f,
        "bottle" to 0f,
        "wine glass" to 0f,
        "cup" to 0f,
        "fork" to 0f,
        "knife" to 0f,
        "spoon" to 0f,
        "bowl" to -0.5f,
        "banana" to 0f,
        "apple" to 0f,
        "sandwich" to 0f,
        "orange" to 0f,
        "broccoli" to -0.5f,
        "carrot" to -0.5f,
        "hot dog" to 0f,
        "pizza" to 0f,
        "donut" to 0f,
        "cake" to 1.0f,
        "book" to 0.5f,
        "clock" to 0.5f,
        "cell phone" to -0.5f,
        "laptop" to -0.5f,
        "tv" to -0.5f,
        // 🔴 干扰（减分）
        "chair" to -0.5f,
        "couch" to -0.5f,
        "bench" to 0f,
        "bed" to -0.5f,
        "dining table" to -0.5f,
        "toilet" to -2.0f,
        "sink" to -1.0f,
        "refrigerator" to -1.0f,
        "microwave" to -1.0f,
        "oven" to -1.0f,
        "toaster" to -1.0f,
        "mouse" to -1.0f,
        "remote" to -1.0f,
        "keyboard" to -1.0f,
        "scissors" to -1.0f,
        "teddy bear" to 0.5f,
        "hair drier" to -1.0f,
        "toothbrush" to -1.0f,
        "traffic light" to -1.0f,
        "fire hydrant" to -0.5f,
        "stop sign" to -1.5f,
        "parking meter" to -1.5f,
        "toy" to -1.0f,
    )

    /** 中文标签（方便 UI 显示） */
    private val SCORE_MAP_CN = mapOf(
        "person" to "人物",
        "bird" to "飞鸟",
        "cat" to "猫咪",
        "dog" to "狗狗",
        "horse" to "马",
        "sheep" to "羊",
        "cow" to "牛",
        "elephant" to "大象",
        "bear" to "熊",
        "zebra" to "斑马",
        "giraffe" to "长颈鹿",
        "potted plant" to "盆栽",
        "vase" to "花瓶",
        "boat" to "船",
        "surfboard" to "冲浪板",
        "kite" to "风筝",
        "frisbee" to "飞盘",
        "skis" to "滑雪板",
        "snowboard" to "雪板",
        "tennis racket" to "网球拍",
        "baseball bat" to "球棒",
        "baseball glove" to "手套",
        "bicycle" to "自行车",
        "motorcycle" to "摩托车",
        "car" to "汽车",
        "airplane" to "飞机",
        "train" to "火车",
        "truck" to "卡车",
        "bus" to "公交车",
        "cake" to "蛋糕",
        "book" to "书本",
        "clock" to "时钟",
        "umbrella" to "雨伞",
        "bench" to "长椅",
    )
}

/**
 * 引导建议：分数 + 方向 + 文字提示
 */
data class SceneAdvice(
    val score: Float,        // 0 - 100
    val angle: Float?,       // 引导箭头方向（度），null = 不需要移动
    val text: String,        // 引导文字
)
