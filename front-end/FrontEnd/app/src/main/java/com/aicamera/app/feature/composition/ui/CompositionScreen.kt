package com.aicamera.app.feature.composition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.CompositionUiState
import com.aicamera.app.feature.composition.viewmodel.CompositionViewModel

/**
 * Composition (构图) tab — placeholder.
 *
 * TODO: Implement composition rule engine overlay with grid lines,
 *      subject detection, and movement guidance arrows.
 */
@Composable
fun CompositionRoute(
    viewModel: CompositionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    CompositionTab(state = state, onSelectMode = viewModel::selectMode)
}

@Composable
fun CompositionTab(
    state: CompositionUiState,
    onSelectMode: (CompositionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Composition mode selector
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableModes.forEach { mode ->
                val isActive = mode == state.currentMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isActive) Color(0xCCFFFFFF)
                            else Color(0x44000000)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.displayName,
                        color = if (isActive) Color.Black else Color(0xAAFFFFFF),
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Placeholder
        if (state.guidanceText != null) {
            Text(
                text = state.guidanceText ?: "",
                color = Color.White,
                fontSize = 16.sp,
            )
        } else {
            Text(
                text = "选择一种构图方式开始引导",
                color = Color(0xAAFFFFFF),
                fontSize = 14.sp,
            )
        }
    }
}
