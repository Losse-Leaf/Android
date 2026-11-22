package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils

/**
 * 账本 · 心愿单列表
 * 规则：
 * - 按状态分组：向往中 → 已圆满 → 已拔草
 * - 分组标题带数量，空组不显示；已圆满与已拔草默认折叠，点击展开
 * - 条目行尾：向往中显示文字按钮「达成归册」，其余状态显示对应日期
 * - 左滑删除
 */
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
        state = actualListState,
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
