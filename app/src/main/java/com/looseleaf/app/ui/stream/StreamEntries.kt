package com.looseleaf.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.utils.FormatUtils

/** 热力图单格边长（dp），供 LifeActivityHeatmap 与其调用方共享 */
internal const val HEATMAP_CELL_DP = 12

/**
 * 那年今日 (On This Day)
 * 一张带朱砂时钟徽记的纸页卡片；展开后列出往年同日的散页，每条左侧一道细竖线串起时间。
 */
@Composable
internal fun OnThisDayCard(
    entries: List<LifeEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseStandard),
        label = "onThisDayChevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.archiveCard)
            .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.accentTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FolioGlyphs.Clock,
                    contentDescription = null,
                    tint = colors.accentSmallText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.stream_entries_text_tpl_01, entries.size),
                style = ArchiveTextStyles.essayTitle.copy(
                    fontSize = ArchiveTextStyles.rowTitle.fontSize,
                    lineHeight = ArchiveTextStyles.rowTitle.lineHeight
                ),
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = FolioGlyphs.ChevronDown,
                contentDescription = stringResource(R.string.stream_entries_contentDescription_01),
                tint = colors.inkMuted,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized)) +
                fadeIn(tween(ArchiveMotion.DurationBase)),
            exit = shrinkVertically(tween(ArchiveMotion.DurationFast)) + fadeOut(tween(ArchiveMotion.DurationFast))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.sm)
                            .clickable(role = Role.Button) { onSelectEntry(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 14.dp)
                                .width(2.dp)
                                .height(30.dp)
                                .clip(ArchiveShapes.full)
                                .background(colors.accentWarm.copy(alpha = 0.35f))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifBlank { "无题" },
                                style = ArchiveTextStyles.essay.copy(fontWeight = FontWeight.Medium),
                                color = colors.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.content.isNotBlank()) {
                                Text(
                                    text = item.content,
                                    style = ArchiveTextStyles.meta,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = FormatUtils.formatDisplayDate(item.occurredAt),
                            style = ArchiveTextStyles.meta,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * 散页配图 (Stream Image Attachments)
 * - 1 张：4:3 横幅；2 / 4 张：方形双列；其余：方形三列
 * - 12dp 圆角、4dp 间隙，加载前以相纸衬底色占位
 */
@Composable
internal fun StreamImageAttachments(
    attachments: List<Attachment>,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    val gap = 4.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        when (attachments.size) {
            1 -> {
                AttachmentTile(
                    attachment = attachments.first(),
                    apiClient = apiClient,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                )
            }
            2, 4 -> {
                attachments.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        row.forEach { att ->
                            AttachmentTile(
                                attachment = att,
                                apiClient = apiClient,
                                onPhotoClick = onPhotoClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
            else -> {
                attachments.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        row.forEach { att ->
                            AttachmentTile(
                                attachment = att,
                                apiClient = apiClient,
                                onPhotoClick = onPhotoClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
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
private fun AttachmentTile(
    attachment: Attachment,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LooseLeafTheme.colors
    val fullUrl = remember(attachment.url) { apiClient.resolveMediaUrl(attachment.url) }
    val meta = attachment.originalFilename
    Box(
        modifier = modifier
            .clip(ArchiveShapes.sm)
            .background(colors.filmMatteBg)
            .clickable(role = Role.Image) { onPhotoClick(fullUrl, meta) }
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
 * 月份标题：一枚朱砂短线 + 加宽字距的「2026 年 11 月」，跨月时出现。
 */
@Composable
internal fun StreamMonthHeader(
    monthDisplay: String,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false
) {
    val colors = LooseLeafTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 4.dp else 30.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .clip(ArchiveShapes.full)
                .background(colors.accentWarm)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = monthDisplay,
            style = ArchiveTextStyles.overline,
            color = colors.inkSecondary
        )
    }
}

/**
 * 日签（吸顶）：衬线大字日期 + 星期 / 相对日的两行小字 + 延伸到右缘的发丝线。
 * 吸顶时背景为不透明画布色，防止下方纸页穿透。
 */
@Composable
internal fun StreamDayHeader(
    dayOfMonth: Int,
    @Suppress("UNUSED_PARAMETER") monthValue: Int,
    weekday: String,
    relativeDay: String?,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.archivePaper)
            .padding(top = 14.dp, bottom = 10.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayOfMonth.toString().padStart(2, '0'),
            style = ArchiveTextStyles.dayNumeral,
            color = colors.inkPrimary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = weekday,
                style = ArchiveTextStyles.meta,
                fontWeight = FontWeight.Medium,
                color = colors.inkSecondary
            )
            if (!relativeDay.isNullOrBlank()) {
                Text(
                    text = relativeDay,
                    style = ArchiveTextStyles.meta,
                    color = colors.accentSmallText
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.borderSubtle)
        )
    }
}

/**
 * 散页条目 (Stream Entry Leaf)
 *
 * 每一条记录是一张独立的散页，放在画布上：
 * - 纸页色底、20dp 圆角、发丝边；按下时轻微下沉（缩放 98.5%）并浮现墨色薄层；
 * - 首行：时间（等宽）· 分类（色点 + 同色字），右侧为同步状态签（已同步时不显示）；
 * - 有图时配图在标题之上；标题衬线、摘要衬线宽行距最多三行；末行地点。
 * - 长按删除（带触感），点击查看详情。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamEntryCard(
    row: StreamRowItem,
    apiClient: APIClient,
    onClick: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = row.entry
    val colors = LooseLeafTheme.colors
    val view = androidx.compose.ui.platform.LocalView.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "leafPress"
    )

    val syncState: Pair<String, Boolean>? = when (entry.syncStatus) {
        SyncManager.STATUS_SYNCED -> null
        SyncManager.STATUS_PENDING_CREATE,
        SyncManager.STATUS_PENDING_UPDATE,
        SyncManager.STATUS_PENDING_DELETE -> "待同步" to false
        else -> "待重试" to true
    }
    val syncAccessibilityDesc = when (entry.syncStatus) {
        SyncManager.STATUS_SYNCED -> "已安全归档"
        SyncManager.STATUS_PENDING_CREATE,
        SyncManager.STATUS_PENDING_UPDATE,
        SyncManager.STATUS_PENDING_DELETE -> "本地修改待同步放行"
        else -> "同步遇到问题待重验"
    }

    val (tagLabel, tagTint) = when (entry.type) {
        "wish" -> "心愿" to colors.tagWish
        else -> "日常" to colors.tagDaily
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(ArchiveShapes.leaf)
                .background(colors.archiveCard)
                .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf)
                .background(if (isPressed) colors.pressOverlay else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onLongClickLabel = "删除这一页",
                    onClick = { onClick(entry) },
                    onLongClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        onDelete(entry)
                    }
                )
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)
        ) {
            // 首行：时间 · 分类 …… 同步签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.timeDisplay.ifBlank { row.displayTime },
                    style = ArchiveTextStyles.meta,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(tagTint)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tagLabel,
                    style = ArchiveTextStyles.meta,
                    fontWeight = FontWeight.Medium,
                    color = tagTint
                )
                Spacer(modifier = Modifier.weight(1f))
                if (syncState != null) {
                    val (syncLabel, isProblem) = syncState
                    val syncTint = if (isProblem) colors.alertRed else colors.accentSmallText
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .semantics { contentDescription = syncAccessibilityDesc }
                            .clip(ArchiveShapes.full)
                            .background(if (isProblem) colors.negativeTint else colors.accentTint)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(syncTint)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = syncLabel,
                            style = ArchiveTextStyles.meta,
                            color = syncTint
                        )
                    }
                }
            }

            if (entry.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                StreamImageAttachments(
                    attachments = entry.attachments,
                    apiClient = apiClient,
                    onPhotoClick = onPhotoClick
                )
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (entry.title.isNotBlank()) {
                Text(
                    text = entry.title,
                    style = ArchiveTextStyles.essayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary
                )
            }

            if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                Spacer(modifier = Modifier.height(if (entry.title.isNotBlank()) 6.dp else 0.dp))
                Text(
                    text = entry.content,
                    style = ArchiveTextStyles.essay,
                    color = colors.inkSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (row.displayPlace.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = FolioGlyphs.Pin,
                        contentDescription = "地点",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = row.displayPlace,
                        style = ArchiveTextStyles.meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.inkMuted
                    )
                }
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
