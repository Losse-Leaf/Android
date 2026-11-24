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
        assertNull("含验证码的短信必须直接被 parseBillFromText 过滤", req)
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
        assertFalse("备注不应明文暴露连续卡号", req.note!!.contains("6829"))
        assertTrue("卡号应被星号掩码", req.note!!.contains("****"))
    }

    @Test
    fun `支付宝上周总结与周期账单及营销通知应被彻底屏蔽`() {
        val blockedSamples = listOf(
            "支付宝 上周总结：上周共支出 ¥1,280.50，较前一周减少 12%",
            "支付宝 账单小助手 上周支出 890.00 元，点击查看周报",
            "支付宝 你的本周消费报告已生成：共消费 15 笔，总支出 680.50 元",
            "支付宝 花呗账单已出，本期应还 2,340.00 元，最后还款日 11月25日",
            "支付宝 余额宝昨日收益 +2.35 元",
            "支付宝 你有 ¥5.00 消费红包待领取，点击领取",
            "支付宝 付款失败：向星巴克付款 36.50 元，余额不足",
            "支付宝 商品降价提醒 ¥99.00",
            "支付宝 账户余额 500.00 元",
            "支付宝 信用分更新通知，芝麻信用 780 分"
        )
        for (sample in blockedSamples) {
            val parsed = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.eg.android.AlipayGphone"
            )
            assertNull("非真实成交的支付宝通知不得记账: $sample", parsed)
        }
    }

    @Test
    fun `微信普通好友群聊提到消费金额不得误记`() {
        val chatSamples = listOf(
            "小明 我昨天吃饭消费了 50.00 元",
            "家庭群 刚刚转账给物业 320.00 元",
            "同事群 这个耳机只要 ¥299.00"
        )
        for (sample in chatSamples) {
            val parsed = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.tencent.mm"
            )
            assertNull("微信普通聊天消息不得误记: $sample", parsed)
        }
    }

    @Test
    fun `支出通知含红包抵扣或收款方字样时类型与分类必须合法`() {
        val customExpenseDict = listOf("餐饮", "交通", "购物", "其他")
        val customIncomeDict = listOf("工资收入", "礼金红包", "其他收入")

        // 1. 含「红包抵扣」的支出绝不能落到非法的「薪资」分类
        val redPacketDiscountTx = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 付款 36.50 元（含红包抵扣 2.00 元）",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = customExpenseDict,
            incomeCategories = customIncomeDict
        )
        assertNotNull(redPacketDiscountTx)
        assertEquals("expense", redPacketDiscountTx!!.type)
        assertEquals(3650L, redPacketDiscountTx.amount)
        assertTrue(
            "分类必须落在合法支出字典内，实际: ${redPacketDiscountTx.category}",
            redPacketDiscountTx.category in customExpenseDict
        )

        // 2. 含「收款方：星巴克」的支付宝支出绝不能被误判为 income
        val payeeExpenseTx = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 交易成功，收款方：星巴克，付款 38.00 元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = customExpenseDict,
            incomeCategories = customIncomeDict
        )
        assertNotNull(payeeExpenseTx)
        assertEquals("expense", payeeExpenseTx!!.type)
        assertEquals(3800L, payeeExpenseTx.amount)
        assertEquals("餐饮", payeeExpenseTx.category)

        // 3. 用户完全自定义分类字典（删除「餐饮」「其他」）时，仍保证命中字典内合法分类
        val renamedDict = listOf("日常开销", "吃喝玩乐")
        val renamedTx = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 付款 25.00 元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = renamedDict,
            incomeCategories = listOf("个人入账")
        )
        assertNotNull(renamedTx)
        assertTrue(renamedTx!!.category in renamedDict)
    }

    @Test
    fun `真实信用卡消费短信带可用额度后缀仍可正常解析`() {
        val req = LedgerNotificationListenerService.parseBillFromText(
            content = "【招商银行】您尾号 8888 的信用卡 11月24日 快捷支付人民币 58.00 元，当前可用额度 30000.00 元。",
            pkg = "com.cmbchina.pb"
        )
        assertNotNull(req)
        assertEquals("expense", req!!.type)
        assertEquals(5800L, req.amount)
        assertEquals("银行卡", req.paymentMethod)
    }

    @Test
    fun `组合标题与长正文时不漏掉上周总结关键字`() {
        val combined = LedgerNotificationListenerService.extractNotificationContent(
            title = "支付宝",
            text = "点击查看详情",
            bigText = "上周总结：你上周共支出 1,280.50 元",
            subText = "账单小助手"
        )
        assertTrue(combined.contains("上周总结"))
        assertNull(
            LedgerNotificationListenerService.parseBillFromText(
                content = combined,
                pkg = "com.eg.android.AlipayGphone"
            )
        )
    }

    @Test
    fun `支付宝送红包与优惠券及体验金与账户互转均不得记为支出或收入`() {
        val promoAndTransferSamples = listOf(
            "支付宝 送你 1.50 元付款红包，今日可用",
            "支付宝 恭喜获得实体店消费红包 2.00 元，已存入卡包",
            "支付宝 支付有礼：送你 0.88 元通用红包",
            "支付宝 1.88 元红包已到账，快去使用吧",
            "支付宝 商家赠送 3.00 元到店红包，满 10 元可用",
            "支付宝 付款立减 2.00 元红包已发放",
            "支付宝 余额宝体验金 1000.00 元已到账",
            "支付宝 饿了么 8 元无门槛红包已到账",
            "支付宝 滴滴打车 5 元立减券已到账",
            "支付宝 话费充值券 3.00 元已到账",
            "支付宝 品牌福利：支付专享红包 3.00 元",
            "支付宝 零钱提现到账 500.00 元",
            "支付宝 转入余额宝 200.00 元"
        )
        for (sample in promoAndTransferSamples) {
            val parsed = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.eg.android.AlipayGphone"
            )
            assertNull("平台赠红包/优惠券/体验金/互转不得记账: $sample", parsed)
        }
    }

    @Test
    fun `真实消费回执括号内含消费红包抵扣或立减附注仍精确提取实付金额与商户`() {
        val req1 = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 支付助手：你已向星巴克付款 36.50 元（已用消费红包抵扣 2.00 元）",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = listOf("餐饮", "交通", "购物", "其他")
        )
        assertNotNull(req1)
        assertEquals("expense", req1!!.type)
        assertEquals(3650L, req1.amount)
        assertEquals("星巴克", req1.title)
        assertEquals("餐饮", req1.category)

        val req2 = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 付款 18.80 元（已立减 1.20 元）",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = listOf("餐饮", "交通", "购物", "其他")
        )
        assertNotNull(req2)
        assertEquals("expense", req2!!.type)
        assertEquals(1880L, req2.amount)
    }

    @Test
    fun `银行快捷支付通知支持括号通道与财付通商户抽取且不误抓余额`() {
        val cmbWithMerchant = LedgerNotificationListenerService.parseBillFromText(
            content = "【招商银行】您尾号 8888 的储蓄卡快捷支付(财付通-星巴克)人民币 36.50 元，当前余额 1200.00 元",
            pkg = "com.cmbchina.pb",
            expenseCategories = listOf("餐饮", "购物", "其他")
        )
        assertNotNull(cmbWithMerchant)
        assertEquals(3650L, cmbWithMerchant!!.amount)
        assertEquals("星巴克", cmbWithMerchant.title)
        assertEquals("餐饮", cmbWithMerchant.category)

        val ccbIntermediary = LedgerNotificationListenerService.parseBillFromText(
            content = "【建设银行】您尾号4321的储蓄卡11月24日10:05支出(微信支付)35.00元，活期余额1234.56元",
            pkg = "com.chinamworld.main",
            expenseCategories = listOf("餐饮", "购物", "其他")
        )
        assertNotNull(ccbIntermediary)
        assertEquals(3500L, ccbIntermediary!!.amount)
        assertEquals("expense", ccbIntermediary.type)
    }
}
