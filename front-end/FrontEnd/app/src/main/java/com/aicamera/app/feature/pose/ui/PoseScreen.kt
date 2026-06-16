package com.aicamera.app.feature.pose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicamera.app.feature.pose.state.PoseUiState
import com.aicamera.app.feature.pose.viewmodel.PoseViewModel

/**
 * Pose (姿势) tab — placeholder.
 *
 * TODO: Implement pose template selector, real-time skeleton overlay,
 *      and angle deviation feedback.
 */
@Composable
fun PoseRoute(
    viewModel: PoseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    PoseTab(state = state)
}

@Composable
fun PoseTab(
    state: PoseUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.selectedTemplate != null) {
            Text(
                text = "当前姿势: ${state.selectedTemplate.name}",
                color = Color.White,
                fontSize = 16.sp,
            )
        } else {
            Text(
                text = "选择一个姿势模板开始实时对比",
                color = Color(0xAAFFFFFF),
                fontSize = 14.sp,
            )
        }

        // TODO: Show pose skeleton overlay
        // TODO: Show angle deviation list
    }
}
