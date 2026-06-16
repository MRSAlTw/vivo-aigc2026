package com.aicamera.app.feature.exploration.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicamera.app.core.ui.components.GuidanceOverlay
import com.aicamera.app.feature.exploration.state.ExplorationUiEvent
import com.aicamera.app.feature.exploration.state.ExplorationUiState
import com.aicamera.app.feature.exploration.viewmodel.ExplorationViewModel

/**
 * Exploration (寻景) tab content.
 *
 * Renders the guidance overlay (grid + arrow) when exploring,
 * and start/stop controls. Camera preview is handled by MainScreen's
 * shared CameraPreview.
 */
@Composable
fun ExplorationRoute(
    viewModel: ExplorationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // One-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorationUiEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ExplorationUiEvent.ExploreCompleted -> { }
                is ExplorationUiEvent.ExploreFailed -> { }
            }
        }
    }

    ExplorationTab(
        state = state,
        onStartExplore = viewModel::onStartExplore,
        onStopExplore = viewModel::onStopExplore,
    )
}

/**
 * Pure exploration UI — no camera controls, no mode selector.
 *
 * Lays out on top of the shared CameraPreview in MainScreen.
 */
@Composable
fun ExplorationTab(
    state: ExplorationUiState,
    onStartExplore: () -> Unit,
    onStopExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {

        // ── Guidance overlay (grid + directional arrow) ──
        AnimatedVisibility(
            visible = state.isExploring,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GuidanceOverlay(
                showGrid = true,
                arrowAngle = state.guidanceAngle,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Scene score badge ──
        AnimatedVisibility(visible = state.isExploring && state.sceneScore != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "场景评分: ${String.format("%.1f", state.sceneScore ?: 0f)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ── Guidance text ──
        AnimatedVisibility(visible = state.isExploring && state.guidanceText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = state.guidanceText ?: "",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ── Explore controls ──
        ExploreControls(
            isExploring = state.isExploring,
            isLoading = state.isLoading,
            hasUltraWide = state.hasUltraWide,
            onStartExplore = onStartExplore,
            onStopExplore = onStopExplore,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

/**
 * Explore-specific controls (start/stop buttons, status).
 * Positioned above the global shutter controls.
 */
@Composable
private fun ExploreControls(
    isExploring: Boolean,
    isLoading: Boolean,
    hasUltraWide: Boolean,
    onStartExplore: () -> Unit,
    onStopExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ultra-wide indicator
        if (isExploring && hasUltraWide) {
            Text(
                text = "🔭 超广角已启用",
                color = Color(0xFF4FC3F7),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (isExploring) {
            // ── Stop explore button ──
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCCFF5252))
                    .clickable(enabled = !isLoading, onClick = onStopExplore)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isLoading) "寻景中..." else "停止寻景",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            // ── Start explore button ──
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC2196F3))
                    .clickable(onClick = onStartExplore)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔍", fontSize = 16.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "开始寻景",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Ultra-wide availability hint
            if (!hasUltraWide) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "设备不支持超广角，将使用主摄扫描",
                    color = Color(0xAAFFFFFF),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
