package com.looseleaf.app

import com.looseleaf.app.data.api.humanizeNetworkError
import com.looseleaf.app.model.RestoreGearAssetRequest
import com.looseleaf.app.ui.components.formatGearCompanionDays
import com.looseleaf.app.ui.components.formatGearShortDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import javax.net.ssl.SSLException

class ArchiveGearFormatTest {

    private val refToday = LocalDate.of(2026, 11, 3)

    @Test
    fun `formatGearShortDate formats same-year date without year prefix`() {
        assertEquals("10/19", formatGearShortDate("2026-10-19", refToday))
        assertEquals("10/21", formatGearShortDate("2026-10-21T09:30:00+08:00", refToday))
    }

    @Test
    fun `formatGearShortDate preserves full year for cross-year date`() {
        assertEquals("2024/06/08", formatGearShortDate("2024-06-08", refToday))
        assertNull(formatGearShortDate(null, refToday))
        assertNull(formatGearShortDate("   ", refToday))
    }

    @Test
    fun `formatGearCompanionDays computes inclusive minimum 1 day`() {
        assertEquals("已陪伴 15 天", formatGearCompanionDays("2026-10-19", refToday))
        assertEquals("已陪伴 1 天", formatGearCompanionDays("2026-11-03", refToday))
        assertEquals("已陪伴 1 天", formatGearCompanionDays("2026-11-05", refToday))
        assertNull(formatGearCompanionDays("", refToday))
    }

    @Test
    fun `RestoreGearAssetRequest serializes snake_case fields cleanly`() {
        val json = Json { encodeDefaults = true }
        val payload = json.encodeToString(
            RestoreGearAssetRequest(
                id = "gear_123",
                name = "Sony A7C II",
                spec = "银黑单机身",
                category = "影像",
                priceCents = 1399900L,
                purchaseDate = "2026-10-19",
                status = "active"
            )
        )
        assertTrue(payload.contains("\"price_cents\":1399900"))
        assertTrue(payload.contains("\"purchase_date\":\"2026-10-19\""))
    }

    @Test
    fun `humanizeNetworkError maps raw IOExceptions to clean Chinese copy`() {
        assertEquals(
            "网络连接异常：无法解析中枢地址，请检查网络或代理设置",
            humanizeNetworkError(UnknownHostException("Unable to resolve host \"codeactivityhub.top\""))
        )
        assertEquals(
            "网络连接超时，请稍后重试",
            humanizeNetworkError(SocketTimeoutException("timeout"))
        )
        assertEquals(
            "无法连接中枢服务，请检查网络设置",
            humanizeNetworkError(ConnectException("Connection refused"))
        )
        assertEquals(
            "安全连接握手失败，请检查网络或代理设置",
            humanizeNetworkError(SSLException("SSL handshake aborted"))
        )
        assertEquals(
            "网络连接异常，请检查网络设置",
            humanizeNetworkError(IOException("unexpected end of stream"))
        )
    }
}
