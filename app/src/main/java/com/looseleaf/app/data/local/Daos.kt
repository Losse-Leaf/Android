package com.looseleaf.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllEntriesFlow(): Flow<List<CachedEntryEntity>>

    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllEntries(): List<CachedEntryEntity>

    /** 只判「有没有数据」：几千条档案时避免为 isNotEmpty() 拖全表（含大文本列） */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_entries LIMIT 1)")
    suspend fun hasAny(): Boolean

    @Query("SELECT * FROM cached_entries WHERE local_id = :localId LIMIT 1")
    suspend fun getEntryByLocalId(localId: Long): CachedEntryEntity?

    @Query("SELECT * FROM cached_entries WHERE server_id = :serverId LIMIT 1")
    suspend fun getEntryByServerId(serverId: Long): CachedEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CachedEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CachedEntryEntity>)

    @Update
    suspend fun update(entry: CachedEntryEntity)

    @Query("UPDATE cached_entries SET server_id = :serverId, sync_status = 'SYNCED' WHERE local_id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)

    @Query("DELETE FROM cached_entries WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM cached_entries WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM cached_entries")
    suspend fun clearAll()

    /** 旧版增量合并可能给同一个 server_id 插过重复行，全量同步时收口到最早的一行 */
    @Query(
        "DELETE FROM cached_entries WHERE server_id IS NOT NULL AND local_id NOT IN " +
            "(SELECT MIN(local_id) FROM cached_entries WHERE server_id IS NOT NULL GROUP BY server_id)"
    )
    suspend fun deleteDuplicateServerIds()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllTransactionsFlow(): Flow<List<CachedTransactionEntity>>

    @Query("SELECT * FROM cached_transactions WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllTransactions(): List<CachedTransactionEntity>

    /** 只判「有没有数据」，同 [hasAny] 于 EntryDao 的说明 */
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

    /** 同 cached_entries：收口历史遗留的重复 server_id */
    @Query(
        "DELETE FROM cached_transactions WHERE server_id IS NOT NULL AND local_id NOT IN " +
            "(SELECT MIN(local_id) FROM cached_transactions WHERE server_id IS NOT NULL GROUP BY server_id)"
    )
    suspend fun deleteDuplicateServerIds()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events WHERE failed_at IS NULL ORDER BY created_at ASC")
    suspend fun getAllPendingEvents(): List<OutboxEventEntity>

    /**
     * 按 (entity_type, local_target_id, action) 查找事件——**含失败态**。
     *
     * 编辑未同步条目时必须用它而不是 [getAllPendingEvents]：
     * 若该条目的 CREATE 事件此前已进入失败态（pending 查询会把它过滤掉），
     * 按旧写法会「本地行已更新、载荷却没改写」，重试后旧内容被推上服务器，
     * 再被服务器旧值覆盖回来——用户编辑静默丢失。
     */
    @Query(
        "SELECT * FROM outbox_events WHERE entity_type = :entityType " +
            "AND local_target_id = :localTargetId AND action = :action " +
            "ORDER BY created_at ASC LIMIT 1"
    )
    suspend fun findEventByTarget(entityType: String, localTargetId: Long, action: String): OutboxEventEntity?

    @Query("SELECT * FROM outbox_events WHERE failed_at IS NOT NULL ORDER BY failed_at DESC")
    suspend fun getFailedEvents(): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE failed_at IS NULL")
    fun getPendingEventCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE failed_at IS NOT NULL")
    fun getFailedEventCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OutboxEventEntity)

    @Delete
    suspend fun delete(event: OutboxEventEntity)

    @Query("DELETE FROM outbox_events WHERE event_id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM outbox_events WHERE entity_type = :entityType AND local_target_id = :localTargetId")
    suspend fun deleteByLocalTarget(entityType: String, localTargetId: Long)

    @Query("DELETE FROM outbox_events")
    suspend fun clearAll()

    @Query("UPDATE outbox_events SET retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun incrementRetry(eventId: String)

    /** 进入失败态：停止自动重试，但保留事件与原始载荷，等待用户处置。 */
    @Query("UPDATE outbox_events SET failed_at = :failedAt, last_error = :error, retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun markFailed(eventId: String, failedAt: String, error: String?)

    /** 用户点「重试」：清空失败态与重试计数，重新进入待发队列。 */
    @Query("UPDATE outbox_events SET failed_at = NULL, last_error = NULL, retry_count = 0 WHERE failed_at IS NOT NULL")
    suspend fun resetAllFailed()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM cached_projects ORDER BY `order` ASC, id DESC")
    fun getAllProjectsFlow(): Flow<List<CachedProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<CachedProjectEntity>)

    @Query("DELETE FROM cached_projects")
    suspend fun clearAll()
}

@Dao
interface WishDao {
    @Query("SELECT * FROM cached_wishes ORDER BY id DESC")
    fun getAllWishesFlow(): Flow<List<CachedWishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wishes: List<CachedWishEntity>)

    @Query("DELETE FROM cached_wishes")
    suspend fun clearAll()
}

@Dao
interface FootprintDao {
    @Query("SELECT * FROM cached_footprints ORDER BY visited_at DESC")
    fun getAllFootprintsFlow(): Flow<List<CachedFootprintEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(footprints: List<CachedFootprintEntity>)

    @Query("DELETE FROM cached_footprints")
    suspend fun clearAll()
}
