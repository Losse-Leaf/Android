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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveHeaderIconButton
import com.looseleaf.app.ui.components.FolioGlyphs
import java.time.LocalDate

/**
 * 散页 · 刊头 (Stream Masthead)
 *
 * 展开态（列表在顶部）：
 *   第一行：日期小标（「十一月 二十三 · 周一」加宽字距）+ 右侧工具（搜索 / 筛选 / 邮戳 / 设置）
 *   第二行：一枚朱砂小方印 + 衬线大字「散页」
 * 收起态（列表上滑）：刊头高度从 116dp 收到 60dp，大字缩到 21sp，日期淡出，
 *   工具组始终停在右上，与标题落在同一条基线带上。
 * 长按标题保留原有的隐藏入口回调。
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
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val fraction = collapseFraction.coerceIn(0f, 1f)

    val headerHeight = lerp(116.dp, 60.dp, fraction)
    val titleSize = lerp(34.sp, 21.sp, fraction)
    val sealSize = lerp(22.dp, 15.dp, fraction)
    val dateAlpha = (1f - fraction * 2.4f).coerceIn(0f, 1f)
    val dateHeight = lerp(22.dp, 0.dp, (fraction * 1.6f).coerceIn(0f, 1f))

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
        "${now.year}  ·  ${months[now.monthValue - 1]} ${now.dayOfMonth} 日  ·  $weekday"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(colors.archivePaper)
            .padding(start = ArchiveSpacing.gutter, end = 10.dp)
    ) {
        // 标题组（左下）
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = lerp(14.dp, 12.dp, fraction), end = 176.dp)
        ) {
            if (dateHeight > 0.5.dp) {
                Box(modifier = Modifier.height(dateHeight)) {
                    Text(
                        text = dateLine,
                        style = ArchiveTextStyles.overline,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(dateAlpha)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onTitleLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onTitleLongClick
                    )
                } else Modifier
            ) {
                MastheadSeal(size = sealSize)
                Spacer(modifier = Modifier.width(lerp(12.dp, 9.dp, fraction)))
                Text(
                    text = "散页",
                    fontFamily = ArchiveFontFamilies.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize,
                    letterSpacing = lerp(4.sp, 2.sp, fraction),
                    color = colors.inkPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 工具组（右上，收起态时垂直居中于 60dp 栏）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp),
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
}

/**
 * 刊头小方印：圆角方块里一个反白的「页」字，随刊头收起同比缩小。
 */
@Composable
private fun MastheadSeal(size: androidx.compose.ui.unit.Dp) {
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
