package com.looseleaf.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedTransactionEntity
import com.looseleaf.app.data.local.monthRangeOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * AND-4：账本月度查询的边界与汇总语义。
 *
 * 月份过滤已从 Compose 内存 filter 下推到 SQL，边界一旦写错，
 * 用户会在「月初/月末」看到少一条或多一条账目 —— 这里用真实 SQLite 钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerMonthQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
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
    fun `月度汇总与分类占比由 SQL 聚合`() = runBlocking {
        seed()
        val range = monthRangeOf(YearMonth.of(2026, 10))
        val totals = db.transactionDao().getMonthlyTotalsFlow(range.startIso, range.endIso).first()
        // 支出 1000 + 2000 + 3000；收入 9000（待删除的 5000 不计）
        assertEquals(6000L, totals.totalExpense)
        assertEquals(9000L, totals.totalIncome)

        val categories = db.transactionDao()
            .getMonthlyCategoryTotalsFlow(range.startIso, range.endIso)
            .first()
        assertEquals(listOf("餐饮", "交通"), categories.map { it.category })
        assertEquals(listOf(4000L, 2000L), categories.map { it.total })
    }

    @Test
    fun `空月份汇总为零而不是 null`() = runBlocking {
        val range = monthRangeOf(YearMonth.of(2024, 2))
        val totals = db.transactionDao().getMonthlyTotalsFlow(range.startIso, range.endIso).first()
        assertEquals(0L, totals.totalExpense)
        assertEquals(0L, totals.totalIncome)
        assertTrue(db.transactionDao().getMonthlyCategoryTotalsFlow(range.startIso, range.endIso).first().isEmpty())
    }

    @Test
    fun `月度范围查询命中 occurred_at 索引`() {
        seed()
        val range = monthRangeOf(YearMonth.of(2026, 10))
        val plan = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT * FROM cached_transactions " +
                "WHERE sync_status != 'PENDING_DELETE' " +
                "AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC",
            arrayOf(range.startIso, range.endIso)
        ).use { cursor ->
            val detailIndex = cursor.getColumnIndexOrThrow("detail")
            while (cursor.moveToNext()) plan += cursor.getString(detailIndex)
        }
        assertTrue("应使用 SEARCH + occurred_at 索引，实际执行计划：$plan",
            plan.any { it.contains("SEARCH", ignoreCase = true) } &&
                plan.any { it.contains("index_cached_transactions_occurred_at") })
    }
}
