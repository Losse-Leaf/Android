package com.looseleaf.app.ui.stream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import java.io.File
import java.time.LocalDate

/** 热力图单格边长（dp），供 LifeActivityHeatmap 与其调用方共享 */
internal const val HEATMAP_CELL_DP = 12
@Composable
internal fun OnThisDayCard(
    entries: List<LifeEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    PaperCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = colors.accentWarm.copy(alpha = 0.06f),
        borderColor = colors.accentWarm.copy(alpha = 0.4f)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "那年今日 · 岁月回响 (${entries.size})",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.accentWarm
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "展开或收起",
                    tint = colors.accentWarm,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                PaperDivider(color = colors.accentWarm.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                entries.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onSelectEntry(item) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            if (item.content.isNotBlank()) {
                                Text(
                                    text = item.content,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatDisplayDate(item.occurredAt),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自然下渗分类过滤：遵循 docs/README.md §三.8 规范，带城市属地归入游历，带项目归入造物，带心愿归入心愿
 */
internal fun entryMatchesFilter(entry: LifeEntry, filter: String): Boolean {
    if (filter == "all") return true
    val t = entry.type.lowercase()
    if (filter == "travel") {
        return t == "travel" || !entry.cityCode.isNullOrBlank()
    }
    if (filter == "project") {
        return t == "project" || (entry.projectId != null && entry.projectId > 0)
    }
    if (filter == "wish") {
        return t == "wish" || (entry.wishId != null && entry.wishId > 0)
    }
    if (filter == "daily" || filter == "life") {
        return (t == "life" || t == "daily") && entry.cityCode.isNullOrBlank() && (entry.projectId == null || entry.projectId <= 0) && (entry.wishId == null || entry.wishId <= 0)
    }
    return t == filter
}

@Composable
internal fun LifeActivityHeatmap(
    entries: List<LifeEntry>,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val dateCountMap = remember(entries) {
        // 统一按北京时间归日：直接截 occurredAt 前 10 位，在 UTC 串（…T20:00Z）上会偏到前一天
        entries.groupingBy { FormatUtils.formatDisplayDate(it.occurredAt) }.eachCount()
    }

    val weeksCount = 52
    // key 必须含 entries：固定用常量 52 会让日期网格在数据刷新后不重算；
    // 组合进入时 remember 自然重算，跨天由下一次重组覆盖
    val calWeeks = remember(entries) {
        val today = LocalDate.now(FormatUtils.BeijingZone)
        val list = mutableListOf<List<Pair<String, Boolean>>>()
        for (w in (0 until weeksCount).reversed()) {
            val days = mutableListOf<Pair<String, Boolean>>()
            for (d in 0 until 7) {
                val daysAgo = w * 7 + (6 - d)
                val targetDate = today.minusDays(daysAgo.toLong())
                val key = String.format(java.util.Locale.US, "%04d.%02d.%02d", targetDate.year, targetDate.monthValue, targetDate.dayOfMonth)
                days.add(key to (daysAgo == 0))
            }
            list.add(days)
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.archiveSubtle.copy(alpha = 0.5f))
            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = colors.accentMoss,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "时光生活刻度",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkPrimary
                )
                Text(
                    text = " · 共 ${entries.size} 篇手记",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }

            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isExpanded) "收起刻度" else "展开网格",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 11.sp,
                    color = colors.inkSecondary
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            val cellDp = HEATMAP_CELL_DP.dp
            val gapDp = 3.dp
            val stepDp = cellDp + gapDp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // 364 个可点击 Box 改为一次 Canvas 绘制：组合节点从 364 降到 1
                Canvas(
                    modifier = Modifier
                        .width(stepDp * calWeeks.size)
                        .height(stepDp * 7)
                        .pointerInput(calWeeks, dateCountMap) {
                            detectTapGestures { offset ->
                                val step = stepDp.toPx()
                                val col = (offset.x / step).toInt()
                                val row = (offset.y / step).toInt()
                                val week = calWeeks.getOrNull(col) ?: return@detectTapGestures
                                val (dateKey, _) = week.getOrNull(row) ?: return@detectTapGestures
                                val count = dateCountMap[dateKey] ?: 0
                                ArchiveToastManager.show(
                                    "$dateKey: ${if (count > 0) "$count 篇手记" else "静默留白"}"
                                )
                            }
                        }
                ) {
                    val step = stepDp.toPx()
                    val cell = cellDp.toPx()
                    val radius = CornerRadius(1.5.dp.toPx())
                    calWeeks.forEachIndexed { col, week ->
                        week.forEachIndexed { row, (dateKey, isToday) ->
                            val count = dateCountMap[dateKey] ?: 0
                            val topLeft = Offset(col * step, row * step)
                            val cellSize = Size(cell, cell)
                            val fill = when {
                                count >= 3 -> colors.accentWarm
                                count == 2 -> colors.accentWarm.copy(alpha = 0.65f)
                                count == 1 -> colors.accentWarm.copy(alpha = 0.35f)
                                else -> colors.archivePaper
                            }
                            drawRoundRect(
                                color = fill,
                                topLeft = topLeft,
                                size = cellSize,
                                cornerRadius = radius
                            )
                            drawRoundRect(
                                color = if (isToday) colors.accentWarm
                                else colors.borderDivider.copy(alpha = 0.6f),
                                topLeft = topLeft,
                                size = cellSize,
                                cornerRadius = radius,
                                style = Stroke(width = if (isToday) 0.8.dp.toPx() else 0.3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EntryAttachmentsGrid(
    attachments: List<Attachment>,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(modifier = modifier) {
        val count = attachments.size
        when (count) {
            1 -> {
                val att = attachments.first()
                val fullUrl = apiClient.resolveMediaUrl(att.url)
                val meta = att.originalFilename
                FilmFrame(
                    imageUrl = fullUrl,
                    metadataText = meta,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier.padding(vertical = 4.dp),
                    onClick = { onPhotoClick(fullUrl, meta) }
                )
            }
            2, 4 -> {
                attachments.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (att in row) {
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            FilmFrame(
                                imageUrl = fullUrl,
                                metadataText = null,
                                aspectRatio = 1f,
                                modifier = Modifier.weight(1f),
                                onClick = { onPhotoClick(fullUrl, meta) }
                            )
                        }
                    }
                }
            }
            else -> {
                attachments.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (att in row) {
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            FilmFrame(
                                imageUrl = fullUrl,
                                metadataText = null,
                                aspectRatio = 1f,
                                modifier = Modifier.weight(1f),
                                onClick = { onPhotoClick(fullUrl, meta) }
                            )
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StreamEntryCard(
    entry: LifeEntry,
    apiClient: APIClient,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val cardBg = when (entry.type) {
        "travel" -> colors.accentWarm.copy(alpha = 0.04f)
        "project" -> colors.accentMoss.copy(alpha = 0.04f)
        "wish" -> colors.archivePaper
        else -> colors.archiveSubtle
    }
    val cardBorder = when (entry.type) {
        "travel" -> colors.accentWarm.copy(alpha = 0.35f)
        "project" -> colors.accentMoss.copy(alpha = 0.35f)
        "wish" -> colors.accentWarm.copy(alpha = 0.25f)
        else -> colors.borderDivider
    }

    PaperCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = cardBg,
        borderColor = cardBorder
    ) {
        Column {
            // 头部：时间戳、地点与操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        softWrap = false
                    )

                    val placeString = listOfNotNull(entry.cityName, entry.location).distinct().filter { it.isNotBlank() }.joinToString(" · ")
                    if (placeString.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = if (entry.type == "travel") colors.accentWarm else colors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = placeString,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (entry.type == "travel") colors.accentWarm else colors.inkSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.syncStatus != "SYNCED") {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accentWarm)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除记录",
                            tint = colors.inkMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 标题
            Text(
                text = entry.title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp,
                color = colors.inkPrimary
            )

            // 正文内容
            if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.content,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = colors.inkSecondary
                )
            }

            // 关联胶片相纸附件渲染 (多图网格与触击放大灯箱)
            if (entry.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                EntryAttachmentsGrid(
                    attachments = entry.attachments,
                    apiClient = apiClient,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

/**
 * 时间流列表行：把「距上一条的天数」预计算进不可变行对象，
 * 让 LazyColumn 滚动期只做字段读取（AOPT-03b）。
 */
@Immutable
internal data class StreamRowItem(
    val entry: LifeEntry,
    val gapDays: Long
)
