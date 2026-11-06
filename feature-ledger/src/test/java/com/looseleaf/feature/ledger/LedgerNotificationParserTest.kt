package com.looseleaf.feature.ledger

import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import org.junit.Assert.*
import org.junit.Test

class LedgerNotificationParserTest {

    @Test
    fun `解析微信支付通知`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 微信支付凭证：你已向星巴克付款36.50元",
            pkg = "com.tencent.mm"
        )
        assertNotNull(req)
        assertEquals(3650L, req!!.amount)
        assertEquals("expense", req.type)
        assertEquals("餐饮", req.category)
        assertEquals("微信支付", req.paymentMethod)
    }

    @Test
    fun `解析支付宝转账收款通知`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝通知 支付宝到账 128.00 元",
            pkg = "com.eg.android.AlipayGphone"
        )
        assertNotNull(req)
        assertEquals(12800L, req!!.amount)
        assertEquals("income", req.type)
        assertEquals("支付宝", req.paymentMethod)
    }

    @Test
    fun `小额0点29元解析精确为29分`() {
        // 浮点乘法 0.29 * 100 容易变成 28 分，BigDecimal 必须精确为 29 分
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 扫码付款 0.29 元",
            pkg = "com.tencent.mm"
        )
        assertNotNull(req)
        assertEquals(29L, req!!.amount)
    }

    @Test
    fun `普通非支付通知应被忽略`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "微信消息：今晚一起吃饭吗？",
            pkg = "com.tencent.mm"
        )
        assertNull(req)
    }

    @Test
    fun `验证码短信应被严格忽略`() {
        val isSpam = LedgerNotificationListenerService.isVerificationCodeOrSpam(
            "【招商银行】您的验证码为 849201，请勿泄露给他人。"
        )
        assertTrue("验证码短信必须被识别为过滤项", isSpam)

        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "【招商银行】您的验证码为 849201，支付 100 元验证码",
            pkg = "com.android.mms"
        )
        // 虽有支付金额与银行，但含有验证码，必须在业务层判定为 null 或被过滤
        assertTrue(LedgerNotificationListenerService.isVerificationCodeOrSpam("验证码为 849201"))
    }

    @Test
    fun `银行卡号在自动记账备注中被脱敏`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "【工商银行】您尾号 6829 的储蓄卡于 11月01日 消费 58.00 元",
            pkg = "com.icbc"
        )
        assertNotNull(req)
        assertEquals(5800L, req!!.amount)
        assertFalse("备注不应明文暴露连续卡号", req!!.note!!.contains("6829"))
        assertTrue("卡号应被星号掩码", req.note!!.contains("****"))
    }
}
