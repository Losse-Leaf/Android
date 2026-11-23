package com.looseleaf.app.ui.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveHeaderIconButton
import com.looseleaf.app.ui.components.FolioGlyphs
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 散页 · 刊头 (Stream Masthead)
 *
 * 两段式结构，彻底避免「日期与工具组抢宽度」：
 * - 顶栏（最小 60dp，内容垂直居中）：左侧为收起态的小标题，右侧为工具组（邮戳 / 搜索 / 筛选 / 设置）。
 *   小标题用 weight(1f) 占据工具组以外的全部宽度，收起态下与右侧按钮严格居中对齐。
 * - 展开区：日期一行 + 方印与「散页」大标题一行，各自独占整行宽度，不与工具组并排，
 *   因此在 360dp 窄屏与系统大字号下都不会被省略号截断（极端字号下允许自然换行，而不是截断）。
 *
 * 收起时展开区的高度按 (1 - collapseFraction) 等比收缩并淡出，小标题随之淡入。
 * 展开区的完整高度通过 [onCollapsibleHeightChanged] 回报给宿主，
 * 让嵌套滚动吃掉的距离恰好等于刊头收缩的距离，列表不会出现「两倍速」位移。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamCoverHeader(
    collapseFraction: Float,
    activeFilterCount: Int,
    onFilterClick: () -> Unit,
    onSearchClick: () -> Unit,
    isSyncing: Boolean,
    pendingCount: Int,
    failedCount: Int,
    onFailedStampClick: () -> Unit,
    onPendingStampClick: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onTitleLongClick: (() -> Unit)? = null,
    onCollapsibleHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val expandedAlpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
    val compactAlpha = ((fraction - 0.55f) / 0.45f).coerceIn(0f, 1f)

    val dateLine = remember {
        val now = LocalDate.now(com.looseleaf.app.utils.FormatUtils.BeijingZone)
        val months = listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
        val weekday = when (now.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
        "${now.year} · ${months[now.monthValue - 1]}${now.dayOfMonth}日 · $weekday"
    }

    val titleClickModifier = if (onTitleLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onLongClick = onTitleLongClick
        )
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.archivePaper)
            .padding(start = ArchiveSpacing.gutter, end = 10.dp)
    ) {
        // ---------- 顶栏：收起态小标题 + 工具组 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = compactAlpha }
                    .then(if (compactAlpha > 0f) titleClickModifier else Modifier)
            ) {
                MastheadSeal(size = 16.dp)
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "散页",
                    fontFamily = ArchiveFontFamilies.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 21.sp,
                    letterSpacing = 2.sp,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SyncPostmarkStamp(
                    isSyncing = isSyncing,
                    pendingCount = pendingCount,
                    failedCount = failedCount,
                    onFailedClick = onFailedStampClick,
                    onPendingClick = onPendingStampClick,
                    modifier = Modifier.padding(end = 4.dp)
                )
                ArchiveHeaderIconButton(
                    onClick = onSearchClick,
                    contentDescription = "搜索",
                    imageVector = FolioGlyphs.Search
                )
                ArchiveHeaderIconButton(
                    onClick = onFilterClick,
                    contentDescription = if (activeFilterCount > 0) "筛选，已启用 $activeFilterCount 项" else "筛选",
                    imageVector = FolioGlyphs.Filter,
                    highlighted = activeFilterCount > 0,
                    badge = if (activeFilterCount > 0) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentWarm)
                            )
                        }
                    } else null
                )
                if (onOpenSettings != null) {
                    ArchiveHeaderIconButton(
                        onClick = onOpenSettings,
                        contentDescription = "设置",
                        imageVector = FolioGlyphs.Tune
                    )
                }
            }
        }

        // ---------- 展开区：日期 + 方印大标题，各占整行 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    // 按收起比例等比收缩高度（内容自身按完整尺寸测量，不会被压扁或截断）
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val visibleHeight = (placeable.height * (1f - fraction)).roundToInt()
                    layout(placeable.width, visibleHeight) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .onSizeChanged { onCollapsibleHeightChanged(it.height) }
                .graphicsLayer { alpha = expandedAlpha }
                .padding(bottom = 14.dp)
        ) {
            Text(
                text = dateLine,
                style = ArchiveTextStyles.overline,
                color = colors.inkMuted,
                // 宁可换行也不截断：日期独占整行，正常字号下一行即可放下
                softWrap = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = titleClickModifier
            ) {
                MastheadSeal(size = 22.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "散页",
                    fontFamily = ArchiveFontFamilies.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    letterSpacing = 4.sp,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * 刊头小方印：圆角方块里一个反白的「页」字。
 */
@Composable
private fun MastheadSeal(size: Dp) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = -4f }
            .clip(ArchiveShapes.xs)
            .background(colors.accentWarm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "页",
            fontFamily = ArchiveFontFamilies.Display,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.58f).sp,
            color = colors.onAccent,
            maxLines = 1,
            softWrap = false
        )
    }
}
