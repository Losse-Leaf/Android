package com.looseleaf.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.LooseLeafTheme
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R

/**
 * 纸墨骨架屏 (MOB-P2-44)
 *
 * 此前加载态只有一颗裸 `CircularProgressIndicator` 浮在空白区域，
 * 与「宣纸手账」的视觉语言完全脱节。这里用一张张**半透明的纸片**
 * 代替转圈，读起来像「正在铺开一页空白稿纸」。
 *
 * 动效遵循系统「移除动画」偏好（[com.looseleaf.app.utils.MotionPrefs]）。
 */
@Composable
fun PaperSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    lineHeight: Dp = 12.dp,
    showAvatar: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val animationsEnabled = remember(context) {
        com.looseleaf.app.utils.MotionPrefs.animationsEnabled(context)
    }

    val alpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = stringResource(R.string.paper_skeleton_label_01))
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100),
                repeatMode = RepeatMode.Reverse
            ),
            label = stringResource(R.string.paper_skeleton_label_02)
        )
        animated
    } else {
        0.55f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showAvatar) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colors.archiveSubtle, RoundedCornerShape(6.dp))
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(120.dp)
                        .height(12.dp)
                        .background(colors.archiveSubtle, RoundedCornerShape(3.dp))
                        .alpha(alpha)
                )
            }
        }
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.55f else 1f)
                    .height(lineHeight)
                    .background(colors.archiveSubtle, RoundedCornerShape(3.dp))
                    .alpha(alpha)
            )
        }
    }
}
