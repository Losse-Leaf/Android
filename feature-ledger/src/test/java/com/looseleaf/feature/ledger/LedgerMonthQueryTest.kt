package com.looseleaf.feature.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.feature.ledger.data.CachedTransactionEntity
import com.looseleaf.feature.ledger.data.LedgerDatabase
import com.looseleaf.feature.ledger.data.monthRangeOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * AND-4：账本月度查询的边界与汇总语义。
 *
 * 验证独立 LedgerDatabase 上的月度流水查询与下推聚合。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerMonthQueryTest {

    private lateinit var db: LedgerDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tx(
        id: Long,
        occurredAt: String,
        amount: Long,
        type: String = "expense",
        category: String = "餐饮",
        syncStatus: String = "SYNCED"
    ) = CachedTransactionEntity(
        localId = id,
        serverId = id,
        amount = amount,
        type = type,
        category = category,
        title = "账目 $id",
        occurredAt = occurredAt,
        syncStatus = syncStatus
    )

    private fun seed() {
        runBlocking {
            db.transactionDao().insertAll(
                listOf(
                    tx(1, "2026-09-30T23:59:59+08:00", 111),
                    // 月初边界：含
                    tx(2, "2026-10-01T00:00:00+08:00", 1000, category = "餐饮"),
                    tx(3, "2026-10-15T12:00:00+08:00", 2000, category = "交通"),
                    tx(4, "2026-10-31T23:59:59+08:00", 3000, category = "餐饮"),
                    // 下月起点：不含
                    tx(5, "2026-11-01T00:00:00+08:00", 4000),
                    // 已标记删除：任何月份都不出现
                    tx(6, "2026-10-20T10:00:00+08:00", 5000, syncStatus = "PENDING_DELETE"),
                    // 收入
                    tx(7, "2026-10-05T09:00:00+08:00", 9000, type = "income", category = "薪资")
                )
            )
        }
    }

    @Test
    fun `月份边界为左闭右开且排除待删除`() = runBlocking {
        seed()
        val range = monthRangeOf(YearMonth.of(2026, 10))
        assertEquals("2026-10-01T00:00:00+08:00", range.startIso)
        assertEquals("2026-11-01T00:00:00+08:00", range.endIso)

        val rows = db.transactionDao().getTransactionsInRangeFlow(range.startIso, range.endIso).first()
        assertEquals(listOf(4L, 3L, 7L, 2L), rows.map { it.localId })
    }

    @Test
    fun `跨年月份区间正确`() {
        val range = monthRangeOf(YearMonth.of(2026, 12))
        assertEquals("2026-12-01T00:00:00+08:00", range.startIso)
        assertEquals("2027-01-01T00:00:00+08:00", range.endIso)
    }

    @Test
    fun `月度收支汇总下推聚合正确`() = runBlocking {
        seed()
        val range = monthRangeOf(YearMonth.of(2026, 10))
        val totals = db.transactionDao().getMonthlyTotalsFlow(range.startIso, range.endIso).first()
        // 支出：tx2(1000) + tx3(2000) + tx4(3000) = 6000
        assertEquals(6000L, totals.totalExpense)
        // 收入：tx7(9000) = 9000
        assertEquals(9000L, totals.totalIncome)
    }

    @Test
    fun `月度分类统计降序正确`() = runBlocking {
        seed()
        val range = monthRangeOf(YearMonth.of(2026, 10))
        val catTotals = db.transactionDao().getMonthlyCategoryTotalsFlow(range.startIso, range.endIso).first()
        assertEquals(2, catTotals.size)
        // 餐饮: 1000 + 3000 = 4000
        assertEquals("餐饮", catTotals[0].category)
        assertEquals(4000L, catTotals[0].total)
        // 交通: 2000
        assertEquals("交通", catTotals[1].category)
        assertEquals(2000L, catTotals[1].total)
    }
}
