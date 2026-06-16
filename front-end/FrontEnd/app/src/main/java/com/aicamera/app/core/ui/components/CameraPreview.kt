package com.aicamera.app.core.ui.components

import android.view.ScaleGestureDetector
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.aicamera.app.core.camera.CameraController

/**
 * Shared camera preview composable.
 *
 * Renders a full-screen CameraX PreviewView and handles pinch-to-zoom.
 * This composable is placed at the bottom layer of MainScreen and persists across all tabs.
 *
 * @param cameraController  CameraX controller singleton
 * @param lifecycleOwner    LifecycleOwner for binding CameraX
 * @param currentZoomRatio  Current zoom ratio (from CameraViewModel state)
 * @param minZoomRatio      Minimum allowed zoom
 * @param maxZoomRatio      Maximum allowed zoom
 * @param onZoomChange      Called when user pinches to zoom
 * @param modifier          Compose modifier
 */
@Composable
fun CameraPreview(
    cameraController: CameraController,
    lifecycleOwner: LifecycleOwner,
    currentZoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared zoom base reference for pinch gesture
    val zoomBaseRef = remember { mutableFloatStateOf(1f) }
    val currentZoomRef = remember { mutableFloatStateOf(1f) }

    // Sync from external zoom changes (e.g., programmatic zoom, CameraViewModel updates)
    LaunchedEffect(currentZoomRatio) {
        currentZoomRef.floatValue = currentZoomRatio
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                cameraController.bindToLifecycle(this, lifecycleOwner)

                // Pinch-to-zoom gesture
                val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        zoomBaseRef.floatValue = currentZoomRef.floatValue
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        zoomBaseRef.floatValue *= detector.scaleFactor
                        val newZoom = zoomBaseRef.floatValue.coerceIn(
                            minZoomRatio.coerceAtMost(1.0f),
                            maxZoomRatio.coerceAtLeast(8.0f)
                        )
                        onZoomChange(newZoom)
                        return true
                    }
                }
                val scaleDetector = ScaleGestureDetector(ctx, scaleListener)
                setOnTouchListener { _, event ->
                    scaleDetector.onTouchEvent(event)
                    true // Consume event
                }
            }
        },
        modifier = modifier
    )
}
