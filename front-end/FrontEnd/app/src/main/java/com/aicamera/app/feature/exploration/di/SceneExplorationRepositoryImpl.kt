package com.aicamera.app.feature.exploration.di

import com.aicamera.app.core.camera.CameraFrame
import com.aicamera.app.core.data.repository.AngleAdvice
import com.aicamera.app.core.data.repository.SceneExplorationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SceneExplorationRepositoryImpl : SceneExplorationRepository {

    override fun observeBestAngle(frames: Flow<CameraFrame>): Flow<AngleAdvice> {
        return frames.map { frame ->
            AngleAdvice(
                deltaYaw = 0f,
                deltaPitch = 0f,
                confidence = 0.5f
            )
        }
    }

    override suspend fun startExplore() {
        // Mock implementation - no-op
    }

    override suspend fun stopExplore() {
        // Mock implementation - no-op
    }
}
