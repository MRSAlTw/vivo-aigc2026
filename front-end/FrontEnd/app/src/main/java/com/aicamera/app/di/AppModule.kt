package com.aicamera.app.di

import android.content.Context
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.core.camera.CameraControllerImpl
import com.aicamera.app.core.camera.PhotoSaver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用级 Hilt 模块。
 * 全局单例在此提供，各 feature Repository 的绑定在各 feature/di 中声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCameraController(
        @ApplicationContext context: Context
    ): CameraController {
        return CameraControllerImpl(context)
    }

    @Provides
    @Singleton
    fun providePhotoSaver(
        @ApplicationContext context: Context
    ): PhotoSaver {
        return PhotoSaver(context)
    }
}