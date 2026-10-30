package com.looseleaf.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 纸色容器（无投影、无白卡片，平铺在纸面上）
 */
@Composable
fun ArchiveCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 散页统一纸面呼吸骨架屏 (ArchiveSkeleton)
 */
@Composable
fun ArchiveSkeleton(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ArchiveShapes.radiusSmall
) {
    val colors = LooseLeafTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.archiveSubtle.copy(alpha = alpha), shape)
    )
}
