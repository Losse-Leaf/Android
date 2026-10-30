package com.looseleaf.feature.ledger

import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `普通非支付通知应被忽略`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "微信消息：今晚一起吃饭吗？",
            pkg = "com.tencent.mm"
        )
        assertNull(req)
    }
}
