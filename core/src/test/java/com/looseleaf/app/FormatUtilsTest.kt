package com.looseleaf.app

import com.looseleaf.app.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testCentsToYuanString() {
        assertEquals("¥42.50", FormatUtils.centsToYuanString(4250L))
        assertEquals("¥0.00", FormatUtils.centsToYuanString(0L))
        assertEquals("¥0.05", FormatUtils.centsToYuanString(5L))
        assertEquals("-¥12.80", FormatUtils.centsToYuanString(-1280L))
    }

    @Test
    fun testYuanStringToCents() {
        assertEquals(4250L, FormatUtils.yuanStringToCents("42.50"))
        assertEquals(3850L, FormatUtils.yuanStringToCents("38.5"))
        assertEquals(10000L, FormatUtils.yuanStringToCents("¥ 100"))
        assertEquals(0L, FormatUtils.yuanStringToCents(""))
        assertEquals(99L, FormatUtils.yuanStringToCents("0.99"))
    }

    @Test
    fun testParseNaturalTransactionInput() {
        val parsed = FormatUtils.parseNaturalTransactionInput("42.5 午餐手冲咖啡")
        assertEquals(4250L, parsed.amountCents)
        assertEquals("餐饮", parsed.category)
        assertEquals("午餐手冲咖啡", parsed.title)
        assertEquals("expense", parsed.type)

        val incomeParsed = FormatUtils.parseNaturalTransactionInput("+5000 工资发放")
        assertEquals(500000L, incomeParsed.amountCents)
        assertEquals("income", incomeParsed.type)
        assertEquals("工资收入", incomeParsed.category)

        val customIncomeParsed = FormatUtils.parseNaturalTransactionInput(
            input = "+5000 工资发放",
            knownCategories = listOf("吃喝", "杂项"),
            incomeCategories = listOf("月薪", "其他入账")
        )
        assertEquals("income", customIncomeParsed.type)
        assertEquals("月薪", customIncomeParsed.category)
    }

    @Test
    fun testInferCategoryRespectsTypeAndKnownDictionary() {
        // 支出文案包含「红包」或「利息」绝不能误判成收入分类「薪资」
        assertEquals(
            "其他",
            FormatUtils.inferCategoryFromText(
                text = "支付宝 付款 36.50 元（含红包抵扣 2.00 元）",
                knownCategories = listOf("餐饮", "交通", "购物", "其他"),
                type = "expense"
            )
        )
        assertEquals(
            "利息手续费",
            FormatUtils.inferCategoryFromText(
                text = "信用卡分期利息扣款 18.00 元",
                knownCategories = listOf("餐饮", "利息手续费", "其他"),
                type = "expense"
            )
        )
        // 用户删改默认分类后，返回值必须保证落在 knownCategories 中
        assertEquals(
            "杂项",
            FormatUtils.inferCategoryFromText(
                text = "支付宝 付款 36.50 元（含红包抵扣）",
                knownCategories = listOf("杂项", "生活"),
                type = "expense"
            )
        )
        // 收入文案严格在收入字典中匹配
        assertEquals(
            "礼金红包",
            FormatUtils.inferCategoryFromText(
                text = "微信收款 收到红包 88.00 元",
                knownCategories = listOf("工资收入", "礼金红包", "其他收入"),
                type = "income"
            )
        )
    }

    @Test
    fun testResolveQuickRecordCents() {
        // 手输优先：备注解析出的金额不能覆盖用户明确敲下的数字
        assertEquals(3000L, FormatUtils.resolveQuickRecordCents(3000L, 4250L))
        // 手输为空时采纳语义解析 —— 正是「备注写 42.5 午餐，点记下自动采纳」的场景
        assertEquals(4250L, FormatUtils.resolveQuickRecordCents(0L, 4250L))
        // 两者都空 / 解析无效：不可提交
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, null))
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, 0L))
        assertEquals(0L, FormatUtils.resolveQuickRecordCents(0L, -1L))
        // 手输有效、无解析结果：照常提交
        assertEquals(1280L, FormatUtils.resolveQuickRecordCents(1280L, null))
    }

    @Test
    fun testCalculateDaysBetween() {
        val iso1 = "2026-10-01T23:00:00+08:00"
        val iso2 = "2026-10-16T08:00:00+08:00"
        val days = FormatUtils.calculateDaysBetween(iso1, iso2)
        assertEquals(15L, days)

        // 纯日期格式解析与格式化测试
        assertEquals("2026.10.09", FormatUtils.formatDisplayDate("2026-10-09"))
        assertEquals("2026.10.09", FormatUtils.formatDisplayDate("2026.10.09"))
        assertEquals("2026.10.09 14:30", FormatUtils.formatDisplayDateTime("2026-10-09 14:30:00"))
    }
}
