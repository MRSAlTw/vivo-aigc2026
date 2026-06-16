package com.aicamera.app.feature.voice.di

import com.aicamera.app.core.voice.AsrMode
import com.aicamera.app.core.voice.TtsState
import com.aicamera.app.core.voice.VoiceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceControllerImpl : VoiceController {

    private val _asrResultFlow = MutableStateFlow("")
    private val _ttsStateFlow = MutableStateFlow<TtsState>(TtsState.Idle)

    override val asrResultFlow: Flow<String> = _asrResultFlow.asStateFlow()
    override val ttsStateFlow: Flow<TtsState> = _ttsStateFlow.asStateFlow()

    override suspend fun startListening(mode: AsrMode) {
        // Mock implementation - simulate listening
        _asrResultFlow.value = "Mock voice input"
    }

    override suspend fun stopListening() {
        // Mock implementation - stop listening
    }

    override suspend fun speak(text: String) {
        // Mock implementation - simulate speaking
        _ttsStateFlow.value = TtsState.Speaking
        _ttsStateFlow.value = TtsState.Finished
    }

    override suspend fun release() {
        // Mock implementation - clean up resources
    }
}
