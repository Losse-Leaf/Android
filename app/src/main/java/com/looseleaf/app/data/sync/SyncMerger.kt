package com.looseleaf.app.data.sync

import androidx.room.withTransaction
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.CachedTransactionEntity
import com.looseleaf.app.data.local.EntrySyncIndex
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Transaction
import kotlinx.serialization.json.Json

/**
 * 增量 / 全量合并引擎 (MOB-P1-41)
 *
 * 从 [SyncManager] 里抽出来的**可测试**合并逻辑：
 * - 「逐页 upsert」：把远端一页写进本地（保留本地未同步的修改）；
 * - 「全量收口」：删除服务端已经不存在的已同步行。
 *
 * 之所以抽出来：合并是整个离线同步里**最不能出错**的一段 ——
 * 一旦覆盖了用户本地还没上传的修改，内容就会静默丢失。
 * 抽成独立对象后可以直接用真实的 SQLite 写单元测试（见 SchemaIndexTest / SyncMergeTest）。
 */
internal object SyncMerger {

    /**
     * 逐页 upsert 生活档案。
     *
     * 规则：
     * 1. 服务端标记软删除（deletedAt 非空）→ 本地对应行一并移除；
     * 2. 本地行处于待同步态（PENDING_*）→ **绝不覆盖正文**，等发件箱把它送上去；
     * 3. 其余情况按 localId upsert（localId=0 表示新增）。
     */
    suspend fun upsertEntries(
        database: AppDatabase,
        remote: List<LifeEntry>,
        localByServerId: Map<Long, EntrySyncIndex>,
        json: Json
    ) {
        if (remote.isEmpty()) return
        val activeRemote = remote.filter { it.deletedAt.isNullOrBlank() }

        // 服务端已软删除 → 本地一并移除
        val softDeleted = remote.filter { !it.deletedAt.isNullOrBlank() }
        if (softDeleted.isNotEmpty()) {
            database.withTransaction {
                softDeleted.forEach { database.entryDao().deleteByServerId(it.id) }
            }
        }

        // 构建批量列表后一次性 insertAll：单事务 N 条 INSERT 改为一次批量执行
        val toUpsert = mutableListOf<CachedEntryEntity>()
        for (item in activeRemote) {
            val existing = localByServerId[item.id]
            if (existing != null && existing.syncStatus != "SYNCED") {
                // 本地有待推送修改：绝不覆盖正文，等发件箱把它送上去
                continue
            }
            toUpsert += item.toCacheEntity(existing?.localId ?: 0, json)
        }
        if (toUpsert.isNotEmpty()) {
            database.withTransaction { database.entryDao().insertAll(toUpsert) }
        }
    }

    /** 全量收口：删除「服务端已不存在」的已同步行（保留待同步行）。 */
    suspend fun finalizeFullEntries(
        database: AppDatabase,
        remoteIds: Set<Long>,
        localByServerId: Map<Long, EntrySyncIndex>
    ) {
        val toDelete = localByServerId.filter { (serverId, local) ->
            local.syncStatus == "SYNCED" && serverId !in remoteIds
        }.keys
        if (toDelete.isEmpty()) return
        database.withTransaction {
            toDelete.forEach { database.entryDao().deleteByServerId(it) }
        }
    }

    /** 逐页 upsert 记账流水（规则与档案一致）。 */
    suspend fun upsertTransactions(
        database: AppDatabase,
        remote: List<Transaction>,
        localByServerId: Map<Long, EntrySyncIndex>
    ) {
        if (remote.isEmpty()) return
        val activeRemote = remote.filter { it.deletedAt.isNullOrBlank() }
        val softDeleted = remote.filter { !it.deletedAt.isNullOrBlank() }
        if (softDeleted.isNotEmpty()) {
            database.withTransaction {
                softDeleted.forEach { database.transactionDao().deleteByServerId(it.id) }
            }
        }

        val toUpsert = mutableListOf<CachedTransactionEntity>()
        for (item in activeRemote) {
            val existing = localByServerId[item.id]
            if (existing != null && existing.syncStatus != "SYNCED") {
                continue
            }
            toUpsert += item.toCacheEntity(existing?.localId ?: 0)
        }
        if (toUpsert.isNotEmpty()) {
            database.withTransaction { database.transactionDao().insertAll(toUpsert) }
        }
    }

    /**
     * MOB-P2-47：撤销一次生活档案删除。
     *
     * 仅当「DELETE 事件仍在发件箱中」（即尚未上传到服务端）时才可撤销；
     * 若事件已被 Worker 处理（服务端已删），则返回 false —— 此时任何本地恢复
     * 都会造成「服务端没有、本地有」的幽灵记录。
     */
    suspend fun undoDeleteEntry(
        database: AppDatabase,
        ticket: DeleteUndoTicket
    ): Boolean {
        val entity = ticket.entity as? CachedEntryEntity ?: return false
        val stillQueued = database.outboxDao().getAllPendingEvents().any {
            it.entityType == "entry" && it.action == "DELETE" && it.localTargetId == entity.localId
        }
        if (!stillQueued) return false
        database.withTransaction {
            database.outboxDao().deleteByLocalTarget("entry", entity.localId)
            database.entryDao().insertAll(listOf(entity))
        }
        return true
    }

    /** MOB-P2-47：撤销一次账目删除 */
    suspend fun undoDeleteTransaction(
        database: AppDatabase,
        ticket: DeleteUndoTicket
    ): Boolean {
        val entity = ticket.entity as? CachedTransactionEntity ?: return false
        val stillQueued = database.outboxDao().getAllPendingEvents().any {
            it.entityType == "transaction" && it.action == "DELETE" && it.localTargetId == entity.localId
        }
        if (!stillQueued) return false
        database.withTransaction {
            database.outboxDao().deleteByLocalTarget("transaction", entity.localId)
            database.transactionDao().insertAll(listOf(entity))
        }
        return true
    }

    /** 全量收口：删除「服务端已不存在」的已同步流水。 */
    suspend fun finalizeFullTransactions(
        database: AppDatabase,
        remoteIds: Set<Long>,
        localByServerId: Map<Long, EntrySyncIndex>
    ) {
        val toDelete = localByServerId.filter { (serverId, local) ->
            local.syncStatus == "SYNCED" && serverId !in remoteIds
        }.keys
        if (toDelete.isEmpty()) return
        database.withTransaction {
            toDelete.forEach { database.transactionDao().deleteByServerId(it) }
        }
    }
}
