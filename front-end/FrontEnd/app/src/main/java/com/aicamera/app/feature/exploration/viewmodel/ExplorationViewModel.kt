package com.aicamera.app.feature.exploration.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.ai.AiEngine
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.feature.exploration.domain.SceneScoreEngine
import com.aicamera.app.feature.exploration.state.ExplorationUiEvent
import com.aicamera.app.feature.exploration.state.ExplorationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "AICamera.ExplorationVM"

/**
 * ViewModel for the 寻景 (Scene Exploration) tab.
 *
 * 寻景管线:
 *   CameraX 帧流 → conflate(防堆积) → 每 N 帧采样 → Dispatchers.Default 并行推理 → UI 更新
 *
 * 性能关键点:
 *   - TFLite 推理在 Dispatchers.Default 执行，不阻塞主线程
 *   - YOLOv8n 和 NIMA 并行推理（使用独立 TFLite Interpreter）
 *   - conflate() 确保推理慢时不堆积帧
 */
@HiltViewModel
class ExplorationViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val aiEngine: AiEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorationUiState())
    val uiState: StateFlow<ExplorationUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExplorationUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 当前寻景协程 Job，用于停止 */
    private var explorationJob: Job? = null

    init {
        updateUltraWideState()
    }

    /** 检查超广角支持 */
    private fun updateUltraWideState() {
        _uiState.update {
            it.copy(hasUltraWide = cameraController.hasUltraWideSupported)
        }
    }

    // ────────── 寻景控制 ──────────

    /**
     * 开始寻景：
     *  1. 检测超广角
     *  2. 切超广角
     *  3. 订阅帧流，降低采样 + 后台并行推理 + 更新 UI
     */
    fun onStartExplore() {
        Log.d(TAG, ">>> onStartExplore called, job active=${explorationJob?.isActive}")
        if (explorationJob?.isActive == true) return  // 已在寻景中

        explorationJob = viewModelScope.launch {
            Log.d(TAG, ">>> 协程已启动")
            updateUltraWideState()
            val hasUW = _uiState.value.hasUltraWide

            _uiState.update { it.copy(isExploring = true, isLoading = true) }
            Log.d(TAG, ">>> isExploring=true, isLoading=true 已设置")

            // 切超广角
            if (hasUW) {
                cameraController.switchToUltraWide(true)
                _uiState.update { it.copy(isUltraWideActive = true) }
            }

            Log.d(TAG, "▶ 开始寻景${if (hasUW) "（超广角）" else "（主摄）"}")

            // 核心管线：
            //   conflate → 帧堆积时只保留最新
            //   filter   → 每 N 帧处理一次（降采样）
            //   collect  → withContext(Default) 跑推理，不阻塞 Main
            cameraController.frameFlow
                .conflate()                        // 推理慢时丢弃中间帧
                .withIndex()                       // 带序号
                .filter { (index, _) ->
                    // 每 10 帧处理一次（~30fps ≈ 每秒最多 3 次推理请求）
                    index % 10 == 0
                }
                .collect { (_, frame) ->
                    // 在后台线程执行 YOLO + NIMA 并行推理，不阻塞预览
                    val advice = withContext(Dispatchers.Default) {
                        runInference(frame)
                    }

                    // 回到 Main 线程更新 UI
                    _uiState.update {
                        it.copy(
                            sceneScore = advice.score,
                            guidanceAngle = advice.angle,
                            guidanceText = advice.text,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * 在后台线程并行执行 YOLO 目标检测 + NIMA 美学评分。
     *
     * YOLO 和 NIMA 使用独立的 TFLite Interpreter，线程安全可并行。
     */
    private suspend fun runInference(frame: com.aicamera.app.core.camera.CameraFrame) = coroutineScope {
        val yoloDeferred = async { aiEngine.detectObjects(frame) }
        val nimaDeferred = async { aiEngine.assessAesthetics(frame) }

        val result = yoloDeferred.await()
        val nimaScore = nimaDeferred.await()

        if (result.objects.isEmpty()) {
            Log.v(TAG, "当前帧未检测到目标，NIMA 评分: $nimaScore")
        }

        SceneScoreEngine.evaluate(
            objects = result.objects,
            nimaScore = nimaScore,
            imgWidth = frame.bitmap.width,
            imgHeight = frame.bitmap.height,
        )
    }

    /**
     * 停止寻景：
     *  1. 取消推理协程
     *  2. 切回主摄 1.0x
     *  3. 重置状态
     */
    fun onStopExplore() {
        explorationJob?.cancel()
        explorationJob = null

        viewModelScope.launch {
            // 切回主摄
            if (_uiState.value.isUltraWideActive) {
                cameraController.switchToUltraWide(false)
            }

            _uiState.update {
                ExplorationUiState()  // 全部重置
            }

            Log.d(TAG, "■ 停止寻景")
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * MutableStateFlow.update 扩展（避免样板代码）
 */
private fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
