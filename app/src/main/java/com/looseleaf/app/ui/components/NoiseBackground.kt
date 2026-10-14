package com.looseleaf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import com.looseleaf.app.theme.LooseLeafTheme
import kotlin.random.Random

/**
 * 纸张微噪点与护眼背景 (Noise Paper Background)
 * 模拟手工棉纸纤维质感，根据浅色/暗房模式自适应点阵渲染
 */
@Composable
fun NoiseBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.archivePaper)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grainColor = colors.grainColor
            val rng = Random(42)
            val points = ArrayList<Offset>(300)
            val w = size.width
            val h = size.height
            if (w > 0 && h > 0) {
                for (i in 0 until 200) {
                    points.add(Offset(rng.nextFloat() * w, rng.nextFloat() * h))
                }
                drawPoints(
                    points = points,
                    pointMode = PointMode.Points,
                    color = grainColor,
                    strokeWidth = 1.5f
                )
            }
        }
        content()
    }
}
