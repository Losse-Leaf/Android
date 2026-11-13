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
/** 仅用于一次性时间迁移的轻量投影 */
data class TimeRow(
    @ColumnInfo(name = "local_id") val localId: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: String
)

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

    /**
     * 时间流的**全能组合筛选分页查询**
     */
    @Query(
        """
        SELECT * FROM cached_entries 
        WHERE sync_status != 'PENDING_DELETE'
          AND (:year IS NULL OR substr(occurred_at, 1, 4) = :year)
          AND (:month IS NULL OR substr(occurred_at, 6, 2) = :month)
          AND (
            :hasCategories = 0
            OR (:includeDaily = 1 AND lower(type) IN ('life','daily') AND city_code IS NULL AND (project_id IS NULL OR project_id <= 0) AND (wish_id IS NULL OR wish_id <= 0))
            OR (:includeTravel = 1 AND (lower(type) = 'travel' OR city_code IS NOT NULL))
            OR (:includeProject = 1 AND (lower(type) = 'project' OR project_id > 0))
            OR (:includeWish = 1 AND (lower(type) = 'wish' OR wish_id > 0))
          )
          AND (:hasImage = 0 OR (attachments_json != '[]' AND attachments_json != '' AND attachments_json IS NOT NULL))
          AND (
            (:isPending = 0 AND :isFailed = 0)
            OR (:isPending = 1 AND sync_status IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE'))
            OR (:isFailed = 1 AND EXISTS (SELECT 1 FROM outbox_events WHERE local_target_id = cached_entries.local_id AND failed_at IS NOT NULL))
          )
        ORDER BY occurred_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getEntriesPageFiltered(
        year: String?,
        month: String?,
        hasCategories: Boolean,
        includeDaily: Boolean,
        includeTravel: Boolean,
        includeProject: Boolean,
        includeWish: Boolean,
        hasImage: Boolean,
        isPending: Boolean,
        isFailed: Boolean,
        limit: Int,
        offset: Int
    ): List<CachedEntryEntity>

    /** 组合筛选的总条数（实时驱动面板底部 "查看 N 条"） */
    @Query(
        """
        SELECT COUNT(*) FROM cached_entries 
        WHERE sync_status != 'PENDING_DELETE'
          AND (:year IS NULL OR substr(occurred_at, 1, 4) = :year)
          AND (:month IS NULL OR substr(occurred_at, 6, 2) = :month)
          AND (
            :hasCategories = 0
            OR (:includeDaily = 1 AND lower(type) IN ('life','daily') AND city_code IS NULL AND (project_id IS NULL OR project_id <= 0) AND (wish_id IS NULL OR wish_id <= 0))
            OR (:includeTravel = 1 AND (lower(type) = 'travel' OR city_code IS NOT NULL))
            OR (:includeProject = 1 AND (lower(type) = 'project' OR project_id > 0))
            OR (:includeWish = 1 AND (lower(type) = 'wish' OR wish_id > 0))
          )
          AND (:hasImage = 0 OR (attachments_json != '[]' AND attachments_json != '' AND attachments_json IS NOT NULL))
          AND (
            (:isPending = 0 AND :isFailed = 0)
            OR (:isPending = 1 AND sync_status IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE'))
            OR (:isFailed = 1 AND EXISTS (SELECT 1 FROM outbox_events WHERE local_target_id = cached_entries.local_id AND failed_at IS NOT NULL))
          )
        """
    )
    suspend fun countEntriesFiltered(
        year: String?,
        month: String?,
        hasCategories: Boolean,
        includeDaily: Boolean,
        includeTravel: Boolean,
        includeProject: Boolean,
        includeWish: Boolean,
        hasImage: Boolean,
        isPending: Boolean,
        isFailed: Boolean
    ): Int

    /** 查询某年份下实际有记录的月份列表 (1~12)，无记录的置灰 */
    @Query(
        """
        SELECT DISTINCT CAST(substr(occurred_at, 6, 2) AS INTEGER) FROM cached_entries
        WHERE substr(occurred_at, 1, 4) = :year AND sync_status != 'PENDING_DELETE'
        ORDER BY 1 ASC
        """
    )
    suspend fun getAvailableMonthsForYear(year: String): List<Int>

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

    /**
     * UPDATE 投递成功后的**条件化定向回写**（服务端权威附件清单）。
     *
     * 只写 attachments_json 一列，绝不整行覆盖——UI 的乐观编辑不持 outboxMutex，
     * API 往返期间本地行可能已被换成新内容。行若仍处于待发态（PENDING_*，
     * 即编辑窗口内又被改过），服务端快照不落库，等下一轮 UPDATE 投递成功后再写。
     */
    @Query(
        "UPDATE cached_entries SET attachments_json = :attachmentsJson " +
            "WHERE local_id = :localId AND sync_status NOT LIKE 'PENDING_%'"
    )
    suspend fun updateAttachmentsIfNotPending(localId: Long, attachmentsJson: String)

    @Query("DELETE FROM cached_entries WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("UPDATE cached_entries SET sync_status = :status WHERE local_id = :localId")
    suspend fun updateSyncStatus(localId: Long, status: String)

    @Query("DELETE FROM cached_entries WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM cached_entries")
    suspend fun clearAll()

    @Query("SELECT local_id, occurred_at FROM cached_entries")
    suspend fun getEntryTimeRows(): List<TimeRow>

    @Query("UPDATE cached_entries SET occurred_at = :iso WHERE local_id = :localId")
    suspend fun updateEntryOccurredAt(localId: Long, iso: String)

    /** 旧版增量合并可能给同一个 server_id 插过重复行，全量同步时收口到最早的一行 */
    @Query(
        "DELETE FROM cached_entries WHERE server_id IS NOT NULL AND local_id NOT IN " +
            "(SELECT MIN(local_id) FROM cached_entries WHERE server_id IS NOT NULL GROUP BY server_id)"
    )
    suspend fun deleteDuplicateServerIds()

    /** 同一 server_id 同时存在待同步本地行与已同步旧行时，保留本地行 */
    @Query(
        "DELETE FROM cached_entries WHERE server_id IS NOT NULL AND sync_status = 'SYNCED' " +
            "AND EXISTS (SELECT 1 FROM cached_entries p WHERE p.server_id = cached_entries.server_id " +
            "AND p.sync_status != 'SYNCED')"
    )
    suspend fun deleteSyncedShadowsOfPending()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events WHERE failed_at IS NULL ORDER BY created_at ASC, rowid ASC")
    suspend fun getAllPendingEvents(): List<OutboxEventEntity>

    /** OPT-05：分批读取待同步事件，避免离线积压大量事件时整表载入内存 */
    @Query("SELECT * FROM outbox_events WHERE failed_at IS NULL ORDER BY created_at ASC, rowid ASC LIMIT :limit")
    suspend fun getPendingEventsBatch(limit: Int): List<OutboxEventEntity>

    /** OPT-05：分批读取并排除本轮已尝试过的事件 */
    @Query(
        "SELECT * FROM outbox_events WHERE failed_at IS NULL AND event_id NOT IN (:excludedIds) " +
            "ORDER BY created_at ASC, rowid ASC LIMIT :limit"
    )
    suspend fun getPendingEventsBatchExcluding(limit: Int, excludedIds: List<String>): List<OutboxEventEntity>

    /** OPT-05：只读取待同步的删除事件（用于宽限期检查），避免读出全部事件的大载荷 */
    @Query(
        "SELECT * FROM outbox_events WHERE failed_at IS NULL AND action = 'DELETE' " +
            "ORDER BY created_at ASC, rowid ASC"
    )
    suspend fun getPendingDeleteEvents(): List<OutboxEventEntity>

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
            "ORDER BY created_at ASC, rowid ASC LIMIT 1"
    )
    suspend fun findEventByTarget(entityType: String, localTargetId: Long, action: String): OutboxEventEntity?

    /**
     * P0-3：按「本地或服务端目标」查找事件，兼容旧版本
     * `local_target_id = -1, server_target_id = 负数临时 id` 的写法。
     * 迁移与 UI 编辑可能并发，编辑路径必须两种写法都能命中，避免重复入队。
     */
    @Query(
        "SELECT * FROM outbox_events WHERE entity_type = :entityType AND action = :action " +
            "AND (local_target_id = :targetId OR server_target_id = :targetId) " +
            "ORDER BY created_at ASC, rowid ASC LIMIT 1"
    )
    suspend fun findEventByLocalOrServerTarget(
        entityType: String,
        targetId: Long,
        action: String
    ): OutboxEventEntity?

    /** 投递前重读最新载荷：同一轮 flush 内事件可能被合并编辑或补绑服务端 id。 */
    @Query("SELECT * FROM outbox_events WHERE event_id = :eventId")
    suspend fun findEventById(eventId: String): OutboxEventEntity?

    @Query("SELECT * FROM outbox_events WHERE failed_at IS NOT NULL ORDER BY failed_at DESC")
    suspend fun getFailedEvents(): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE failed_at IS NULL")
    fun getPendingEventCountFlow(): Flow<Int>

    /** AND-7：一次性判重（仅供后台决定是否唤醒 WorkManager） */
    @Query("SELECT COUNT(*) FROM outbox_events WHERE failed_at IS NULL")
    suspend fun countPendingEvents(): Int

    /** 解绑前警告用：待发 + 失败态都属于「只存在于本机」的数据。 */
    @Query("SELECT COUNT(*) FROM outbox_events")
    suspend fun countAllEvents(): Int

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

    /**
     * P0-3：删除临时行时连旧版本「server_target_id = 负数临时 id」的事件一起撤销。
     * 仅用于 project / wish / footprint —— 其它实体两列数值域不同，不能混用。
     */
    @Query(
        "DELETE FROM outbox_events WHERE entity_type = :entityType " +
            "AND (local_target_id = :localTargetId OR server_target_id = :localTargetId)"
    )
    suspend fun deleteByLocalOrServerTarget(entityType: String, localTargetId: Long)

    /**
     * P0-3：整表拉取对账时，保护仍被发件箱引用（待更新 / 待归册 / 待删除）的本地行。
     *
     * 临时行（id < 0）不会出现在这里 —— 它们不在远端 id 域内，对账时自然保留。
     */
    @Query(
        "SELECT local_target_id FROM outbox_events " +
            "WHERE entity_type = :entityType AND local_target_id > 0 " +
            "UNION " +
            "SELECT server_target_id FROM outbox_events " +
            "WHERE entity_type = :entityType AND server_target_id IS NOT NULL AND server_target_id > 0"
    )
    suspend fun getProtectedServerTargetIds(entityType: String): List<Long>

    /**
     * P0-3：CREATE 拿到真实 server id 后，把同一临时目标的后续事件整体换绑。
     *
     * 临时本地行会被替换成 `id = serverId` 的权威行，因此 `local_target_id`
     * 也必须一起换绑，否则后续 UPDATE / RESOLVE 会找不到本地行。
     * 同时清空失败态：CREATE 成功后，之前因“等 CREATE”而失败的事件应重新自动投递。
     */
    @Query(
        "UPDATE outbox_events SET local_target_id = :serverId, server_target_id = :serverId, " +
            "failed_at = NULL, last_error = NULL, retry_count = 0 " +
            "WHERE entity_type = :entityType AND local_target_id = :tempLocalTargetId AND action != 'CREATE'"
    )
    suspend fun rebindFollowUpsToServerId(entityType: String, tempLocalTargetId: Long, serverId: Long)

    /**
     * P0-3：旧版本把 project/wish/footprint 的临时目标写成
     * `local_target_id = -1, server_target_id = 负数临时 id`。
     * 启动时一次性迁移到新模型：`local_target_id = 临时 id, server_target_id = NULL`。
     */
    @Query(
        "UPDATE outbox_events SET local_target_id = server_target_id, server_target_id = NULL " +
            "WHERE entity_type IN ('project', 'wish', 'footprint') " +
            "AND local_target_id = -1 AND server_target_id IS NOT NULL AND server_target_id < 0"
    )
    suspend fun repairLegacyNegativeSimpleEntityTargets(): Int

    /**
     * P0-3：旧版本已同步实体的 UPDATE / RESOLVE / DELETE 事件也写 `local_target_id = -1`，
     * 只带正数 `server_target_id`。迁移后让失败事件放弃逻辑也能按 local_target_id 定位本地行。
     */
    @Query(
        "UPDATE outbox_events SET local_target_id = server_target_id " +
            "WHERE entity_type IN ('project', 'wish', 'footprint') " +
            "AND local_target_id = -1 AND server_target_id IS NOT NULL AND server_target_id > 0"
    )
    suspend fun repairLegacyPositiveSimpleEntityTargets(): Int

    /**
     * P0-3：在线更新已经成功时，删掉**载荷相同**的待发 UPDATE。
     * 若投递期间用户又改了内容（载荷不同），保留事件交给下一轮按新载荷重发。
     */
    @Query(
        "DELETE FROM outbox_events WHERE event_id = :eventId AND payload = :expectedPayload"
    )
    suspend fun deleteIfPayloadUnchanged(eventId: String, expectedPayload: String): Int

    /**
     * P0-3：临时行只有一个上传入口（CREATE 载荷就是最新内容）。
     * 旧版本离线编辑可能留下同一临时目标的 UPDATE；编辑时清掉它，
     * 避免 CREATE 成功后旧 UPDATE 又把更旧的内容发回服务端。
     */
    @Query(
        "DELETE FROM outbox_events WHERE entity_type = :entityType AND action = 'UPDATE' " +
            "AND (local_target_id = :tempLocalId OR server_target_id = :tempLocalId)"
    )
    suspend fun deleteUpdatesForTempTarget(entityType: String, tempLocalId: Long)

    /**
     * CREATE 拿到服务端 id 后，把同一本地目标待发的 UPDATE 绑定到该 id。
     *
     * 这是「CREATE 响应丢失后再编辑」的关键：CREATE 载荷保持首次内容不变，
     * 后续编辑独立入队为 UPDATE；服务端按幂等键返回旧记录后，UPDATE 才真正把新内容补上去。
     */
    @Query(
        "UPDATE outbox_events SET server_target_id = :serverId, failed_at = NULL, " +
            "last_error = NULL, retry_count = 0 " +
            "WHERE entity_type = :entityType AND local_target_id = :localTargetId AND action = 'UPDATE'"
    )
    suspend fun bindPendingUpdatesToServerId(entityType: String, localTargetId: Long, serverId: Long)

    @Query("DELETE FROM outbox_events")
    suspend fun clearAll()

    @Query("UPDATE outbox_events SET retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun incrementRetry(eventId: String)

    /** 进入失败态：停止自动重试，但保留事件与原始载荷，等待用户处置。 */
    @Query("UPDATE outbox_events SET failed_at = :failedAt, last_error = :error, retry_count = retry_count + 1 WHERE event_id = :eventId")
    suspend fun markFailed(eventId: String, failedAt: String, error: String?)

    /** 用户点「单条重试」：清空该事件失败态与重试计数，重新进入待发队列。 */
    @Query("UPDATE outbox_events SET failed_at = NULL, last_error = NULL, retry_count = 0 WHERE event_id = :eventId")
    suspend fun resetFailedById(eventId: String)

    /** 用户点「全部重试」：清空失败态与重试计数，重新进入待发队列。 */
    @Query("UPDATE outbox_events SET failed_at = NULL, last_error = NULL, retry_count = 0 WHERE failed_at IS NOT NULL")
    suspend fun resetAllFailed()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM cached_projects ORDER BY `order` ASC, id DESC")
    fun getAllProjectsFlow(): Flow<List<CachedProjectEntity>>

    @Query("SELECT * FROM cached_projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CachedProjectEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM cached_projects LIMIT 1)")
    suspend fun hasAny(): Boolean

    /** P0-3：整表拉取对账用，包含负数临时行。 */
    @Query("SELECT id FROM cached_projects")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: CachedProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<CachedProjectEntity>)

    @Query("DELETE FROM cached_projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cached_projects WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM cached_projects")
    suspend fun clearAll()
}

@Dao
interface WishDao {
    @Query("SELECT * FROM cached_wishes ORDER BY id DESC")
    fun getAllWishesFlow(): Flow<List<CachedWishEntity>>

    @Query("SELECT * FROM cached_wishes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CachedWishEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM cached_wishes LIMIT 1)")
    suspend fun hasAny(): Boolean

    /** P0-3：整表拉取对账用，包含负数临时行。 */
    @Query("SELECT id FROM cached_wishes")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wish: CachedWishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wishes: List<CachedWishEntity>)

    @Query("DELETE FROM cached_wishes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cached_wishes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM cached_wishes")
    suspend fun clearAll()
}

@Dao
interface FootprintDao {
    @Query("SELECT * FROM cached_footprints ORDER BY visited_at DESC")
    fun getAllFootprintsFlow(): Flow<List<CachedFootprintEntity>>

    @Query("SELECT * FROM cached_footprints WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CachedFootprintEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM cached_footprints LIMIT 1)")
    suspend fun hasAny(): Boolean

    /** P0-3：整表拉取对账用，包含负数临时行。 */
    @Query("SELECT id FROM cached_footprints")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(footprint: CachedFootprintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(footprints: List<CachedFootprintEntity>)

    @Query("DELETE FROM cached_footprints WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cached_footprints WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM cached_footprints")
    suspend fun clearAll()
}
