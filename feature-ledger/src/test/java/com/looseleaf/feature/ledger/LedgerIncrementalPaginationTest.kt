package com.looseleaf.feature.ledger

import com.looseleaf.app.model.Transaction
import com.looseleaf.feature.ledger.data.LedgerRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2-3：增量账目分页只按「非墓碑行」推进 offset。
 *
 * 服务端在增量首页会额外注入 purge 墓碑，但墓碑不占服务端 offset。
 * 若把墓碑算进 offset，会跳过服务端真实行，造成静默漏拉；
 * 若用整页行数判断终止，墓碑混入时又会误判为还有下一页。
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
    fun `墓碑不计入活跃行数`() {
        val batch = listOf(
            tx(1),
            tx(2, deletedAt = "2026-11-08T10:00:00+08:00"),
            tx(3),
            tx(4, deletedAt = "2026-11-08T11:00:00+08:00"),
            tx(5)
        )

        assertEquals(3, LedgerRepository.activeTransactionCount(batch))
    }

    @Test
    fun `整页都是墓碑时活跃行数为零`() {
        val batch = listOf(
            tx(1, deletedAt = "2026-11-08T10:00:00+08:00"),
            tx(2, deletedAt = "2026-11-08T10:00:01+08:00")
        )

        assertEquals(0, LedgerRepository.activeTransactionCount(batch))
    }

    @Test
    fun `空白 deleted_at 视为活跃行`() {
        val batch = listOf(tx(1, deletedAt = ""), tx(2, deletedAt = "   "), tx(3))

        assertEquals(3, LedgerRepository.activeTransactionCount(batch))
    }
}
