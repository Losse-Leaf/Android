package com.looseleaf.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 钢笔尖悬浮操作按钮 (Fountain Pen FAB)
 *
 * 统一化规范：
 * - 赤陶色圆形 56dp，只有笔尖图标，无文字、无投影，尺寸与账本 / 工坊的 ＋ 一致
 * - 点击即开速记抽屉。**不再有长按快捷菜单** —— 原先「文字 / 照片 / 语音」三项
 *   全部指向同一个速记抽屉，是个假的三选一，只会让人多点一次。
 */
@Composable
internal fun FountainPenFab(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // 向下滚动时收缩并淡出隐藏；向上滚动时恢复
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(tween(ArchiveMotion.DurationFast)) + fadeIn(tween(ArchiveMotion.DurationFast)),
            exit = scaleOut(tween(ArchiveMotion.DurationFast)) + fadeOut(tween(ArchiveMotion.DurationFast))
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accentWarm)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fountain_pen),
                    contentDescription = "写一页",
                    tint = colors.archivePaper,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
