package com.looseleaf.app.ui.wishes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.ui.components.ArchiveEmptyState
import com.looseleaf.app.ui.components.ArchiveSwipeRevealRow
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.utils.FormatUtils

/**
 * 账本 · 心愿单
 *
 * - 按状态分组为三张纸页卡片：向往中 → 已圆满 → 已拔草；空组不显示；
 * - 「向往中」常驻展开；「已圆满」「已拔草」默认折叠，点组头展开；
 * - 条目左侧为状态印记：向往中是赭金空心环，已圆满是松绿实心对勾，已拔草是墨灰斜线；
 * - 向往中的条目行尾是一枚「达成」描边胶囊；其余状态行尾显示日期；
 * - 左滑删除，点击编辑。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WishesList(
    wishes: List<Wish>,
    onResolveClick: (Wish) -> Unit,
    onEditClick: (Wish) -> Unit,
    onDeleteClick: (Wish) -> Unit,
    onNewWishClick: () -> Unit = {},
    listState: LazyListState? = null,
    modifier: Modifier = Modifier
) {
    val actualListState = listState ?: rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    if (wishes.isEmpty()) {
        ArchiveEmptyState(
            title = "心愿清单还空着",
            subtitle = "把想要的东西先写下来，等它真正值得时再带回家",
            actionText = "许下心愿",
            onActionClick = onNewWishClick,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val wantingWishes = remember(wishes) { wishes.filter { it.status == "wanting" } }
    val fulfilledWishes = remember(wishes) { wishes.filter { it.status == "fulfilled" } }
    val relinquishedWishes = remember(wishes) { wishes.filter { it.status == "relinquished" } }
    val wantingBudget = remember(wantingWishes) { wantingWishes.sumOf { it.budget } }

    var isFulfilledExpanded by rememberSaveable { mutableStateOf(false) }
    var isRelinquishedExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = actualListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)
    ) {
        // 1. 向往中
        if (wantingWishes.isNotEmpty()) {
            item(key = "header_wanting") {
                WishGroupHeader(
                    title = "向往中",
                    count = wantingWishes.size,
                    summary = if (wantingBudget > 0) "预算合计 ${FormatUtils.formatMoney(wantingBudget)}" else null,
                    isCollapsible = false
                )
            }
            itemsIndexed(wantingWishes, key = { _, it -> it.id }) { index, wish ->
                WishCardSlot(index = index, total = wantingWishes.size, modifier = Modifier.animateItem()) {
                    ArchiveSwipeRevealRow(
                        onDelete = { onDeleteClick(wish) },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = LooseLeafTheme.colors.archiveCard
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
        }

        // 2. 已圆满
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
                    WishCardSlot(index = index, total = fulfilledWishes.size, modifier = Modifier.animateItem()) {
                        ArchiveSwipeRevealRow(
                            onDelete = { onDeleteClick(wish) },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = LooseLeafTheme.colors.archiveCard
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
        }

        // 3. 已拔草
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
                    WishCardSlot(index = index, total = relinquishedWishes.size, modifier = Modifier.animateItem()) {
                        ArchiveSwipeRevealRow(
                            onDelete = { onDeleteClick(wish) },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = LooseLeafTheme.colors.archiveCard
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
}

/**
 * 把一组条目拼成一张连续的纸页卡片：首项圆上角、末项圆下角，中间直角，两侧发丝边。
 */
@Composable
private fun WishCardSlot(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val radius = 20.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == total - 1) radius else 0.dp,
        bottomEnd = if (index == total - 1) radius else 0.dp
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.archiveCard)
            .border(ArchiveLines.hairline, colors.borderCard, shape)
    ) {
        content()
    }
}

@Composable
private fun WishGroupHeader(
    title: String,
    count: Int,
    isCollapsible: Boolean,
    isExpanded: Boolean = false,
    summary: String? = null,
    onToggle: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseStandard),
        label = "wishGroupChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (isCollapsible && onToggle != null) {
                    Modifier
                        .clip(ArchiveShapes.md)
                        .clickable(role = Role.Button, onClick = onToggle)
                } else Modifier
            )
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(2.dp)
                .clip(ArchiveShapes.full)
                .background(if (isCollapsible) colors.inkMuted else colors.tagWish)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = ArchiveTextStyles.overline,
            color = colors.inkSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = ArchiveTextStyles.meta,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        if (summary != null) {
            Text(
                text = summary,
                style = ArchiveTextStyles.meta,
                color = colors.inkMuted,
                maxLines = 1
            )
        }
        if (isCollapsible) {
            Text(
                text = if (isExpanded) "收起" else "展开",
                style = ArchiveTextStyles.meta,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = FolioGlyphs.ChevronDown,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressBg by animateColorAsState(
        targetValue = if (isPressed) colors.pressOverlay else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "wishPress"
    )
    val isRelinquished = wish.status == "relinquished"
    val isFulfilled = wish.status == "fulfilled"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.archiveCard)
            .background(pressBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onItemClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.Top
        ) {
            WishStatusMark(status = wish.status)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wish.title,
                    style = ArchiveTextStyles.essayTitle.copy(
                        fontSize = ArchiveTextStyles.rowTitle.fontSize,
                        lineHeight = ArchiveTextStyles.rowTitle.lineHeight,
                        textDecoration = if (isRelinquished) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isRelinquished) colors.inkMuted else colors.inkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val note = wish.note?.takeIf { it.isNotBlank() }
                if (note != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = note,
                        style = ArchiveTextStyles.body,
                        color = colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (wish.budget > 0 || wish.actualCost > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (wish.budget > 0) {
                            WishMetaChip(label = "预算", value = FormatUtils.centsToYuanString(wish.budget))
                        }
                        if (wish.actualCost > 0) {
                            WishMetaChip(
                                label = if (isFulfilled) "实付" else "实际",
                                value = FormatUtils.centsToYuanString(wish.actualCost),
                                emphasized = isFulfilled
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (onResolveClick != null) {
                Box(
                    modifier = Modifier
                        .clip(ArchiveShapes.full)
                        .border(ArchiveLines.hairline, colors.tagWish.copy(alpha = 0.6f), ArchiveShapes.full)
                        .clickable(role = Role.Button, onClick = onResolveClick)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "达成",
                        style = ArchiveTextStyles.label,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.tagWish
                    )
                }
            } else {
                val dateStr = wish.updatedAt?.takeIf { it.isNotBlank() } ?: wish.targetDate
                if (!dateStr.isNullOrBlank()) {
                    Text(
                        text = FormatUtils.formatDisplayDate(dateStr),
                        style = ArchiveTextStyles.meta,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        if (showDivider) {
            ArchiveDivider(modifier = Modifier.padding(start = 54.dp))
        }
    }
}

/** 状态印记：向往中空心环、已圆满实心对勾、已拔草斜线 */
@Composable
private fun WishStatusMark(status: String) {
    val colors = LooseLeafTheme.colors
    when (status) {
        "fulfilled" -> Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(24.dp)
                .clip(ArchiveShapes.full)
                .background(colors.accentMoss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FolioGlyphs.Check,
                contentDescription = "已圆满",
                tint = colors.onAccent,
                modifier = Modifier.size(15.dp)
            )
        }
        "relinquished" -> Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(24.dp)
                .clip(ArchiveShapes.full)
                .background(colors.archiveSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FolioGlyphs.Close,
                contentDescription = "已拔草",
                tint = colors.inkMuted,
                modifier = Modifier.size(13.dp)
            )
        }
        else -> Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(24.dp)
                .clip(ArchiveShapes.full)
                .border(1.5.dp, colors.tagWish, ArchiveShapes.full),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.tagWish.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun WishMetaChip(label: String, value: String, emphasized: Boolean = false) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .clip(ArchiveShapes.full)
            .background(if (emphasized) colors.positiveTint else colors.archiveSubtle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ArchiveTextStyles.meta,
            color = if (emphasized) colors.accentMoss else colors.inkMuted
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = value,
            style = ArchiveTextStyles.meta,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) colors.accentMoss else colors.inkPrimary
        )
    }
}
