package com.aicamera.app.feature.pose.di

import com.aicamera.app.core.data.repository.PoseTemplateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object PoseModule {

    @Provides
    fun providePoseTemplateRepository(): PoseTemplateRepository {
        return PoseTemplateRepositoryImpl()
    }
}
