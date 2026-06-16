package com.aicamera.app.core.data.repository

import com.aicamera.app.core.camera.CameraFrame
import kotlinx.coroutines.flow.Flow

/**
 * Scene exploration module Repository interface (inter-module contract)
 * Implementation should inject the AI engine in feature/exploration or this module
 */
interface SceneExplorationRepository {
    fun observeBestAngle(frames: Flow<CameraFrame>): Flow<AngleAdvice>
    suspend fun startExplore()
    suspend fun stopExplore()
}

data class AngleAdvice(
    val deltaYaw: Float,
    val deltaPitch: Float,
    val confidence: Float,
)
