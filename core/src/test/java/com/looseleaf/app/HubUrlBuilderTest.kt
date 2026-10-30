package com.looseleaf.app

import com.looseleaf.app.data.api.buildHubUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 中枢 URL 构建回归测试。
 *
 * v1.5.0 曾在 buildRequest 里对已解析路径调用 encodedPath("")——OkHttp 要求
 * 该参数以 "/" 开头，空串抛 IllegalArgumentException，导致全部 API 请求
 * 在发出前就失败（表现为「升级后连不上后端」）。本测试锁死正确行为。
 */
class HubUrlBuilderTest {

    private val hub = "http://192.168.31.23:8088"

    @Test
    fun testPlainApiPath() {
        val url = buildHubUrl(hub, "/api/v1/entries")
        assertEquals("http://192.168.31.23:8088/api/v1/entries", url.toString())
    }

    @Test
    fun testPathWithoutLeadingSlashIsNormalized() {
        val url = buildHubUrl(hub, "api/health")
        assertEquals("http://192.168.31.23:8088/api/health", url.toString())
    }

    @Test
    fun testQueryParamsAreAppended() {
        val url = buildHubUrl(hub, "/api/v1/entries", queryParams = mapOf("page" to "2", "pageSize" to "100"))
        assertEquals("http://192.168.31.23:8088/api/v1/entries?page=2&pageSize=100", url.toString())
    }

    @Test
    fun testSegmentWithUnsafeCharsIsEncoded() {
        // 备份下载等场景：路径段含空格等未安全字符时必须自动百分号编码，
        // 而不是让整串 toHttpUrlOrNull 直接返回 null
        val url = buildHubUrl(hub, "/api/v1/system/backup/download/looseleaf backup 2026.db")
        assertEquals(
            "http://192.168.31.23:8088/api/v1/system/backup/download/looseleaf%20backup%202026.db",
            url.toString()
        )
    }

    @Test
    fun testHttpsBaseKeepsScheme() {
        val url = buildHubUrl("https://archive.example.com/", "/api/v1/stats")
        assertEquals("https://archive.example.com/api/v1/stats", url.toString())
    }

    @Test
    fun testInvalidBaseUrlThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            buildHubUrl("not a url", "/api/health")
        }
    }
}
