package com.aicamera.app.feature.exploration.state

/**
 * Exploration module one-time events (Toast / Navigation, etc., not stored in UiState)
 */
sealed interface ExplorationUiEvent {
    data class ShowToast(val message: String) : ExplorationUiEvent
    data object ShutterTriggered : ExplorationUiEvent
    data class NavigateToComposition(val mode: String) : ExplorationUiEvent
    data class NavigateToPose(val templateId: Long) : ExplorationUiEvent
}

