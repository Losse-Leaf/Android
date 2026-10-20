package com.looseleaf.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.looseleaf.app.theme.LooseLeafTheme
import java.util.Random

/**
 * 宣纸纤维噪点底纹 (Noise Background)
 *
 * 原实现每次绘制随机撒 200 个 1.5px 的点——在 2K 屏（约 460 万像素）上覆盖率仅 0.004%，
 * 肉眼完全看不出纸感。现改为一次性生成 128×128 的确定性噪点瓦片，
 * 用 ImageShader 平铺整屏（对齐 macOS 端 NoiseBackground.swift 的瓦片思路，AOPT-08a）：
 * 纹理密度与屏幕分辨率解耦，且只需一次 drawRect。
 */
@Composable
fun NoiseBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    val grainArgb = colors.grainColor.toArgb()
    val tile = remember(grainArgb) { buildNoiseTile(grainArgb) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.archivePaper)
            .drawWithCache {
                val brush = ShaderBrush(
                    ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    ) {
        content()
    }
}

/**
 * 生成 128×128 的宣纸纤维噪点瓦片（确定性随机，保证每次启动纹理一致）。
 * 约 5% 的像素带色，其余透明——平铺后形成细密而不抢戏的纸纤维颗粒。
 */
private fun buildNoiseTile(grainArgb: Int): ImageBitmap {
    val size = 128
    val pixels = IntArray(size * size)
    val rng = Random(42L)
    val grainCount = (size * size * 0.05f).toInt()
    repeat(grainCount) {
        pixels[rng.nextInt(size * size)] = grainArgb
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
