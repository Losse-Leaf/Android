package com.looseleaf.app.ui.projectswishes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils

/**
 * 散页工坊 · 造物列表
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onDeleteClick: (Project) -> Unit = {},
    onNewProjectClick: () -> Unit = {},
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    if (projects.isEmpty()) {
        ArchiveEmptyState(
            title = "造物工坊还空着",
            actionText = "新建造物",
            onActionClick = onNewProjectClick,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        itemsIndexed(projects, key = { _, it -> it.id }) { index, proj ->
            ArchiveSwipeRevealRow(
                onDelete = { onDeleteClick(proj) },
                modifier = Modifier.fillMaxWidth()
            ) {
                ArchiveItemRow(
                    title = proj.title,
                    onClick = { onProjectClick(proj) },
                    trailingInfo = {
                        proj.status?.takeIf { it.isNotBlank() }?.let { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dotColor = when (status) {
                                    "进行中" -> colors.tagTravel // 蓝灰色
                                    "已完成" -> colors.inkSecondary // 中性灰板岩色（禁止使用绿/红/赤陶）
                                    "维护中" -> colors.tagProject // 大地赭黄
                                    else -> colors.inkMuted
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = status,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                    },
                    description = {
                        val desc = proj.description?.takeIf { it.isNotBlank() }
                            ?: proj.subtitle?.takeIf { it.isNotBlank() }
                        if (!desc.isNullOrBlank()) {
                            ArchiveItemDescription(text = desc, maxLines = 2)
                        }
                    },
                    metadata = proj.subtitle?.takeIf { it.isNotBlank() && !proj.description.isNullOrBlank() }?.let { sub ->
                        {
                            ArchiveItemMetadata(text = sub)
                        }
                    },
                    extraContent = proj.githubUrl?.takeIf { it.isNotBlank() }?.let { githubUrl ->
                        {
                            val cleanUrl = githubUrl.removePrefix("https://").removePrefix("http://")
                            val displayUrl = if (cleanUrl.length > 35 && cleanUrl.contains('/')) {
                                val repo = cleanUrl.substringAfterLast('/')
                                val prefix = cleanUrl.substring(0, 16.coerceAtMost(cleanUrl.lastIndexOf('/')))
                                "$prefix…/$repo"
                            } else {
                                cleanUrl
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            val target = if (githubUrl.startsWith("http://") || githubUrl.startsWith("https://")) {
                                                githubUrl
                                            } else {
                                                "https://$githubUrl"
                                            }
                                            runCatching { uriHandler.openUri(target) }
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setText(AnnotatedString(githubUrl))
                                            ArchiveToastManager.show("已复制链接")
                                        }
                                    )
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_link),
                                    contentDescription = "代码仓库链接",
                                    tint = colors.accentWarm,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = displayUrl,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.accentWarm,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    showDivider = index < projects.size - 1
                )
            }
        }
    }
}

/**
 * 散页工坊 · 心愿列表
 * 规则：
 * - 移除筛选 chip，改为按状态分组：向往中 → 已圆满 → 已拔草
 * - 分组标题带数量，空组不显示；已圆满与已拔草默认折叠，只显示标题和数量，点击展开
 * - 条目行尾：向往中显示文字按钮"达成归册"（赤陶），其余状态显示对应日期（等宽次要色）
 * - 去掉绿色圆圈对勾与灯泡图标，禁用绿色；描述合并最多 2 行；元信息显示预算与实际花费
 * - 左滑删除
 */
@Composable
internal fun WishesList(
    wishes: List<Wish>,
    onResolveClick: (Wish) -> Unit,
    onEditClick: (Wish) -> Unit,
    onDeleteClick: (Wish) -> Unit,
    onNewWishClick: () -> Unit = {},
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (wishes.isEmpty()) {
        ArchiveEmptyState(
            title = "心愿清单还空着",
            actionText = "许下心愿",
            onActionClick = onNewWishClick,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val wantingWishes = remember(wishes) { wishes.filter { it.status == "wanting" } }
    val fulfilledWishes = remember(wishes) { wishes.filter { it.status == "fulfilled" } }
    val relinquishedWishes = remember(wishes) { wishes.filter { it.status == "relinquished" } }

    var isFulfilledExpanded by rememberSaveable { mutableStateOf(false) }
    var isRelinquishedExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        // 1. 向往中分组
        if (wantingWishes.isNotEmpty()) {
            item(key = "header_wanting") {
                WishGroupHeader(
                    title = "向往中",
                    count = wantingWishes.size,
                    isCollapsible = false
                )
            }
            itemsIndexed(wantingWishes, key = { _, it -> it.id }) { index, wish ->
                ArchiveSwipeRevealRow(
                    onDelete = { onDeleteClick(wish) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WishItemRow(
                        wish = wish,
                        onItemClick = { onEditClick(wish) },
                        onResolveClick = { onResolveClick(wish) },
                        showDivider = index < wantingWishes.size - 1
                    )
                }
            }
        }

        // 2. 已圆满分组（默认折叠）
        if (fulfilledWishes.isNotEmpty()) {
            item(key = "header_fulfilled") {
                WishGroupHeader(
                    title = "已圆满",
                    count = fulfilledWishes.size,
                    isCollapsible = true,
                    isExpanded = isFulfilledExpanded,
                    onToggle = { isFulfilledExpanded = !isFulfilledExpanded }
                )
            }
            if (isFulfilledExpanded) {
                itemsIndexed(fulfilledWishes, key = { _, it -> it.id }) { index, wish ->
                    ArchiveSwipeRevealRow(
                        onDelete = { onDeleteClick(wish) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WishItemRow(
                            wish = wish,
                            onItemClick = { onEditClick(wish) },
                            onResolveClick = null,
                            showDivider = index < fulfilledWishes.size - 1
                        )
                    }
                }
            }
        }

        // 3. 已拔草分组（默认折叠）
        if (relinquishedWishes.isNotEmpty()) {
            item(key = "header_relinquished") {
                WishGroupHeader(
                    title = "已拔草",
                    count = relinquishedWishes.size,
                    isCollapsible = true,
                    isExpanded = isRelinquishedExpanded,
                    onToggle = { isRelinquishedExpanded = !isRelinquishedExpanded }
                )
            }
            if (isRelinquishedExpanded) {
                itemsIndexed(relinquishedWishes, key = { _, it -> it.id }) { index, wish ->
                    ArchiveSwipeRevealRow(
                        onDelete = { onDeleteClick(wish) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WishItemRow(
                            wish = wish,
                            onItemClick = { onEditClick(wish) },
                            onResolveClick = null,
                            showDivider = index < relinquishedWishes.size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WishGroupHeader(
    title: String,
    count: Int,
    isCollapsible: Boolean,
    isExpanded: Boolean = false,
    onToggle: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressBgColor = if (isPressed) colors.archiveCard else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(pressBgColor)
            .then(
                if (isCollapsible && onToggle != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                color = colors.inkMuted
            )
        }
        if (isCollapsible) {
            Text(
                text = if (isExpanded) "收起" else "展开",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkMuted
            )
        }
    }
}

@Composable
private fun WishItemRow(
    wish: Wish,
    onItemClick: () -> Unit,
    onResolveClick: (() -> Unit)?,
    showDivider: Boolean
) {
    val colors = LooseLeafTheme.colors

    ArchiveItemRow(
        title = wish.title,
        onClick = onItemClick,
        trailingInfo = {
            if (onResolveClick != null) {
                ArchiveTextButton(
                    text = "达成归册",
                    onClick = onResolveClick
                )
            } else {
                // 已圆满或已拔草：显示日期（等宽次要色）
                val dateStr = wish.updatedAt?.takeIf { it.isNotBlank() } ?: wish.targetDate
                if (!dateStr.isNullOrBlank()) {
                    val formatted = FormatUtils.formatDisplayDate(dateStr)
                    Text(
                        text = formatted,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }
            }
        },
        description = wish.note?.takeIf { it.isNotBlank() }?.let { note ->
            {
                ArchiveItemDescription(text = note, maxLines = 2)
            }
        },
        metadata = if (wish.budget > 0 || wish.actualCost > 0) {
            {
                val budgetYuan = FormatUtils.centsToYuanString(wish.budget)
                val actualYuan = FormatUtils.centsToYuanString(wish.actualCost)
                val metaText = when {
                    wish.budget > 0 && wish.actualCost > 0 -> "预算 $budgetYuan · 实际 $actualYuan"
                    wish.budget > 0 -> "预算 $budgetYuan"
                    else -> "实际 $actualYuan"
                }
                ArchiveItemMetadata(text = metaText)
            }
        } else null,
        showDivider = showDivider
    )
}

/**
 * 散页工坊 · 山川足迹列表
 * 规则：
 * - 顶部概览（无卡片）：大号等宽数字 N + "座城市已点亮"，下一行次要色"M 个省份"
 * - 列表按年份分组（分组标题"2026"，次要色小字）
 * - 条目：第一行 城市名（衬线 SemiBold）+ 行尾日期（等宽次要色，如 10.11）
 * - 第二行："行旅手记 · 徒步"（次要色，单行省略，去除城市名重复前缀）
 * - 第三行："N 篇手记"（次要色小字，0 篇不显示）
 * - 去掉编号和撕票线，点击进入手记
 */
@Composable
internal fun FootprintsList(
    footprints: List<Footprint>,
    onFootprintClick: (Footprint) -> Unit,
    onNewFootprintClick: () -> Unit = {},
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (footprints.isEmpty()) {
        ArchiveEmptyState(
            title = "山川足迹还空着",
            actionText = "点亮足迹",
            onActionClick = onNewFootprintClick,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val uniqueCities = remember(footprints) {
        footprints.map { it.cityCode.ifBlank { it.cityName } }.distinct().size
    }
    val uniqueProvinces = remember(footprints) {
        footprints.mapNotNull {
            val code = it.cityCode.trim()
            if (code.length >= 2) code.substring(0, 2) else null
        }.distinct().size
    }

    val groupedByYear = remember(footprints) {
        footprints.groupBy { foot ->
            val dateStr = foot.visitedAt ?: ""
            if (dateStr.length >= 4) dateStr.substring(0, 4) else "其它"
        }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        // 顶部概览
        item(key = "footprint_overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = uniqueCities.toString(),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "座城市已点亮",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 14.sp,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (uniqueProvinces > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$uniqueProvinces 个省份",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }
            }
        }

        // 按年份分组渲染
        groupedByYear.forEach { (year, yearFootprints) ->
            item(key = "year_$year") {
                Text(
                    text = year,
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            itemsIndexed(yearFootprints, key = { _, it -> it.id }) { index, foot ->
                val displayDate = remember(foot.visitedAt) {
                    val full = FormatUtils.formatDisplayDate(foot.visitedAt)
                    if (full.length >= 10 && full[4] == '.') full.substring(5) else full
                }

                // 山川条目第二行：显示手记标题或首行内容（单行省略），没有手记就不显示这一行；去掉固定的"行旅手记"前缀
                val rawTitle = foot.title?.trim()
                    ?.removePrefix("${foot.cityName} · ")
                    ?.removePrefix("${foot.cityName}·")
                    ?.removePrefix("${foot.cityName} ")
                    ?.removePrefix("行旅手记 · ")
                    ?.removePrefix("行旅手记·")
                    ?.removePrefix("行旅手记")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val firstLineNote = foot.note?.lineSequence()
                    ?.map { it.trim() }
                    ?.firstOrNull { it.isNotBlank() }

                val secondLineText = rawTitle ?: firstLineNote
                val descriptionContent: (@Composable () -> Unit)? = if (!secondLineText.isNullOrBlank()) {
                    {
                        ArchiveItemDescription(text = secondLineText, maxLines = 1)
                    }
                } else null

                val entriesCount = foot.entriesCount ?: 0

                ArchiveItemRow(
                    title = foot.cityName,
                    onClick = { onFootprintClick(foot) },
                    trailingInfo = {
                        Text(
                            text = displayDate,
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    },
                    description = descriptionContent,
                    metadata = if (entriesCount > 0) {
                        {
                            ArchiveItemMetadata(text = "$entriesCount 篇手记")
                        }
                    } else null,
                    showDivider = index < yearFootprints.size - 1
                )
            }
        }
    }
}
