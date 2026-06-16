package com.aicamera.app.feature.voice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.voice.VoiceController
import com.aicamera.app.feature.voice.state.VoiceUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val voiceController: VoiceController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    fun startListening() {
        viewModelScope.launch {
            voiceController.startListening(_uiState.value.asrMode)
            _uiState.value = _uiState.value.copy(isListening = true)
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            voiceController.stopListening()
            _uiState.value = _uiState.value.copy(isListening = false)
        }
    }

    fun speak(text: String) {
        viewModelScope.launch {
            voiceController.speak(text)
        }
    }

    fun toggleWakeWordMode() {
        _uiState.value = _uiState.value.copy(wakeWordMode = !_uiState.value.wakeWordMode)
    }
}

