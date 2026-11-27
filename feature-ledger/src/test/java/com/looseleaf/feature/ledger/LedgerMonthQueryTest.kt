package com.looseleaf.feature.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.feature.ledger.data.CachedTransactionEntity
import com.looseleaf.feature.ledger.data.LedgerDatabase
import com.looseleaf.feature.ledger.data.monthRangeOf
import com.looseleaf.feature.ledger.data.toCacheEntity
import com.looseleaf.feature.ledger.data.toDomainModel
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
 * AND-4：账本月度查询的边界与流水聚合语义。
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
        source: String? = null,
        feeAmount: Long = 0,
        syncStatus: String = "SYNCED"
    ) = CachedTransactionEntity(
        localId = id,
        serverId = id,
        amount = amount,
        type = type,
        category = category,
        title = "账目 $id",
        occurredAt = occurredAt,
        source = source,
        feeAmount = feeAmount,
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
    fun `月度流水聚合与分期手续费口径正确`() = runBlocking {
        seed()
        db.transactionDao().insert(
            tx(
                id = 8,
                occurredAt = "2026-10-18T10:00:00+08:00",
                amount = 10500,
                category = "利息手续费",
                source = "installment_repay",
                feeAmount = 500
            )
        )
        val range = monthRangeOf(YearMonth.of(2026, 10))
        val domainList = db.transactionDao()
            .getTransactionsInRangeFlow(range.startIso, range.endIso)
            .first()
            .map { it.toDomainModel() }

        val totalExpense = domainList
            .filter { it.type != "income" }
            .sumOf { if (it.source == "installment_repay") it.feeAmount else it.amount }
        val totalIncome = domainList
            .filter { it.type == "income" }
            .sumOf { it.amount }

        // 支出：tx2(1000) + tx3(2000) + tx4(3000) + tx8分期仅计手续费(500) = 6500
        assertEquals(6500L, totalExpense)
        assertEquals(9000L, totalIncome)
    }

    @Test
    fun `远端UTC跨日时间串经toCacheEntity归一化为北京时间后正确归入次月查询`() = runBlocking {
        // UTC 2026-10-31T17:00:00Z 对应北京时间 2026-11-01T01:00:00+08:00，必须归入 11 月而非 10 月
        val remoteUtcTx = com.looseleaf.app.model.Transaction(
            id = 99L,
            amount = 8800L,
            type = "income",
            category = "工资收入",
            title = "凌晨入账",
            occurredAt = "2026-10-31T17:00:00Z"
        )
        db.transactionDao().insert(remoteUtcTx.toCacheEntity())

        val octRows = db.transactionDao()
            .getTransactionsInRangeFlow(monthRangeOf(YearMonth.of(2026, 10)).startIso, monthRangeOf(YearMonth.of(2026, 10)).endIso)
            .first()
        val novRows = db.transactionDao()
            .getTransactionsInRangeFlow(monthRangeOf(YearMonth.of(2026, 11)).startIso, monthRangeOf(YearMonth.of(2026, 11)).endIso)
            .first()

        assertEquals(emptyList<Long>(), octRows.map { it.serverId })
        assertEquals(listOf(99L), novRows.map { it.serverId })
    }
}
