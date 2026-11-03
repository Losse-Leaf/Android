package com.looseleaf.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveTextButton
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDate
import java.util.Locale

@Immutable
private data class HeatmapDay(
    val date: LocalDate,
    val dateKey: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val count: Int
)

/**
 * 展台专享 · 时光刻度热力图 (ShowcaseHeatmap)
 * 1. 标题左"时光刻度" + 右侧等宽小字"共 N 篇" + 右侧文字按钮"年度年鉴"（赤陶、无图标）
 * 2. 默认视口展示最近 15 周（约三个半月），可横向滑动回看最近 26 周，右侧对齐今天
 * 3. 正文墨色四档透明度（6% / 20% / 45% / 75%），圆角 4dp；今天 1dp 内嵌描边（严格与邻格同尺寸对齐）
 * 4. 月份标注于每个月第一列上方，左侧紧凑贴齐"一 / 三 / 五"，右下角图例"少 ■■■■ 多"
 * 5. 点选高亮（2dp 赤陶内嵌描边）并在下方展示"X月X日 · N 篇"
 */
@Composable
fun ShowcaseHeatmap(
    dayCounts: Map<String, Int>,
    totalEntryCount: Int,
    onOpenAlmanac: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }
    val totalCount = remember(dayCounts, totalEntryCount) {
        if (totalEntryCount > 0) totalEntryCount else dayCounts.values.sum()
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedDateKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // SectionHeader: 左"时光刻度" + 右侧次要色等宽小字"共 N 篇" + 再右文字按钮"年度年鉴"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "时光刻度",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = ArchiveTypeScale.bodyLarge,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "共 $totalCount 篇",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = ArchiveTypeScale.caption,
                    color = colors.inkSecondary,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
            }

            ArchiveTextButton(
                text = "年度年鉴",
                onClick = onOpenAlmanac
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val visibleCols = 15
            val numCols = 26
            val leftLabelWidth = 13.dp
            val labelSpacing = 6.dp
            val gapDp = 4.dp

            val viewportGridWidth = (maxWidth - leftLabelWidth - labelSpacing).coerceAtLeast(140.dp)
            val cellDp = ((viewportGridWidth - gapDp * (visibleCols - 1)) / visibleCols).coerceIn(13.dp, 20.dp)
            val stepDp = cellDp + gapDp
            val totalCanvasWidth = cellDp * numCols + gapDp * (numCols - 1)

            val todayRow = today.dayOfWeek.value - 1 // 0 = Mon ... 6 = Sun
            val lastCol = numCols - 1

            val gridMatrix = remember(today, numCols, dayCounts) {
                val matrix = mutableListOf<List<HeatmapDay>>()
                for (col in 0 until numCols) {
                    val colDays = mutableListOf<HeatmapDay>()
                    for (row in 0 until 7) {
                        val daysAgo = (lastCol - col) * 7 + (todayRow - row)
                        if (daysAgo < 0) {
                            colDays.add(
                                HeatmapDay(
                                    date = today.plusDays((-daysAgo).toLong()),
                                    dateKey = "",
                                    isToday = false,
                                    isFuture = true,
                                    count = 0
                                )
                            )
                        } else {
                            val d = today.minusDays(daysAgo.toLong())
                            val k = String.format(Locale.US, "%04d.%02d.%02d", d.year, d.monthValue, d.dayOfMonth)
                            colDays.add(
                                HeatmapDay(
                                    date = d,
                                    dateKey = k,
                                    isToday = (daysAgo == 0),
                                    isFuture = false,
                                    count = dayCounts[k] ?: 0
                                )
                            )
                        }
                    }
                    matrix.add(colDays)
                }
                matrix
            }

            // 计算月份标注列：每个包含月初第一天的列打上对应月份
            val monthLabels = remember(gridMatrix) {
                val labels = mutableListOf<Pair<Int, String>>()
                gridMatrix.forEachIndexed { col, colDays ->
                    val firstDay = colDays.firstOrNull { it.date.dayOfMonth == 1 && !it.isFuture }
                    if (firstDay != null) {
                        labels.add(col to "${firstDay.date.monthValue}月")
                    }
                }
                if (labels.isNotEmpty()) {
                    val firstLabeledCol = labels.first().first
                    if (firstLabeledCol >= 3) {
                        val firstDate = gridMatrix[0].firstOrNull { !it.isFuture }?.date
                        if (firstDate != null) {
                            labels.add(0, 0 to "${firstDate.monthValue}月")
                        }
                    }
                }
                labels
            }

            val monthHeaderHeightDp = 20.dp
            val gridHeightDp = monthHeaderHeightDp + cellDp * 7 + gapDp * 6
            val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
            var didInitialScroll by remember { mutableStateOf(false) }
            LaunchedEffect(scrollState.maxValue) {
                if (!didInitialScroll && scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                    didInitialScroll = true
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // 固定左侧星期坐标轴：一 / 三 / 五，紧凑贴齐左边界且横向滑动时保持不动
                    Canvas(
                        modifier = Modifier
                            .width(leftLabelWidth)
                            .height(gridHeightDp)
                    ) {
                        val cellPx = cellDp.toPx()
                        val stepPx = stepDp.toPx()
                        val monthHeaderHeightPx = monthHeaderHeightDp.toPx()
                        val weekPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = ArchiveTypeScale.micro.toPx()
                            color = colors.inkSecondary.toArgb()
                            typeface = android.graphics.Typeface.SERIF
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                        val weekFm = weekPaint.fontMetrics
                        val textYCenterOffset = (cellPx - (weekFm.ascent + weekFm.descent)) / 2f

                        drawIntoCanvas { canvas ->
                            listOf(0 to "一", 2 to "三", 4 to "五").forEach { (r, label) ->
                                val y = monthHeaderHeightPx + r * stepPx + textYCenterOffset
                                canvas.nativeCanvas.drawText(label, 0f, y, weekPaint)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(labelSpacing))

                    // 右侧可横向滑动的 7×26 周热力图（首屏默认停在右侧最近 15 周）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(totalCanvasWidth)
                                .height(gridHeightDp)
                                .semantics {
                                    contentDescription = "时光刻度热力图，展示最近 $numCols 周，累计 $totalCount 篇手记"
                                }
                                .pointerInput(gridMatrix, cellDp, stepDp) {
                                    detectTapGestures { offset ->
                                        val stepPx = stepDp.toPx()
                                        val cellPx = cellDp.toPx()
                                        val monthHeaderHeightPx = monthHeaderHeightDp.toPx()
                                        if (offset.y < monthHeaderHeightPx) return@detectTapGestures
                                        val col = (offset.x / stepPx).toInt()
                                        val row = ((offset.y - monthHeaderHeightPx) / stepPx).toInt()
                                        val inCellX = (offset.x - col * stepPx) <= cellPx
                                        val inCellY = (offset.y - monthHeaderHeightPx - row * stepPx) <= cellPx
                                        if (col in 0 until numCols && row in 0 until 7 && inCellX && inCellY) {
                                            val day = gridMatrix[col][row]
                                            if (!day.isFuture) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedDate = day.date
                                                selectedDateKey = day.dateKey
                                            }
                                        }
                                    }
                                }
                        ) {
                            val cellPx = cellDp.toPx()
                            val stepPx = stepDp.toPx()
                            val monthHeaderHeightPx = monthHeaderHeightDp.toPx()
                            val cornerPx = 4.dp.toPx()
                            val radius = CornerRadius(cornerPx)

                            val monthPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textSize = ArchiveTypeScale.micro.toPx()
                                color = colors.inkSecondary.toArgb()
                                typeface = android.graphics.Typeface.SERIF
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                            val monthBaselineY = monthHeaderHeightPx - 5.dp.toPx()

                            drawIntoCanvas { canvas ->
                                monthLabels.forEach { (col, monthName) ->
                                    val rawX = col * stepPx
                                    val textW = monthPaint.measureText(monthName)
                                    val safeX = rawX.coerceAtMost((size.width - textW).coerceAtLeast(0f))
                                    canvas.nativeCanvas.drawText(monthName, safeX, monthBaselineY, monthPaint)
                                }
                            }

                            gridMatrix.forEachIndexed { col, colDays ->
                                colDays.forEachIndexed { row, day ->
                                    if (!day.isFuture) {
                                        val topLeft = Offset(col * stepPx, monthHeaderHeightPx + row * stepPx)
                                        val cellSize = Size(cellPx, cellPx)

                                        val fillColor = when {
                                            day.count >= 4 -> colors.inkPrimary.copy(alpha = 0.75f)
                                            day.count in 2..3 -> colors.inkPrimary.copy(alpha = 0.45f)
                                            day.count == 1 -> colors.inkPrimary.copy(alpha = 0.20f)
                                            else -> colors.inkPrimary.copy(alpha = 0.06f)
                                        }

                                        drawRoundRect(
                                            color = fillColor,
                                            topLeft = topLeft,
                                            size = cellSize,
                                            cornerRadius = radius
                                        )

                                        // 今天的格子：严格内嵌 1dp 正文色描边，外轮廓尺寸与同列邻格完全一致
                                        if (day.isToday) {
                                            val strokePx = 1.dp.toPx()
                                            val half = strokePx / 2f
                                            drawRoundRect(
                                                color = colors.inkPrimary,
                                                topLeft = Offset(topLeft.x + half, topLeft.y + half),
                                                size = Size(
                                                    (cellPx - strokePx).coerceAtLeast(1f),
                                                    (cellPx - strokePx).coerceAtLeast(1f)
                                                ),
                                                cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(1f)),
                                                style = Stroke(width = strokePx)
                                            )
                                        }

                                        // 选中格：严格内嵌 2dp 赤陶描边
                                        if (selectedDateKey == day.dateKey) {
                                            val selStroke = 2.dp.toPx()
                                            val selHalf = selStroke / 2f
                                            drawRoundRect(
                                                color = colors.accentWarm,
                                                topLeft = Offset(topLeft.x + selHalf, topLeft.y + selHalf),
                                                size = Size(
                                                    (cellPx - selStroke).coerceAtLeast(1f),
                                                    (cellPx - selStroke).coerceAtLeast(1f)
                                                ),
                                                cornerRadius = CornerRadius((cornerPx - selHalf).coerceAtLeast(1f)),
                                                style = Stroke(width = selStroke)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 右下角图例："少 ■■■■ 多"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "少",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.micro,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf(0.06f, 0.20f, 0.45f, 0.75f).forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.5.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.inkPrimary.copy(alpha = alpha))
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "多",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.micro,
                        color = colors.inkSecondary
                    )
                }

                // 点选状态反馈行
                if (selectedDate != null) {
                    val count = dayCounts[selectedDateKey] ?: 0
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedDate!!.monthValue}月${selectedDate!!.dayOfMonth}日 · $count 篇",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = ArchiveTypeScale.body,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        )
                    }
                } else if (totalCount == 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "还没有手记",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.caption,
                        color = colors.inkSecondary
                    )
                }
            }
        }
    }
}
