package com.looseleaf.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 骨架屏 (ArchiveSkeleton)
 * 极慢的呼吸透明度，绝不使用高光扫描；凹槽色在两套主题下都与纸页拉开一档。
 */
@Composable
fun ArchiveSkeleton(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ArchiveShapes.radiusSmall
) {
    val colors = LooseLeafTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
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

/**
 * 散页卡片 (ArchiveLeafCard)
 * Folio 的基本容器：画布之上一张更亮的纸，20dp 圆角，一圈几乎看不见的发丝边。
 */
@Composable
fun ArchiveLeafCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color? = null,
    shape: RoundedCornerShape = ArchiveShapes.leaf,
    contentPadding: PaddingValues = PaddingValues(ArchiveSpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor ?: colors.archiveCard)
            .border(ArchiveLines.hairline, borderColor ?: colors.borderCard, shape)
            .padding(contentPadding),
        content = content
    )
}
