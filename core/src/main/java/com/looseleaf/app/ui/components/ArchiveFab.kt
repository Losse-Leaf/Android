package com.looseleaf.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一悬浮操作按钮 (ArchiveFab)
 *
 * 第 2 批把三处各写一遍的 FAB 收敛到这里：
 * - 时间流：自绘 Box + 内置 AnimatedVisibility（钢笔尖 22dp）
 * - 账本 / 工坊：M3 `FloatingActionButton` + 外层 AnimatedVisibility（＋ 24dp，elevation 硬置 0）
 *
 * 统一契约：
 * - 56dp 圆形赤陶，无投影、无 ripple（纸不做水波），图标统一 22dp
 * - [visible] 控制显隐，动效统一 120ms `ArchiveMotion.DurationFast` 的 scale + fade；
 *   三处原本各写一份 enter/exit，账本与工坊还用了默认时长，快慢不一
 * - 按下态用第 1 批定义的 [LooseLeafTheme.colors]`accentPress`，不是 Material 涟漪；
 *   纸面按下更深、暗房按下更亮，文字对比度始终达标
 * - `Role.Button` 语义 + 轻触感反馈；按钮名称由 [icon] 里 Icon 的 contentDescription 提供，
 *   故这里不另设 contentDescription，避免读屏重复播报
 *
 * [modifier] 施加在 AnimatedVisibility 容器上，各页自己的 align / padding / Snackbar
 * 避让 offset 都从这里传，组件不假设自己被放在 Scaffold 还是 Box 里。
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

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(ArchiveMotion.DurationFast)) + fadeIn(tween(ArchiveMotion.DurationFast)),
        exit = scaleOut(tween(ArchiveMotion.DurationFast)) + fadeOut(tween(ArchiveMotion.DurationFast)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ArchiveShapes.full)
                .background(if (isPressed) colors.accentPress else colors.accentWarm)
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
