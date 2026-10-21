package com.looseleaf.app

import com.looseleaf.app.updater.ReleaseInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOB-P1-xx：`ReleaseInfo` 的序列化往返。
 *
 * 这条测试是为了守住一个**曾经真实发生过的 release-only 崩溃**：
 * `ReleaseInfo` 少了 `@Serializable`，`encodeToString` 依然能编译通过，
 * 却会在运行时抛 "Serializer for class 'xxx' is not found"；
 * 而且只有**云端确实有新版本**时才会走到那行 —— 也就是最需要更新功能的那一刻崩。
 *
 * debug 包因为类名不混淆，报错更直观；release 包里类名被 R8 混淆成一个字母，
 * 排查极其困难。所以这里用一条单测把「它必须能被序列化」钉死。
 */
class ReleaseInfoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sample = ReleaseInfo(
        tagName = "v1.6.4",
        versionName = "1.6.4",
        body = "发布说明",
        apkUrl = "https://github.com/Losse-Leaf/Android/releases/download/v1.6.4/LooseLeaf-v1.6.4.apk",
        publishedAt = "2026-10-21T00:00:00Z",
        apkSha256 = "a".repeat(64),
        apkSizeBytes = 3_228_656L
    )

    @Test
    fun `ReleaseInfo 能被序列化并原样还原`() {
        val text = json.encodeToString(sample)
        assertTrue("序列化结果不应为空", text.isNotBlank())
        val back = json.decodeFromString<ReleaseInfo>(text)
        assertEquals(sample, back)
    }

    @Test
    fun `可空字段缺失时也能还原`() {
        // 旧版本缓存里可能没有这两个字段 —— 缺失时必须回落成 null 而不是崩
        val minimal = json.decodeFromString<ReleaseInfo>(
            """{"tagName":"v1.6.3","versionName":"1.6.3","body":"","apkUrl":"https://x/y.apk","publishedAt":"2026-10-20T00:00:00Z"}"""
        )
        assertEquals("v1.6.3", minimal.tagName)
        assertEquals(null, minimal.apkSha256)
        assertEquals(null, minimal.apkSizeBytes)
    }
}
