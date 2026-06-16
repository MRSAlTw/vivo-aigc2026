package com.aicamera.app.core.voice

import kotlinx.coroutines.flow.Flow

/**
 * Voice interaction controller interface
 * Encapsulates iFlytek ASR + TTS SDK, upper layer does not directly depend on iFlytek types
 */
interface VoiceController {

    /** ASR recognition result stream (text) */
    val asrResultFlow: Flow<String>

    /** TTS playback state */
    val ttsStateFlow: Flow<TtsState>

    /** Start ASR (in online or offline mode) */
    suspend fun startListening(mode: AsrMode)

    /** Stop ASR */
    suspend fun stopListening()

    /** Speak text */
    suspend fun speak(text: String)

    /** Release SDK resources */
    suspend fun release()
}

enum class AsrMode {
    /** Online recognition (high accuracy, requires internet) */
    ONLINE,
    /** Offline command word recognition (fixed instruction set, usable without internet) */
    OFFLINE_COMMAND,
}

sealed class TtsState {
    data object Idle : TtsState()
    data object Speaking : TtsState()
    data object Finished : TtsState()
    data class Error(val message: String) : TtsState()
}
