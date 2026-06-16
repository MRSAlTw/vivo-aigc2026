package com.aicamera.app.core.data.model

import kotlinx.serialization.Serializable

/**
 * Pose template model
 * Stores normalized joint point coordinates
 * Source: Extracted from reference photos through BlazePose with 33 points, stored offline
 */
@Serializable
data class PoseTemplate(
    val id: Long = 0,
    val name: String,
    val category: PoseCategory,
    val thumbnailPath: String,         // High-fidelity thumbnail path
    val keypoints: Map<String, FloatArray>, // Joint points [x, y] normalized 0-1
)

@Serializable
enum class PoseCategory {
    OUTDOOR,
    INDOOR,
    TRAVEL,
    STREET_SNAP,
    PORTRAIT,
}
