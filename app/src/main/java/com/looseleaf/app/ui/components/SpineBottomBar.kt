package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 书脊式底部导航项数据结构
 */
data class SpineNavigationItem(
    val route: String,
    val label: String,
    val icon: Painter,
    val selectedIcon: Painter? = null
)

/**
 * 散页手账 · 统一底栏 (Stream Unified Bottom Bar)
 *
 * 统一化规范：
 * - 底栏与页面同色，顶部一条 1dp 淡线
 * - 选中态：顶部赤陶小指示条 + 图标与文字赤陶色加粗 + 轻触感反馈，扫一眼即分清选中项
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
    val workshopIcon = painterResource(R.drawable.ic_nav_craft)
    val profileIcon = painterResource(R.drawable.ic_nav_gallery)

    val tabs = remember(streamIcon, ledgerIcon, workshopIcon, profileIcon) {
        listOf(
            SpineNavigationItem("stream", "时间流", streamIcon),
            SpineNavigationItem("ledger", "账本", ledgerIcon),
            SpineNavigationItem("workshop", "工坊", workshopIcon),
            SpineNavigationItem("profile", "展台", profileIcon)
        )
    }

    Surface(
        color = colors.archivePaper,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ArchiveLines.hairline)
                    .background(colors.borderDivider)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .height(60.dp)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (tab in tabs) {
                        val isSelected = when (tab.route) {
                            "workshop" -> currentRoute in setOf("workshop", "projects", "footprints", "wishes")
                            else -> tab.route == currentRoute
                        }

                        SpineBottomTabItem(
                            item = tab,
                            isSelected = isSelected,
                            onClick = { onRouteSelected(tab.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpineBottomTabItem(
    item: SpineNavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentWarm else colors.inkMuted,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "contentColor"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 0.dp,
        animationSpec = tween(ArchiveMotion.DurationBase),
        label = "indicatorWidth"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
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
        contentAlignment = Alignment.Center
    ) {
        // 顶部选中指示条（不单靠颜色区分选中态）
        if (indicatorWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(indicatorWidth)
                    .height(2.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.accentWarm)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = if (isSelected && item.selectedIcon != null) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = ArchiveTypeScale.caption,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                color = contentColor
            )
        }
    }
}
