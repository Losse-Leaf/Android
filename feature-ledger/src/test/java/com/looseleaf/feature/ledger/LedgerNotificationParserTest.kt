package com.looseleaf.feature.ledger

import com.looseleaf.feature.ledger.service.AutoLedgerParser
import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import com.looseleaf.feature.ledger.service.NotificationChannelGroup
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

    @Test
    fun `微信转账通知各种常见文案与带数字昵称及对方已收款均可精准识别`() {
        val expenseDict = listOf("餐饮", "购物", "其他")
        val incomeDict = listOf("工资收入", "礼金红包", "其他收入")

        // 1. 好友昵称带数字或括号（「向YN1753转账100.00元」「给小王(财务)转账200元」）
        val digitNick = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 向YN1753转账100.00元",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict
        )
        assertNotNull(digitNick)
        assertEquals("expense", digitNick!!.type)
        assertEquals(10000L, digitNick.amount)
        assertEquals("转账 · YN1753", digitNick.title)
        assertEquals("微信支付", digitNick.paymentMethod)

        val parenNick = LedgerNotificationListenerService.parseBillFromText(
            content = "[2条]微信支付: 给小王(财务)转账200元",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict
        )
        assertNotNull(parenNick)
        assertEquals("expense", parenNick!!.type)
        assertEquals(20000L, parenNick.amount)
        assertEquals("转账 · 小王(财务)", parenNick.title)

        // 2. 转账成功 + 逗号金额（「微信支付 转账成功，金额¥200.00」）
        val transferSuccess = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 转账成功，金额¥200.00",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict
        )
        assertNotNull(transferSuccess)
        assertEquals("expense", transferSuccess!!.type)
        assertEquals(20000L, transferSuccess.amount)
        assertEquals("微信转账", transferSuccess.title)

        // 3. 「对方已收款」属于我方转出确认，必须记为支出而非收入
        val peerAccepted = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 对方已收款，转账金额¥50.00",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict,
            incomeCategories = incomeDict
        )
        assertNotNull(peerAccepted)
        assertEquals("expense", peerAccepted!!.type)
        assertEquals(5000L, peerAccepted.amount)

        // 4. 「收到张三的转账」「小王给你转账」「微信转账到账」必须记为收入
        val receivedFrom = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 收到张三的转账50.00元",
            pkg = "com.tencent.mm",
            incomeCategories = incomeDict
        )
        assertNotNull(receivedFrom)
        assertEquals("income", receivedFrom!!.type)
        assertEquals(5000L, receivedFrom.amount)
        assertEquals("收到转账 · 张三", receivedFrom.title)

        val sentToMe = LedgerNotificationListenerService.parseBillFromText(
            content = "服务通知 小王给你转账88.80元",
            pkg = "com.tencent.mm",
            incomeCategories = incomeDict
        )
        assertNotNull(sentToMe)
        assertEquals("income", sentToMe!!.type)
        assertEquals(8880L, sentToMe.amount)
        assertEquals("收到转账 · 小王", sentToMe.title)

        val transferArrived = LedgerNotificationListenerService.parseBillFromText(
            content = "微信支付 微信转账到账，金额¥120.00",
            pkg = "com.tencent.mm",
            incomeCategories = incomeDict
        )
        assertNotNull(transferArrived)
        assertEquals("income", transferArrived!!.type)
        assertEquals(12000L, transferArrived.amount)

        // 5. 银行短信/App 的财付通-微信转账（含负号金额与荣耀短信包名）
        val abcSigned = LedgerNotificationListenerService.parseBillFromText(
            content = "【农业银行】您尾号1234账户11月24日完成财付通交易人民币-66.50，余额1000.00",
            pkg = "com.hihonor.mms",
            expenseCategories = expenseDict
        )
        assertNotNull(abcSigned)
        assertEquals("expense", abcSigned!!.type)
        assertEquals(6650L, abcSigned.amount)
        assertEquals("微信支付", abcSigned.paymentMethod)

        val smsTenpayTransfer = LedgerNotificationListenerService.parseBillFromText(
            content = "您尾号8821账户11月24日21:15财付通-微信转账，金额50.00元，活期余额900.00元",
            pkg = "com.android.messaging",
            expenseCategories = expenseDict
        )
        assertNotNull(smsTenpayTransfer)
        assertEquals("expense", smsTenpayTransfer!!.type)
        assertEquals(5000L, smsTenpayTransfer.amount)
        assertEquals("微信支付", smsTenpayTransfer.paymentMethod)
    }

    @Test
    fun `普通好友聊天说微信转账仍被屏蔽不误记`() {
        val casualChat = LedgerNotificationListenerService.parseBillFromText(
            content = "小明 我刚刚微信转账给你 50.00 元了",
            pkg = "com.tencent.mm"
        )
        assertNull("普通聊天消息提到微信转账不得误记", casualChat)
    }

    @Test
    fun `农业银行入账与转存及支付宝各种扣款通知均可精确识别`() {
        val incomeDict = listOf("工资收入", "礼金红包", "其他收入")
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")

        // 1. 农业银行 95599 短信/App 入账与转存（无+号、无「元」字、含尾号4位数字）
        val abcDeposit = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户11月25日14:20完成转存交易人民币5000.00，余额15000.00。",
            pkg = "com.android.bankabc",
            incomeCategories = incomeDict
        )
        assertNotNull(abcDeposit)
        assertEquals("income", abcDeposit!!.type)
        assertEquals(500000L, abcDeposit.amount)
        assertEquals("银行卡", abcDeposit.paymentMethod)

        val abcSalarySms = LedgerNotificationListenerService.parseBillFromText(
            content = "95599 【中国农业银行】您尾号6618账户11月25日完成代发工资交易人民币8500.00，当前余额18500.00",
            pkg = "com.android.mms",
            incomeCategories = incomeDict
        )
        assertNotNull(abcSalarySms)
        assertEquals("income", abcSalarySms!!.type)
        assertEquals(850000L, abcSalarySms.amount)

        val abcUnionpayIn = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号1234借记卡收入500.00元，活期余额3200.00元",
            pkg = "com.abchina.abcpocket",
            incomeCategories = incomeDict
        )
        assertNotNull(abcUnionpayIn)
        assertEquals("income", abcUnionpayIn!!.type)
        assertEquals(50000L, abcUnionpayIn.amount)

        // 2. 农业银行财付通/支付宝扣款（负号与卡尾号遮罩）
        val abcExpense = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户11月25日完成财付通交易人民币-38.50，余额1200.00",
            pkg = "com.android.bankabc",
            expenseCategories = expenseDict
        )
        assertNotNull(abcExpense)
        assertEquals("expense", abcExpense!!.type)
        assertEquals(3850L, abcExpense.amount)

        // 3. 支付宝真实扣款：含「本月」「花呗」「自动扣款」与千分位金额不得被误拦
        val alipayAutoDebit = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 自动扣款：已从你的招商银行储蓄卡(1234)扣款28.00元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull(alipayAutoDebit)
        assertEquals("expense", alipayAutoDebit!!.type)
        assertEquals(2800L, alipayAutoDebit.amount)
        assertEquals("支付宝", alipayAutoDebit.paymentMethod)

        val alipayWithThisMonthNote = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 你有一笔42.75元的支出，点击查看本月消费明细",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull("含「本月」的非总结型单笔付款通知不得被误杀", alipayWithThisMonthNote)
        assertEquals("expense", alipayWithThisMonthNote!!.type)
        assertEquals(4275L, alipayWithThisMonthNote.amount)

        val alipayHuabei = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 花呗：你在星巴克消费了36.80元，本月累计额度充足",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull(alipayHuabei)
        assertEquals("expense", alipayHuabei!!.type)
        assertEquals(3680L, alipayHuabei.amount)
    }

    @Test
    fun `支付宝账单提醒标题与尾随奖励尾巴以及微信银行公众号动账均可识别`() {
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")
        val incomeDict = listOf("工资收入", "礼金红包", "其他收入")

        // 1. 支付宝单笔支出带「账单提醒」前缀不得再被误杀
        val alipayBillReminder = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 账单提醒：你有一笔28.50元的支出",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull(alipayBillReminder)
        assertEquals("expense", alipayBillReminder!!.type)
        assertEquals(2850L, alipayBillReminder.amount)

        // 2. 支付宝真实消费末尾附带蚂蚁森林/支付有礼/非括号抵扣描述不得被误杀
        val alipayWithRewardFooter = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 支付助手：向瑞幸咖啡付款18.00元，获得蚂蚁森林能量",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull(alipayWithRewardFooter)
        assertEquals("expense", alipayWithRewardFooter!!.type)
        assertEquals(1800L, alipayWithRewardFooter.amount)
        assertEquals("瑞幸咖啡", alipayWithRewardFooter.title)
        assertEquals("餐饮", alipayWithRewardFooter.category)

        val alipayWithDiscountFooter = LedgerNotificationListenerService.parseBillFromText(
            content = "支付宝 交易提醒：付款36.50元，含红包抵扣2.00元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = expenseDict
        )
        assertNotNull(alipayWithDiscountFooter)
        assertEquals("expense", alipayWithDiscountFooter!!.type)
        assertEquals(3650L, alipayWithDiscountFooter.amount)

        // 3. 微信内「中国农业银行微银行 / 招商银行信用卡」公众号动账推送
        val wxAbcAlert = AutoLedgerParser.parse(
            content = "中国农业银行微银行 交易提醒：您尾号7619账户支出人民币50.00元",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict,
            incomeCategories = incomeDict
        )
        assertNotNull(wxAbcAlert)
        assertEquals("expense", wxAbcAlert!!.request.type)
        assertEquals(5000L, wxAbcAlert.request.amount)
        assertEquals("银行卡", wxAbcAlert.request.paymentMethod)
        assertEquals(AutoLedgerParser.ORIGIN_BANK, wxAbcAlert.notifyOrigin)
        assertEquals(NotificationChannelGroup.BANK_APP, wxAbcAlert.channelGroup)

        val wxCmbCreditAlert = AutoLedgerParser.parse(
            content = "招商银行信用卡 动账通知：尾号8888消费人民币128.00元",
            pkg = "com.tencent.mm",
            expenseCategories = expenseDict
        )
        assertNotNull(wxCmbCreditAlert)
        assertEquals("expense", wxCmbCreditAlert!!.request.type)
        assertEquals(12800L, wxCmbCreditAlert.request.amount)
        assertEquals("银行卡", wxCmbCreditAlert.request.paymentMethod)
        assertEquals(AutoLedgerParser.ORIGIN_BANK, wxCmbCreditAlert.notifyOrigin)

        // 4. 未收录包名的地区银行/定制短信 App 兜底识别
        assertTrue(
            AutoLedgerParser.looksLikeUnlistedBankOrSmsNotification(
                "【中国农业银行】您尾号4321账户完成转存交易人民币5000.00"
            )
        )
        assertFalse(
            AutoLedgerParser.looksLikeUnlistedBankOrSmsNotification(
                "朋友聊天：明天去农业银行门口见面"
            )
        )

        // 5. 农业银行 95599 各类来账/代付/带正负号跨行转账与动账通知
        val abcOtherBankIn = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户完成他行来账交易人民币5000.00，余额15000.00",
            pkg = "com.android.mms",
            incomeCategories = incomeDict
        )
        assertNotNull(abcOtherBankIn)
        assertEquals("income", abcOtherBankIn!!.type)
        assertEquals(500000L, abcOtherBankIn.amount)

        val abcUnionpayDisburse = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户完成银联代付交易人民币3200.00，余额18200.00",
            pkg = "com.android.mms",
            incomeCategories = incomeDict
        )
        assertNotNull(abcUnionpayDisburse)
        assertEquals("income", abcUnionpayDisburse!!.type)
        assertEquals(320000L, abcUnionpayDisburse.amount)

        val abcSignedTransferIn = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户完成跨行转账交易人民币+500.00，余额18700.00",
            pkg = "com.android.mms",
            incomeCategories = incomeDict
        )
        assertNotNull(abcSignedTransferIn)
        assertEquals("income", abcSignedTransferIn!!.type)
        assertEquals(50000L, abcSignedTransferIn.amount)

        val abcSignedTransferOut = LedgerNotificationListenerService.parseBillFromText(
            content = "【中国农业银行】您尾号4321账户完成跨行转账交易人民币-500.00，余额18200.00",
            pkg = "com.android.mms",
            expenseCategories = expenseDict
        )
        assertNotNull(abcSignedTransferOut)
        assertEquals("expense", abcSignedTransferOut!!.type)
        assertEquals(50000L, abcSignedTransferOut.amount)

        val abcAppComeIn = LedgerNotificationListenerService.parseBillFromText(
            content = "动账通知 您尾号4321账户发生交易，交易类型：他行来账，交易金额：5000.00元",
            pkg = "com.android.bankabc",
            incomeCategories = incomeDict
        )
        assertNotNull(abcAppComeIn)
        assertEquals("income", abcAppComeIn!!.type)
        assertEquals(500000L, abcAppComeIn.amount)

        assertTrue(
            AutoLedgerParser.isFinancialSms(
                sender = "95599",
                body = "【中国农业银行】您尾号4321账户完成转存交易CNY5000.00"
            )
        )
    }

    @Test
    fun `农行无符号入账与中性动账通知绝不误判为支出`() {
        val incomeDict = listOf("工资收入", "礼金红包", "退款报销", "其他收入")
        val expenseDict = listOf("餐饮", "交通", "购物", "其他")

        // 1. 农行无负号正数「完成转账/支付宝/财付通交易人民币500.00」应识别为收入，而不是支出
        val abcPositiveCases = listOf(
            "【中国农业银行】您尾号4321账户11月25日完成转账交易人民币500.00，余额15500.00",
            "【中国农业银行】您尾号4321账户11月25日完成支付宝交易人民币500.00，余额15500.00",
            "95599 您尾号4321账户完成财付通交易人民币500.00，余额15500.00"
        )
        for (sample in abcPositiveCases) {
            val req = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.android.bankabc",
                expenseCategories = expenseDict,
                incomeCategories = incomeDict
            )
            assertNotNull("农行无负号入账应能正常识别: $sample", req)
            assertEquals("农行无负号正数不得误判为支出: $sample", "income", req!!.type)
            assertEquals(50000L, req.amount)
        }

        // 2. 「收到跨行转账500.00元」不得误落入「转账」支出模板
        val receivedInterbank = LedgerNotificationListenerService.parseBillFromText(
            content = "银行通知：您尾号4321账户收到跨行转账500.00元",
            pkg = "com.icbc",
            expenseCategories = expenseDict,
            incomeCategories = incomeDict
        )
        assertNotNull(receivedInterbank)
        assertEquals("income", receivedInterbank!!.type)
        assertEquals(50000L, receivedInterbank.amount)

        // 3. 纯方向中性的「动账通知/转账通知/交易成功」不带消费/支出/入账动词时应放弃而非凭空造支出
        val ambiguousSamples = listOf(
            "工商银行 动账通知：您尾号8888储蓄卡账户金额500.00元，当前余额3500.00元",
            "建设银行 转账通知：交易金额500.00元，余额2500.00元"
        )
        for (sample in ambiguousSamples) {
            val parsed = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.icbc",
                expenseCategories = expenseDict,
                incomeCategories = incomeDict
            )
            assertNull("方向不明确的动账通知不得盲目记为支出: $sample", parsed)
        }

        // 4. 非农行无正负号「完成跨行转账交易」/「完成提现交易」/裸「跨行转账」不得误判为支出或收入
        val strictNoGuessSamples = listOf(
            "【招商银行】您尾号8888账户完成跨行转账交易人民币500.00，余额5000.00",
            "【中国农业银行】您尾号4321账户完成提现交易人民币500.00，余额5000.00",
            "建设银行 跨行转账500.00元"
        )
        for (sample in strictNoGuessSamples) {
            val parsed = LedgerNotificationListenerService.parseBillFromText(
                content = sample,
                pkg = "com.cmbchina.pb",
                expenseCategories = expenseDict,
                incomeCategories = incomeDict
            )
            assertNull("非明确消费/入账的中性或提现交易不得误记: $sample", parsed)
        }
    }

    @Test
    fun `智能分类引擎支持跨平台复合词消除历史误判并支持商户记忆与自定义关键词`() {
        val fullExpenseDict = listOf(
            "餐饮", "交通", "购物", "居家", "订阅", "娱乐", "医疗", "学习", "其他"
        )

        val drug = AutoLedgerParser.parse(
            content = "支付宝 你已向美团买药付款39.90元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("医疗", drug.request.category)

        val taxi = AutoLedgerParser.parse(
            content = "微信支付 向美团打车付款24.50元",
            pkg = "com.tencent.mm",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("交通", taxi.request.category)

        val hotel = AutoLedgerParser.parse(
            content = "支付宝 你已向美团酒店付款380.00元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("娱乐", hotel.request.category)

        val grid = AutoLedgerParser.parse(
            content = "支付宝 你已向国家电网付款120.00元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("居家", grid.request.category)

        val mobile = AutoLedgerParser.parse(
            content = "微信支付 向中国移动付款100.00元",
            pkg = "com.tencent.mm",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("居家", mobile.request.category)

        val icloud = AutoLedgerParser.parse(
            content = "支付宝 自动扣款：Apple iCloud 6.00元",
            pkg = "com.eg.android.AlipayGphone",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("订阅", icloud.request.category)

        val transferOut = AutoLedgerParser.parse(
            content = "微信支付 向张三转账200.00元",
            pkg = "com.tencent.mm",
            expenseCategories = fullExpenseDict
        )!!
        assertEquals("其他", transferOut.request.category)

        // 商户历史记忆与自定义关键词覆盖默认规则
        val memoryOverride = AutoLedgerParser.parse(
            content = "微信支付 你已向楼下便利店付款18.00元",
            pkg = "com.tencent.mm",
            expenseCategories = fullExpenseDict,
            learnedExpenseRules = mapOf("楼下便利店" to "餐饮")
        )!!
        assertEquals("餐饮", memoryOverride.request.category)

        val customKwOverride = AutoLedgerParser.parse(
            content = "微信支付 你已向蓝鲸手作付款68.00元",
            pkg = "com.tencent.mm",
            expenseCategories = fullExpenseDict,
            expenseCategoryKeywords = mapOf("娱乐" to listOf("蓝鲸手作"))
        )!!
        assertEquals("娱乐", customKwOverride.request.category)
    }
}
