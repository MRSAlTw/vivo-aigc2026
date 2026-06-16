package com.aicamera.app.feature.voice.domain

/**
 * 鎰忓浘瑙ｆ瀽鍣ㄣ€? * 涓€鏈燂細绂荤嚎鍏抽敭璇嶅尮閰嶏紙鍛戒护璇?鈫?鍔ㄤ綔锛夈€? * 浜屾湡锛氭帴鍏ュ湪绾?LLM/NLU 澶勭悊澶嶆潅璇彞銆? */
interface IntentParser {
    fun parse(text: String): ParsedIntent?
}

data class ParsedIntent(
    val type: IntentType,
    val params: Map<String, String> = emptyMap(),
    val confidence: Float,
)

enum class IntentType {
    SHOOT,
    CHANGE_COMPOSITION,
    CHANGE_POSE,
    START_EXPLORE,
    STOP_EXPLORE,
    UNKNOWN,
}

