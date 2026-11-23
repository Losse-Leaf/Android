package com.looseleaf.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 悬浮操作按钮 (ArchiveFab)
 *
 * Folio 中 FAB 是一枚「朱砂印」：58dp 的圆角方印（不是正圆），柔和远投影，
 * 按下加深一档并下沉缩放，松手阻尼回弹；显隐使用缩放 + 淡入淡出。
 * 按钮名称由 [icon] 里 Icon 的 contentDescription 提供，这里不另设，避免读屏重复。
 */
@Composable
fun ArchiveFab(
    onClick: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "fabScale"
    )
    val bg by animateColorAsState(
        targetValue = if (isPressed) colors.accentPress else colors.accentWarm,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "fabBg"
    )

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized), initialScale = 0.6f) +
            fadeIn(tween(ArchiveMotion.DurationFast)),
        exit = scaleOut(tween(ArchiveMotion.DurationFast), targetScale = 0.6f) +
            fadeOut(tween(ArchiveMotion.DurationFast)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isPressed) 4.dp else 14.dp,
                    shape = ArchiveShapes.lg,
                    spotColor = colors.accentWarm.copy(alpha = 0.45f),
                    ambientColor = colors.frameShadowAmbient
                )
                .size(58.dp)
                .clip(ArchiveShapes.lg)
                .background(bg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}
