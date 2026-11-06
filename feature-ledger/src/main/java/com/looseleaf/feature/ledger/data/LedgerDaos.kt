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
    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL ORDER BY occurred_at DESC")
    fun getAllTransactionsFlow(): Flow<List<CachedTransactionEntity>>

    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL ORDER BY occurred_at DESC")
    suspend fun getAllTransactions(): List<CachedTransactionEntity>

    /**
     * AND-4：只取某个月份的流水。
     *
     * `occurred_at` 是 ISO 字符串（北京时间 +08:00），同格式下可直接做范围比较，
     * 从而命中 `index_cached_transactions_occurred_at` 索引。
     * 边界：[startIso] 含、[endIso] 不含。
     */
    @Query(
        "SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL " +
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
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL " +
            "AND occurred_at >= :startIso AND occurred_at < :endIso"
    )
    fun getMonthlyTotalsFlow(startIso: String, endIso: String): Flow<MonthlyTotalsRow>

    /** AND-4：月度分类支出下推 SQL（GROUP BY category，按金额降序） */
    @Query(
        "SELECT category AS category, " +
            "COALESCE(SUM(CASE WHEN source = 'installment_repay' THEN fee_amount ELSE amount END), 0) AS total " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL AND type = 'expense' " +
            "AND occurred_at >= :startIso AND occurred_at < :endIso " +
            "GROUP BY category HAVING total > 0 ORDER BY total DESC"
    )
    fun getMonthlyCategoryTotalsFlow(
        startIso: String,
        endIso: String
    ): Flow<List<CategoryTotalRow>>

    @Query(
        "SELECT local_id AS localId, server_id AS serverId, sync_status AS syncStatus " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' AND deleted_at IS NULL"
    )
    suspend fun getTransactionSyncIndex(): List<LedgerSyncIndex>

    @Query("SELECT EXISTS(SELECT 1 FROM cached_transactions WHERE deleted_at IS NULL LIMIT 1)")
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

    @Query("UPDATE cached_transactions SET server_id = :serverId, sync_status = 'SYNCED', updated_at = COALESCE(:updatedAt, updated_at) WHERE local_id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long, updatedAt: String? = null)

    @Query("UPDATE cached_transactions SET sync_status = 'PENDING_DELETE' WHERE local_id = :localId")
    suspend fun markPendingDelete(localId: Long)

    @Query("UPDATE cached_transactions SET deleted_at = :deletedAt WHERE server_id = :serverId")
    suspend fun softDeleteByServerId(serverId: Long, deletedAt: String)

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
    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM ledger_outbox_events")
    suspend fun getNextSequence(): Long

    @Query("SELECT * FROM ledger_outbox_events WHERE failed_at IS NULL AND (next_retry_at IS NULL OR next_retry_at <= :now) ORDER BY sequence ASC")
    suspend fun getAllPendingEvents(now: String): List<LedgerOutboxEventEntity>

    suspend fun getAllPendingEvents(): List<LedgerOutboxEventEntity> {
        val now = java.time.Instant.now().toString()
        return getAllPendingEvents(now)
    }

    @Query("SELECT * FROM ledger_outbox_events WHERE local_target_id = :localTargetId AND failed_at IS NULL LIMIT 1")
    suspend fun findActiveEventByLocalTarget(localTargetId: Long): LedgerOutboxEventEntity?

    @Query("SELECT * FROM ledger_outbox_events WHERE local_target_id = :localTargetId AND action = :action LIMIT 1")
    suspend fun findEventByTarget(localTargetId: Long, action: String): LedgerOutboxEventEntity?

    @Query("SELECT * FROM ledger_outbox_events WHERE failed_at IS NOT NULL ORDER BY failed_at DESC")
    suspend fun getFailedEvents(): List<LedgerOutboxEventEntity>

    @Query("SELECT COUNT(*) FROM ledger_outbox_events WHERE failed_at IS NULL")
    suspend fun countPendingEvents(): Int

    @Query("SELECT COUNT(*) FROM ledger_outbox_events WHERE failed_at IS NOT NULL")
    suspend fun countFailedEvents(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LedgerOutboxEventEntity)

    @Query("UPDATE ledger_outbox_events SET payload = :payload WHERE event_id = :eventId")
    suspend fun updatePayload(eventId: String, payload: String)

    @Query("UPDATE ledger_outbox_events SET server_target_id = :serverId WHERE local_target_id = :localTargetId AND server_target_id IS NULL")
    suspend fun bindServerId(localTargetId: Long, serverId: Long)

    @Query("UPDATE ledger_outbox_events SET failed_at = NULL, last_error = NULL, retry_count = 0, next_retry_at = NULL WHERE event_id = :eventId")
    suspend fun resetEventToPending(eventId: String)

    @Query("UPDATE ledger_outbox_events SET failed_at = NULL, last_error = NULL, retry_count = 0, next_retry_at = NULL WHERE failed_at IS NOT NULL")
    suspend fun retryAllFailed()

    @Query("DELETE FROM ledger_outbox_events WHERE failed_at IS NOT NULL")
    suspend fun discardAllFailed()

    @Query("DELETE FROM ledger_outbox_events WHERE failed_at IS NOT NULL AND failed_at < :beforeIso")
    suspend fun cleanExpiredFailedEvents(beforeIso: String)

    @Query("DELETE FROM ledger_outbox_events WHERE event_id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM ledger_outbox_events WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM ledger_outbox_events WHERE local_target_id = :localTargetId")
    suspend fun deleteByLocalTarget(localTargetId: Long)

    @Query("UPDATE ledger_outbox_events SET retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun incrementRetry(eventId: String)

    @Query("UPDATE ledger_outbox_events SET next_retry_at = :nextRetryAt, retry_count = retry_count + 1, last_error = :error WHERE event_id = :eventId")
    suspend fun scheduleRetry(eventId: String, nextRetryAt: String, error: String)

    @Query("UPDATE ledger_outbox_events SET failed_at = :failedAt, last_error = :lastError WHERE event_id = :eventId")
    suspend fun markFailed(eventId: String, failedAt: String, lastError: String)

    suspend fun recordFailure(eventId: String, error: String) {
        val now = java.time.Instant.now().toString()
        markFailed(eventId, now, error)
    }
}
