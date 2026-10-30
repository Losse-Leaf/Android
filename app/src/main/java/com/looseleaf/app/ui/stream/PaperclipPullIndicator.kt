package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页手账 · 黄铜回形针下拉刷新指示器 (Paperclip Pull Indicator)
 *
 * 替代默认 Material 3 蓝色转圈：
 * 1. 下拉时：黄铜回形针随着下拉深度顺时针旋转
 * 2. 刷新中：以复古质感平缓转动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.PaperclipPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinningAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    // 下拉距离旋转角
    val pullAngle = (state.distanceFraction * 270f).coerceIn(0f, 270f)
    val currentAngle = if (isRefreshing) spinningAngle else pullAngle

    if (state.distanceFraction > 0.05f || isRefreshing) {
        Box(
            modifier = modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = (state.distanceFraction * 80.dp.toPx()).coerceAtMost(100.dp.toPx())
                }
                .size(36.dp)
                .shadow(
                    elevation = ArchiveShadows.floatingLayerElevation,
                    shape = CircleShape,
                    spotColor = Color(0x382A180E),
                    ambientColor = Color(0x282A180E)
                )
                .clip(CircleShape)
                .background(colors.archiveCard)
                .border(0.8.dp, colors.borderDivider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_paperclip),
                contentDescription = "下拉刷新",
                tint = colors.accentWarm,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(currentAngle)
            )
        }
    }
}
