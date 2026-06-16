package com.aicamera.app.feature.pose.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.data.model.PoseTemplate
import com.aicamera.app.core.data.repository.PoseTemplateRepository
import com.aicamera.app.feature.pose.state.PoseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoseViewModel @Inject constructor(
    private val poseRepo: PoseTemplateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PoseUiState())
    val uiState: StateFlow<PoseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            poseRepo.getAllTemplates().collect { templates ->
                _uiState.value = _uiState.value.copy(availableTemplates = templates)
            }
        }
    }

    fun selectTemplate(template: PoseTemplate) {
        _uiState.value = _uiState.value.copy(selectedTemplate = template)
        // TODO: Start MoveNet real-time comparison
    }
}
