package com.looseleaf.feature.ledger

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import org.junit.After
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
}
