package com.looseleaf.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** MOB-P1-42：条目同步索引的轻量投影（只取合并所需的三列） */
data class EntrySyncIndex(
    val localId: Long,
    val serverId: Long?,
    val syncStatus: String
)

/** MOB-P1-32：热力图的按日计数行（SQL 侧 GROUP BY 的结果） */
data class DayCountRow(
    val day: String,
    val count: Int
)


@Dao
interface EntryDao {
    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    fun getAllEntriesFlow(): Flow<List<CachedEntryEntity>>

    @Query("SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' ORDER BY occurred_at DESC")
    suspend fun getAllEntries(): List<CachedEntryEntity>

    /**
     * MOB-P1-42：合并时的**投影查询**。
     *
     * mergeEntries 只需要 (localId, serverId, syncStatus) 三列，
     * 原先却把整表（含 title / content / attachments_json 大文本列）拉进内存
     * 再做映射 —— 而这段代码正跑在**持有 SQLite 写锁的事务里**，
     * 既拉长事务时长又放大内存峰值。
     */
    @Query(
        "SELECT local_id AS localId, server_id AS serverId, sync_status AS syncStatus " +
            "FROM cached_entries WHERE sync_status != 'PENDING_DELETE'"
    )
    suspend fun getEntrySyncIndex(): List<EntrySyncIndex>

    /** 只判「有没有数据」：几千条档案时避免为 isNotEmpty() 拖全表（含大文本列） */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_entries LIMIT 1)")
    suspend fun hasAny(): Boolean

    /**
     * MOB-P1-32：可用年份（SQL DISTINCT）。
     * 原先是把**全量档案**物化到内存后再遍历取年份 ——
     * 2 万条档案时这是一次完整物化 + 一次全表扫描，纯属浪费。
     */
    @Query(
        "SELECT DISTINCT substr(occurred_at, 1, 4) AS yearStr FROM cached_entries " +
            "WHERE sync_status != 'PENDING_DELETE' ORDER BY yearStr DESC"
    )
    fun getAvailableYearStringsFlow(): Flow<List<String>>

    /**
     * MOB-P1-32：热力图的按日计数（SQL GROUP BY）。
     * 原先是在 Kotlin 侧对全量档案 groupBy + 每条一次日期解析。
     */
    @Query(
        "SELECT substr(occurred_at,1,4) || '.' || substr(occurred_at,6,2) || '.' || substr(occurred_at,9,2) AS day, " +
            "COUNT(*) AS count FROM cached_entries WHERE sync_status != 'PENDING_DELETE' GROUP BY day"
    )
    fun getDayCountsFlow(): Flow<List<DayCountRow>>

    /**
     * MOB-P1-32：往年同月同日的本地条目（SQL 直接筛选）。
     * monthDay 形如 "10-09"（ISO 串的第 6~10 位），year 为四位年份字符串。
     */
    @Query(
        "SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' " +
            "AND substr(occurred_at, 6, 5) = :monthDay AND substr(occurred_at, 1, 4) != :year " +
            "ORDER BY occurred_at DESC"
    )
    fun getLocalOnThisDayFlow(monthDay: String, year: String): Flow<List<CachedEntryEntity>>

    /**
     * MOB-P1-32：时间流的**分页查询**（列表只物化可见窗口，不再全表驻留）。
     *
     * filter 语义（**SQL 即唯一事实源**，Kotlin 侧不再保留第二份实现以免语义漂移）：
     * - all     → 全部
     * - travel  → type='travel' 或有属地城市
     * - project → type='project' 或关联了造物
     * - wish    → type='wish' 或关联了心愿
     * - daily   → 日常且无属地/无造物/无心愿
     */
    @Query(
        "SELECT * FROM cached_entries WHERE sync_status != 'PENDING_DELETE' " +
            "AND (:year IS NULL OR substr(occurred_at, 1, 4) = :year) " +
            "AND (" +
            "  :filter = 'all' " +
            "  OR (:filter = 'travel' AND (lower(type) = 'travel' OR city_code IS NOT NULL)) " +
            "  OR (:filter = 'project' AND (lower(type) = 'project' OR project_id > 0)) " +
            "  OR (:filter = 'wish' AND (lower(type) = 'wish' OR wish_id > 0)) " +
            "  OR (:filter IN ('daily','life') AND lower(type) IN ('life','daily') " +
            "     AND city_code IS NULL AND (project_id IS NULL OR project_id <= 0) " +
            "     AND (wish_id IS NULL OR wish_id <= 0))" +
            ") ORDER BY occurred_at DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getEntriesPage(
        filter: String,
        year: String?,
        limit: Int,
        offset: Int
    ): List<CachedEntryEntity>

    /**
     * MOB-P1-32：**数据变更信号**。
     *
     * Room 的失效追踪是表级的：这张表发生任何写入都会让本查询重新执行并发射，
     * 因此它可以作为「本地数据变了，该刷新列表了」的廉价信号 ——
     * 只跑一个 COUNT(*)，不必把全量档案物化进内存。
     */
    @Query("SELECT COUNT(*) FROM cached_entries")
    fun getEntriesRevisionFlow(): Flow<Long>

    /** MOB-P1-32：分页列表的总数（用于判断是否还有更多） */
    @Query(
        "SELECT COUNT(*) FROM cached_entries WHERE sync_status != 'PENDING_DELETE' " +
            "AND (:year IS NULL OR substr(occurred_at, 1, 4) = :year) " +
            "AND (" +
            "  :filter = 'all' " +
            "  OR (:filter = 'travel' AND (lower(type) = 'travel' OR city_code IS NOT NULL)) " +
            "  OR (:filter = 'project' AND (lower(type) = 'project' OR project_id > 0)) " +
            "  OR (:filter = 'wish' AND (lower(type) = 'wish' OR wish_id > 0)) " +
            "  OR (:filter IN ('daily','life') AND lower(type) IN ('life','daily') " +
            "     AND city_code IS NULL AND (project_id IS NULL OR project_id <= 0) " +
            "     AND (wish_id IS NULL OR wish_id <= 0))" +
            ")"
    )
    suspend fun countEntries(filter: String, year: String?): Int

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

    /** MOB-P1-41：账务合并用的轻量同步索引（只取三列，避免拖大文本列进事务） */
    @Query(
        "SELECT local_id AS localId, server_id AS serverId, sync_status AS syncStatus " +
            "FROM cached_transactions WHERE sync_status != 'PENDING_DELETE'"
    )
    suspend fun getTransactionSyncIndex(): List<EntrySyncIndex>

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
     * MOB-P1-49：清理长期滞留的死信。
     * 失败事件此前**永久保留**，每轮 worker 都要把整条队列（含 payload）读进内存，
     * 发件箱会随时间无限膨胀。
     */
    @Query("DELETE FROM outbox_events WHERE failed_at IS NOT NULL AND failed_at < :cutoffIso")
    suspend fun purgeFailedEventsBefore(cutoffIso: String): Int

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
