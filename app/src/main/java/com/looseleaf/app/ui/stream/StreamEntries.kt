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
import androidx.compose.ui.semantics.Role
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
                        fontSize = 14.sp,
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
                            .clip(RoundedCornerShape(4.dp))
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
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = colors.inkMuted
        )
    }
}

/**
 * 日期分组行（23 周五 + 今天/昨天次要色小字；月份已由上方月份分组标题承担，避免重复）
 * 吸顶时背景为不透明纸色防止内容穿透
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
            .padding(top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayOfMonth.toString().padStart(2, '0'),
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            color = colors.inkPrimary,
            modifier = Modifier.alignByBaseline()
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = weekday,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = colors.inkSecondary,
            modifier = Modifier.alignByBaseline()
        )

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
                    role = Role.Button,
                    onLongClickLabel = "删除这一页",
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
                // 第一行：时间（等宽） + 低饱和四色分类微标签（色点 + 同色文字，双重编码易辨识）
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
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp,
                        color = colors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(tint)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = tint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 有图时展示紧凑配图条；无图时压紧元信息与标题间距至 4dp，提升一屏信息密度
                if (entry.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StreamImageAttachments(
                        attachments = entry.attachments,
                        apiClient = apiClient,
                        onPhotoClick = onPhotoClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 第二行：标题（衬线 SemiBold，最多 2 行）
                Text(
                    text = entry.title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary
                )

                // 第三行：摘要（最多 2 行，次要色，空则省略）
                if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.content,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.1.sp,
                        color = colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 最后一行：位置等元信息（次要色小字，无则省略）
                if (row.displayPlace.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
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
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // 条目间距收敛至 20dp（10dp + 1dp 淡线 + 10dp），一屏可容纳 5~6 条纯文本手记
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderDivider)
        )
        Spacer(modifier = Modifier.height(10.dp))
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
