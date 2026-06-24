package com.aicamera.app.feature.composition.viewmodel

import android.content.Context
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.core.camera.CameraController
import com.aicamera.app.feature.composition.domain.CompositionRuleEngineImpl
import com.aicamera.app.feature.composition.domain.FaceDetectionAnalyzer
import com.aicamera.app.feature.composition.domain.YoloSubjectDetector
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.CompositionUiEvent
import com.aicamera.app.feature.composition.state.CompositionUiState
import com.aicamera.app.feature.composition.state.FaceGuideData
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import javax.inject.Inject

/** EMA 平滑因子：0.20 = 20% 新值权重，更强的平滑以消除 YOLO/ML Kit 逐帧检测框抖动 */
private const val EMA_ALPHA = 0.20f

/**
 * 构图模块 ViewModel（Hilt 注入）。
 * 接收 YOLO+ML Kit 双检测结果 → 择优定位主体 → 调用 RuleEngine 生成引导。
 *
 * 扩展说明：
 * - 保留学姐的 selectMode / analyzeComposition 骨架方法
 * - 新增 setActive / onCompositionChange / onSubjectsDetected 等完整逻辑
 * - 引入 CompositionRuleEngineImpl 做几何规则解耦
 * - setActive 时自动挂载 FaceDetectionAnalyzer 到 CameraX 管线
 */
@HiltViewModel
class CompositionViewModel @Inject constructor(
    private val cameraController: CameraController,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompositionUiState())
    val uiState: StateFlow<CompositionUiState> = _uiState.asStateFlow()

    private val _events = Channel<CompositionUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 构图规则引擎 */
    private val ruleEngine = CompositionRuleEngineImpl()

    /** 当前挂载的分析器（null 表示未激活） */
    private var activeAnalyzer: FaceDetectionAnalyzer? = null

    /** YOLO 检测器（延迟创建，避免首次启动加载模型） */
    private var yoloDetector: YoloSubjectDetector? = null

    /** EMA 平滑后的主体中心坐标（归一化），消除 YOLO/ML Kit 逐帧检测框抖动 */
    private var emaCenterX = 0.5f
    private var emaCenterY = 0.5f
    private var emaInitialized = false

    /** EMA 平滑后的检测框尺寸（归一化），消除框边界抖动 */
    private var emaFaceW = 0f
    private var emaFaceH = 0f
    private var emaBodyW = 0f
    private var emaBodyH = 0f

    // ═══════════════════════════════════════════
    // 学姐原有 API（保留）
    // ═══════════════════════════════════════════

    fun selectMode(mode: CompositionMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }

    fun analyzeComposition() {
        viewModelScope.launch {
            // 由 FaceDetectionAnalyzer 驱动实时分析，此方法保留用于外部触发
        }
    }

    // ═══════════════════════════════════════════
    // 3.2 新增 API
    // ═══════════════════════════════════════════

    fun setActive(active: Boolean) {
        if (active) {
            // 进入构图模式时重置 EMA，避免旧值影响
            emaInitialized = false
            emaFaceW = 0f; emaFaceH = 0f
            emaBodyW = 0f; emaBodyH = 0f

            // 挂载 FaceDetectionAnalyzer 到 CameraX 管线
            attachAnalyzer()
        } else {
            // 从相机管线卸载分析器
            detachAnalyzer()
        }
        _uiState.update { it.copy(isActive = active) }
    }

    fun onCompositionChange(mode: CompositionMode) {
        _uiState.update { state ->
            val guide = state.faceGuide
            if (guide != null && (guide.hasFace || guide.hasBody)) {
                val sc = computeSubjectCenter(guide)
                state.copy(
                    currentMode = mode,
                    faceGuide = computeFaceGuide(
                        sc.x, sc.y,
                        guide.faceWidth, guide.faceHeight,
                        guide.hasBody, guide.bodyWidth, guide.bodyHeight,
                        guide.hasFace, mode,
                    ),
                )
            } else state.copy(currentMode = mode)
        }
    }

    fun sendEvent(event: CompositionUiEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    // ═══════════════════════════════════════════
    // 分析器生命周期
    // ═══════════════════════════════════════════

    private fun attachAnalyzer() {
        if (activeAnalyzer != null) return

        if (yoloDetector == null) {
            yoloDetector = YoloSubjectDetector(appContext)
        }

        activeAnalyzer = FaceDetectionAnalyzer(
            yoloDetector = yoloDetector!!,
            onSubjectsDetected = this::onSubjectsDetected,
            analysisScope = viewModelScope,
        )
        cameraController.setAnalyzer(activeAnalyzer)
    }

    private fun detachAnalyzer() {
        cameraController.setAnalyzer(null)
        activeAnalyzer = null
    }

    // ═══════════════════════════════════════════
    // 检测回调（由 FaceDetectionAnalyzer 调用）
    // ═══════════════════════════════════════════

    fun onSubjectsDetected(
        faces: List<Face>,
        personBoxes: List<YoloSubjectDetector.Detection>,
        frameWidth: Int, frameHeight: Int,
    ) {
        if (!_uiState.value.isActive) return

        val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        val mainPerson = personBoxes.maxByOrNull { it.confidence }

        val guide = if (mainFace != null || mainPerson != null) {
            // 提取人脸数据
            val fcx: Float; val fcy: Float; val fw: Float; val fh: Float
            if (mainFace != null) {
                val c = FaceDetectionAnalyzer.faceCenterNormalized(mainFace, frameWidth, frameHeight)
                val s = FaceDetectionAnalyzer.faceSizeNormalized(mainFace, frameWidth, frameHeight)
                fcx = c.x; fcy = c.y; fw = s.first; fh = s.second
            } else { fcx = 0.5f; fcy = 0.5f; fw = 0f; fh = 0f }

            // 提取人体数据
            val bcx: Float; val bcy: Float; val bw: Float; val bh: Float; val hasB: Boolean
            if (mainPerson != null) {
                val b = mainPerson.boundingBox
                bcx = b.centerX(); bcy = b.centerY(); bw = b.width(); bh = b.height()
                hasB = true
            } else { bcx = 0.5f; bcy = 0.5f; bw = 0f; bh = 0f; hasB = false }

            // ── 原始主体中心：人体 > 人脸 > 默认 ──
            val rawSc = if (hasB) PointF(bcx, bcy) else PointF(fcx, fcy)

            // ── EMA 平滑：消除 YOLO/ML Kit 逐帧检测框抖动 ──
            if (!emaInitialized) {
                emaCenterX = rawSc.x
                emaCenterY = rawSc.y
                emaFaceW = fw; emaFaceH = fh
                emaBodyW = bw; emaBodyH = bh
                emaInitialized = true
            } else {
                emaCenterX = EMA_ALPHA * rawSc.x + (1f - EMA_ALPHA) * emaCenterX
                emaCenterY = EMA_ALPHA * rawSc.y + (1f - EMA_ALPHA) * emaCenterY
                emaFaceW = EMA_ALPHA * fw + (1f - EMA_ALPHA) * emaFaceW
                emaFaceH = EMA_ALPHA * fh + (1f - EMA_ALPHA) * emaFaceH
                emaBodyW = EMA_ALPHA * bw + (1f - EMA_ALPHA) * emaBodyW
                emaBodyH = EMA_ALPHA * bh + (1f - EMA_ALPHA) * emaBodyH
            }

            computeFaceGuide(
                emaCenterX, emaCenterY,
                emaFaceW, emaFaceH,
                hasB, emaBodyW, emaBodyH,
                mainFace != null,
                _uiState.value.currentMode,
            )
        } else {
            FaceGuideData(hasFace = false, directionText = "未检测到人物，请对准拍摄对象")
        }

        // 自动推荐：使用平滑后的位置
        val recommendation = if (mainPerson != null || mainFace != null) {
            ruleEngine.autoRecommend(emaCenterX, emaCenterY)
        } else null

        _uiState.update { it.copy(faceGuide = guide, recommendedMode = recommendation) }
    }

    // ═══════════════════════════════════════════
    // 主体中心：YOLO人体 > 人脸 > 画面中心
    // ═══════════════════════════════════════════

    private fun computeSubjectCenter(guide: FaceGuideData): PointF {
        return if (guide.hasBody) PointF(guide.bodyCenterX, guide.bodyCenterY)
        else PointF(guide.faceCenterX, guide.faceCenterY)
    }

    // ═══════════════════════════════════════════
    // 引导计算（调用 RuleEngine）
    // ═══════════════════════════════════════════

    private fun computeFaceGuide(
        subCx: Float, subCy: Float,
        faceW: Float, faceH: Float,
        hasBody: Boolean, bodyW: Float, bodyH: Float,
        hasFace: Boolean,
        mode: CompositionMode,
    ): FaceGuideData {
        val target = ruleEngine.idealFacePosition(subCx, subCy, mode)
        val dx = target.x - subCx
        val dy = target.y - subCy
        val distance = sqrt(dx * dx + dy * dy).toFloat()
        val aligned = distance < 0.055f

        // 尺寸检查仅在有对应检测时才生效
        val effectiveW = if (hasFace) faceW else bodyW
        val effectiveH = if (hasFace) faceH else bodyH
        val directionText = ruleEngine.buildDirectionText(dx, dy, effectiveW, effectiveH, aligned)

        return FaceGuideData(
            faceCenterX = if (hasFace) subCx else 0.5f,
            faceCenterY = if (hasFace) subCy else 0.5f,
            faceWidth = faceW,
            faceHeight = faceH,
            bodyCenterX = if (hasBody) subCx else 0.5f,
            bodyCenterY = if (hasBody) subCy else 0.5f,
            bodyWidth = bodyW,
            bodyHeight = bodyH,
            hasBody = hasBody,
            targetX = target.x,
            targetY = target.y,
            directionText = directionText,
            isAligned = aligned,
            hasFace = hasFace,
        )
    }

    override fun onCleared() {
        super.onCleared()
        detachAnalyzer()
        yoloDetector?.close()
    }
}
