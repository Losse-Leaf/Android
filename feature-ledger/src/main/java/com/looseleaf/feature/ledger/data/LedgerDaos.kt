package com.looseleaf.feature.ledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class MonthlyTotalsRow(
    @ColumnInfo(name = "total_expense") val totalExpense: Long = 0,
    @ColumnInfo(name = "total_income") val totalIncome: Long = 0
)

data class CategoryTotalRow(
    val category: String = "",
    val total: Long = 0
)

data class LedgerSyncIndex(
    val localId: Long,
    val serverId: Long?,
    val syncStatus: String
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllTransactionsFlow(): Flow<List<CachedTransactionEntity>>

    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllTransactions(): List<CachedTransactionEntity>

    /**
     * AND-4：只取某个月份的流水。
     *
     * `occurred_at` 是 ISO 字符串（北京时间 +08:00），同格式下可直接做范围比较，
     * 从而命中 `index_cached_transactions_occurred_at` 索引。
     * 边界：[startIso] 含、[endIso] 不含。
     */
    @Query(
        "SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' " +
            "AND occurred_at >= :startIso AND occurred_at < :endIso " +
            "ORDER BY occurred_at DESC"
    )
    fun getTransactionsInRangeFlow(
        startIso: String,
        endIso: String
    ): Flow<List<CachedTransactionEntity>>

    /**
     * AND-4：月度收支汇总下推 SQL。
     *
     * 金额是 Long（分），SUM 结果用 COALESCE 兜住空月份。
     */
    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN source = 'installment_repay' THEN fee_amount WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS total_expense, " +
            "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS total_income " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' " +
            "AND occurred_at >= :startIso AND occurred_at < :endIso"
    )
    fun getMonthlyTotalsFlow(startIso: String, endIso: String): Flow<MonthlyTotalsRow>

    /** AND-4：月度分类支出下推 SQL（GROUP BY category，按金额降序） */
    @Query(
        "SELECT category AS category, " +
            "COALESCE(SUM(CASE WHEN source = 'installment_repay' THEN fee_amount ELSE amount END), 0) AS total " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND type = 'expense' " +
            "AND occurred_at >= :startIso AND occurred_at < :endIso " +
            "GROUP BY category HAVING total > 0 ORDER BY total DESC"
    )
    fun getMonthlyCategoryTotalsFlow(
        startIso: String,
        endIso: String
    ): Flow<List<CategoryTotalRow>>

    @Query(
        "SELECT local_id AS localId, server_id AS serverId, sync_status AS syncStatus " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE'"
    )
    suspend fun getTransactionSyncIndex(): List<LedgerSyncIndex>

    @Query("SELECT EXISTS(SELECT 1 FROM cached_transactions LIMIT 1)")
    suspend fun hasAny(): Boolean

    @Query("SELECT * FROM cached_transactions WHERE local_id = :localId LIMIT 1")
    suspend fun getTransactionByLocalId(localId: Long): CachedTransactionEntity?

    @Query("SELECT * FROM cached_transactions WHERE server_id = :serverId LIMIT 1")
    suspend fun getTransactionByServerId(serverId: Long): CachedTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: CachedTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CachedTransactionEntity>)

    @Update
    suspend fun update(transaction: CachedTransactionEntity)

    @Query("UPDATE cached_transactions SET server_id = :serverId, sync_status = 'SYNCED' WHERE local_id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)

    @Query("DELETE FROM cached_transactions WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM cached_transactions WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM cached_transactions")
    suspend fun clearAll()

    /** 收口历史遗留的重复 server_id */
    @Query(
        "DELETE FROM cached_transactions WHERE server_id IS NOT NULL AND local_id NOT IN " +
            "(SELECT MIN(local_id) FROM cached_transactions WHERE server_id IS NOT NULL GROUP BY server_id)"
    )
    suspend fun deleteDuplicateServerIds()

    /**
     * 同一 server_id 同时存在「待同步本地行」与「已同步旧行」时，保留本地行。
     */
    @Query(
        "DELETE FROM cached_transactions WHERE server_id IS NOT NULL AND sync_status = 'SYNCED' " +
            "AND EXISTS (SELECT 1 FROM cached_transactions p WHERE p.server_id = cached_transactions.server_id " +
            "AND p.sync_status != 'SYNCED')"
    )
    suspend fun deleteSyncedShadowsOfPending()
}

@Dao
interface LedgerOutboxDao {
    @Query("SELECT * FROM ledger_outbox_events WHERE failed_at IS NULL ORDER BY created_at ASC")
    suspend fun getAllPendingEvents(): List<LedgerOutboxEventEntity>

    @Query("SELECT * FROM ledger_outbox_events WHERE local_target_id = :localTargetId AND action = :action LIMIT 1")
    suspend fun findEventByTarget(localTargetId: Long, action: String): LedgerOutboxEventEntity?

    @Query("SELECT * FROM ledger_outbox_events WHERE failed_at IS NOT NULL ORDER BY failed_at DESC")
    suspend fun getFailedEvents(): List<LedgerOutboxEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LedgerOutboxEventEntity)

    @Query("DELETE FROM ledger_outbox_events WHERE event_id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM ledger_outbox_events WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM ledger_outbox_events WHERE local_target_id = :localTargetId")
    suspend fun deleteByLocalTarget(localTargetId: Long)

    @Query("UPDATE ledger_outbox_events SET retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun incrementRetry(eventId: String)

    @Query("UPDATE ledger_outbox_events SET failed_at = :failedAt, last_error = :lastError WHERE event_id = :eventId")
    suspend fun markFailed(eventId: String, failedAt: String, lastError: String)

    suspend fun recordFailure(eventId: String, error: String) {
        val now = java.time.LocalDateTime.now().toString()
        markFailed(eventId, now, error)
    }
}
