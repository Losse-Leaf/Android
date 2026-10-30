package com.looseleaf.app

import com.looseleaf.app.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 金额换算必须走字符串解析：这些用例在 Double 路径上会出现 434.999… 之类的尾差。
 */
class AmountCentsTest {

    @Test
    fun testExactCents() {
        assertEquals(29L, FormatUtils.yuanStringToCents("0.29"))
        assertEquals(435L, FormatUtils.yuanStringToCents("4.35"))
        assertEquals(3850L, FormatUtils.yuanStringToCents("38.5"))
        assertEquals(123456L, FormatUtils.yuanStringToCents("1,234.56"))
        assertEquals(10000L, FormatUtils.yuanStringToCents("¥ 100"))
        assertEquals(-1280L, FormatUtils.yuanStringToCents("-12.80"))
        assertEquals(0L, FormatUtils.yuanStringToCents(""))
    }

    @Test
    fun testRoundThirdDecimal() {
        assertEquals(101L, FormatUtils.yuanStringToCents("1.005"))
        assertEquals(100L, FormatUtils.yuanStringToCents("1.004"))
        assertEquals(100L, FormatUtils.yuanStringToCents("0.999"))
    }

    @Test
    fun testLargeAmountKeepsPrecision() {
        // 9,999,999,999.99 元；Double 在这个量级已经无法逐分精确
        assertEquals(999_999_999_999L, FormatUtils.yuanStringToCents("9999999999.99"))
    }

    @Test
    fun testInvalidInputFallsBackToZero() {
        assertEquals(0L, FormatUtils.yuanStringToCents("abc"))
        assertEquals(0L, FormatUtils.yuanStringToCents("1.2.3"))
        assertEquals(0L, FormatUtils.yuanStringToCents("--1"))
    }
}
