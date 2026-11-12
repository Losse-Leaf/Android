package com.looseleaf.app

import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.FormatUtils.AmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // ── 自定义数字键盘的输入规则（金额行改纯展示后的唯一写入口）──────────
    // 这些用例保证「系统键盘/粘贴」路径被移除后，金额串永远合法、不会静默归零。

    @Test
    fun keypadRejectsSecondDotAndThirdDecimal() {
        assertEquals("1.2", AmountInput.dot("1.2"))
        assertEquals("1.23", AmountInput.digit("1.2", "3"))
        assertEquals("1.23", AmountInput.digit("1.23", "4"))
        assertEquals("12+3.4", AmountInput.digit(AmountInput.plus("12"), "3").let {
            AmountInput.digit(AmountInput.dot(it), "4")
        })
    }

    @Test
    fun keypadKeepsLeadingZeroDotUsable() {
        // 修复不能顺手把「按 . 再按 5」变成 5：用户应得到 0.5
        assertEquals("0.", AmountInput.dot(AmountInput.EMPTY))
        assertEquals("0.5", AmountInput.digit("0.", "5"))
        assertEquals("0", AmountInput.digit(AmountInput.EMPTY, "0"))
        assertEquals("5", AmountInput.digit(AmountInput.EMPTY, "5"))
    }

    @Test
    fun keypadLimitsLengthAndPlusSegments() {
        assertEquals("12345678", AmountInput.digit("1234567", "8"))
        assertEquals("12345678", AmountInput.digit("12345678", "9"))
        assertEquals("12+", AmountInput.plus("12"))
        assertEquals("12+", AmountInput.plus("12+"))
        assertEquals("0", AmountInput.plus("0"))
        assertEquals("1", AmountInput.backspace("12"))
        assertEquals("0", AmountInput.backspace("1"))
    }

    @Test
    fun sanitizeNeverProducesUnparseableAmount() {
        // 复刻「粘贴 1.2.3 / 1.999 / 超长数字」的输入：重放按键规则后必须仍可解析
        val dirty = listOf("1.2.3", "1.999", "abc", "1e5", "999999999999", "--1", "12..34+5.678")
        for (raw in dirty) {
            val clean = AmountInput.sanitize(raw)
            assertTrue("sanitize($raw) = $clean 含多个小数点", clean.count { it == '.' } <= clean.count { it == '+' } + 1)
            val cents = AmountInput.toCents(clean)
            assertTrue("sanitize($raw) = $clean 解析为负数", cents >= 0L)
            if (raw.any { it.isDigit() }) {
                assertTrue("sanitize($raw) = $clean 含数字却被静默归零", cents > 0L)
            }
            assertEquals("sanitize 必须幂等: $raw", clean, AmountInput.sanitize(clean))
        }
        assertEquals("1.23", AmountInput.sanitize("1.2.3"))
        assertEquals("15", AmountInput.sanitize("1e5"))
        assertEquals("99999999", AmountInput.sanitize("999999999999"))
    }

    @Test
    fun keypadPlusSegmentsSumInCents() {
        // 「12.34 + 5.67」累加串必须按段求和，不能被整体解析成 0
        val amount = AmountInput.sanitize("12.34+5.67")
        assertEquals("12.34+5.67", amount)
        assertEquals(1801L, AmountInput.toCents(amount))
        assertEquals(0L, AmountInput.toCents(AmountInput.EMPTY))
        assertEquals(1500L, AmountInput.toCents("12+3"))
    }
}
