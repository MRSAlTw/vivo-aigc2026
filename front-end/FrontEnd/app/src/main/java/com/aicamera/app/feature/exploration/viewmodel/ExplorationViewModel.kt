package com.aicamera.app.feature.exploration.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.feature.exploration.state.ExplorationUiEvent
import com.aicamera.app.feature.exploration.state.ExplorationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the 寻景 (Scene Exploration) tab.
 *
 * Manages exploration lifecycle: start/stop explore, ultrawide switching,
 * guidance data. Camera controls (shutter, zoom, flip) are handled by
 * CameraViewModel in the camera module.
 */
@HiltViewModel
class ExplorationViewModel @Inject constructor(
    private val cameraController: CameraController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorationUiState())
    val uiState: StateFlow<ExplorationUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExplorationUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // 超广角检测：基于变焦范围（minZoomRatio < 1.0 即有超广角）
        // 需要等 CameraX 绑定完成后 minZoomRatio 才有值
        // hasUltraWideSupported 是一个属性，会随相机状态更新
        updateUltraWideState()
    }

    /** 检查是否支持超广角（基于 minZoomRatio < 1.0x） */
    private fun updateUltraWideState() {
        val supported = cameraController.hasUltraWideSupported
        _uiState.value = _uiState.value.copy(hasUltraWide = supported)
        if (supported) {
            Log.d("ExplorationVM", "✅ 支持超广角（minZoomRatio=${cameraController.minZoomRatio}x）")
        } else {
            Log.d("ExplorationVM", "❌ 不支持超广角（minZoomRatio=${cameraController.minZoomRatio}x）")
        }
    }

    // ────────── 寻景控制 ──────────

    /**
     * Start scene exploration:
     * 1. Refresh ultra-wide state (camera may have just bound)
     * 2. Switch to ultrawide via zoom-to-min (if minZoom < 1.0x)
     * 3. Begin analyzing frames for best angle
     */
    fun onStartExplore() {
        viewModelScope.launch {
            // 重新检测超广角（此时 CameraX 已经绑定，minZoomRatio 有正确值）
            updateUltraWideState()
            val hasUW = _uiState.value.hasUltraWide

            _uiState.value = _uiState.value.copy(isExploring = true, isLoading = true)

            // 通过变焦到最小倍率来启用超广角（硬件自动切换传感器）
            if (hasUW) {
                Log.d("ExplorationVM", "▶ 启用超广角：zoom → ${cameraController.minZoomRatio}x")
                cameraController.switchToUltraWide(true)
                _uiState.value = _uiState.value.copy(isUltraWideActive = true)
            } else {
                Log.d("ExplorationVM", "设备不支持超广角，使用主摄扫描")
            }

            // TODO: 接入 YOLOv8n + NIMA 进行场景评分和角度推荐
            simulateExploration()

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * Stop scene exploration and return to main camera.
     */
    fun onStopExplore() {
        viewModelScope.launch {
            // 切回主摄
            if (_uiState.value.isUltraWideActive) {
                cameraController.switchToUltraWide(false)
                _uiState.value = _uiState.value.copy(isUltraWideActive = false)
            }

            _uiState.value = _uiState.value.copy(
                isExploring = false,
                sceneScore = null,
                guidanceAngle = null,
                guidanceText = null,
            )
        }
    }

    private suspend fun simulateExploration() {
        // Placeholder: simulate finding angles
        // Real implementation will:
        // 1. Collect frames via cameraController.frameFlow
        // 2. Run YOLOv8n for scene object detection
        // 3. Score regions via NIMA-MobileNet
        // 4. Output best AngleAdvice (deltaYaw, deltaPitch)
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
