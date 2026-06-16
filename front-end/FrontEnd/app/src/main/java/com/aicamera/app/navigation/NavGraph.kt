package com.aicamera.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicamera.app.feature.exploration.ui.ExplorationRoute

/**
 * 全局导航图�? * �?feature module 提供 Composable route，在此统一注册�? * 一期仅 ExplorationRoute（取景预�?+ 寻景入口），后续 feature 逐步接入�? */
object Routes {
    const val MAIN_CAMERA = "main_camera"
    const val COMPOSITION = "composition"
    const val POSE = "pose"
    const val VOICE_SETTINGS = "voice_settings"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_CAMERA,
    ) {
        composable(Routes.MAIN_CAMERA) {
            ExplorationRoute(
                onNavigateToComposition = { navController.navigate(Routes.COMPOSITION) },
                onNavigateToPose = { navController.navigate(Routes.POSE) },
            )
        }
    }
}

