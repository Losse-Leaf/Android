package com.looseleaf.app.ui.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页手账 · 分类标签行 (Stream Filter Bar)
 *
 * 统一化规范：
 * - 去掉所有边框与底色，改为纯文字标签横向排列
 * - 选中：正文色加粗 + 下方 2dp 赤陶下划线；未选中：次要暖灰
 * - 分类色点保留，6dp，放在文字前
 * - 右侧 24dp 渐隐；标签行下方仅一条 1dp 淡线，与全页同一种线
 */
@Composable
internal fun StreamFilterBar(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val filters = remember(colors) {
        listOf(
            FilterItem("all", "全部", colors.accentWarm),
            FilterItem("daily", "日常", colors.tagDaily),
            FilterItem("travel", "游历", colors.tagTravel),
            FilterItem("project", "造物", colors.tagProject),
            FilterItem("wish", "心愿", colors.tagWish)
        )
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(colors.archivePaper)
    ) {
        // 标签行下方全宽仅一条 1dp 极淡暖灰实线，与全页同一种线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderDivider)
                .align(Alignment.BottomCenter)
        )

        // 纯文字标签横向排列
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in filters) {
                val isSelected = item.key == selectedFilter

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onFilterSelected(item.key) }
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 分类色点 6dp，置于文字前
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(item.color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // 文字与正下方的 2dp 下划线（通过 IntrinsicSize.Max 严格与单行文字等宽）
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.label,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkMuted,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // 选中：文字正下方 2dp 赤陶下划线（宽度与文字严格一致）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) colors.accentWarm else Color.Transparent)
                        )
                    }
                }
            }
        }

        // 右侧 24dp 渐隐遮罩
        if (scrollState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.archivePaper
                            )
                        )
                    )
            )
        }
    }
}

private data class FilterItem(
    val key: String,
    val label: String,
    val color: Color
)

/** 兼容旧调用的空实现（已并入封面区） */
@Composable
internal fun YearFilterBar(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    // 兼容空桩
}
