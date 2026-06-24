package com.aicamera.app.feature.composition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.feature.composition.state.CompositionMode

/** 选中高亮色 */
private val SELECTED_COLOR = Color(0xFFFFD60A)

/**
 * 构图方式横向选择器。
 *
 * 符合需求 F-CG-01：自动推荐的模式排在第一位并标注"推荐"；
 * 符合需求 F-CG-02：用户可点击其他模式手动切换。
 */
@Composable
fun CompositionSelector(
    currentMode: CompositionMode,
    recommendedMode: CompositionMode?,
    onModeSelected: (CompositionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 推荐模式置顶，其余保持原有枚举顺序
    val orderedModes = buildList {
        if (recommendedMode != null) {
            add(recommendedMode)
        }
        CompositionMode.entries.forEach { mode ->
            if (mode != recommendedMode) {
                add(mode)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
    ) {
        orderedModes.forEach { mode ->
            val selected = mode == currentMode
            val isRecommended = mode == recommendedMode

            Box(modifier = Modifier.width(100.dp)) {
                Text(
                    text = mode.label,
                    color = if (selected) SELECTED_COLOR else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .align(Alignment.Center),
                )

                // F-CG-01：仅在系统推荐的模式上显示"推荐"标签
                if (isRecommended) {
                    Text(
                        text = "推荐",
                        color = SELECTED_COLOR,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 0.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
