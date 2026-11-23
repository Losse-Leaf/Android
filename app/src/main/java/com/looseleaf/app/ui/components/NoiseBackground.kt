package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 画布背景（沿用旧名 NoiseBackground 以保持调用点不变）
 *
 * Folio 放弃了噪点纹理：在高分屏上它要么看不见，要么显得脏。
 * 改为纯净画布 + 顶部一层极淡的「窗边光」径向渐变 —— 浅色是纸面被晨光照亮的一角，
 * 深色是一盏台灯在墨色桌面上的余晖。只画一次 drawRect，无位图、无分配。
 */
@Composable
fun NoiseBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    val glow = colors.canvasGlow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.archivePaper)
            .drawWithCache {
                val radius = size.maxDimension * 0.9f
                val brush = Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent),
                    center = Offset(size.width * 0.18f, -size.height * 0.05f),
                    radius = if (radius > 0f) radius else 1f
                )
                onDrawBehind {
                    if (glow.alpha > 0f) drawRect(brush = brush)
                }
            }
    ) {
        content()
    }
}
