package com.looseleaf.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
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
 * 严格遵循阶段 3 规范：
 * 1. 标题左"时光刻度" + 右侧等宽小字"共 N 篇" + 右侧文字按钮"年度年鉴"（赤陶、无图标）
 * 2. 7 行（周一在上）× N 列（自适应屏宽 20~22 周），右侧含今天
 * 3. 正文墨色四档透明度（6% / 20% / 45% / 75%），不用赤陶；圆角 4dp；今天 1dp 正文色描边
 * 4. 月份标注于每个月第一列上方，左侧标 一 / 三 / 五，右下角图例"少 ■■■■ 多"
 * 5. 点选高亮（2dp 赤陶描边）并在下方展示"X月X日 · N 篇"
 */
@Composable
fun ShowcaseHeatmap(
    dayCounts: Map<String, Int>,
    totalEntryCount: Int,
    onOpenAlmanac: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
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
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "共 $totalCount 篇",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 12.sp,
                    color = colors.inkSecondary
                )
            }

            ArchiveTextButton(
                text = "年度年鉴",
                onClick = onOpenAlmanac
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val numCols = 21
            val leftLabelWidth = 14.dp
            val labelSpacing = 6.dp
            val gapDp = 3.dp

            val totalGridWidth = (maxWidth - leftLabelWidth - labelSpacing).coerceAtLeast(100.dp)
            val cellDp = (totalGridWidth - gapDp * (numCols - 1)) / numCols
            val stepDp = cellDp + gapDp

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

            // 计算月份标注列
            val monthLabels = remember(gridMatrix) {
                val labels = mutableListOf<Pair<Int, String>>()
                var lastLabeledCol = -5
                gridMatrix.forEachIndexed { col, colDays ->
                    val firstDay = colDays.firstOrNull { it.date.dayOfMonth == 1 && !it.isFuture }
                    if (firstDay != null && col - lastLabeledCol >= 3) {
                        labels.add(col to "${firstDay.date.monthValue}月")
                        lastLabeledCol = col
                    } else if (col == 0 && colDays.isNotEmpty()) {
                        labels.add(0 to "${colDays[0].date.monthValue}月")
                        lastLabeledCol = 0
                    }
                }
                labels
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // 上方月份标注
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    monthLabels.forEach { (col, monthName) ->
                        val xPos = leftLabelWidth + labelSpacing + stepDp * col
                        Text(
                            text = monthName,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 10.sp,
                            color = colors.inkSecondary,
                            modifier = Modifier.offset(x = xPos)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 网格与左侧星期标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // 左侧只标 一 / 三 / 五
                    Column(
                        modifier = Modifier.width(leftLabelWidth)
                    ) {
                        for (r in 0 until 7) {
                            Box(
                                modifier = Modifier
                                    .height(cellDp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                when (r) {
                                    0 -> Text("一", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 9.5.sp, color = colors.inkSecondary)
                                    2 -> Text("三", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 9.5.sp, color = colors.inkSecondary)
                                    4 -> Text("五", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 9.5.sp, color = colors.inkSecondary)
                                }
                            }
                            if (r < 6) {
                                Spacer(modifier = Modifier.height(gapDp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(labelSpacing))

                    // 7xN 热力图 Canvas
                    val gridHeight = cellDp * 7 + gapDp * 6
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight)
                            .semantics {
                                contentDescription = "时光刻度热力图，共统计 $numCols 周，累计 $totalCount 篇手记"
                            }
                            .pointerInput(gridMatrix) {
                                detectTapGestures { offset ->
                                    val stepPx = stepDp.toPx()
                                    val col = (offset.x / stepPx).toInt()
                                    val row = (offset.y / stepPx).toInt()
                                    if (col in 0 until numCols && row in 0 until 7) {
                                        val day = gridMatrix[col][row]
                                        if (!day.isFuture) {
                                            selectedDate = day.date
                                            selectedDateKey = day.dateKey
                                        }
                                    }
                                }
                            }
                    ) {
                        val cellPx = cellDp.toPx()
                        val stepPx = stepDp.toPx()
                        val radius = CornerRadius(4.dp.toPx())

                        gridMatrix.forEachIndexed { col, colDays ->
                            colDays.forEachIndexed { row, day ->
                                if (!day.isFuture) {
                                    val topLeft = Offset(col * stepPx, row * stepPx)
                                    val cellSize = Size(cellPx, cellPx)

                                    // 四档透明度：无记录 6% / 1 篇 20% / 2–3 篇 45% / 4 篇以上 75%
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

                                    // 今天的格子加 1dp 正文色描边
                                    if (day.isToday) {
                                        drawRoundRect(
                                            color = colors.inkPrimary,
                                            topLeft = topLeft,
                                            size = cellSize,
                                            cornerRadius = radius,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }

                                    // 选中格加 2dp 赤陶描边
                                    if (selectedDateKey == day.dateKey) {
                                        drawRoundRect(
                                            color = colors.accentWarm,
                                            topLeft = topLeft,
                                            size = cellSize,
                                            cornerRadius = radius,
                                            style = Stroke(width = 2.dp.toPx())
                                        )
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
                        fontSize = 10.sp,
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
                        fontSize = 10.sp,
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
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        )
                    }
                } else if (totalCount == 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "还没有手记",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                }
            }
        }
    }
}
