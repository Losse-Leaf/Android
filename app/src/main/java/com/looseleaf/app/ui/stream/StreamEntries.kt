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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import com.looseleaf.app.data.sync.SyncManager
import androidx.compose.ui.res.painterResource

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
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stream_entries_text_tpl_01, entries.size),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.accentWarm
                    )
                }
                Icon(
                    painter = if (isExpanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.stream_entries_contentDescription_01),
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


@Composable
internal fun LifeActivityHeatmap(
    // MOB-P1-32：改为接收「按日计数」而不是全量档案 ——
    // 原先要在组合期对全量条目 groupBy + 每条一次日期解析（2 万条 = 2 万次解析）。
    dayCounts: Map<String, Int>,
    totalEntryCount: Int = 0,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    onOpenAlmanac: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    var isExpanded by rememberSaveable { mutableStateOf(if (collapsible) initiallyExpanded else true) }

    // MOB-P1-32：按日计数由 SQL GROUP BY 提供，主线程零计算
    val dateCountMap = dayCounts
    // MOB-P1-80：热力图的无障碍描述（composable 作用域取值）
    val heatmapDescription = stringResource(
        R.string.stream_entries_contentDescription_tpl_01,
        dateCountMap.size
    )

    val weeksCount = 52
    // MOB-P0-07：日期网格只依赖「今天」，与 entries 无关 ——
    // 原先以 entries 为记忆键，导致每次同步后都重跑 364 次 String.format。
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }
    val calWeeks = remember(today) {
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
                    text = stringResource(R.string.stream_entries_text_01),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.stream_entries_text_tpl_02, totalEntryCount),
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }

            if (onOpenAlmanac != null) {
                TextButton(
                    onClick = onOpenAlmanac,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_auto_stories),
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "年度年鉴",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 11.5.sp,
                        color = colors.accentWarm
                    )
                }
            } else if (collapsible) {
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
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            val cellDp = HEATMAP_CELL_DP.dp
            val gapDp = 3.dp
            val stepDp = cellDp + gapDp
            val scrollState = rememberScrollState()
            LaunchedEffect(calWeeks.size) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                // 364 个可点击 Box 改为一次 Canvas 绘制：组合节点从 364 降到 1
                Canvas(
                    modifier = Modifier
                        .width(stepDp * calWeeks.size)
                        .height(stepDp * 7)
                        // MOB-P1-80：热力图原本是「一个 Canvas」，在无障碍树里完全不存在 ——
                        // 读屏用户既听不到有多少天有记录，也无法聚焦。
                        // 这里补上整体描述与点击语义（每日计数由 Toast 播报）。
                        .semantics {
                            // MOB-P1-80：描述文本必须在 composable 作用域取值（semantics lambda 不是 composable）
                            contentDescription = heatmapDescription
                        }
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
    row: StreamRowItem,
    apiClient: APIClient,
    // MOB-P1-24：回调改为「传实体」的稳定签名 ——
    // 原先在 items{} 里写 `onClick = { selectedEntryForDetail = row.entry }`，
    // 每个 item 每次重组都会产生新 lambda ⇒ 卡片参数不稳定 ⇒ 整块 items 无法跳过重组，
    // 任意一项状态变化都会让屏幕内所有卡片重新组合。
    onClick: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = row.entry
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
            .clickable { onClick(entry) },
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
                        // MOB-P0-06：改读预格式化字段，不再每帧解析 ISO 时间串
                        text = row.displayTime,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        softWrap = false
                    )

                    val placeString = row.displayPlace
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
                    if (entry.syncStatus != SyncManager.STATUS_SYNCED) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accentWarm)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = { onDelete(entry) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.stream_entries_contentDescription_02),
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
                    color = colors.inkSecondary,
                    // MOB-P2-41：LazyColumn 以无限高度测量 item ——
                    // 一条 10000 字的长随笔会撑出 10000px 高的单个 item，
                    // 造成巨型布局 pass 与滚动掉帧。列表内截断，详情抽屉里再全文展开。
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
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
    /**
     * LazyColumn 的 item key（AND-2）。
     *
     * 基础值就是 `entry.id`（`serverId ?: -localId`）；本地库里若残留同 id 的
     * 重复行（历史脏数据），第二行起追加**出现次序号**，例如 `42#1`。
     * 序号只与「同一 id 的第几次出现」有关，与全局下标无关 ——
     * 顶部插入新记录时旧 key 保持不变，LazyColumn 可以继续复用已有行。
     */
    val localKey: String,
    val gapDays: Long,
    /** MOB-P0-06：预格式化的时间戳 —— 避免滚动期每帧每项重新解析 ISO 字符串 */
    val displayTime: String,
    /** MOB-P0-06：预拼接的属地字符串 */
    val displayPlace: String,
    /**
     * 上一条记录的 key（首条为 null）。
     *
     * 间隔天数依赖「上一条是谁」，刷新窗口时用它判断某一行能否原样复用，
     * 避免为新插入的记录把后面所有行都重新格式化一遍。
     */
    val previousKey: String? = null,
)
