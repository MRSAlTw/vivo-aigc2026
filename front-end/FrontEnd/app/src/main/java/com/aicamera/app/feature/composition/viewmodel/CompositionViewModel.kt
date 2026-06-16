package com.aicamera.app.feature.composition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.CompositionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompositionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CompositionUiState())
    val uiState: StateFlow<CompositionUiState> = _uiState.asStateFlow()

    fun selectMode(mode: CompositionMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }

    fun analyzeComposition() {
        viewModelScope.launch {
            // TODO: Call YOLO detection -> Geometric rules scoring -> Update guidance
        }
    }
}
