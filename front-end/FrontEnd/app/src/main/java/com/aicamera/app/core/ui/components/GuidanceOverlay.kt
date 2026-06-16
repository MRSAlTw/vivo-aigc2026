package com.aicamera.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import com.aicamera.app.core.ui.theme.GuidanceArrow
import com.aicamera.app.core.ui.theme.OverlayGrid
import kotlin.math.cos
import kotlin.math.sin

/**
 * Guidance frame overlay with grid lines and arrow guidance.
 * The feature controls display content through state, this Composable is only responsible for rendering.
 */
@Composable
fun GuidanceOverlay(
    showGrid: Boolean = false,
    arrowAngle: Float? = null,       // Guidance arrow angle (degrees)
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (showGrid) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            // Vertical thirds
            for (i in 1..2) {
                val x = size.width * i / 3
                drawLine(OverlayGrid, Offset(x, 0f), Offset(x, size.height), pathEffect = dash)
            }
            // Horizontal thirds
            for (i in 1..2) {
                val y = size.height * i / 3
                drawLine(OverlayGrid, Offset(0f, y), Offset(size.width, y), pathEffect = dash)
            }
        }
        // Guidance arrow (simplified as a circle + line pointing from center to direction)
        arrowAngle?.let { angle ->
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.15f
            val radians = Math.toRadians(angle.toDouble()).toFloat()
            val tip = Offset(
                center.x + radius * cos(radians),
                center.y + radius * sin(radians),
            )
            drawCircle(GuidanceArrow, 12f, tip)
            drawLine(GuidanceArrow, center, tip, strokeWidth = 4f)
        }
    }
}

