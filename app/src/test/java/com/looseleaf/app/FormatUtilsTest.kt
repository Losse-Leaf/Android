package com.looseleaf.app

import com.looseleaf.app.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testCentsToYuanString() {
        assertEquals("¥ 42.50", FormatUtils.centsToYuanString(4250L))
        assertEquals("¥ 0.00", FormatUtils.centsToYuanString(0L))
        assertEquals("¥ 0.05", FormatUtils.centsToYuanString(5L))
        assertEquals("-¥ 12.80", FormatUtils.centsToYuanString(-1280L))
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
