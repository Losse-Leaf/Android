package com.looseleaf.feature.ledger

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.feature.ledger.service.AutoLedgerDedupeDecision
import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerNotificationDedupeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LedgerNotificationListenerService.clearDedupeCache(context)
    }

    @After
    fun tearDown() {
        LedgerNotificationListenerService.clearDedupeCache(context)
    }

    @Test
    fun `跨 5 分钟整点边界与不同 sbnKey 的重复通知必须被滑动窗口去重`() {
        val pkg = "com.eg.android.AlipayGphone"
        val normalized = LedgerNotificationListenerService.normalizeContentForDedupe(
            "支付宝 你已向星巴克付款 36.50 元"
        )

        // 模拟 12:04:59（跨越旧版 5 分钟固定分桶 300_000ms 边界前 1 秒）
        val t1 = 299_000L
        assertFalse(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|alipay|1001",
                notificationTimestampMs = t1,
                type = "expense",
                amountCents = 3650L,
                normalizedContent = normalized,
                nowMs = t1
            )
        )

        // 2 秒后（12:05:01）换了 sbnKey 重投同一内容 —— 必须判定为重复！
        val t2 = 301_000L
        assertTrue(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|alipay|1002",
                notificationTimestampMs = t2,
                type = "expense",
                amountCents = 3650L,
                normalizedContent = normalized,
                nowMs = t2
            )
        )
    }

    @Test
    fun `服务重启清空内存后仍通过持久化去重拦截历史通知重投`() {
        val pkg = "com.eg.android.AlipayGphone"
        val normalized = LedgerNotificationListenerService.normalizeContentForDedupe(
            "支付宝 付款 28.00 元"
        )
        val postWhen = 1_700_000_000_000L

        assertFalse(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|alipay|2001",
                notificationTimestampMs = postWhen,
                type = "expense",
                amountCents = 2800L,
                normalizedContent = normalized,
                nowMs = postWhen
            )
        )

        // 模拟系统杀掉并重建 NotificationListenerService 进程（内存缓存清空）
        LedgerNotificationListenerService.resetInMemoryDedupeCacheForTest()

        // 2 小时后系统重投同一条状态栏通知（相同的 sbnKey 与 when）—— 必须仍然去重
        assertTrue(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|alipay|2001",
                notificationTimestampMs = postWhen,
                type = "expense",
                amountCents = 2800L,
                normalizedContent = normalized,
                nowMs = postWhen + 2 * 60 * 60 * 1000L
            )
        )
    }

    @Test
    fun `同一通知 30 秒内原地展开文案去重，但相隔 1 分钟的两笔不同消费允许分别记账`() {
        val pkg = "com.tencent.mm"
        val t0 = 1_700_000_100_000L

        assertFalse(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|wechat|99",
                notificationTimestampMs = t0,
                type = "expense",
                amountCents = 1500L,
                normalizedContent = "微信支付 付款15.00元",
                nowMs = t0
            )
        )

        // 3 秒后同 sbnKey 原地更新为更完整的文案 —— 应视为同一笔通知去重
        assertTrue(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|wechat|99",
                notificationTimestampMs = t0 + 3_000L,
                type = "expense",
                amountCents = 1500L,
                normalizedContent = "微信支付 微信支付凭证：你已向瑞幸咖啡付款15.00元",
                nowMs = t0 + 3_000L
            )
        )

        // 90 秒后在另一家店消费了另一笔 15.00 元（不同正文、不同时间）—— 即使复用同 sbnKey 也应允许记账
        assertFalse(
            LedgerNotificationListenerService.checkAndRecordDuplicate(
                context = context,
                pkg = pkg,
                sbnKey = "0|wechat|99",
                notificationTimestampMs = t0 + 90_000L,
                type = "expense",
                amountCents = 1500L,
                normalizedContent = "微信支付 微信支付凭证：你已向全家便利店付款15.00元",
                nowMs = t0 + 90_000L
            )
        )
    }

    @Test
    fun `分组摘要通知与常驻通知及过旧重投通知必须忽略`() {
        val nowMs = 1_700_000_900_000L

        val groupSummary = Notification().apply {
            flags = Notification.FLAG_GROUP_SUMMARY
        }
        assertTrue(LedgerNotificationListenerService.shouldIgnoreNotification(groupSummary, nowMs, nowMs))

        val ongoing = Notification().apply {
            flags = Notification.FLAG_ONGOING_EVENT
        }
        assertTrue(LedgerNotificationListenerService.shouldIgnoreNotification(ongoing, nowMs, nowMs))

        val promo = Notification().apply {
            category = Notification.CATEGORY_PROMO
        }
        assertTrue(LedgerNotificationListenerService.shouldIgnoreNotification(promo, nowMs, nowMs))

        val staleNormal = Notification()
        assertTrue(
            LedgerNotificationListenerService.shouldIgnoreNotification(
                staleNormal,
                postTimeMs = nowMs - 15 * 60 * 1000L,
                nowMs = nowMs
            )
        )

        val freshNormal = Notification()
        assertFalse(
            LedgerNotificationListenerService.shouldIgnoreNotification(
                freshNormal,
                postTimeMs = nowMs - 2_000L,
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `单笔消费触发微信与招行App与银行短信与云闪付四端提醒时仅记一笔`() = runBlocking {
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")
        val records = mutableMapOf<Long, CreateTransactionRequest>()
        var nextLocalId = 1L
        val t0 = 1_700_100_000_000L

        val d1 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.tencent.mm",
            sbnKey = "0|wx|1",
            notificationTimestampMs = t0,
            content = "微信支付 微信支付凭证：你已向星巴克付款36.50元",
            expenseCategories = expenseDict,
            nowMs = t0,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d1 is AutoLedgerDedupeDecision.RecordNew)

        val d2 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.cmbchina.pb",
            sbnKey = "0|cmb|1",
            notificationTimestampMs = t0 + 2_000L,
            content = "招商银行 交易提醒：您尾号8888的储蓄卡快捷支付(财付通)人民币36.50元",
            expenseCategories = expenseDict,
            nowMs = t0 + 2_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d2 is AutoLedgerDedupeDecision.DropDuplicate)

        val d3 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.android.mms",
            sbnKey = "0|sms|1",
            notificationTimestampMs = t0 + 18_000L,
            content = "【招商银行】您尾号8888的储蓄卡11月24日快捷支付人民币36.50元，余额1200.00元",
            expenseCategories = expenseDict,
            nowMs = t0 + 18_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d3 is AutoLedgerDedupeDecision.DropDuplicate)

        val d4 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.unionpay",
            sbnKey = "0|union|1",
            notificationTimestampMs = t0 + 25_000L,
            content = "云闪付 消费提醒：您尾号8888的卡消费36.50元",
            expenseCategories = expenseDict,
            nowMs = t0 + 25_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d4 is AutoLedgerDedupeDecision.DropDuplicate)

        assertEquals(1, records.size)
        val saved = records.values.single()
        assertEquals(3650L, saved.amount)
        assertEquals("星巴克", saved.title)
        assertEquals("餐饮", saved.category)
        assertEquals("微信支付", saved.paymentMethod)
    }

    @Test
    fun `银行短信先到且支付宝后到时原地补全商户与分类且不重复记账`() = runBlocking {
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")
        val records = mutableMapOf<Long, CreateTransactionRequest>()
        var nextLocalId = 10L
        var enrichCalls = 0
        val t0 = 1_700_200_000_000L

        // 1. 银行短信先到（信息少：银行卡 + 其他）
        val d1 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.android.mms",
            sbnKey = "0|sms|10",
            notificationTimestampMs = t0,
            content = "【工商银行】您尾号6829的储蓄卡于11月24日消费36.50元",
            expenseCategories = expenseDict,
            nowMs = t0,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> enrichCalls++; records[id] = req; true }
        )
        assertTrue(d1 is AutoLedgerDedupeDecision.RecordNew)
        assertEquals(1, records.size)
        assertEquals("其他", records[10L]!!.category)

        // 2. 4 秒后支付宝到账回执到达（带商户「星巴克」与分类「餐饮」）—— 原地补全，不新建第 2 条
        val d2 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.eg.android.AlipayGphone",
            sbnKey = "0|alipay|10",
            notificationTimestampMs = t0 + 4_000L,
            content = "支付助手 你已向星巴克付款36.50元",
            expenseCategories = expenseDict,
            nowMs = t0 + 4_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> enrichCalls++; records[id] = req; true }
        )
        assertTrue(d2 is AutoLedgerDedupeDecision.EnrichExisting)
        assertEquals(10L, (d2 as AutoLedgerDedupeDecision.EnrichExisting).existingLocalId)

        // 3. 9 秒后工行 App 推送到达（质量分低于支付宝）—— 直接丢弃，不覆盖已补全的星巴克
        val d3 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.icbc",
            sbnKey = "0|icbc|10",
            notificationTimestampMs = t0 + 9_000L,
            content = "工商银行 动账通知：您尾号6829的储蓄卡消费36.50元",
            expenseCategories = expenseDict,
            nowMs = t0 + 9_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> enrichCalls++; records[id] = req; true }
        )
        assertTrue(d3 is AutoLedgerDedupeDecision.DropDuplicate)

        assertEquals(1, records.size)
        assertEquals(1, enrichCalls)
        val finalTx = records[10L]!!
        assertEquals("星巴克", finalTx.title)
        assertEquals("餐饮", finalTx.category)
        assertEquals("支付宝", finalTx.paymentMethod)
    }

    @Test
    fun `支付宝同笔支付触发支付助手与花呗双提醒去重，但短时间不同商户同金额正常分记两笔`() = runBlocking {
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")
        val records = mutableMapOf<Long, CreateTransactionRequest>()
        var nextLocalId = 1L
        val t0 = 1_700_300_000_000L

        // 支付宝同一笔咖啡同时弹「支付助手」和「花呗」
        val a1 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.eg.android.AlipayGphone",
            sbnKey = "0|alipay|a1",
            notificationTimestampMs = t0,
            content = "支付助手 你已向瑞幸咖啡付款18.00元",
            expenseCategories = expenseDict,
            nowMs = t0,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        val a2 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.eg.android.AlipayGphone",
            sbnKey = "0|alipay|a2",
            notificationTimestampMs = t0 + 3_000L,
            content = "花呗 花呗消费 18.00 元",
            expenseCategories = expenseDict,
            nowMs = t0 + 3_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(a1 is AutoLedgerDedupeDecision.RecordNew)
        assertTrue(a2 is AutoLedgerDedupeDecision.DropDuplicate)
        assertEquals(1, records.size)

        // 15 秒后在同一平台给「全家便利店」又付了一笔 18.00 元（明确不同商户）—— 应正常记为第 2 笔
        val a3 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.eg.android.AlipayGphone",
            sbnKey = "0|alipay|a3",
            notificationTimestampMs = t0 + 15_000L,
            content = "支付助手 你已向全家便利店付款18.00元",
            expenseCategories = expenseDict,
            nowMs = t0 + 15_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(a3 is AutoLedgerDedupeDecision.RecordNew)
        assertEquals(2, records.size)
    }

    @Test
    fun `微信支付与支付宝各自独立同金额消费不会被误合并`() = runBlocking {
        val expenseDict = listOf("餐饮", "购物", "其他")
        val records = mutableMapOf<Long, CreateTransactionRequest>()
        var nextLocalId = 1L
        val t0 = 1_700_400_000_000L

        val w1 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.tencent.mm",
            sbnKey = "0|wx|w1",
            notificationTimestampMs = t0,
            content = "微信支付 扫码付款 20.00 元",
            expenseCategories = expenseDict,
            nowMs = t0,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        val z1 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.eg.android.AlipayGphone",
            sbnKey = "0|zfb|z1",
            notificationTimestampMs = t0 + 45_000L,
            content = "支付宝 扫码付款 20.00 元",
            expenseCategories = expenseDict,
            nowMs = t0 + 45_000L,
            onCreate = { req -> nextLocalId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(w1 is AutoLedgerDedupeDecision.RecordNew)
        assertTrue(z1 is AutoLedgerDedupeDecision.RecordNew)
        assertEquals(2, records.size)
    }
}
