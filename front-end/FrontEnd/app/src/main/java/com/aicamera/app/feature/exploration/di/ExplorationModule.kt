package com.aicamera.app.feature.exploration.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Exploration module Hilt bindings
 * Maps SceneExplorer interface to concrete implementation
 */
@Module
@InstallIn(ViewModelComponent::class)
object ExplorationModule

