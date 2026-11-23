package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 分段切换 (ArchiveSegmentedTabs)
 *
 * 凹槽轨道 + 一枚会滑动的纸页滑块：选中项落在纸页上，未选项沉在槽里。
 * counts 非空且 > 0 时，在标签后显示等宽数字（选中为朱砂，未选为次要色）。
 */
@Composable
fun ArchiveSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    counts: List<Int>? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    if (tabs.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(ArchiveShapes.full)
            .background(colors.archiveSubtle)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / tabs.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = ArchiveMotion.glide(),
            label = "segmentThumb"
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = 3.dp,
                    shape = ArchiveShapes.full,
                    spotColor = colors.cardShadowSpot,
                    ambientColor = colors.cardShadowAmbient
                )
                .clip(ArchiveShapes.full)
                .background(colors.archiveCard)
        )

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == safeIndex
                val count = counts?.getOrNull(index) ?: 0
                val fg by animateColorAsState(
                    targetValue = if (isSelected) colors.inkPrimary else colors.inkMuted,
                    animationSpec = tween(ArchiveMotion.DurationFast),
                    label = "segmentFg$index"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = isSelected }
                        .clip(ArchiveShapes.full)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onTabSelected(index)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = ArchiveTextStyles.label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (count > 999) "999+" else count.toString(),
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) colors.accentSmallText else colors.inkMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 类型切换按钮 (ArchiveTypeToggleButton)
 * 选中为反相墨胶囊，未选为次要色文字；通常两到三个并排成组使用。
 */
@Composable
fun ArchiveTypeToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(
        targetValue = if (isSelected) colors.inverseSurface else colors.archiveSubtle.copy(alpha = 0f),
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "toggleBg"
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) colors.inverseInk else colors.inkMuted,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "toggleFg"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .semantics { selected = isSelected }
            .clip(ArchiveShapes.full)
            .background(bg)
            .clickable(
                role = Role.Tab,
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ArchiveTextStyles.label,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1
        )
    }
}
