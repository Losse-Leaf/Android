package com.looseleaf.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 下拉刷新指示器（沿用旧名 PaperclipPullIndicator 以保持调用点不变）
 *
 * Folio 版本：一枚纸页圆片里的朱砂弧线。
 * - 下拉时：弧线长度随拉动距离从 0 生长到 300°，同时圆片下沉、淡入；
 * - 刷新中：固定 90° 的短弧以 1.1s 一周匀速旋转；
 * - 静止：不渲染任何视图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.PaperclipPullIndicator(
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
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val fraction = state.distanceFraction.coerceIn(0f, 1.5f)
    if (fraction > 0.05f || isRefreshing) {
        val sweep = if (isRefreshing) 90f else (fraction.coerceAtMost(1f) * 300f)
        val start = if (isRefreshing) spinningAngle else -90f
        val track = colors.borderSubtle
        val arc = colors.accentWarm

        Box(
            modifier = modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = (fraction * 72.dp.toPx()).coerceAtMost(96.dp.toPx())
                    alpha = if (isRefreshing) 1f else fraction.coerceIn(0f, 1f)
                }
                .size(40.dp)
                .shadow(
                    elevation = ArchiveShadows.floatingLayerElevation,
                    shape = CircleShape,
                    spotColor = colors.cardShadowSpot,
                    ambientColor = colors.cardShadowAmbient
                )
                .clip(CircleShape)
                .background(colors.surfaceRaised)
                .border(ArchiveLines.hairline, colors.borderSubtle, CircleShape)
                .semantics { contentDescription = if (isRefreshing) "正在同步" else "下拉刷新" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val strokeWidth = 2.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = arc,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}
