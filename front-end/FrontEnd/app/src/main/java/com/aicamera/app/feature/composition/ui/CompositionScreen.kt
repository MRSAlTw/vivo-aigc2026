package com.aicamera.app.feature.composition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.CompositionUiState
import com.aicamera.app.feature.composition.viewmodel.CompositionViewModel

/**
 * Composition (构图) tab。
 *
 * 接入 CompositionOverlay（辅助线 + 动态引导）+ CompositionSelector（模式选择器）。
 * 符合需求 F-CG-01~F-CG-03。
 */
@Composable
fun CompositionRoute(
    viewModel: CompositionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    CompositionScreen(
        state = state,
        onSelectMode = { mode ->
            viewModel.onCompositionChange(mode)
            viewModel.selectMode(mode)
        },
    )
}

@Composable
fun CompositionScreen(
    state: CompositionUiState,
    onSelectMode: (CompositionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 全屏构图叠加层（辅助线 + 检测框 + 引导箭头 + 文字提示）
        CompositionOverlay(
            mode = state.currentMode,
            faceGuide = state.faceGuide,
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部：构图方式选择器
        CompositionSelector(
            currentMode = state.currentMode,
            recommendedMode = state.recommendedMode,
            onModeSelected = onSelectMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
        )
    }
}
