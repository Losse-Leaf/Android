package com.looseleaf.feature.ledger

import com.looseleaf.app.model.Transaction
import com.looseleaf.feature.ledger.ui.computeMonthlyLedgerSummary
import com.looseleaf.feature.ledger.ui.isIncomeTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMonthlySummaryTest {

    private fun tx(
        id: Long,
        amount: Long,
        type: String = "expense",
        category: String = if (type.trim().equals("income", ignoreCase = true)) "工资收入" else "餐饮",
        source: String? = null,
        feeAmount: Long = 0L
    ) = Transaction(
        id = id,
        amount = amount,
        type = type,
        category = category,
        title = "账目 $id",
        source = source,
        feeAmount = feeAmount
    )

    @Test
    fun `新增一笔收入后总收入与结余同步增加且总支出绝对不变`() {
        val before = listOf(
            tx(1, amount = 12_000L, type = "expense", category = "餐饮"),
            tx(2, amount = 8_000L, type = "expense", category = "交通"),
            tx(3, amount = 50_000L, type = "income", category = "工资收入")
        )
        val beforeSummary = computeMonthlyLedgerSummary(before)
        assertEquals(50_000L, beforeSummary.totalIncome)
        assertEquals(20_000L, beforeSummary.totalExpense)
        assertEquals(30_000L, beforeSummary.balance)

        val extraIncome = 15_000L
        val after = before + tx(4, amount = extraIncome, type = "income", category = "礼金红包")
        val afterSummary = computeMonthlyLedgerSummary(after)
        assertEquals(beforeSummary.totalIncome + extraIncome, afterSummary.totalIncome)
        assertEquals(beforeSummary.totalExpense, afterSummary.totalExpense)
        assertEquals(beforeSummary.balance + extraIncome, afterSummary.balance)
    }

    @Test
    fun `正结余零结余与负结余口径准确`() {
        val zero = computeMonthlyLedgerSummary(
            listOf(
                tx(1, amount = 10_000L, type = "income"),
                tx(2, amount = 10_000L, type = "expense")
            )
        )
        assertEquals(0L, zero.balance)

        val deficit = computeMonthlyLedgerSummary(
            listOf(
                tx(1, amount = 10_000L, type = "income"),
                tx(2, amount = 25_000L, type = "expense")
            )
        )
        assertEquals(10_000L, deficit.totalIncome)
        assertEquals(25_000L, deficit.totalExpense)
        assertEquals(-15_000L, deficit.balance)
    }

    @Test
    fun `分期还款仅手续费计入月支出与利息手续费分类`() {
        val summary = computeMonthlyLedgerSummary(
            listOf(
                tx(1, amount = 30_000L, type = "expense", category = "购物"),
                tx(
                    2,
                    amount = 10_500L,
                    type = "expense",
                    category = "购物",
                    source = "installment_repay",
                    feeAmount = 500L
                ),
                tx(3, amount = 50_000L, type = "income")
            )
        )
        assertEquals(50_000L, summary.totalIncome)
        assertEquals(30_500L, summary.totalExpense)
        assertEquals(19_500L, summary.balance)
        assertEquals(listOf("购物" to 30_000L, "利息手续费" to 500L), summary.categoryTotals)
    }

    @Test
    fun `收支类型判断兼容首尾空格与大小写`() {
        val paddedIncome = tx(1, amount = 5_000L, type = " Income ")
        val paddedExpense = tx(2, amount = 2_000L, type = "EXPENSE")

        assertTrue(isIncomeTransaction(paddedIncome))
        assertFalse(isIncomeTransaction(paddedExpense))

        val summary = computeMonthlyLedgerSummary(listOf(paddedIncome, paddedExpense))
        assertEquals(5_000L, summary.totalIncome)
        assertEquals(2_000L, summary.totalExpense)
        assertEquals(3_000L, summary.balance)
    }
}
