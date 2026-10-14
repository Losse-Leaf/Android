package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.CachedTransactionEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 散页离线优先数据同步调度中枢 (SyncManager)
 * 职责：
 * 1. 0ms 本地乐观入库 (Optimistic UI Update)
 * 2. 持久化记录发件箱事件 (Outbox)
 * 3. 调度后台静默同步工作器 (WorkManager)
 * 4. 远程增量拉取与本地缓存合并
 */
class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val database = AppDatabase.getInstance(context)
    private val apiClient = APIClient.getInstance(context)
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // 观察待同步队列总数
    val pendingEventCountFlow: Flow<Int> = database.outboxDao().getPendingEventCountFlow()

    // 观察全量生活档案 (合并本地乐观与云端权威)
    val entriesFlow: Flow<List<LifeEntry>> = database.entryDao().getAllEntriesFlow().map { entities ->
        entities.map { it.toDomainModel(json) }
    }

    // 观察全量记账明细 (合并本地乐观与云端权威)
    val transactionsFlow: Flow<List<Transaction>> = database.transactionDao().getAllTransactionsFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * 乐观创建生活档案 (0ms 上屏)
     */
    suspend fun createEntryOptimistic(
        type: String,
        title: String,
        content: String,
        occurredAt: String = FormatUtils.beijingNowIsoString(),
        location: String? = null,
        cityCode: String? = null,
        attachmentIds: List<Long>? = null,
        camera: String? = null
    ): Long {
        val entryEntity = CachedEntryEntity(
            localId = 0,
            serverId = null,
            type = type,
            title = title,
            content = content,
            occurredAt = occurredAt,
            location = location,
            cityCode = cityCode,
            camera = camera,
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
        val localId = database.entryDao().insert(entryEntity)

        val req = CreateEntryRequest(
            type = type,
            title = title,
            content = content,
            occurredAt = occurredAt,
            location = location,
            cityCode = cityCode,
            attachmentIds = attachmentIds
        )
        val event = OutboxEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = "entry",
            action = "CREATE",
            payload = json.encodeToString(req),
            localTargetId = localId,
            serverTargetId = null
        )
        database.outboxDao().insert(event)

        scheduleSync()
        return localId
    }

    /**
     * 乐观创建记账流水 (0ms 上屏)
     */
    suspend fun createTransactionOptimistic(
        amountCents: Long,
        type: String = "expense",
        category: String,
        title: String,
        note: String? = null,
        paymentMethod: String? = null,
        occurredAt: String = FormatUtils.beijingNowIsoString(),
        cityCode: String? = null
    ): Long {
        val txEntity = CachedTransactionEntity(
            localId = 0,
            serverId = null,
            amount = amountCents,
            type = type,
            category = category,
            title = title,
            note = note,
            paymentMethod = paymentMethod,
            occurredAt = occurredAt,
            cityCode = cityCode,
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
        val localId = database.transactionDao().insert(txEntity)

        val req = CreateTransactionRequest(
            amount = amountCents,
            type = type,
            category = category,
            title = title,
            note = note,
            paymentMethod = paymentMethod,
            occurredAt = occurredAt,
            cityCode = cityCode
        )
        val event = OutboxEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = "transaction",
            action = "CREATE",
            payload = json.encodeToString(req),
            localTargetId = localId,
            serverTargetId = null
        )
        database.outboxDao().insert(event)

        scheduleSync()
        return localId
    }

    /**
     * 删除单条生活记录
     */
    suspend fun deleteEntry(localId: Long) {
        val entity = database.entryDao().getEntryByLocalId(localId) ?: return
        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "entry",
                action = "DELETE",
                payload = "{}",
                localTargetId = localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        }
        database.entryDao().deleteByLocalId(localId)
        scheduleSync()
    }

    /**
     * 删除单笔记账
     */
    suspend fun deleteTransaction(localId: Long) {
        val entity = database.transactionDao().getTransactionByLocalId(localId) ?: return
        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "transaction",
                action = "DELETE",
                payload = "{}",
                localTargetId = localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        }
        database.transactionDao().deleteByLocalId(localId)
        scheduleSync()
    }

    /**
     * 从后端全量增量拉取最新数据，并优雅合并本地缓存 (保留未同步项)
     */
    suspend fun pullRemoteData(): Result<Unit> = kotlin.runCatching {
        // 1. 同步发件箱
        flushOutboxNow()

        // 2. 拉取生活档案
        val entriesResp = apiClient.fetchEntries(page = 1, pageSize = 100)
        val remoteEntries = entriesResp.entries
        val pendingEntries = database.entryDao().getAllEntries().filter { it.syncStatus != "SYNCED" }

        val newEntities = remoteEntries.map { remote ->
            CachedEntryEntity(
                localId = 0,
                serverId = remote.id,
                type = remote.type,
                title = remote.title,
                content = remote.content,
                occurredAt = remote.occurredAt,
                location = remote.location,
                cityCode = remote.cityCode,
                cityName = remote.cityName,
                camera = null,
                attachmentsJson = json.encodeToString(remote.attachments),
                syncStatus = "SYNCED",
                createdAt = remote.createdAt ?: remote.occurredAt
            )
        }

        database.entryDao().clearAll()
        database.entryDao().insertAll(pendingEntries + newEntities)

        // 3. 拉取记账记录
        val remoteTxs = apiClient.fetchTransactions(limit = 200)
        val pendingTxs = database.transactionDao().getAllTransactions().filter { it.syncStatus != "SYNCED" }

        val newTxEntities = remoteTxs.map { remote ->
            CachedTransactionEntity(
                localId = 0,
                serverId = remote.id,
                amount = remote.amount,
                type = remote.type,
                category = remote.category,
                title = remote.title,
                note = remote.note,
                paymentMethod = remote.paymentMethod,
                occurredAt = remote.occurredAt,
                cityCode = remote.cityCode,
                syncStatus = "SYNCED",
                createdAt = remote.createdAt ?: remote.occurredAt
            )
        }

        database.transactionDao().clearAll()
        database.transactionDao().insertAll(pendingTxs + newTxEntities)
    }

    /**
     * 立即调度发件箱同步
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            OutboxSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun flushOutboxNow() {
        val pending = database.outboxDao().getAllPendingEvents()
        if (pending.isEmpty()) return

        for (event in pending) {
            try {
                when (event.entityType) {
                    "entry" -> {
                        if (event.action == "CREATE") {
                            val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                            val serverEntry = apiClient.createEntry(req)
                            database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                            database.outboxDao().delete(event)
                        } else if (event.action == "DELETE" && event.serverTargetId != null) {
                            apiClient.deleteEntry(event.serverTargetId)
                            database.outboxDao().delete(event)
                        }
                    }
                    "transaction" -> {
                        if (event.action == "CREATE") {
                            val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                            val serverTx = apiClient.createTransaction(req)
                            database.transactionDao().markSynced(event.localTargetId, serverTx.id)
                            database.outboxDao().delete(event)
                        } else if (event.action == "DELETE" && event.serverTargetId != null) {
                            apiClient.deleteTransaction(event.serverTargetId)
                            database.outboxDao().delete(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "flushOutboxNow error on event ${event.eventId}: ${e.message}")
            }
        }
    }
}

// 扩展转换函数
fun CachedEntryEntity.toDomainModel(json: Json): LifeEntry {
    val atts = try {
        json.decodeFromString<List<com.looseleaf.app.model.Attachment>>(attachmentsJson)
    } catch (_: Exception) {
        emptyList()
    }
    return LifeEntry(
        id = serverId ?: -localId,
        type = type,
        title = title,
        content = content,
        occurredAt = occurredAt,
        location = location,
        cityCode = cityCode,
        cityName = cityName,
        attachments = atts,
        syncStatus = syncStatus,
        createdAt = createdAt
    )
}

fun CachedTransactionEntity.toDomainModel(): Transaction {
    return Transaction(
        id = serverId ?: -localId,
        amount = amount,
        type = type,
        category = category,
        title = title,
        note = note,
        paymentMethod = paymentMethod,
        occurredAt = occurredAt,
        cityCode = cityCode,
        syncStatus = syncStatus,
        createdAt = createdAt
    )
}
