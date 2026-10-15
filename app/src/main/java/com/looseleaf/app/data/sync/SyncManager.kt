package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.looseleaf.app.darkroom.ExtractedExif
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.CachedTransactionEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.model.Attachment
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class OfflineMediaFileItem(
    val filePath: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class OfflineMediaEntryPayload(
    val localImageFilePath: String = "",
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val files: List<OfflineMediaFileItem> = emptyList(),
    val req: CreateEntryRequest,
    val camera: String? = null
)

/**
 * 散页离线优先数据同步调度中枢 (SyncManager)
 * 职责：
 * 1. 0ms 本地乐观入库 (Optimistic UI Update)
 * 2. 持久化记录发件箱事件 (Outbox，支持 entry / transaction / media_entry)
 * 3. 调度后台静默同步工作器 (WorkManager)
 * 4. 远程增量拉取 (?since= 游标) 与软删除对齐 (deleted_at)
 */
class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_PREFS_NAME = "looseleaf_sync_prefs"
        private const val KEY_ENTRIES_CURSOR = "last_entries_sync_cursor"
        private const val KEY_TX_CURSOR = "last_tx_sync_cursor"

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
    val outboxMutex = Mutex()

    private val syncPrefs by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

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
     * 将压制后的相纸字节保存至离线发件箱目录
     */
    fun saveCompressedImageToOutboxDir(bytes: ByteArray): File {
        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }
        val targetFile = File(outboxDir, "img_${UUID.randomUUID()}.jpg")
        targetFile.writeBytes(bytes)
        return targetFile
    }

    /**
     * 离线相纸图文归册 (entityType = "media_entry")：
     * 保存压缩后 JPEG 至 filesDir/outbox_media/img_{UUID}.jpg，
     * 创建带本地 file:// 预览 Attachment 的 CachedEntryEntity 立即在时间流展示，
     * 并入队 media_entry 发件箱事件供网络恢复后自动上传与建档。
     */
    suspend fun createMediaEntryOffline(
        localImageFilePath: String,
        mimeType: String,
        width: Int,
        height: Int,
        req: CreateEntryRequest,
        camera: String?,
        additionalMediaFiles: List<OfflineMediaFileItem> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }

        fun ensureInOutboxDir(sourcePath: String): File {
            val src = File(sourcePath)
            if (src.parentFile?.absolutePath == outboxDir.absolutePath && src.exists()) {
                return src
            }
            val dest = File(outboxDir, "img_${UUID.randomUUID()}.jpg")
            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
            }
            return dest
        }

        val allItems = mutableListOf<OfflineMediaFileItem>()
        if (localImageFilePath.isNotBlank()) {
            val savedPrimary = ensureInOutboxDir(localImageFilePath)
            allItems.add(
                OfflineMediaFileItem(
                    filePath = savedPrimary.absolutePath,
                    mimeType = mimeType,
                    width = width,
                    height = height
                )
            )
        }
        for (extra in additionalMediaFiles) {
            if (extra.filePath.isNotBlank()) {
                val savedExtra = ensureInOutboxDir(extra.filePath)
                allItems.add(extra.copy(filePath = savedExtra.absolutePath))
            }
        }

        val previewAttachments = allItems.mapIndexed { idx, item ->
            val file = File(item.filePath)
            Attachment(
                id = -(idx + 1).toLong(),
                objectKey = "local_outbox/${file.name}",
                originalFilename = file.name,
                mimeType = item.mimeType,
                size = if (file.exists()) file.length() else 0L,
                width = item.width.takeIf { it > 0 },
                height = item.height.takeIf { it > 0 },
                url = "file://${file.absolutePath}"
            )
        }

        val entryEntity = CachedEntryEntity(
            localId = 0,
            serverId = null,
            type = req.type,
            title = req.title,
            content = req.content,
            occurredAt = req.occurredAt,
            location = req.location,
            cityCode = req.cityCode,
            cityName = req.cityName,
            camera = camera,
            attachmentsJson = json.encodeToString(previewAttachments),
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
        val localId = database.entryDao().insert(entryEntity)

        val primaryItem = allItems.firstOrNull()
        val payloadObj = OfflineMediaEntryPayload(
            localImageFilePath = primaryItem?.filePath ?: localImageFilePath,
            mimeType = primaryItem?.mimeType ?: mimeType,
            width = primaryItem?.width ?: width,
            height = primaryItem?.height ?: height,
            files = allItems,
            req = req,
            camera = camera
        )

        val event = OutboxEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = "media_entry",
            action = "CREATE",
            payload = json.encodeToString(payloadObj),
            localTargetId = localId,
            serverTargetId = null
        )
        database.outboxDao().insert(event)

        scheduleSync()
        localId
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
        cityName: String? = null,
        attachmentIds: List<Long>? = null,
        attachments: List<Attachment> = emptyList(),
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
            cityName = cityName,
            camera = camera,
            attachmentsJson = json.encodeToString(attachments),
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
            cityName = cityName,
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
     * 乐观编辑更新生活档案 (0ms 本地更新 + 队列同步)
     */
    suspend fun updateEntryOptimistic(
        entryId: Long,
        req: CreateEntryRequest
    ) {
        val entity = if (entryId < 0) {
            database.entryDao().getEntryByLocalId(-entryId)
        } else {
            database.entryDao().getEntryByServerId(entryId)
                ?: database.entryDao().getEntryByLocalId(entryId)
        } ?: return

        val newSyncStatus = if (entity.serverId != null && entity.serverId > 0) {
            "PENDING_UPDATE"
        } else {
            "PENDING_CREATE"
        }

        val updatedEntity = entity.copy(
            type = req.type,
            title = req.title,
            content = req.content,
            occurredAt = req.occurredAt,
            location = req.location,
            cityCode = req.cityCode,
            cityName = req.cityName ?: entity.cityName,
            syncStatus = newSyncStatus
        )
        database.entryDao().update(updatedEntity)

        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "entry",
                action = "UPDATE",
                payload = json.encodeToString(req),
                localTargetId = entity.localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        } else {
            // 尚未同步至云端的本地条目，更新其对应的 CREATE 事件载荷
            val pending = database.outboxDao().getAllPendingEvents()
            val createEvent = pending.firstOrNull {
                it.localTargetId == entity.localId && it.action == "CREATE"
            }
            if (createEvent != null) {
                if (createEvent.entityType == "entry") {
                    database.outboxDao().insert(
                        createEvent.copy(payload = json.encodeToString(req))
                    )
                } else if (createEvent.entityType == "media_entry") {
                    runCatching {
                        val oldPayload = json.decodeFromString<OfflineMediaEntryPayload>(createEvent.payload)
                        val newPayload = oldPayload.copy(req = req)
                        database.outboxDao().insert(
                            createEvent.copy(payload = json.encodeToString(newPayload))
                        )
                    }
                }
            }
        }

        scheduleSync()
    }

    /**
     * 乐观创建记账流水 (0ms 上屏，支持分期付款与支付方式)
     */
    suspend fun createTransactionOptimistic(
        amountCents: Long,
        type: String = "expense",
        category: String,
        title: String,
        note: String? = null,
        paymentMethod: String? = null,
        occurredAt: String = FormatUtils.beijingNowIsoString(),
        cityCode: String? = null,
        wishId: Long? = null,
        footprintId: Long? = null,
        isInstallment: Boolean = false,
        installmentChannelCode: String? = null,
        installmentTotalAmount: Long? = null,
        installmentMonths: Int? = null,
        installmentMonthlyPay: Long? = null,
        installmentRemainMonths: Int? = null,
        repaymentDay: Int? = null
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
            cityCode = cityCode,
            wishId = wishId,
            footprintId = footprintId,
            isInstallment = isInstallment,
            installmentChannelCode = installmentChannelCode,
            installmentTotalAmount = installmentTotalAmount,
            installmentMonths = installmentMonths,
            installmentMonthlyPay = installmentMonthlyPay,
            installmentRemainMonths = installmentRemainMonths,
            repaymentDay = repaymentDay
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
     * 乐观更新记账流水 (0ms 本地修改 + 队列同步)
     */
    suspend fun updateTransactionOptimistic(
        txId: Long,
        req: CreateTransactionRequest
    ) {
        val entity = if (txId < 0) {
            database.transactionDao().getTransactionByLocalId(-txId)
        } else {
            database.transactionDao().getTransactionByServerId(txId)
                ?: database.transactionDao().getTransactionByLocalId(txId)
        } ?: return

        val newSyncStatus = if (entity.serverId != null && entity.serverId > 0) {
            "PENDING_UPDATE"
        } else {
            "PENDING_CREATE"
        }

        val updatedEntity = entity.copy(
            amount = req.amount,
            type = req.type,
            category = req.category,
            title = req.title,
            note = req.note,
            paymentMethod = req.paymentMethod,
            occurredAt = req.occurredAt,
            cityCode = req.cityCode,
            syncStatus = newSyncStatus
        )
        database.transactionDao().update(updatedEntity)

        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "transaction",
                action = "UPDATE",
                payload = json.encodeToString(req),
                localTargetId = entity.localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        } else {
            val pending = database.outboxDao().getAllPendingEvents()
            val createEvent = pending.firstOrNull {
                it.entityType == "transaction" && it.localTargetId == entity.localId && it.action == "CREATE"
            }
            if (createEvent != null) {
                database.outboxDao().insert(
                    createEvent.copy(payload = json.encodeToString(req))
                )
            }
        }

        scheduleSync()
    }

    /**
     * 删除单条生活记录 (支持已同步正数 serverId 与未同步负数 -localId)
     */
    suspend fun deleteEntry(idOrLocalId: Long) {
        val entity = if (idOrLocalId < 0) {
            database.entryDao().getEntryByLocalId(-idOrLocalId)
        } else {
            database.entryDao().getEntryByServerId(idOrLocalId)
                ?: database.entryDao().getEntryByLocalId(idOrLocalId)
        } ?: return

        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "entry",
                action = "DELETE",
                payload = "{}",
                localTargetId = entity.localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        } else {
            database.outboxDao().deleteByLocalTarget("entry", entity.localId)
            database.outboxDao().deleteByLocalTarget("media_entry", entity.localId)
        }
        database.entryDao().deleteByLocalId(entity.localId)
        scheduleSync()
    }

    /**
     * 删除单笔记账 (支持已同步正数 serverId 与未同步负数 -localId)
     */
    suspend fun deleteTransaction(idOrLocalId: Long) {
        val entity = if (idOrLocalId < 0) {
            database.transactionDao().getTransactionByLocalId(-idOrLocalId)
        } else {
            database.transactionDao().getTransactionByServerId(idOrLocalId)
                ?: database.transactionDao().getTransactionByLocalId(idOrLocalId)
        } ?: return

        if (entity.serverId != null && entity.serverId > 0) {
            val event = OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = "transaction",
                action = "DELETE",
                payload = "{}",
                localTargetId = entity.localId,
                serverTargetId = entity.serverId
            )
            database.outboxDao().insert(event)
        } else {
            database.outboxDao().deleteByLocalTarget("transaction", entity.localId)
        }
        database.transactionDao().deleteByLocalId(entity.localId)
        scheduleSync()
    }

    /**
     * 清空本地缓存与待发队列 (设备解绑时调用)
     */
    suspend fun clearAllLocalData() {
        database.withTransaction {
            database.entryDao().clearAll()
            database.transactionDao().clearAll()
            database.outboxDao().clearAll()
        }
        syncPrefs.edit().clear().apply()
        runCatching {
            File(context.filesDir, "outbox_media").deleteRecursively()
        }
    }

    /**
     * 从后端拉取最新数据（支持 ?since= 增量游标与软删除 deleted_at 对齐），
     * 并在单一 Room 事务内合并本地缓存 (保留未同步项与本地 EXIF camera)
     */
    suspend fun pullRemoteData(forceFull: Boolean = false): Result<Unit> = kotlin.runCatching {
        // 1. 同步发件箱
        flushOutboxNow()

        // 2. 拉取生活档案 (增量或全量)
        val currentLocalEntries = database.entryDao().getAllEntries()
        val entriesCursor = syncPrefs.getString(KEY_ENTRIES_CURSOR, null)
        val useIncrementalEntries = !forceFull && currentLocalEntries.isNotEmpty() && !entriesCursor.isNullOrBlank()

        val entriesResp = apiClient.fetchEntries(
            page = 1,
            pageSize = 100,
            since = if (useIncrementalEntries) entriesCursor else null
        )
        val remoteEntries = entriesResp.entries

        database.withTransaction {
            val currentLocal = database.entryDao().getAllEntries()
            val cameraByServerId = currentLocal
                .filter { it.serverId != null && !it.camera.isNullOrBlank() }
                .associate { it.serverId!! to it.camera }

            if (useIncrementalEntries) {
                for (remote in remoteEntries) {
                    if (!remote.deletedAt.isNullOrBlank()) {
                        database.entryDao().deleteByServerId(remote.id)
                    } else {
                        val existing = database.entryDao().getEntryByServerId(remote.id)
                        val entity = CachedEntryEntity(
                            localId = existing?.localId ?: 0,
                            serverId = remote.id,
                            type = remote.type,
                            title = remote.title,
                            content = remote.content,
                            occurredAt = remote.occurredAt,
                            location = remote.location,
                            cityCode = remote.cityCode,
                            cityName = remote.cityName,
                            camera = remote.camera ?: existing?.camera ?: cameraByServerId[remote.id],
                            attachmentsJson = json.encodeToString(remote.attachments),
                            syncStatus = "SYNCED",
                            createdAt = remote.createdAt ?: remote.occurredAt
                        )
                        database.entryDao().insert(entity)
                    }
                }
            } else {
                val activeRemote = remoteEntries.filter { it.deletedAt.isNullOrBlank() }
                val remoteIds = activeRemote.map { it.id }.toSet()
                val pendingEntries = currentLocal.filter {
                    it.syncStatus != "SYNCED" && (it.serverId == null || it.serverId !in remoteIds)
                }

                val newEntities = activeRemote.map { remote ->
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
                        camera = remote.camera ?: cameraByServerId[remote.id],
                        attachmentsJson = json.encodeToString(remote.attachments),
                        syncStatus = "SYNCED",
                        createdAt = remote.createdAt ?: remote.occurredAt
                    )
                }

                database.entryDao().clearAll()
                database.entryDao().insertAll(pendingEntries + newEntities)
            }
        }

        val maxEntryUpdated = remoteEntries
            .mapNotNull { it.deletedAt?.takeIf { d -> d.isNotBlank() } ?: it.updatedAt }
            .maxOrNull()
        if (!maxEntryUpdated.isNullOrBlank()) {
            syncPrefs.edit().putString(KEY_ENTRIES_CURSOR, maxEntryUpdated).apply()
        } else if (entriesCursor.isNullOrBlank()) {
            syncPrefs.edit().putString(KEY_ENTRIES_CURSOR, FormatUtils.beijingNowIsoString()).apply()
        }

        // 3. 拉取记账记录 (增量或全量)
        val currentLocalTxs = database.transactionDao().getAllTransactions()
        val txCursor = syncPrefs.getString(KEY_TX_CURSOR, null)
        val useIncrementalTxs = !forceFull && currentLocalTxs.isNotEmpty() && !txCursor.isNullOrBlank()

        val remoteTxs = apiClient.fetchTransactions(
            since = if (useIncrementalTxs) txCursor else null,
            limit = 200
        )

        database.withTransaction {
            val localTxs = database.transactionDao().getAllTransactions()
            if (useIncrementalTxs) {
                for (remote in remoteTxs) {
                    if (!remote.deletedAt.isNullOrBlank()) {
                        database.transactionDao().deleteByServerId(remote.id)
                    } else {
                        val existing = database.transactionDao().getTransactionByServerId(remote.id)
                        val entity = CachedTransactionEntity(
                            localId = existing?.localId ?: 0,
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
                        database.transactionDao().insert(entity)
                    }
                }
            } else {
                val activeRemoteTxs = remoteTxs.filter { it.deletedAt.isNullOrBlank() }
                val remoteTxIds = activeRemoteTxs.map { it.id }.toSet()
                val pendingTxs = localTxs.filter {
                    it.syncStatus != "SYNCED" && (it.serverId == null || it.serverId !in remoteTxIds)
                }

                val newTxEntities = activeRemoteTxs.map { remote ->
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
        }

        val maxTxUpdated = remoteTxs
            .mapNotNull { it.deletedAt?.takeIf { d -> d.isNotBlank() } ?: it.updatedAt }
            .maxOrNull()
        if (!maxTxUpdated.isNullOrBlank()) {
            syncPrefs.edit().putString(KEY_TX_CURSOR, maxTxUpdated).apply()
        } else if (txCursor.isNullOrBlank()) {
            syncPrefs.edit().putString(KEY_TX_CURSOR, FormatUtils.beijingNowIsoString()).apply()
        }
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

    /**
     * 执行单条 Outbox 事件的远端投递
     */
    suspend fun processOutboxEvent(event: OutboxEventEntity) {
        when (event.entityType) {
            "entry" -> {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                        val serverEntry = apiClient.createEntry(req)
                        database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetId = event.serverTargetId
                        if (targetId != null && targetId > 0) {
                            val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                            val serverEntry = apiClient.updateEntry(targetId, req)
                            database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                        }
                        database.outboxDao().delete(event)
                    }
                    "DELETE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                apiClient.deleteEntry(targetServerId)
                            } catch (e: APIException) {
                                if (e.code != 404) throw e
                            }
                        }
                        database.entryDao().deleteByLocalId(event.localTargetId)
                        database.outboxDao().delete(event)
                    }
                    else -> database.outboxDao().delete(event)
                }
            }
            "media_entry" -> {
                if (event.action == "CREATE") {
                    val payload = json.decodeFromString<OfflineMediaEntryPayload>(event.payload)
                    val itemsToUpload = if (payload.files.isNotEmpty()) {
                        payload.files
                    } else if (payload.localImageFilePath.isNotBlank()) {
                        listOf(
                            OfflineMediaFileItem(
                                filePath = payload.localImageFilePath,
                                mimeType = payload.mimeType,
                                width = payload.width,
                                height = payload.height
                            )
                        )
                    } else {
                        emptyList()
                    }

                    val uploadedAttachments = mutableListOf<Attachment>()
                    val tempFiles = mutableListOf<File>()

                    for (item in itemsToUpload) {
                        val file = File(item.filePath)
                        if (file.exists()) {
                            tempFiles.add(file)
                            val bytes = file.readBytes()
                            val processed = ProcessedImageResult(
                                compressedBytes = bytes,
                                width = item.width,
                                height = item.height,
                                mimeType = item.mimeType,
                                exif = ExtractedExif()
                            )
                            val att = ImagePipeline.uploadImage(
                                apiClient = apiClient,
                                processed = processed,
                                filename = file.name
                            )
                            uploadedAttachments.add(att)
                        }
                    }

                    val mergedAttachmentIds = (
                        (payload.req.attachmentIds ?: emptyList()) + uploadedAttachments.map { it.id }
                    ).distinct()

                    val finalReq = payload.req.copy(
                        attachmentIds = mergedAttachmentIds.takeIf { it.isNotEmpty() }
                    )
                    val serverEntry = apiClient.createEntry(finalReq)

                    val localEntity = database.entryDao().getEntryByLocalId(event.localTargetId)
                    if (localEntity != null) {
                        val finalAttachments = if (serverEntry.attachments.isNotEmpty()) {
                            serverEntry.attachments
                        } else {
                            uploadedAttachments
                        }
                        database.entryDao().update(
                            localEntity.copy(
                                serverId = serverEntry.id,
                                syncStatus = "SYNCED",
                                attachmentsJson = json.encodeToString(finalAttachments)
                            )
                        )
                    } else {
                        database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                    }

                    for (f in tempFiles) {
                        runCatching { f.delete() }
                    }
                    database.outboxDao().delete(event)
                } else {
                    database.outboxDao().delete(event)
                }
            }
            "transaction" -> {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                        val serverTx = apiClient.createTransaction(req)
                        database.transactionDao().markSynced(event.localTargetId, serverTx.id)
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                            apiClient.updateTransaction(targetServerId, req)
                            database.transactionDao().markSynced(event.localTargetId, targetServerId)
                        }
                        database.outboxDao().delete(event)
                    }
                    "DELETE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                apiClient.deleteTransaction(targetServerId)
                            } catch (e: APIException) {
                                if (e.code != 404) throw e
                            }
                        }
                        database.transactionDao().deleteByLocalId(event.localTargetId)
                        database.outboxDao().delete(event)
                    }
                    else -> database.outboxDao().delete(event)
                }
            }
            else -> {
                database.outboxDao().delete(event)
            }
        }
    }

    suspend fun flushOutboxNow() = outboxMutex.withLock {
        val pending = database.outboxDao().getAllPendingEvents()
        if (pending.isEmpty()) return@withLock

        for (event in pending) {
            try {
                processOutboxEvent(event)
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
        camera = camera,
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
