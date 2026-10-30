package com.looseleaf.app

import com.looseleaf.app.model.CreateTransactionRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分期字段的**三态**序列化（后端契约演进后的回归）
 *
 * 背景：后端把 `is_installment` 从「普通布尔」改成了三态：
 * - **省略该字段** = 本次不携带 → 服务端**保持原值**
 * - `is_installment: false` **且**不带 `clear_installment` → 服务端**也保持原值**
 *   （这是为了保护老客户端：它们的字段不可选，必定会发 false）
 * - `is_installment: false` **且** `clear_installment: true` → 服务端**才真的降级**为
 *   一次性支出并清空账期字段
 *
 * 坑：客户端开启了 `encodeDefaults = false`，等于默认值的字段会被**整个省略**。
 * 原先 `isInstallment: Boolean = false` 让「取消分期」这个动作连 key 都没发出去
 * ⇒ 服务端收到 nil ⇒ 分期没被取消，而界面却弹「已保存账目修订」。
 *
 * 这里把三态全部钉死，防止再有人把默认值改回非空布尔。
 */
class InstallmentTriStateSerializationTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private fun base(
        isInstallment: Boolean?,
        clearInstallment: Boolean = false
    ) = CreateTransactionRequest(
        amount = 1200000,
        type = "expense",
        category = "数码",
        title = "显示器",
        occurredAt = "2026-10-21T12:00:00+08:00",
        isInstallment = isInstallment,
        clearInstallment = clearInstallment
    )

    @Test
    fun `is_installment 为 null 时字段被省略`() {
        val text = json.encodeToString(base(null))
        assertFalse(
            "is_installment=null 时应当省略该字段（本次不携带，服务端保持原值），实际: $text",
            text.contains("\"is_installment\"")
        )
    }

    @Test
    fun `is_installment 为 false 时必须显式发出去`() {
        val text = json.encodeToString(base(false))
        assertTrue(
            "is_installment=false 必须显式发出去，否则取消分期的意图会丢失，实际: $text",
            text.contains("\"is_installment\":false")
        )
    }

    @Test
    fun `is_installment 为 true 时必须显式发出去`() {
        val text = json.encodeToString(base(true))
        assertTrue("is_installment=true 必须发出去，实际: $text", text.contains("\"is_installment\":true"))
    }

    @Test
    fun `clear_installment 为 false 时字段被省略`() {
        val text = json.encodeToString(base(false, clearInstallment = false))
        assertFalse(
            "clear_installment=false 应当省略（它不是默认值就是 true 的字段语义），实际: $text",
            text.contains("\"clear_installment\"")
        )
    }

    @Test
    fun `取消分期时同时带上 false 与 clear_installment`() {
        val text = json.encodeToString(base(false, clearInstallment = true))
        assertTrue(
            "取消分期必须显式声明 clear_installment=true，实际: $text",
            text.contains("\"clear_installment\":true")
        )
        assertTrue(
            "取消分期必须同时带上 is_installment=false，实际: $text",
            text.contains("\"is_installment\":false")
        )
    }

    @Test
    fun `保留分期时不得带上 clear_installment`() {
        val text = json.encodeToString(base(true))
        assertFalse(
            "保留分期时不能出现 clear_installment，否则会被服务端误判成取消，实际: $text",
            text.contains("\"clear_installment\"")
        )
    }

    @Test
    fun `三态往返后保持原值`() {
        listOf(null, false, true).forEach { value ->
            val back = json.decodeFromString<CreateTransactionRequest>(json.encodeToString(base(value)))
            assertEquals("往返后 is_installment 应当保持 $value", value, back.isInstallment)
        }
    }

    @Test
    fun `取消分期的往返不会丢语义`() {
        val back = json.decodeFromString<CreateTransactionRequest>(
            json.encodeToString(base(false, clearInstallment = true))
        )
        assertEquals("往返后 is_installment 应当是 false", false, back.isInstallment)
        assertEquals("往返后 clear_installment 应当是 true", true, back.clearInstallment)
    }
}
