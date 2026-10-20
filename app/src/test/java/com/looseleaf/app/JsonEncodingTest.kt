package com.looseleaf.app

import com.looseleaf.app.model.AddGearAssetRequest
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateTransactionRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOB-P1-35：请求体编码契约。
 *
 * Android 端把 kotlinx.serialization 切到 `encodeDefaults = false` 之后，
 * 「等于默认值的字段」不再出现在请求体里 —— 请求体与发件箱（outbox 存的就是这份请求体）
 * 一起瘦身。
 *
 * 但这条约定有个前提：**服务端必须把「省略」与「显式默认值」视为同一件事**。
 * 后端已配套实现（entry.type → life、transaction.type → expense、wish.kind → item 等），
 * 这里用测试把「哪些字段会被省略」钉死，避免将来有人给请求模型加默认值时静默改掉语义。
 */
class JsonEncodingTest {

    /** 与 APIClient / SyncManager 保持一致 */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }

    @Test
    fun `条目请求省略等于默认值的字段`() {
        val payload = json.encodeToString(
            CreateEntryRequest(
                title = "一则随笔",
                content = "正文",
                occurredAt = "2026-01-01T08:00:00+08:00"
            )
        )
        // type 的默认值是 life —— 省略它，服务端会回落成 life
        assertFalse("默认值字段不应出现在请求体里", payload.contains("\"type\""))
        // 必填字段必须还在
        assertTrue(payload.contains("\"title\":\"一则随笔\""))
        assertTrue(payload.contains("\"occurred_at\":\"2026-01-01T08:00:00+08:00\""))
        // 一串 null 不该再白白占体积
        assertFalse("null 字段不应出现在请求体里", payload.contains("null"))
    }

    @Test
    fun `非默认的类型仍然下发`() {
        val payload = json.encodeToString(
            CreateEntryRequest(
                type = "travel",
                title = "一次远行",
                content = "正文",
                occurredAt = "2026-01-01T08:00:00+08:00"
            )
        )
        assertTrue("非默认值的 type 必须照常下发", payload.contains("\"type\":\"travel\""))
    }

    @Test
    fun `记账类型为默认时省略为 expense`() {
        val payload = json.encodeToString(
            CreateTransactionRequest(
                amount = 1800,
                category = "餐饮",
                title = "午饭",
                occurredAt = "2026-01-01T08:00:00+08:00"
            )
        )
        assertFalse(payload.contains("\"type\""))
        assertTrue(payload.contains("\"amount\":1800"))
    }

    @Test
    fun `随设备的建账开关永远下发`() {
        // createTransaction 的客户端默认值是 true、服务端默认值是 false，
        // 一旦被省略就会悄悄少记一笔账 —— 因此模型里去掉了默认值，它必须永远出现。
        val payload = json.encodeToString(
            AddGearAssetRequest(name = "一台相机", createTransaction = true)
        )
        assertTrue(
            "create_transaction 必须永远随请求下发",
            payload.contains("\"create_transaction\":true")
        )
    }
}
