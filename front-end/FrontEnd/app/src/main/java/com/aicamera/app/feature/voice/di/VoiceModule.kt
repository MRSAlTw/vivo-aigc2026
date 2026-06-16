package com.aicamera.app.feature.voice.di

import com.aicamera.app.core.voice.VoiceController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object VoiceModule {

    @Provides
    fun provideVoiceController(): VoiceController {
        return VoiceControllerImpl()
    }
}
