package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 导航项数据结构
 */
data class SpineNavigationItem(
    val route: String,
    val label: String,
    val icon: Painter,
    val selectedIcon: Painter? = null
)

/**
 * 散页 · 悬浮书签导航 (Folio Dock)
 *
 * 不再是贴底的整条底栏，而是一枚悬浮在画布上的胶囊：
 * - 胶囊为浮起层（surfaceRaised）+ 发丝边 + 柔和远投影；
 * - 选中项被一枚反相墨色滑块托起（墨底纸字），切换时滑块以阻尼弹簧滑动，不过冲；
 * - 未选项只留图标与次要色文字；轻触感反馈仅在切换时触发。
 * 函数名保留 SpineBottomBar 以兼容外部调用。
 */
@Composable
fun SpineBottomBar(
    currentRoute: String,
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val streamIcon = painterResource(R.drawable.ic_nav_book)
    val ledgerIcon = painterResource(R.drawable.ic_nav_ledger)

    val tabs = remember(streamIcon, ledgerIcon) {
        listOf(
            SpineNavigationItem("stream", "散页", streamIcon),
            SpineNavigationItem("ledger", "记账", ledgerIcon)
        )
    }
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val itemWidth = 116.dp
    val thumbOffset by animateDpAsState(
        targetValue = itemWidth * selectedIndex,
        animationSpec = ArchiveMotion.glide(),
        label = "dockThumb"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.archivePaper)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(top = 6.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = ArchiveShadows.dockElevation,
                    shape = ArchiveShapes.full,
                    spotColor = colors.frameShadowSpot,
                    ambientColor = colors.frameShadowAmbient
                )
                .clip(ArchiveShapes.full)
                .background(colors.surfaceRaised)
                .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.full)
                .padding(5.dp)
        ) {
            // 滑块
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(itemWidth)
                    .height(48.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.inverseSurface)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    DockItem(
                        item = tab,
                        isSelected = index == selectedIndex,
                        onClick = { onRouteSelected(tab.route) },
                        modifier = Modifier
                            .width(itemWidth)
                            .height(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    item: SpineNavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "dockItemPress"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.inverseInk else colors.inkSecondary,
        animationSpec = tween(ArchiveMotion.DurationBase),
        label = "dockItemColor"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(ArchiveShapes.full)
            .semantics { selected = isSelected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.label,
            style = ArchiveTextStyles.label,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 供页面内容计算底部留白：悬浮导航不覆盖内容（宿主 Scaffold 已为其预留高度），
 * 列表只需再留出 FAB 的空间。
 */
val FolioDockContentGap = 88.dp
