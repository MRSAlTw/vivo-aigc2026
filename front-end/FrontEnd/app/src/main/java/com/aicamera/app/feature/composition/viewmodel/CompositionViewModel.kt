package com.aicamera.app.feature.composition.viewmodel

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.app.feature.composition.domain.FaceDetectionAnalyzer
import com.aicamera.app.feature.composition.domain.YoloSubjectDetector
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.CompositionUiEvent
import com.aicamera.app.feature.composition.state.CompositionUiState
import com.aicamera.app.feature.composition.state.FaceGuideData
import com.google.mlkit.vision.face.Face
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import javax.inject.Inject

private const val ALIGN_THRESHOLD = 0.055f
private const val FACE_TOO_SMALL_RATIO = 0.008f
private const val FACE_TOO_LARGE_RATIO = 0.35f

/** EMA 平滑因子：0.20 = 20% 新值权重，更强的平滑以消除 YOLO/ML Kit 逐帧检测框抖动 */
private const val EMA_ALPHA = 0.20f

/**
 * 构图模块 ViewModel（Hilt 注入）。
 * 接收 YOLO+ML Kit 双检测结果 → 择优定位主体 → 生成引导。
 */
@HiltViewModel
class CompositionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CompositionUiState())
    val uiState: StateFlow<CompositionUiState> = _uiState.asStateFlow()

    private val _events = Channel<CompositionUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** EMA 平滑后的主体中心坐标（归一化），消除 YOLO/ML Kit 逐帧检测框抖动 */
    private var emaCenterX = 0.5f
    private var emaCenterY = 0.5f
    private var emaInitialized = false

    /** EMA 平滑后的检测框尺寸（归一化），消除框边界抖动 */
    private var emaFaceW = 0f
    private var emaFaceH = 0f
    private var emaBodyW = 0f
    private var emaBodyH = 0f

    fun setActive(active: Boolean) {
        if (active) {
            // 进入构图模式时重置 EMA，避免旧值影响
            emaInitialized = false
            emaFaceW = 0f; emaFaceH = 0f
            emaBodyW = 0f; emaBodyH = 0f
        }
        _uiState.update { it.copy(isActive = active) }
    }

    fun onCompositionChange(mode: CompositionMode) {
        _uiState.update { state ->
            val guide = state.faceGuide
            if (guide != null && (guide.hasFace || guide.hasBody)) {
                val sc = computeSubjectCenter(guide)
                state.copy(currentMode = mode,
                    faceGuide = computeFaceGuide(sc.x, sc.y, guide.faceWidth, guide.faceHeight,
                        guide.hasBody, guide.bodyWidth, guide.bodyHeight, guide.hasFace, mode))
            } else state.copy(currentMode = mode)
        }
    }

    fun sendEvent(event: CompositionUiEvent) {
        viewModelScope.launch { _events.send(event) }
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
                bcx = b.centerX(); bcy = b.centerY(); bw = b.width(); bh = b.height(); hasB = true
            } else { bcx = 0.5f; bcy = 0.5f; bw = 0f; bh = 0f; hasB = false }

            // ── 原始主体中心 ──
            val rawSc = computeSubjectCenterFromFields(fcx, fcy, hasB, bcx, bcy)

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
            val sc = PointF(emaCenterX, emaCenterY)

            computeFaceGuide(sc.x, sc.y, emaFaceW, emaFaceH, hasB, emaBodyW, emaBodyH, mainFace != null, _uiState.value.currentMode)
        } else {
            FaceGuideData(hasFace = false, directionText = "未检测到人物，请对准拍摄对象")
        }

        // 自动推荐：同样使用平滑后的位置
        val recommendation = if (mainPerson != null || mainFace != null) {
            autoRecommend(emaCenterX, emaCenterY)
        } else null

        _uiState.update { it.copy(faceGuide = guide, recommendedMode = recommendation) }
    }

    // ═══════════════════════════════════════════
    // 主体中心：YOLO人体 > 人脸 > 画面中心
    // ═══════════════════════════════════════════

    private fun computeSubjectCenterFromFields(
        fcX: Float, fcY: Float,
        hasB: Boolean, bcX: Float, bcY: Float,
    ): PointF {
        return if (hasB) PointF(bcX, bcY) else PointF(fcX, fcY)
    }

    private fun computeSubjectCenter(guide: FaceGuideData): PointF {
        return if (guide.hasBody) PointF(guide.bodyCenterX, guide.bodyCenterY)
        else PointF(guide.faceCenterX, guide.faceCenterY)
    }

    // ═══════════════════════════════════════════
    // 引导计算
    // ═══════════════════════════════════════════

    private fun computeFaceGuide(
        subCx: Float, subCy: Float,
        faceW: Float, faceH: Float,
        hasBody: Boolean, bodyW: Float, bodyH: Float,
        hasFace: Boolean,
        mode: CompositionMode,
    ): FaceGuideData {
        val target = idealFacePosition(subCx, subCy, mode)
        val dx = target.x - subCx
        val dy = target.y - subCy
        val distance = sqrt(dx * dx + dy * dy).toFloat()
        val aligned = distance < ALIGN_THRESHOLD

        // 尺寸检查仅在有对应检测时才生效
        val effectiveW = if (hasFace) faceW else bodyW
        val effectiveH = if (hasFace) faceH else bodyH
        val directionText = buildDirectionText(dx, dy, effectiveW, effectiveH, aligned)

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

    private fun idealFacePosition(currentX: Float, currentY: Float, mode: CompositionMode): PointF {
        return when (mode) {
            CompositionMode.RULE_OF_THIRDS -> {
                val cands = listOf(
                    PointF(1f/3, 1f/3), PointF(2f/3, 1f/3),
                    PointF(1f/3, 2f/3), PointF(2f/3, 2f/3),
                )
                cands.minByOrNull { dist(it.x, it.y, currentX, currentY) }!!
            }
            CompositionMode.CENTER -> PointF(0.5f, 0.5f)
            CompositionMode.DIAGONAL -> {
                val d1 = projectToLine(currentX, currentY, 0f, 0f, 1f, 1f)
                val d2 = projectToLine(currentX, currentY, 0f, 1f, 1f, 0f)
                val dist1 = dist(d1.x, d1.y, currentX, currentY)
                val dist2 = dist(d2.x, d2.y, currentX, currentY)
                val p = if (dist1 < dist2) d1 else d2
                PointF(p.x.coerceIn(0.1f, 0.9f), p.y.coerceIn(0.1f, 0.9f))
            }
            CompositionMode.FRAME -> PointF(0.5f, 0.5f)
            CompositionMode.NEGATIVE_SPACE -> PointF(0.28f, 0.5f)
        }
    }

    private fun buildDirectionText(
        dx: Float, dy: Float, effectiveW: Float, effectiveH: Float, aligned: Boolean,
    ): String {
        // ── 优先：未对齐时给出方向引导（不被尺寸检查阻断）──
        if (!aligned) {
            val distance = sqrt(dx * dx + dy * dy)
            val magnitude = when {
                distance < 0.12f -> "一点"
                distance < 0.30f -> ""
                else -> "大幅"
            }

            val h = when {
                dx < -ALIGN_THRESHOLD -> "向右移$magnitude"
                dx > ALIGN_THRESHOLD -> "向左移$magnitude"
                else -> ""
            }
            val v = when {
                dy < -ALIGN_THRESHOLD -> "向下移$magnitude"
                dy > ALIGN_THRESHOLD -> "向上移$magnitude"
                else -> ""
            }

            val dirText = when {
                h.isNotEmpty() && v.isNotEmpty() -> "↗ $h · $v"
                h.isNotEmpty() -> "↔ $h"
                v.isNotEmpty() -> "↕ $v"
                else -> null
            }
            if (dirText != null) return dirText
        }

        // ── 已对齐 → 检查主体尺寸是否合适 ──
        val size = effectiveW * effectiveH
        if (size < FACE_TOO_SMALL_RATIO && size > 0f) return "📷 靠近一点"
        if (size > FACE_TOO_LARGE_RATIO) return "📷 离远一点"

        return "✅ 位置合适"
    }

    // ═══════════════════════════════════════════
    // 自动推荐
    // ═══════════════════════════════════════════

    private fun autoRecommend(fcX: Float, fcY: Float): CompositionMode {
        return CompositionMode.entries.minByOrNull { mode ->
            val i = idealFacePosition(fcX, fcY, mode)
            dist(i.x, i.y, fcX, fcY)
        } ?: CompositionMode.RULE_OF_THIRDS
    }

    // ═══════════════════════════════════════════
    // 几何工具
    // ═══════════════════════════════════════════

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun projectToLine(px: Float, py: Float,
                              ax: Float, ay: Float, bx: Float, by: Float): PointF {
        val abx = bx - ax; val aby = by - ay
        val t = ((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby)
        return PointF(ax + t * abx, ay + t * aby)
    }
}
