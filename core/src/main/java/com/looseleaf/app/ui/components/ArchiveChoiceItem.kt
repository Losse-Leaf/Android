package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 选项条目 (ArchiveChoiceItem)
 * - 未选：纯文字（次要色），可带 6dp 色点
 * - 选中：朱砂淡底胶囊 + 朱砂字，形状变化而不只是颜色变化
 * - 48dp 最小热区
 */
@Composable
fun ArchiveChoiceItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colorDot: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(
        targetValue = if (isSelected) colors.accentTint else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "choiceBg"
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) colors.accentSmallText else colors.inkSecondary,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "choiceFg"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { selected = isSelected }
            .clickable(
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(ArchiveShapes.full)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (colorDot != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colorDot)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = ArchiveTextStyles.label,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 可选胶囊 (ArchiveSelectableCapsule)
 * - 默认（未传 accentColor）：选中为反相墨胶囊（墨底纸字），沉稳不抢戏；
 * - 传入 accentColor（如收入绿）：选中为该色淡底 + 同色细边 + 同色字；
 * - 未选：透明底 + 一圈分隔线 + 次要字
 */
@Composable
fun ArchiveSelectableCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingDotColor: Color? = null,
    isMonospace: Boolean = false,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current

    val selectedBg = accentColor?.copy(alpha = 0.14f) ?: colors.inverseSurface
    val selectedFg = accentColor ?: colors.inverseInk
    val selectedBorder = accentColor ?: colors.inverseSurface

    val bg by animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "capsuleBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) selectedFg else colors.inkSecondary,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "capsuleFg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) selectedBorder else colors.borderDivider,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "capsuleBorder"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .semantics { this.selected = selected }
            .clip(ArchiveShapes.full)
            .background(bg)
            .border(width = ArchiveLines.hairline, color = border, shape = ArchiveShapes.full)
            .clickable(
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
            }
            Text(
                text = text,
                style = ArchiveTextStyles.label,
                fontFamily = if (isMonospace) ArchiveFontFamilies.Numeric else ArchiveFontFamilies.Text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
