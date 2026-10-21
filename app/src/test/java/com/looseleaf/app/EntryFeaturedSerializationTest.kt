package com.looseleaf.app

import com.looseleaf.app.model.CreateEntryRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOB-P1-xx：`encodeDefaults = false` 下，「显式设为默认值」必须仍能发出去。
 *
 * 背景：客户端开启了 encodeDefaults=false，字段值等于其默认值时会被整个省略。
 * 若 `featured` 的默认值是 false，那么「取消精选」（featured=false）这个操作
 * 就会被省略 ⇒ 服务端收不到 ⇒ 静默不生效 ⇒ 下次拉取又变回精选。
 *
 * 改成 `Boolean? = null` 之后：
 * - null  → 省略（这次不涉及精选）
 * - false → **显式发送**（取消精选能生效）
 */
class EntryFeaturedSerializationTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private fun base(featured: Boolean?) = CreateEntryRequest(
        type = "life",
        title = "标题",
        content = "正文",
        occurredAt = "2026-10-21T12:00:00+08:00",
        featured = featured
    )

    @Test
    fun `featured 为 null 时字段被省略`() {
        val text = json.encodeToString(base(null))
        assertFalse(
            "featured=null 时应当省略该字段（这次不涉及精选），实际: $text",
            text.contains("\"featured\"")
        )
    }

    @Test
    fun `featured 为 false 时字段被显式发出`() {
        val text = json.encodeToString(base(false))
        assertTrue(
            "featured=false 必须显式发出去，否则「取消精选」会被服务端静默丢弃，实际: $text",
            text.contains("\"featured\":false")
        )
    }

    @Test
    fun `featured 为 true 时字段被显式发出`() {
        val text = json.encodeToString(base(true))
        assertTrue("featured=true 必须发出去，实际: $text", text.contains("\"featured\":true"))
    }

    @Test
    fun `往返后 featured 保持原值`() {
        listOf(null, false, true).forEach { value ->
            val back = json.decodeFromString<CreateEntryRequest>(json.encodeToString(base(value)))
            assertEquals("往返后 featured 应当保持 $value", value, back.featured)
        }
    }
}
