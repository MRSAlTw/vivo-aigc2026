package com.aicamera.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Global navigation graph.
 *
 * Single-activity architecture: the entire app is the main camera screen
 * with bottom tab navigation. Future screens (settings, gallery, etc.)
 * can be added as additional routes here.
 */
object Routes {
    const val MAIN_CAMERA = "main_camera"
    // Future: const val GALLERY = "gallery"
    // Future: const val SETTINGS = "settings"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_CAMERA,
    ) {
        composable(Routes.MAIN_CAMERA) {
            MainScreen()
        }
    }
}
