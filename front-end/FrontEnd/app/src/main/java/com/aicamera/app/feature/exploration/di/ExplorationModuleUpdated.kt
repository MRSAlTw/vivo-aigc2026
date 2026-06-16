package com.aicamera.app.feature.exploration.di

import com.aicamera.app.core.data.repository.SceneExplorationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ExplorationModuleUpdated {

    @Provides
    fun provideSceneExplorationRepository(): SceneExplorationRepository {
        return SceneExplorationRepositoryImpl()
    }
}
