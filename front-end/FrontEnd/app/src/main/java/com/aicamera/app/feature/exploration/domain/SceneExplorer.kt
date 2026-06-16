package com.aicamera.app.feature.exploration.domain

import com.aicamera.app.core.camera.CameraFrame
import com.aicamera.app.core.data.repository.AngleAdvice
import kotlinx.coroutines.flow.Flow

/**
 * Scene exploration domain logic (UseCase)
 * Receives a stream of CameraFrames and outputs angle recommendations
 */
interface SceneExplorer {
    fun observeAngleAdvice(frames: Flow<CameraFrame>): Flow<AngleAdvice>
}

