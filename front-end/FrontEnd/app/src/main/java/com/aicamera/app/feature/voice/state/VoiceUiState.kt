package com.aicamera.app.feature.voice.state

import com.aicamera.app.core.voice.AsrMode

/**
 * 语音交互页面 UI 状态�? */
data class VoiceUiState(
    val isListening: Boolean = false,
    val asrMode: AsrMode = AsrMode.OFFLINE_COMMAND,
    val recognizedText: String = "",
    val lastCommand: String? = null,
    val ttsEnabled: Boolean = true,
    val wakeWordMode: Boolean = false,   // true=唤醒�? false=按住说话
    val isSpeaking: Boolean = false,
)

