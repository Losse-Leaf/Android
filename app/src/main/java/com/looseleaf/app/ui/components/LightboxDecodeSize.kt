package com.looseleaf.app.ui.components

import kotlin.math.max
import kotlin.math.sqrt

/**
 * 灯箱解码尺寸计算（纯 Kotlin，便于 JVM 单测）。
 *
 * 背景：灯箱此前使用 `Size.ORIGINAL`，高像素照片（12MP ≈ 48MB、48MP ≈ 192MB，
 * ARGB_8888）在解码瞬间就可能触发 OOM。这里按容器尺寸 × density 计算解码目标，
 * 并保留 2× 过采样以缓解缩放后的马赛克，同时施加硬上限：
 *  - 单边不超过 [MAX_EDGE_PX]；
 *  - 总像素不超过 [MAX_PIXELS]（16MP，约 64MB ARGB_8888）。
 */
internal object LightboxDecodeSize {

    /** 单边硬上限，避免长图把单边撑到不可控。 */
    const val MAX_EDGE_PX = 4096

    /** 总像素硬上限：16MP ≈ 64MB（ARGB_8888）。 */
    const val MAX_PIXELS = 16_000_000L

    /** 默认 2× 过采样：4× 手势放大时仍有一定细节，且不会像 ORIGINAL 一样失控。 */
    const val DEFAULT_OVERSAMPLE = 2f

    data class Target(val width: Int, val height: Int)

    /**
     * @param widthDp 容器宽度（dp）
     * @param heightDp 容器高度（dp）
     * @param density 屏幕密度（px/dp）
     * @param oversample 过采样倍数，默认 2×
     */
    fun compute(
        widthDp: Float,
        heightDp: Float,
        density: Float,
        oversample: Float = DEFAULT_OVERSAMPLE
    ): Target {
        // 非法/极端输入（折叠屏 0 宽、density 异常等）兜底到 1×1，绝不让 Coil 退回 ORIGINAL。
        if (!widthDp.isFinite() || !heightDp.isFinite() || !density.isFinite() || !oversample.isFinite() ||
            widthDp <= 0f || heightDp <= 0f || density <= 0f || oversample <= 0f
        ) {
            return Target(1, 1)
        }

        val rawWidth = widthDp.toDouble() * density * oversample
        val rawHeight = heightDp.toDouble() * density * oversample

        var scale = 1.0
        val longestEdge = max(rawWidth, rawHeight)
        if (longestEdge > MAX_EDGE_PX) {
            scale *= MAX_EDGE_PX / longestEdge
        }

        val scaledPixels = rawWidth * rawHeight * scale * scale
        if (scaledPixels > MAX_PIXELS) {
            scale *= sqrt(MAX_PIXELS / scaledPixels)
        }

        // 向下取整保证像素上限是真正的硬上限；下限 1 避免 0 尺寸。
        val width = (rawWidth * scale).toInt().coerceAtLeast(1)
        val height = (rawHeight * scale).toInt().coerceAtLeast(1)
        return Target(width, height)
    }
}
