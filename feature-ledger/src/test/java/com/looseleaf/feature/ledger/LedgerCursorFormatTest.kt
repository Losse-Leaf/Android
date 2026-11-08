package com.looseleaf.feature.ledger

import com.looseleaf.feature.ledger.data.LedgerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3：服务端 `X-Sync-Cursor` 是 UTC，账本行时间戳可能是 `+08:00`。
 * 必须先归一化再比较，否则同一时刻会被字符串比较误判，导致游标推进偏慢、
 * 同一批记录被反复拉取。
 */
class LedgerCursorFormatTest {

    @Test
    fun `同一时刻的 UTC 与加八偏移归一化后相等`() {
        val utc = LedgerRepository.normalizeCursor("2026-11-08T02:00:00Z")
        val beijing = LedgerRepository.normalizeCursor("2026-11-08T10:00:00+08:00")

        assertEquals(utc, beijing)
        assertEquals("2026-11-08T02:00:00.000000000Z", beijing)
    }

    @Test
    fun `混合格式下取真实时刻最大的游标`() {
        // 10:00+08:00 = 02:00Z，02:30Z 更晚；旧字符串比较会误选 10:00+08:00。
        val max = LedgerRepository.maxCursorOf(
            "2026-11-08T10:00:00+08:00",
            "2026-11-08T02:30:00Z"
        )

        assertEquals("2026-11-08T02:30:00.000000000Z", max)
    }

    @Test
    fun `旧版加八游标不会阻塞新 UTC 游标推进`() {
        val stored = LedgerRepository.normalizeCursor("2026-11-08T10:00:00+08:00")!!
        val candidate = LedgerRepository.maxCursorOf("2026-11-08T03:00:00Z")!!

        assertTrue("03:00Z 晚于 02:00Z，必须允许推进", candidate > stored)
    }

    @Test
    fun `无法解析的游标原样保留`() {
        assertEquals("not-a-time", LedgerRepository.normalizeCursor("not-a-time"))
        assertNull(LedgerRepository.normalizeCursor("  "))
        assertNull(LedgerRepository.maxCursorOf(null, ""))
    }
}
