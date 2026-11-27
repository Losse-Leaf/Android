package com.looseleaf.feature.ledger

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.feature.ledger.service.AutoLedgerDedupeDecision
import com.looseleaf.feature.ledger.service.AutoLedgerParser
import com.looseleaf.feature.ledger.service.AutoLedgerScreenParser
import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
class AutoLedgerScreenParserTest {

    private lateinit var context: Context
    private val beijingZone = ZoneId.of("Asia/Shanghai")
    private val dtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
    fun `微信零钱转账完成页支持拆分货币符号节点并提取对方昵称`() {
        val nowMs = 1_795_527_000_000L
        val parsed = AutoLedgerScreenParser.parse(
            pkg = "com.tencent.mm",
            rawTexts = listOf(
                "支付成功",
                "待张三确认收款",
                "￥",
                "50.00",
                "收款方",
                "张三",
                "支付方式",
                "零钱",
                "支付有优惠",
                "立减",
                "￥0.50",
                "完成"
            ),
            expenseCategories = listOf("餐饮", "购物", "其他"),
            nowMs = nowMs
        )

        assertNotNull(parsed)
        assertEquals("expense", parsed!!.bill.request.type)
        assertEquals(5000L, parsed.bill.request.amount)
        assertEquals("转账 · 张三", parsed.bill.request.title)
        assertEquals("微信支付", parsed.bill.request.paymentMethod)
        assertEquals("微信", parsed.bill.channelHint)
        assertEquals(AutoLedgerParser.ORIGIN_WALLET, parsed.bill.notifyOrigin)
    }

    @Test
    fun `微信接受好友转账已收钱页面核验近5分钟收款时间并记录收入`() {
        val nowMs = 1_795_527_000_000L
        val recentStr = Instant.ofEpochMilli(nowMs - 20_000L).atZone(beijingZone).format(dtFormat)
        val oldStr = Instant.ofEpochMilli(nowMs - 2 * 60 * 60 * 1000L).atZone(beijingZone).format(dtFormat)

        val recentParsed = AutoLedgerScreenParser.parse(
            pkg = "com.tencent.mm",
            rawTexts = listOf(
                "已收钱",
                "￥",
                "128.00",
                "已存入零钱，可直接消费",
                "查看零钱",
                "转账时间",
                recentStr,
                "收款时间",
                recentStr
            ),
            incomeCategories = listOf("工资收入", "礼金红包", "其他收入"),
            nowMs = nowMs
        )
        assertNotNull(recentParsed)
        assertEquals("income", recentParsed!!.bill.request.type)
        assertEquals(12800L, recentParsed.bill.request.amount)
        assertEquals("微信转账收款", recentParsed.bill.request.title)

        // 翻看 2 小时前的历史已收钱气泡，绝不能重复记账
        val oldParsed = AutoLedgerScreenParser.parse(
            pkg = "com.tencent.mm",
            rawTexts = listOf(
                "已收钱",
                "￥128.00",
                "已存入零钱，可直接消费",
                "转账时间",
                oldStr,
                "收款时间",
                oldStr
            ),
            incomeCategories = listOf("工资收入", "礼金红包", "其他收入"),
            nowMs = nowMs
        )
        assertNull("超过 5 分钟的历史已收钱页面不得误记", oldParsed)
    }

    @Test
    fun `微信未完成转账页密码页聊天页和历史账单详情页均严格拦截`() {
        val nowMs = 1_795_527_000_000L

        // 1. 转账金额输入页（尚未支付）
        assertNull(
            AutoLedgerScreenParser.parse(
                pkg = "com.tencent.mm",
                rawTexts = listOf("转账给 张三", "转账金额", "￥", "50.00", "添加转账说明", "转账"),
                nowMs = nowMs
            )
        )

        // 2. 密码确认弹窗（尚未确认）
        assertNull(
            AutoLedgerScreenParser.parse(
                pkg = "com.tencent.mm",
                rawTexts = listOf("请输入支付密码", "向张三转账", "￥50.00", "确认支付"),
                nowMs = nowMs
            )
        )

        // 3. 尚未点「收款」的待收款页
        assertNull(
            AutoLedgerScreenParser.parse(
                pkg = "com.tencent.mm",
                rawTexts = listOf("待你收款", "￥100.00", "1天内未确认，将退还给对方", "收款"),
                nowMs = nowMs
            )
        )

        // 4. 好友在普通聊天里发「支付成功 ¥50.00 完成」企图干扰
        assertNull(
            AutoLedgerScreenParser.parse(
                pkg = "com.tencent.mm",
                rawTexts = listOf("张三", "支付成功", "¥50.00", "完成", "按住 说话", "更多功能按钮"),
                nowMs = nowMs
            )
        )

        // 5. 历史账单详情页
        assertNull(
            AutoLedgerScreenParser.parse(
                pkg = "com.tencent.mm",
                rawTexts = listOf("支付成功", "张三", "￥50.00", "交易单号", "420000123456", "对订单有疑惑", "常见问题"),
                nowMs = nowMs
            )
        )
    }

    @Test
    fun `微信完成页捕获后若银行App与短信随后到达自动去重仅记一笔`() = runBlocking {
        val nowMs = 1_795_527_000_000L
        val expenseDict = listOf("餐饮", "购物", "其他")
        val records = mutableMapOf<Long, CreateTransactionRequest>()
        var nextId = 1L

        val screenParsed = AutoLedgerScreenParser.parse(
            pkg = "com.tencent.mm",
            rawTexts = listOf("支付成功", "待张三确认收款", "￥", "88.00", "收款方", "张三", "完成"),
            expenseCategories = expenseDict,
            nowMs = nowMs
        )!!

        // 1. 微信前台完成页先捕获（最高优先级，含对象「张三」）
        val d1 = LedgerNotificationListenerService.processParsedBill(
            context = context,
            pkg = "com.tencent.mm",
            sbnKey = screenParsed.screenEventKey,
            notificationTimestampMs = screenParsed.eventTimestampMs,
            bill = screenParsed.bill,
            nowMs = nowMs,
            onCreate = { req -> nextId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d1 is AutoLedgerDedupeDecision.RecordNew)

        // 2. 停留在同一完成页 2 秒后无障碍再触发一次 → 必须去重
        val d2 = LedgerNotificationListenerService.processParsedBill(
            context = context,
            pkg = "com.tencent.mm",
            sbnKey = screenParsed.screenEventKey,
            notificationTimestampMs = screenParsed.eventTimestampMs,
            bill = screenParsed.bill,
            nowMs = nowMs + 2_000L,
            onCreate = { req -> nextId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d2 is AutoLedgerDedupeDecision.DropDuplicate)

        // 3. 3 秒后招行 App 扣款提醒到达 → 跨渠道关联去重
        val d3 = LedgerNotificationListenerService.processIncomingNotification(
            context = context,
            pkg = "com.cmbchina.pb",
            sbnKey = "0|cmb|88",
            notificationTimestampMs = nowMs + 3_000L,
            content = "招商银行 动账通知：您尾号8888的储蓄卡财付通-微信转账支出人民币88.00元",
            expenseCategories = expenseDict,
            nowMs = nowMs + 3_000L,
            onCreate = { req -> nextId++.also { records[it] = req } },
            onEnrich = { id, req -> records[id] = req; true }
        )
        assertTrue(d3 is AutoLedgerDedupeDecision.DropDuplicate)

        assertEquals(1, records.size)
        assertEquals("转账 · 张三", records.values.single().title)
        assertEquals(8800L, records.values.single().amount)
    }

    @Test
    fun `多行折叠通知优先提取最新一条转账消息而不被历史红包消息污染`() {
        val notif = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "微信支付")
                putCharSequence(Notification.EXTRA_TEXT, "[2条]微信支付: 2条新消息")
                putCharSequenceArray(
                    Notification.EXTRA_TEXT_LINES,
                    arrayOf<CharSequence>(
                        "支付有礼：送你 1.50 元红包",
                        "向YN1753转账66.00元"
                    )
                )
            }
        }
        val candidates = LedgerNotificationListenerService.extractCandidateContents(notif)
        assertTrue(candidates.isNotEmpty())
        val firstParsed = candidates
            .asSequence()
            .filterNot { LedgerNotificationListenerService.isVerificationCodeOrSpam(it) }
            .mapNotNull { LedgerNotificationListenerService.parseBillFromText(it, "com.tencent.mm") }
            .firstOrNull()
        assertNotNull(firstParsed)
        assertEquals("expense", firstParsed!!.type)
        assertEquals(6600L, firstParsed.amount)
        assertEquals("转账 · YN1753", firstParsed.title)
    }

    @Test
    fun `支付宝付款完成页使用返回商家按钮也可正常识别`() {
        val nowMs = 1_795_527_000_000L
        val parsed = AutoLedgerScreenParser.parse(
            pkg = "com.eg.android.AlipayGphone",
            rawTexts = listOf(
                "付款成功",
                "￥",
                "42.50",
                "收款方",
                "麦当劳",
                "返回商家"
            ),
            expenseCategories = listOf("餐饮", "购物", "其他"),
            nowMs = nowMs
        )
        assertNotNull(parsed)
        assertEquals("expense", parsed!!.bill.request.type)
        assertEquals(4250L, parsed.bill.request.amount)
        assertEquals("麦当劳", parsed.bill.request.title)
        assertEquals("支付宝", parsed.bill.request.paymentMethod)
        assertEquals("支付宝", parsed.bill.channelHint)
        assertEquals(AutoLedgerParser.ORIGIN_WALLET, parsed.bill.notifyOrigin)
    }
}
