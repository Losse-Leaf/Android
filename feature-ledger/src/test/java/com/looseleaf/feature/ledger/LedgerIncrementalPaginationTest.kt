package com.looseleaf.feature.ledger

import com.looseleaf.app.model.Transaction
import com.looseleaf.feature.ledger.data.LedgerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P1：增量账目分页必须按服务端原始页大小推进。
 *
 * 服务端在增量首页会额外注入 purge 墓碑（不占 offset），
 * 但真实软删行也会返回并占用 offset；两者 deleted_at 都非空，客户端无法区分。
 * 因此不能用「非墓碑行数」判断终止或推进 offset。
 */
class LedgerIncrementalPaginationTest {

    private fun tx(id: Long, deletedAt: String? = null) = Transaction(
        id = id,
        amount = 100,
        type = "expense",
        category = "餐饮",
        title = "账目 $id",
        deletedAt = deletedAt
    )

    @Test
    fun `满页即使包含真实软删行也继续翻页`() {
        // 500 条里前 10 条是真实软删行：旧实现 activeCount=490，会误判末页。
        val batch = List(500) { index ->
            tx(index.toLong() + 1, deletedAt = if (index < 10) "2026-11-08T10:00:00+08:00" else null)
        }

        assertEquals(500, LedgerRepository.nextTransactionOffset(0, batch.size, 500))
        assertEquals(1000, LedgerRepository.nextTransactionOffset(500, batch.size, 500))
    }

    @Test
    fun `首页墓碑把批次撑到满页时仍按页大小推进`() {
        // 500 条真实行 + 10 条 purge 墓碑：服务端 offset 只前进了 500。
        assertEquals(500, LedgerRepository.nextTransactionOffset(0, 510, 500))
        // 495 条真实行 + 10 条墓碑：批次 505 看似满页，实际已到末页；
        // 按 500 推进最多多一次空页请求，不会跳过任何真实行。
        assertEquals(500, LedgerRepository.nextTransactionOffset(0, 505, 500))
    }

    @Test
    fun `短页终止`() {
        assertNull(LedgerRepository.nextTransactionOffset(0, 499, 500))
        assertNull(LedgerRepository.nextTransactionOffset(0, 0, 500))
    }

    @Test
    fun `非零起点按页大小递增`() {
        assertEquals(1500, LedgerRepository.nextTransactionOffset(1000, 500, 500))
    }
}
