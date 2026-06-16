package com.aicamera.app.feature.exploration.state

/**
 * Exploration module one-time events.
 * Navigation events are handled by the tab switch in MainScreen.
 */
sealed interface ExplorationUiEvent {
    data class ShowToast(val message: String) : ExplorationUiEvent
    data class ExploreCompleted(val score: Float) : ExplorationUiEvent
    data object ExploreFailed : ExplorationUiEvent
}
