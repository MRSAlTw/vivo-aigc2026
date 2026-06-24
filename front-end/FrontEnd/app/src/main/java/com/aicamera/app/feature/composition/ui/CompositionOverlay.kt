package com.aicamera.app.feature.composition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.feature.composition.state.CompositionMode
import com.aicamera.app.feature.composition.state.FaceGuideData

/** 辅助线颜色：半透明白色 */
private val LINE_COLOR = Color.White.copy(alpha = 0.5f)

/** 人脸框颜色：黄色半透明 */
private val FACE_BOX_COLOR = Color(0xFFFFD60A).copy(alpha = 0.5f)

/** 目标位置颜色：绿色 */
private val TARGET_COLOR = Color(0xFF4CAF50).copy(alpha = 0.7f)

/** 人体框颜色：青色 */
private val BODY_BOX_COLOR = Color(0xFF00BCD4).copy(alpha = 0.5f)

/** 引导箭头颜色：亮黄色 */
private val ARROW_COLOR = Color(0xFFFFD60A)

/** 辅助线宽度 */
private val LINE_WIDTH = 1.5f

/**
 * 构图叠加层：静态辅助线 + 动态人脸框 + 引导箭头 + 文字提示。
 *
 * 符合需求 F-CG-03：在取景框上叠加辅助线/箭头，告知用户需要往哪个方向移动。
 */
@Composable
fun CompositionOverlay(
    mode: CompositionMode,
    faceGuide: FaceGuideData?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 底层：Canvas 绘制图形
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 静态辅助线
            drawStaticGuides(mode)

            // 动态引导
            faceGuide?.let { guide ->
                if (guide.hasBody) {
                    drawBodyBox(guide)
                }
                if (guide.hasFace || guide.hasBody) {
                    drawTargetIndicator(guide)
                    if (!guide.isAligned) {
                        drawGuidanceArrow(guide)
                    }
                }
                // 有人脸时额外画人脸框（人体已用青色框）
                if (guide.hasFace && guide.faceWidth > 0f) {
                    drawFaceBox(guide)
                }
            }
        }

        // 顶层：文字提示
        faceGuide?.let { guide ->
            if (guide.directionText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = guide.directionText,
                        color = if (guide.isAligned) Color(0xFF4CAF50) else ARROW_COLOR,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// 静态辅助线
// ═══════════════════════════════════════════

private fun DrawScope.drawStaticGuides(mode: CompositionMode) {
    when (mode) {
        CompositionMode.AUTO -> drawRuleOfThirds() // AUTO 默认显示三分线
        CompositionMode.RULE_OF_THIRDS -> drawRuleOfThirds()
        CompositionMode.CENTER -> drawCenterComposition()
        CompositionMode.DIAGONAL -> drawDiagonalComposition()
        CompositionMode.FRAME -> drawFrameComposition()
        CompositionMode.NEGATIVE_SPACE -> drawNegativeSpaceComposition()
    }
}

private fun DrawScope.drawRuleOfThirds() {
    val w = size.width; val h = size.height
    val sw = LINE_WIDTH.dp.toPx()
    drawLine(LINE_COLOR, Offset(0f, h / 3), Offset(w, h / 3), sw)
    drawLine(LINE_COLOR, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), sw)
    drawLine(LINE_COLOR, Offset(w / 3, 0f), Offset(w / 3, h), sw)
    drawLine(LINE_COLOR, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), sw)
}

private fun DrawScope.drawCenterComposition() {
    val w = size.width; val h = size.height
    val cx = w / 2; val cy = h / 2
    val sw = LINE_WIDTH.dp.toPx()
    drawLine(LINE_COLOR, Offset(cx, 0f), Offset(cx, h), sw)
    drawLine(LINE_COLOR, Offset(0f, cy), Offset(w, cy), sw)
    drawRect(
        color = LINE_COLOR,
        topLeft = Offset(cx - w * 0.175f, cy - h * 0.225f),
        size = Size(w * 0.35f, h * 0.45f),
        style = Stroke(sw),
    )
}

private fun DrawScope.drawDiagonalComposition() {
    val w = size.width; val h = size.height
    val sw = LINE_WIDTH.dp.toPx()
    drawLine(LINE_COLOR, Offset(0f, 0f), Offset(w, h), sw)
    drawLine(LINE_COLOR, Offset(w, 0f), Offset(0f, h), sw)
}

private fun DrawScope.drawFrameComposition() {
    val w = size.width; val h = size.height
    val arm = minOf(w, h) * 0.12f; val inset = minOf(w, h) * 0.04f
    val sw = LINE_WIDTH.dp.toPx()
    drawLine(LINE_COLOR, Offset(inset, inset), Offset(inset + arm, inset), sw)
    drawLine(LINE_COLOR, Offset(inset, inset), Offset(inset, inset + arm), sw)
    drawLine(LINE_COLOR, Offset(w - inset, inset), Offset(w - inset - arm, inset), sw)
    drawLine(LINE_COLOR, Offset(w - inset, inset), Offset(w - inset, inset + arm), sw)
    drawLine(LINE_COLOR, Offset(inset, h - inset), Offset(inset + arm, h - inset), sw)
    drawLine(LINE_COLOR, Offset(inset, h - inset), Offset(inset, h - inset - arm), sw)
    drawLine(LINE_COLOR, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), sw)
    drawLine(LINE_COLOR, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), sw)
}

private fun DrawScope.drawNegativeSpaceComposition() {
    val w = size.width; val h = size.height
    val sw = LINE_WIDTH.dp.toPx()
    val lineX = w / 3
    drawLine(LINE_COLOR, Offset(lineX, 0f), Offset(lineX, h), sw)
    val ovalCx = lineX / 2; val ovalCy = h / 2
    drawOval(
        color = LINE_COLOR,
        topLeft = Offset(ovalCx - w * 0.1f, ovalCy - h * 0.18f),
        size = Size(w * 0.2f, h * 0.36f),
        style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
    )
}

// ═══════════════════════════════════════════
// 动态元素：人脸框、目标标记、引导箭头
// ═══════════════════════════════════════════

private fun DrawScope.drawFaceBox(guide: FaceGuideData) {
    val w = size.width; val h = size.height
    val boxW = guide.faceWidth * w
    val boxH = guide.faceHeight * h
    val left = guide.faceCenterX * w - boxW / 2
    val top = guide.faceCenterY * h - boxH / 2

    drawRect(
        color = FACE_BOX_COLOR.copy(alpha = 0.15f),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
    )
    drawRect(
        color = FACE_BOX_COLOR,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        style = Stroke(2.dp.toPx()),
    )
}

private fun DrawScope.drawBodyBox(guide: FaceGuideData) {
    val w = size.width; val h = size.height
    val boxW = guide.bodyWidth * w
    val boxH = guide.bodyHeight * h
    val left = guide.bodyCenterX * w - boxW / 2
    val top = guide.bodyCenterY * h - boxH / 2

    if (boxW <= 0 || boxH <= 0) return

    drawRect(
        color = BODY_BOX_COLOR.copy(alpha = 0.12f),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
    )
    drawRect(
        color = BODY_BOX_COLOR,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        style = Stroke(2.5f.dp.toPx()),
    )
}

private fun DrawScope.drawTargetIndicator(guide: FaceGuideData) {
    val tx = guide.targetX * size.width
    val ty = guide.targetY * size.height
    val r = 12.dp.toPx()

    drawLine(TARGET_COLOR, Offset(tx - r, ty), Offset(tx + r, ty), 2.dp.toPx())
    drawLine(TARGET_COLOR, Offset(tx, ty - r), Offset(tx, ty + r), 2.dp.toPx())
    drawCircle(TARGET_COLOR, r, Offset(tx, ty), style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawGuidanceArrow(guide: FaceGuideData) {
    val subCx = if (guide.hasBody) guide.bodyCenterX else guide.faceCenterX
    val subCy = if (guide.hasBody) guide.bodyCenterY else guide.faceCenterY
    val subW = if (guide.hasBody) guide.bodyWidth else guide.faceWidth
    val subH = if (guide.hasBody) guide.bodyHeight else guide.faceHeight

    val fromX = subCx * size.width
    val fromY = subCy * size.height
    val toX = guide.targetX * size.width
    val toY = guide.targetY * size.height

    val arrowLen = 10.dp.toPx()

    val dx = toX - fromX
    val dy = toY - fromY
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist < 1f) return

    val ndx = dx / dist
    val ndy = dy / dist
    val startX = fromX + ndx * (subW * size.width / 2 + 8.dp.toPx())
    val startY = fromY + ndy * (subH * size.height / 2 + 8.dp.toPx())

    drawLine(
        color = ARROW_COLOR,
        start = Offset(startX, startY),
        end = Offset(toX, toY),
        strokeWidth = 2.5f.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 8f)),
    )

    val tip = Offset(toX, toY)
    val arrowP1 = Offset(toX - ndx * arrowLen + ndy * arrowLen * 0.5f, toY - ndy * arrowLen - ndx * arrowLen * 0.5f)
    val arrowP2 = Offset(toX - ndx * arrowLen - ndy * arrowLen * 0.5f, toY - ndy * arrowLen + ndx * arrowLen * 0.5f)

    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(arrowP1.x, arrowP1.y)
        lineTo(arrowP2.x, arrowP2.y)
        close()
    }
    drawPath(path, ARROW_COLOR)
}
