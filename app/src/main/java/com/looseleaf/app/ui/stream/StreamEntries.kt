package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LocalArchiveDecorConfig
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.radiusSmall)
            .border(1.dp, colors.borderDivider, ArchiveShapes.radiusSmall)
            .padding(14.dp)
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
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stream_entries_text_tpl_01, entries.size),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = colors.inkPrimary
                    )
                }
                Icon(
                    painter = if (isExpanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.stream_entries_contentDescription_01),
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderDivider)
                )
                Spacer(modifier = Modifier.height(10.dp))

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

    Column(modifier = modifier.fillMaxWidth()) {
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

/**
 * 记录配图网格 (Stream Image Attachments)
 * 统一规范：放在标题上方，4dp 圆角，宽度与内容齐，不加边框和旋转
 */
@Composable
internal fun StreamImageAttachments(
    attachments: List<Attachment>,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        val count = attachments.size
        when (count) {
            1 -> {
                val att = attachments.first()
                val fullUrl = apiClient.resolveMediaUrl(att.url)
                val meta = att.originalFilename
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onPhotoClick(fullUrl, meta) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fullUrl)
                            .crossfade(true)
                            .diskCacheKey(fullUrl)
                            .build(),
                        contentDescription = meta ?: "记录照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            2, 4 -> {
                attachments.chunked(2).forEachIndexed { rowIdx, row ->
                    if (rowIdx > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { att ->
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onPhotoClick(fullUrl, meta) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fullUrl)
                                        .crossfade(true)
                                        .diskCacheKey(fullUrl)
                                        .build(),
                                    contentDescription = meta ?: "记录照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                attachments.chunked(3).forEachIndexed { rowIdx, row ->
                    if (rowIdx > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { att ->
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onPhotoClick(fullUrl, meta) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fullUrl)
                                        .crossfade(true)
                                        .diskCacheKey(fullUrl)
                                        .build(),
                                    contentDescription = meta ?: "记录照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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

/** 兼容别名 */
@Composable
internal fun EntryAttachmentsGrid(
    attachments: List<Attachment>,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    StreamImageAttachments(
        attachments = attachments,
        apiClient = apiClient,
        onPhotoClick = onPhotoClick,
        modifier = modifier
    )
}

/**
 * 月份小标题 (纯文字小标题：小字号、字间距 0.1em、次要色，不加两侧线条)
 */
@Composable
internal fun StreamMonthHeader(
    monthDisplay: String,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false
) {
    val colors = LooseLeafTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 0.dp else 16.dp, bottom = 0.dp)
    ) {
        Text(
            text = monthDisplay,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.5.sp,
            letterSpacing = 1.2.sp,
            color = colors.inkMuted
        )
    }
}

/**
 * 日期分组行 (27 大号衬线数字 + 10月 · 周二 正体衬线次要色 + 今天/昨天次要色小字，无虚线与剪刀)
 * 吸顶时文字上沿与标签行底部分割线保持 8dp，背景为不透明纸色防止内容穿透
 */
@Composable
internal fun StreamDayHeader(
    dayOfMonth: Int,
    monthValue: Int,
    weekday: String,
    relativeDay: String?,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.archivePaper)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "27" 大号衬线数字 (28sp Serif Bold)
        Text(
            text = dayOfMonth.toString().padStart(2, '0'),
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 28.sp,
            color = colors.inkPrimary,
            modifier = Modifier.alignByBaseline()
        )

        Spacer(modifier = Modifier.width(8.dp))

        // "10月 · 周二" 正体衬线次要色
        Text(
            text = "${monthValue}月 · $weekday",
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = colors.inkSecondary,
            modifier = Modifier.alignByBaseline()
        )

        // "今天/昨天" 仅为次要色文字，不用赤陶、不加描边
        relativeDay?.let { rel ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = rel,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = colors.inkMuted,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/** 兼容旧调用的和纸便签桩（已移除） */
@Composable
internal fun WashiTapeBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    // 兼容空桩
}

/**
 * 散页手账记录条目 (Stream Entry Item)
 *
 * 统一化规范：
 * - 去掉白色卡片底、描边和投影。每条记录直接写在纸面上，
 *   条目之间用 24dp 间距加一条 1dp 淡线（内容左对齐，线从内容起点开始）
 * - 内部结构：
 *   第一行：时间（等宽、次要色）+ 右侧分类色点与文字；
 *   有图时：图片放在条目内标题上方，4dp 圆角，宽度与内容齐，不加边框和旋转；
 *   第二行：标题（衬线 SemiBold，最多 2 行）；
 *   第三行：摘要（最多 3 行，次要色，行高 1.5，空则省略）；
 *   最后一行：位置等元信息（次要色小字，无则省略）
 * - 按压态：背景短暂变为"略亮一档的纸面"，无缩放动画
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamEntryCard(
    row: StreamRowItem,
    apiClient: APIClient,
    onClick: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    washiTapeText: String? = null,
    modifier: Modifier = Modifier
) {
    val entry = row.entry
    val colors = LooseLeafTheme.colors
    val view = androidx.compose.ui.platform.LocalView.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压态：背景短暂变为"略亮一档的纸面"，无缩放动画
    val pressedBg = if (isPressed) colors.archiveCard else Color.Transparent

    // 同步状态左侧 3dp 细条
    val syncStatusColor = when (entry.syncStatus) {
        SyncManager.STATUS_SYNCED -> Color.Transparent
        SyncManager.STATUS_PENDING_CREATE,
        SyncManager.STATUS_PENDING_UPDATE,
        SyncManager.STATUS_PENDING_DELETE -> colors.tagProject
        else -> colors.accentWarm
    }
    val syncAccessibilityDesc = when (entry.syncStatus) {
        SyncManager.STATUS_SYNCED -> "已安全归档"
        SyncManager.STATUS_PENDING_CREATE,
        SyncManager.STATUS_PENDING_UPDATE,
        SyncManager.STATUS_PENDING_DELETE -> "本地修改待同步放行"
        else -> "同步遇到问题待重验"
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(pressedBg)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onClick(entry) },
                    onLongClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        onDelete(entry)
                    }
                )
        ) {
            // 同步状态左侧 3dp 细条
            if (syncStatusColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .background(syncStatusColor)
                        .semantics { contentDescription = syncAccessibilityDesc }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：时间（等宽）· 分类色点 + 分类名，全部靠左；右侧不再放任何元信息
                val (label, tint) = when (entry.type) {
                    "travel" -> "游历" to colors.tagTravel
                    "project" -> "造物" to colors.tagProject
                    "wish" -> "心愿" to colors.tagWish
                    else -> "日常" to colors.tagDaily
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.timeDisplay.ifBlank { row.displayTime },
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp,
                        color = colors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = " · ",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = label,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.5.sp,
                        color = colors.inkSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }

                // 有图时：图片放在条目内标题上方，4dp 圆角，宽度与内容齐，不加边框和旋转
                // 图片上方与时间行 8dp，下方与标题 12dp；无图时时间行与标题 6dp
                if (entry.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StreamImageAttachments(
                        attachments = entry.attachments,
                        apiClient = apiClient,
                        onPhotoClick = onPhotoClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 第二行：标题（衬线 SemiBold，最多 2 行）
                Text(
                    text = entry.title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.5.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary
                )

                // 第三行：摘要（最多 3 行，次要色，行高 1.5，空则省略）
                // 标题→摘要 4dp
                if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.content,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.1.sp,
                        color = colors.inkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 最后一行：位置等元信息（次要色小字，无则省略）
                // 摘要→位置 8dp
                if (row.displayPlace.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "地点",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = row.displayPlace,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // 条目之间：内容底部 16dp → 1dp 分隔线 → 16dp
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderDivider)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 时间流列表行：把「距上一条的天数」预计算进不可变行对象，
 * 让 LazyColumn 滚动期只做字段读取（AOPT-03b）。
 */
@Immutable
internal data class StreamRowItem(
    val entry: LifeEntry,
    val localKey: String,
    val gapDays: Long,
    val displayTime: String,
    val displayPlace: String,
    val previousKey: String? = null,
    val isFirstOfMonth: Boolean = false,
    val isFirstOfDay: Boolean = false,
    val year: Int = 0,
    val monthValue: Int = 0,
    val dayOfMonth: Int = 0,
    val weekdayName: String = "",
    val relativeDay: String? = null,
    val timeDisplay: String = "",
    val monthDisplay: String = ""
)
