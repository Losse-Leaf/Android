package com.looseleaf.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightboxDecodeSizeTest {

    @Test
    fun largePhone_1080x2400_at3x_staysWithinCaps() {
        val target = LightboxDecodeSize.compute(1080f, 2400f, 3f)

        assertTrue(target.width in 1..LightboxDecodeSize.MAX_EDGE_PX)
        assertTrue(target.height in 1..LightboxDecodeSize.MAX_EDGE_PX)
        assertTrue(
            "总像素 ${target.width}x${target.height} 超过 16MP",
            target.width.toLong() * target.height <= LightboxDecodeSize.MAX_PIXELS
        )
    }

    @Test
    fun tinyScreen_neverReturnsZero() {
        val tiny = LightboxDecodeSize.compute(1f, 1f, 1f)
        assertTrue(tiny.width >= 1)
        assertTrue(tiny.height >= 1)

        // 异常约束（折叠态 0 宽 / density 非法）也绝不能退化成 0 或 ORIGINAL
        assertEquals(LightboxDecodeSize.Target(1, 1), LightboxDecodeSize.compute(0f, 0f, 3f))
        assertEquals(LightboxDecodeSize.Target(1, 1), LightboxDecodeSize.compute(360f, 800f, Float.NaN))
    }

    @Test
    fun aspectRatioIsPreservedWhenCapped() {
        // 2000×1000dp @1x ×2 过采样 = 4000×2000，未触顶，比例保持 2:1
        val target = LightboxDecodeSize.compute(2000f, 1000f, 1f)
        assertEquals(4000, target.width)
        assertEquals(2000, target.height)

        // 4000×4000dp @1x ×2 = 8000×8000：先触单边 4096，再触 16MP，最终仍是正方形
        val square = LightboxDecodeSize.compute(4000f, 4000f, 1f)
        assertEquals(square.width, square.height)
        assertTrue(square.width <= LightboxDecodeSize.MAX_EDGE_PX)
        assertTrue(square.width.toLong() * square.height <= LightboxDecodeSize.MAX_PIXELS)
    }
}
