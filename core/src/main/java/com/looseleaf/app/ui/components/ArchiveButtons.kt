package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 文字按钮 (ArchiveTextButton)
 * - 无底色，按下时浮现一层极淡的墨色胶囊
 * - 普通操作用朱砂，破坏性操作用胭脂，禁用时退为禁用墨色
 * - 保持 48dp 最小热区
 */
@Composable
fun ArchiveTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    textColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val finalTextColor = textColor ?: when {
        !enabled -> colors.inkDisabled
        isDestructive -> colors.alertRed
        else -> colors.accentSmallText
    }
    val pressBg by animateColorAsState(
        targetValue = if (isPressed && enabled) colors.pressOverlay else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "textButtonPress"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(ArchiveShapes.full)
                .background(pressBg)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = ArchiveTextStyles.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = finalTextColor
            )
        }
    }
}

/**
 * 抽屉顶栏主动作按钮 (ArchiveSheetActionButton)
 * - 实心胶囊：启用时朱砂 / 胭脂底 + 反白字；禁用时凹槽底 + 禁用墨色字
 * - 外围保持 ≥48dp 热区，按下轻微收缩
 */
@Composable
fun ArchiveSheetActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "sheetActionScale"
    )
    val bgColor = when {
        !enabled -> colors.archiveSubtle
        isDestructive -> colors.alertRed
        isPressed -> colors.accentPress
        else -> colors.accentWarm
    }
    val contentColor = if (enabled) colors.onAccent else colors.inkDisabled

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(ArchiveShapes.full)
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = ArchiveTextStyles.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

/**
 * 主按钮 (ArchivePrimaryButton)
 * - 52dp 高的满宽朱砂胶囊，反白无衬线字 + 轻触感
 * - 按下：加深一档并收缩 2%，松手阻尼回弹
 * - 禁用：凹槽底 + 禁用墨色字 + 一圈禁用轮廓线，靠轮廓而不是褪色表达「按不了」
 */
@Composable
fun ArchivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "primaryScale"
    )
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> colors.archiveSubtle
            isPressed -> colors.accentPress
            else -> colors.accentWarm
        },
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "primaryBg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ArchiveShapes.full)
            .background(bg)
            .then(
                if (enabled) Modifier
                else Modifier.border(ArchiveLines.hairline, colors.lineDisabled, ArchiveShapes.full)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ArchiveTextStyles.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) colors.onAccent else colors.inkDisabled
        )
    }
}

/**
 * 次级按钮 (ArchiveSecondaryButton)
 * - 与主按钮同尺寸的描边胶囊，用于「次要但同等重要」的并列动作
 */
@Composable
fun ArchiveSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val fg = when {
        !enabled -> colors.inkDisabled
        isDestructive -> colors.alertRed
        else -> colors.inkPrimary
    }
    val border = when {
        !enabled -> colors.lineDisabled
        isDestructive -> colors.alertRed.copy(alpha = 0.45f)
        else -> colors.borderDivider
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(ArchiveShapes.full)
            .background(if (isPressed && enabled) colors.pressOverlay else Color.Transparent)
            .border(ArchiveLines.hairline, border, ArchiveShapes.full)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ArchiveTextStyles.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = fg
        )
    }
}
