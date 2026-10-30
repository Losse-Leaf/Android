package com.looseleaf.app.ui.stream

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveBottomSheet
import java.util.Calendar

/**
 * 手账年份横向滚轮刻度尺 (Horizontal Year Ruler Picker)
 *
 * 替代传统 Material 下拉菜单，以物理刻度尺/黄铜游标形态呈现：
 * - 纯正复古手账木质/黄铜尺刻度
 * - 阻尼吸附滑动 (Snap to center) + 触感回馈
 * - 赤陶色中心指示针
 */
@Composable
internal fun YearRulerPicker(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Log.d("YearRulerPicker", "YearRulerPicker composing! selectedYear=$selectedYear, availableYears=$availableYears")
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val yearsList = remember(availableYears) {
        val minYear = minOf(availableYears.minOrNull() ?: currentYear, currentYear - 3)
        val maxYear = maxOf(availableYears.maxOrNull() ?: currentYear, currentYear + 1)
        val span = (maxYear downTo minYear).toList()
        listOf<Int?>(null) + span
    }

    val itemWidth = 84.dp
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 计算中心对齐的内边距，使首尾刻度均可正对游标
    val horizontalPadding = (screenWidth - itemWidth) / 2

    // 监听中心游标所指项
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) 0
            else {
                val centerOffset = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centerOffset) }?.index ?: 0
            }
        }
    }

    // 初次打开时定位到当前选中的年份
    LaunchedEffect(Unit) {
        val targetIndex = yearsList.indexOf(selectedYear).coerceAtLeast(0)
        listState.scrollToItem(targetIndex)
    }

    // 滑动经过刻度时触发微弱振动
    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centerIndex) {
        if (lastHapticIndex != -1 && lastHapticIndex != centerIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = centerIndex
    }

    val activeItem = yearsList.getOrNull(centerIndex)

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "年份刻度索引",
        actionButtonText = if (activeItem == null) "全部手稿" else "定格 ${activeItem}年",
        onActionClick = {
            onYearSelected(activeItem)
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 诗意手写副标题与重置链接
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "拨动游标 · 追溯往昔手稿",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkMuted
                )

                TextButton(
                    onClick = {
                        onYearSelected(null)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "重置为全部",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.5.sp,
                        color = colors.inkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 当前游标定格的年份大字
            Text(
                text = if (activeItem == null) "全部手稿 · 不限年份" else "${activeItem} 年",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.accentWarm
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 刻度尺卡槽容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(colors.archiveSubtle.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                // 上下木质刻线边
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderDivider.copy(alpha = 0.5f))
                        .align(Alignment.TopCenter)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderDivider.copy(alpha = 0.5f))
                        .align(Alignment.BottomCenter)
                )

                // 横向刻度列表
                LazyRow(
                    state = listState,
                    flingBehavior = flingBehavior,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(yearsList) { index, item ->
                        val isSelected = index == centerIndex

                        Column(
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                .clickable {
                                    // 点击刻度直接滚动至中心
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 刻度线 Canvas
                            val tickColor = if (isSelected) colors.accentWarm else colors.inkMuted.copy(alpha = 0.6f)
                            val subTickColor = colors.inkMuted.copy(alpha = 0.28f)

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                val w = size.width
                                val midX = w / 2

                                // 主刻度（居中）
                                drawLine(
                                    color = tickColor,
                                    start = Offset(midX, 0f),
                                    end = Offset(midX, 24.dp.toPx()),
                                    strokeWidth = if (isSelected) 2.dp.toPx() else 1.2.dp.toPx()
                                )

                                // 4 根细副刻度（分布在左右）
                                val step = w / 5f
                                for (i in 1..4) {
                                    val subX = i * step
                                    if (kotlin.math.abs(subX - midX) > 2f) {
                                        drawLine(
                                            color = subTickColor,
                                            start = Offset(subX, 0f),
                                            end = Offset(subX, 10.dp.toPx()),
                                            strokeWidth = 0.8.dp.toPx()
                                        )
                                    }
                                }
                            }

                            // 刻度文字
                            Text(
                                text = item?.toString() ?: "全部",
                                fontFamily = if (item != null) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                                fontSize = if (isSelected) 14.sp else 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.inkPrimary else colors.inkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }

                // 中心赤陶色游标针
                Canvas(
                    modifier = Modifier
                        .width(16.dp)
                        .height(96.dp)
                        .align(Alignment.Center)
                ) {
                    val midX = size.width / 2

                    // 顶部三角指示箭头
                    val path = Path().apply {
                        moveTo(midX - 5.dp.toPx(), 0f)
                        lineTo(midX + 5.dp.toPx(), 0f)
                        lineTo(midX, 6.dp.toPx())
                        close()
                    }
                    drawPath(path, color = colors.accentWarm)

                    // 垂直游标准心线
                    drawLine(
                        color = colors.accentWarm,
                        start = Offset(midX, 6.dp.toPx()),
                        end = Offset(midX, size.height - 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}
