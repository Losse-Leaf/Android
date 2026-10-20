package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
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
import com.looseleaf.app.data.local.*
import com.looseleaf.app.model.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val req: CreateEntryRequest
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

        /** 单次拉取的分页上限。200 页 ≈ 2 万条记录，足够覆盖多年档案，同时仍能防止死循环 */
        private const val MAX_PAGES = 200

        /** 增量游标回退窗口，避免「同一秒内多条更新」被 since 过滤掉（重复行由 upsert 吸收） */
        private const val CURSOR_OVERLAP_SECONDS = 120L

        /** project/wish 这类没有本地行的事件用 -1 哨兵，绝不能用 0（会误删全表同类事件） */
        const val LOCAL_TARGET_ID_NONE = -1L

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
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("SyncManager", "SyncManager uncaught exception: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val outboxMutex = Mutex()

    /** 拉取互斥：连续下拉/切 Tab 不会并发清库或重复插入 */
    private val pullMutex = Mutex()

    private val syncPrefs by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 观察待同步队列总数 (数据量极小，保持 Eagerly 让徽标即时生效)
    val pendingEventCountFlow: StateFlow<Int> = database.outboxDao().getPendingEventCountFlow()
        .catch { emit(0) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // 观察进入失败态、等待用户处置的事件数
    val failedEventCountFlow: StateFlow<Int> = database.outboxDao().getFailedEventCountFlow()
        .catch { emit(0) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // 以下五个档案流改用 WhileSubscribed(5000)：Eagerly 会让 Room InvalidationTracker
    // 在没有任何界面订阅时也持续观察六张表，并把每行 attachmentsJson 反序列化——
    // 息屏/后台同步期间纯耗电。5000ms 粘性窗口保证切 Tab 不重建。
    // 观察全量生活档案 (合并本地乐观与云端权威，后台 Default 线程反序列化，0ms 即刻上屏杜绝白屏闪烁)
    val entriesFlow: StateFlow<List<LifeEntry>> = database.entryDao().getAllEntriesFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toDomainModel(json) }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 时间流的可用年份（按 occurredAt 前 4 位）。
     *
     * 派生在主线程之外：几千条档案时在 composition 里 remember 全表遍历会阻塞首帧（AOPT-03c）。
     */
    val availableYearsFlow: StateFlow<List<Int>> = entriesFlow
        .map { list ->
            list.mapNotNull { entry ->
                entry.occurredAt.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
            }.distinct().sortedDescending()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 往年同月同日的本地条目（不含今年），作为「那年今日」服务端结果缺失时的本地兜底。
     *
     * formatDisplayDate 需要解析 ISO 时间串，必须离开主线程批量执行（AOPT-03c）。
     */
    val localOnThisDayFlow: StateFlow<List<LifeEntry>> = entriesFlow
        .map { list ->
            val today = java.time.LocalDate.now(FormatUtils.BeijingZone)
            val todayMonthDay = String.format(
                java.util.Locale.US,
                "%02d.%02d",
                today.monthValue,
                today.dayOfMonth
            )
            val thisYearPrefix = "${today.year}."
            list.filter { entry ->
                val formatted = FormatUtils.formatDisplayDate(entry.occurredAt)
                formatted.length >= 10 &&
                    !formatted.startsWith(thisYearPrefix) &&
                    formatted.substring(5, 10) == todayMonthDay
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 观察全量记账明细 (合并本地乐观与云端权威，0ms 内存态直出)
    val transactionsFlow: StateFlow<List<Transaction>> = database.transactionDao().getAllTransactionsFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toTransaction() }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 观察全量造物项目 (离线缓存即时上屏)
    val projectsFlow: StateFlow<List<Project>> = database.projectDao().getAllProjectsFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toProject() }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 观察全量三维心愿 (离线缓存即时上屏)
    val wishesFlow: StateFlow<List<Wish>> = database.wishDao().getAllWishesFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toWish(json) }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 观察全量山川足迹 (离线缓存即时上屏)
    val footprintsFlow: StateFlow<List<Footprint>> = database.footprintDao().getAllFootprintsFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toFootprint(json) }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 将压制后的相纸字节保存至离线发件箱目录
     */
    fun saveCompressedImageToOutboxDir(bytes: ByteArray): File {
        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }
        val targetFile = File(outboxDir, "img_${UUID.randomUUID()}.jpg")
        try {
            targetFile.writeBytes(bytes)
        } catch (e: Exception) {
            // 写一半失败会留下残缺文件，必须清掉，否则又变成孤儿
            runCatching { targetFile.delete() }
            throw e
        }
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
            featured = req.featured,
            projectId = req.projectId,
            wishId = req.wishId,
            footprintId = req.footprintId,
            attachmentsJson = json.encodeToString(previewAttachments),
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
        // 本地记录与发件箱事件必须同事务落库：中途崩溃不能留下「永远 PENDING 却无事件」的孤儿
        val localId = database.withTransaction {
            val id = database.entryDao().insert(entryEntity)
            val primaryItem = allItems.firstOrNull()
            val payloadObj = OfflineMediaEntryPayload(
                localImageFilePath = primaryItem?.filePath ?: localImageFilePath,
                mimeType = primaryItem?.mimeType ?: mimeType,
                width = primaryItem?.width ?: width,
                height = primaryItem?.height ?: height,
                files = allItems,
                req = req
            )
            database.outboxDao().insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = "media_entry",
                    action = "CREATE",
                    payload = json.encodeToString(payloadObj),
                    localTargetId = id,
                    serverTargetId = null
                )
            )
            id
        }

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
        featured: Boolean = false,
        attachmentIds: List<Long>? = null,
        attachments: List<Attachment> = emptyList(),
        projectId: Long? = null,
        wishId: Long? = null,
        footprintId: Long? = null
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
            featured = featured,
            projectId = projectId,
            wishId = wishId,
            footprintId = footprintId,
            attachmentsJson = json.encodeToString(attachments),
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
        val req = CreateEntryRequest(
            type = type,
            title = title,
            content = content,
            occurredAt = occurredAt,
            location = location,
            cityCode = cityCode,
            cityName = cityName,
            featured = featured,
            attachmentIds = attachmentIds,
            projectId = projectId,
            wishId = wishId,
            footprintId = footprintId
        )

        val localId = database.withTransaction {
            val id = database.entryDao().insert(entryEntity)
            database.outboxDao().insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = "entry",
                    action = "CREATE",
                    payload = json.encodeToString(req),
                    localTargetId = id,
                    serverTargetId = null
                )
            )
            id
        }

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
            featured = req.featured,
            projectId = req.projectId ?: entity.projectId,
            wishId = req.wishId ?: entity.wishId,
            footprintId = req.footprintId ?: entity.footprintId,
            syncStatus = newSyncStatus
        )
        database.withTransaction {
            database.entryDao().update(updatedEntity)

            if (entity.serverId != null && entity.serverId > 0) {
                database.outboxDao().insert(
                    OutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        entityType = "entry",
                        action = "UPDATE",
                        payload = json.encodeToString(req),
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId
                    )
                )
            } else {
                // 尚未同步至云端的本地条目，改写其 CREATE 事件载荷。
                // 必须用含失败态的查找：若 CREATE 事件已进失败态而按 pending 找，
                // 会出现「本地行已更新、载荷没改写」，重试后旧内容被推上去再被覆盖回来。
                val createEvent = database.outboxDao().findEventByTarget(
                    entityType = "entry",
                    localTargetId = entity.localId,
                    action = "CREATE"
                ) ?: database.outboxDao().findEventByTarget(
                    entityType = "media_entry",
                    localTargetId = entity.localId,
                    action = "CREATE"
                )
                when {
                    createEvent == null -> Unit
                    createEvent.entityType == "media_entry" -> {
                        val oldPayload = json.decodeFromString<OfflineMediaEntryPayload>(createEvent.payload)
                        val newPayload = oldPayload.copy(req = req)
                        database.outboxDao().insert(
                            createEvent.copy(payload = json.encodeToString(newPayload))
                        )
                    }
                    else -> database.outboxDao().insert(
                        createEvent.copy(payload = json.encodeToString(req))
                    )
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
            wishId = wishId,
            footprintId = footprintId,
            isInstallment = isInstallment,
            installmentChannelCode = installmentChannelCode,
            installmentTotalAmount = installmentTotalAmount,
            installmentMonths = installmentMonths,
            installmentMonthlyPay = installmentMonthlyPay,
            installmentRemainMonths = installmentRemainMonths,
            repaymentDay = repaymentDay,
            syncStatus = "PENDING_CREATE",
            createdAt = FormatUtils.beijingNowIsoString()
        )
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

        val localId = database.withTransaction {
            val id = database.transactionDao().insert(txEntity)
            database.outboxDao().insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = "transaction",
                    action = "CREATE",
                    payload = json.encodeToString(req),
                    localTargetId = id,
                    serverTargetId = null
                )
            )
            id
        }

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
            wishId = req.wishId ?: entity.wishId,
            footprintId = req.footprintId ?: entity.footprintId,
            isInstallment = req.isInstallment,
            installmentChannelCode = req.installmentChannelCode,
            installmentTotalAmount = req.installmentTotalAmount,
            installmentMonths = req.installmentMonths,
            installmentMonthlyPay = req.installmentMonthlyPay,
            installmentRemainMonths = req.installmentRemainMonths,
            repaymentDay = req.repaymentDay,
            syncStatus = newSyncStatus
        )
        database.withTransaction {
            database.transactionDao().update(updatedEntity)

            if (entity.serverId != null && entity.serverId > 0) {
                database.outboxDao().insert(
                    OutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        entityType = "transaction",
                        action = "UPDATE",
                        payload = json.encodeToString(req),
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId
                    )
                )
            } else {
                // 与 updateEntryOptimistic 同理：必须含失败态，否则编辑会静默丢失
                val createEvent = database.outboxDao().findEventByTarget(
                    entityType = "transaction",
                    localTargetId = entity.localId,
                    action = "CREATE"
                )
                if (createEvent != null) {
                    database.outboxDao().insert(
                        createEvent.copy(payload = json.encodeToString(req))
                    )
                }
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

        // 未同步的图文条目：连 filesDir/outbox_media 里的相纸一起清理，避免删了记录留下孤儿文件
        val orphanMediaPaths = if (entity.serverId == null) {
            database.outboxDao().getAllPendingEvents()
                .filter { it.entityType == "media_entry" && it.localTargetId == entity.localId }
                .flatMap { event ->
                    runCatching {
                        json.decodeFromString<OfflineMediaEntryPayload>(event.payload).files
                    }.getOrDefault(emptyList())
                }
                .map { it.filePath }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        database.withTransaction {
            if (entity.serverId != null && entity.serverId > 0) {
                database.outboxDao().insert(
                    OutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        entityType = "entry",
                        action = "DELETE",
                        payload = "{}",
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId
                    )
                )
            } else {
                database.outboxDao().deleteByLocalTarget("entry", entity.localId)
                database.outboxDao().deleteByLocalTarget("media_entry", entity.localId)
            }
            database.entryDao().deleteByLocalId(entity.localId)
        }
        orphanMediaPaths.forEach { path -> runCatching { File(path).delete() } }
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

        database.withTransaction {
            if (entity.serverId != null && entity.serverId > 0) {
                database.outboxDao().insert(
                    OutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        entityType = "transaction",
                        action = "DELETE",
                        payload = "{}",
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId
                    )
                )
            } else {
                database.outboxDao().deleteByLocalTarget("transaction", entity.localId)
            }
            database.transactionDao().deleteByLocalId(entity.localId)
        }
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
            database.projectDao().clearAll()
            database.wishDao().clearAll()
            database.footprintDao().clearAll()
        }
        syncPrefs.edit().clear().apply()
        runCatching {
            File(context.filesDir, "outbox_media").deleteRecursively()
        }
    }

    /**
     * 从后端拉取最新数据（支持 ?since= 增量游标与软删除 deleted_at 对齐）。
     *
     * 合并原则（不再整表 clearAll 重插）：
     * 1. 本地未同步（PENDING_*）的行内容永远优先，服务端版本不覆盖；
     * 2. 已同步行按 serverId upsert，保留 localId，避免发件箱事件指向失效主键；
     * 3. 只有「服务端已软删」或「全量拉取中缺失」的已同步行才真正删除；
     * 4. 任一分段失败都不推进该分段的游标，避免永久漏数据。
     */
    suspend fun pullRemoteData(forceFull: Boolean = false): Result<Unit> = pullMutex.withLock {
        kotlin.runCatching {
            // 不在这里内联 flush：多图上传可能要几十秒，会把「下拉刷新」卡死在发件箱上。
            // 每次入队都已 scheduleSync()，Worker 受网络约束自会跑；此处只做拉取。
            val failures = mutableListOf<String>()
            // ---------- 2. 生活档案 ----------
            val entriesCursor = syncPrefs.getString(KEY_ENTRIES_CURSOR, null)
            val useIncrementalEntries =
                !forceFull && database.entryDao().hasAny() && !entriesCursor.isNullOrBlank()

            val remoteEntries = mutableListOf<LifeEntry>()
            var entriesComplete = true
            try {
                var page = 1
                var pages = 0
                while (pages < MAX_PAGES) {
                    val resp = apiClient.fetchEntries(
                        page = page,
                        pageSize = 100,
                        since = if (useIncrementalEntries) cursorForRequest(entriesCursor) else null
                    )
                    remoteEntries.addAll(resp.entries)
                    pages++
                    // 只用「不满页」做终止条件：resp.total 是 COUNT 快照，
                    // 并发写入会偏小，导致 size >= total 提前 break 漏拉后续页
                    if (resp.entries.size < 100) break
                    page++
                }
                if (pages >= MAX_PAGES) {
                    throw IllegalStateException("分页超过 $MAX_PAGES 页，本次拉取已中止")
                }
            } catch (e: Exception) {
                entriesComplete = false
                failures += "生活档案：${e.message}"
            }

            if (entriesComplete) {
                database.withTransaction { mergeEntries(remoteEntries, useIncrementalEntries) }
                val maxEntryUpdated = maxUpdatedCursor(
                    remoteEntries.flatMap { listOfNotNull(it.updatedAt, it.deletedAt) }
                )
                if (maxEntryUpdated != null) {
                    syncPrefs.edit().putString(KEY_ENTRIES_CURSOR, maxEntryUpdated).apply()
                } else if (entriesCursor.isNullOrBlank()) {
                    syncPrefs.edit().putString(KEY_ENTRIES_CURSOR, FormatUtils.beijingNowIsoString()).apply()
                }
            }

            // ---------- 3. 记账流水 ----------
            val txCursor = syncPrefs.getString(KEY_TX_CURSOR, null)
            val useIncrementalTxs =
                !forceFull && database.transactionDao().hasAny() && !txCursor.isNullOrBlank()

            val remoteTxs = mutableListOf<Transaction>()
            var txsComplete = true
            try {
                var offset = 0
                var pages = 0
                while (pages < MAX_PAGES) {
                    val batch = apiClient.fetchTransactions(
                        since = if (useIncrementalTxs) cursorForRequest(txCursor) else null,
                        limit = 200,
                        offset = if (offset > 0) offset else null
                    )
                    remoteTxs.addAll(batch)
                    pages++
                    if (batch.size < 200) break
                    offset += batch.size
                }
                if (pages >= MAX_PAGES) {
                    throw IllegalStateException("分页超过 $MAX_PAGES 页，本次拉取已中止")
                }
            } catch (e: Exception) {
                txsComplete = false
                failures += "记账流水：${e.message}"
            }

            if (txsComplete) {
                database.withTransaction { mergeTransactions(remoteTxs, useIncrementalTxs) }
                val maxTxUpdated = maxUpdatedCursor(
                    remoteTxs.flatMap { listOfNotNull(it.updatedAt, it.deletedAt) }
                )
                if (maxTxUpdated != null) {
                    syncPrefs.edit().putString(KEY_TX_CURSOR, maxTxUpdated).apply()
                } else if (txCursor.isNullOrBlank()) {
                    syncPrefs.edit().putString(KEY_TX_CURSOR, FormatUtils.beijingNowIsoString()).apply()
                }
            }

            // ---------- 4. 造物 / 心愿 / 足迹（只读缓存，走整表替换） ----------
            try {
                val remoteProjects = apiClient.fetchProjects()
                database.withTransaction {
                    database.projectDao().clearAll()
                    database.projectDao().insertAll(remoteProjects.map { it.toCacheEntity() })
                }
            } catch (e: Exception) {
                failures += "造物：${e.message}"
            }

            try {
                val allWishes = mutableListOf<Wish>()
                var wishPage = 1
                while (wishPage <= MAX_PAGES) {
                    val wishes = apiClient.fetchWishes(page = wishPage, pageSize = 100)
                    allWishes.addAll(wishes)
                    if (wishes.size < 100) break
                    wishPage++
                }
                if (wishPage > MAX_PAGES) throw IllegalStateException("心愿分页超限")
                database.withTransaction {
                    database.wishDao().clearAll()
                    database.wishDao().insertAll(allWishes.map { it.toCacheEntity(json) })
                }
            } catch (e: Exception) {
                failures += "心愿：${e.message}"
            }

            try {
                val remoteFootprints = apiClient.fetchFootprints()
                database.withTransaction {
                    database.footprintDao().clearAll()
                    database.footprintDao().insertAll(remoteFootprints.map { it.toCacheEntity(json) })
                }
            } catch (e: Exception) {
                failures += "足迹：${e.message}"
            }

            if (failures.isNotEmpty()) {
                val authExpired = failures.any { it.contains("401") }
                val suffix = if (authExpired) "（设备凭据已失效，请重新扫码配对）" else ""
                throw IllegalStateException(failures.joinToString("；") + suffix)
            }
        }.also { result ->
            // runCatching 会把 CancellationException 转成 Result.failure 吞掉，
            // 违背结构化并发——用户离开页面导致的取消必须继续向上传播
            val ex = result.exceptionOrNull()
            if (ex is CancellationException) throw ex
        }
    }

    /** 增量/全量共用的档案合并；未同步的本地行内容优先。 */
    private suspend fun mergeEntries(remote: List<LifeEntry>, incremental: Boolean) {
        val activeRemote = remote.filter { it.deletedAt.isNullOrBlank() }
        val remoteIds = activeRemote.map { it.id }.toSet()

        // 服务端已软删除 → 本地一并移除
        for (item in remote) {
            if (!item.deletedAt.isNullOrBlank()) {
                database.entryDao().deleteByServerId(item.id)
            }
        }

        // 全量拉取：收口历史重复行，并清理「服务端已不存在」的已同步行。
        // 收口必须发生在读映射之前，否则 upsert 会把已删除的重复行再插回来。
        if (!incremental) {
            database.entryDao().deleteDuplicateServerIds()
        }

        // 一次读全表建映射，删除判断与 upsert 复用同一份数据（几千条时避免反复拖全表）
        val localByServerId = database.entryDao().getAllEntries()
            .mapNotNull { entity -> entity.serverId?.let { it to entity } }
            .toMap()

        if (!incremental) {
            for ((serverId, local) in localByServerId) {
                if (local.syncStatus == "SYNCED" && serverId !in remoteIds) {
                    database.entryDao().deleteByServerId(serverId)
                }
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
            database.entryDao().insertAll(toUpsert)
        }
    }

    /** 记账流水合并，规则与档案一致。 */
    private suspend fun mergeTransactions(remote: List<Transaction>, incremental: Boolean) {
        val activeRemote = remote.filter { it.deletedAt.isNullOrBlank() }
        val remoteIds = activeRemote.map { it.id }.toSet()

        for (item in remote) {
            if (!item.deletedAt.isNullOrBlank()) {
                database.transactionDao().deleteByServerId(item.id)
            }
        }

        if (!incremental) {
            database.transactionDao().deleteDuplicateServerIds()
        }

        val localByServerId = database.transactionDao().getAllTransactions()
            .mapNotNull { entity -> entity.serverId?.let { it to entity } }
            .toMap()

        if (!incremental) {
            for ((serverId, local) in localByServerId) {
                if (local.syncStatus == "SYNCED" && serverId !in remoteIds) {
                    database.transactionDao().deleteByServerId(serverId)
                }
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
            database.transactionDao().insertAll(toUpsert)
        }
    }

    /** 游标只认「能解析成时间」的值，并按绝对时刻取最大，避免字符串比较在混合时区下失效。 */
    private fun maxUpdatedCursor(values: List<String?>): String? =
        values.mapNotNull { FormatUtils.parseOccurredInstantOrNull(it) }
            .maxOrNull()
            ?.let { FormatUtils.toBeijingIsoString(it) }

    /** 请求时把游标回退一个窗口，抵消「同一秒多条更新」与时钟偏差。 */
    private fun cursorForRequest(cursor: String?): String? =
        FormatUtils.parseOccurredInstantOrNull(cursor)
            ?.minusSeconds(CURSOR_OVERLAP_SECONDS)
            ?.let { FormatUtils.toBeijingIsoString(it) }

    /**
     * 立即调度发件箱同步。
     *
     * 用 APPEND_OR_REPLACE 而不是 REPLACE：连续编辑不会取消正在上传的 worker，
     * 避免「相纸已传到 R2、却来不及建档」留下孤儿对象。
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            OutboxSyncWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }

    /** 解绑设备时取消排队的同步任务，避免拿着已失效的 token 反复重试 */
    fun cancelScheduledSync() {
        workManager.cancelUniqueWork(OutboxSyncWorker.WORK_NAME)
    }

    /** 用户手动重试所有失败事件：清空失败态，重新排队。 */
    fun retryFailedEvents() {
        scope.launch {
            database.outboxDao().resetAllFailed()
            scheduleSync()
        }
    }

    /** 放弃所有失败事件：仅停止重试，本地记录与 PENDING 标记保留，便于排查。 */
    fun discardFailedEvents() {
        scope.launch {
            database.outboxDao().getFailedEvents().forEach { database.outboxDao().deleteById(it.eventId) }
        }
    }

    /**
     * 在线直连创建造物；网络不可达时入发件箱，联网后自动补发。
     * 返回 true 表示「已离线排队」。
     */
    suspend fun createProjectOrQueue(req: CreateProjectRequest): Boolean = try {
        withContext(Dispatchers.IO) { apiClient.createProject(req) }
        false
    } catch (e: APIException) {
        if (e.code == -1) {
            enqueueSimpleEvent("project", "CREATE", json.encodeToString(req), 0)
            true
        } else {
            throw e
        }
    }

    /** 在线直连许愿；网络不可达时入发件箱。返回 true 表示已离线排队。 */
    suspend fun createWishOrQueue(req: CreateWishRequest): Boolean = try {
        withContext(Dispatchers.IO) { apiClient.createWish(req) }
        false
    } catch (e: APIException) {
        if (e.code == -1) {
            enqueueSimpleEvent("wish", "CREATE", json.encodeToString(req), 0)
            true
        } else {
            throw e
        }
    }

    private suspend fun enqueueSimpleEvent(
        entityType: String,
        action: String,
        payload: String,
        serverTargetId: Long
    ) {
        database.outboxDao().insert(
            OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = entityType,
                action = action,
                payload = payload,
                // 注意：这类事件（project/wish）没有本地行，不能用 0——
                // deleteByLocalTarget 按 (entity_type, local_target_id) 删除，
                // 0 会命中全部同类事件。用 -1 哨兵值并在注释里锁死这一约定。
                localTargetId = LOCAL_TARGET_ID_NONE,
                serverTargetId = serverTargetId
            )
        )
        scheduleSync()
    }

    /**
     * 执行单条 Outbox 事件的远端投递
     */
    suspend fun processOutboxEvent(event: OutboxEventEntity) {
        when (event.entityType) {
            "entry" -> {
                when (event.action) {
                    "CREATE" -> {
                        // 投递前复核本地行仍在：用户可能刚删掉这条未同步记录，
                        // 而事件列表是 Worker 启动时快照的——跳过投递直接消费事件，
                        // 避免服务器出现一条本机永远删不掉的幽灵档案。
                        val stillThere = database.entryDao().getEntryByLocalId(event.localTargetId) != null
                        if (stillThere) {
                            val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                            val serverEntry = apiClient.createEntry(req)
                            database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                        }
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetId = event.serverTargetId
                        if (targetId != null && targetId > 0) {
                            try {
                                val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                                val serverEntry = apiClient.updateEntry(targetId, req)
                                database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                            } catch (e: APIException) {
                                // 服务端已删（网页/其他设备）：本地修改已无处可去，
                                // 与 DELETE 同样宽容地消费事件，而不是反复重试进失败态
                                if (e.code == 404) {
                                    database.entryDao().deleteByLocalId(event.localTargetId)
                                } else {
                                    throw e
                                }
                            }
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

                    val tempFiles = java.util.Collections.synchronizedList(mutableListOf<File>())
                    val uploadedAttachments = coroutineScope {
                        itemsToUpload.map { item ->
                            async(Dispatchers.IO) {
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
                                    ImagePipeline.uploadImage(
                                        apiClient = apiClient,
                                        processed = processed,
                                        filename = file.name
                                    )
                                } else null
                            }
                        }.awaitAll().filterNotNull()
                    }

                    val mergedAttachmentIds = (
                        (payload.req.attachmentIds ?: emptyList()) + uploadedAttachments.map { it.id }
                    ).distinct()

                    if (uploadedAttachments.size < itemsToUpload.size) {
                        // 相纸文件被清理/丢失：不要把「缺图条目」悄悄传上去
                        throw IllegalStateException("本地相纸文件缺失，无法完成上传")
                    }

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

                    // 先删事件、后删本地文件：若中途崩溃，重试时事件已不在，
                    // 顶多留下一个孤儿文件（可被清理），绝不出现「文件没了但事件还在」
                    // → 上传必失败 → 反复重试 10 次进失败态的死局。
                    database.outboxDao().delete(event)
                    for (f in tempFiles) {
                        runCatching { f.delete() }
                    }
                } else {
                    database.outboxDao().delete(event)
                }
            }
            "transaction" -> {
                when (event.action) {
                    "CREATE" -> {
                        // 与 entry CREATE 同理：先复核本地行仍在，避免幽灵建档
                        val stillThere = database.transactionDao().getTransactionByLocalId(event.localTargetId) != null
                        if (stillThere) {
                            val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                            val serverTx = apiClient.createTransaction(req)
                            database.transactionDao().markSynced(event.localTargetId, serverTx.id)
                        }
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                                apiClient.updateTransaction(targetServerId, req)
                                database.transactionDao().markSynced(event.localTargetId, targetServerId)
                            } catch (e: APIException) {
                                if (e.code == 404) {
                                    database.transactionDao().deleteByLocalId(event.localTargetId)
                                } else {
                                    throw e
                                }
                            }
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
            "project" -> {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateProjectRequest>(event.payload)
                        apiClient.createProject(req)
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            val req = json.decodeFromString<CreateProjectRequest>(event.payload)
                            apiClient.updateProject(targetServerId, req)
                        }
                        database.outboxDao().delete(event)
                    }
                    "DELETE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                apiClient.deleteProject(targetServerId)
                            } catch (e: APIException) {
                                if (e.code != 404) throw e
                            }
                        }
                        database.outboxDao().delete(event)
                    }
                    else -> database.outboxDao().delete(event)
                }
            }
            "wish" -> {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateWishRequest>(event.payload)
                        apiClient.createWish(req)
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            val req = json.decodeFromString<CreateWishRequest>(event.payload)
                            apiClient.updateWish(targetServerId, req)
                        }
                        database.outboxDao().delete(event)
                    }
                    "DELETE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                apiClient.deleteWish(targetServerId)
                            } catch (e: APIException) {
                                if (e.code != 404) throw e
                            }
                        }
                        database.outboxDao().delete(event)
                    }
                    else -> database.outboxDao().delete(event)
                }
            }
            "footprint" -> {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateFootprintRequest>(event.payload)
                        apiClient.createFootprint(req)
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            val req = json.decodeFromString<CreateFootprintRequest>(event.payload)
                            apiClient.updateFootprint(targetServerId, req)
                        }
                        database.outboxDao().delete(event)
                    }
                    "DELETE" -> {
                        val targetServerId = event.serverTargetId
                        if (targetServerId != null && targetServerId > 0) {
                            try {
                                apiClient.deleteFootprint(targetServerId)
                            } catch (e: APIException) {
                                if (e.code != 404) throw e
                            }
                        }
                        database.outboxDao().delete(event)
                    }
                    else -> database.outboxDao().delete(event)
                }
            }
            else -> {
                // 未知事件类型不再静默删除：标记失败并保留原始载荷，交给用户处置
                database.outboxDao().markFailed(
                    event.eventId,
                    FormatUtils.beijingNowIsoString(),
                    "未知事件类型：${event.entityType}/${event.action}"
                )
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
        featured = featured,
        projectId = projectId,
        wishId = wishId,
        footprintId = footprintId,
        attachments = atts,
        syncStatus = syncStatus,
        createdAt = createdAt
    )
}

fun CachedTransactionEntity.toTransaction(): Transaction {
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
        wishId = wishId,
        footprintId = footprintId,
        isInstallment = isInstallment,
        installmentChannelCode = installmentChannelCode,
        installmentTotalAmount = installmentTotalAmount,
        installmentMonths = installmentMonths,
        installmentMonthlyPay = installmentMonthlyPay,
        installmentRemainMonths = installmentRemainMonths,
        repaymentDay = repaymentDay,
        syncStatus = syncStatus,
        createdAt = createdAt
    )
}

fun CachedProjectEntity.toProject(): Project {
    return Project(
        id = id,
        title = title,
        subtitle = subtitle,
        description = description,
        story = story,
        category = category,
        status = status,
        githubUrl = githubUrl,
        featured = featured,
        order = order,
        updatedAt = updatedAt
    )
}

fun CachedWishEntity.toWish(json: Json): Wish {
    val atts = try {
        json.decodeFromString<List<com.looseleaf.app.model.Attachment>>(attachmentsJson)
    } catch (_: Exception) {
        emptyList()
    }
    return Wish(
        id = id,
        title = title,
        kind = kind,
        note = note,
        spec = spec,
        cityCode = cityCode,
        cityName = cityName,
        budget = budget,
        actualCost = actualCost,
        resolutionType = resolutionType,
        actualTitle = actualTitle,
        status = status,
        targetDate = targetDate,
        fulfilledAt = fulfilledAt,
        gearId = gearId,
        attachments = atts,
        updatedAt = updatedAt
    )
}

fun CachedFootprintEntity.toFootprint(json: Json): Footprint {
    val atts = try {
        json.decodeFromString<List<com.looseleaf.app.model.Attachment>>(attachmentsJson)
    } catch (_: Exception) {
        emptyList()
    }
    return Footprint(
        id = id,
        cityCode = cityCode,
        cityName = cityName,
        title = title,
        visitedAt = visitedAt,
        departedAt = departedAt,
        note = note,
        lifeEntryId = lifeEntryId,
        wishId = wishId,
        attachments = atts,
        entriesCount = entriesCount,
        totalSpent = totalSpent,
        transactionCount = transactionCount,
        updatedAt = updatedAt
    )
}

// ---------- 服务端模型 → Room 缓存实体（统一 upsert 入口，避免各处手抄字段漂移） ----------

fun LifeEntry.toCacheEntity(localId: Long, json: Json): CachedEntryEntity = CachedEntryEntity(
    localId = localId,
    serverId = id,
    type = type,
    title = title,
    content = content,
    occurredAt = occurredAt,
    location = location,
    cityCode = cityCode,
    cityName = cityName,
    featured = featured,
    projectId = projectId,
    wishId = wishId,
    footprintId = footprintId,
    attachmentsJson = json.encodeToString(attachments),
    syncStatus = "SYNCED",
    createdAt = createdAt ?: occurredAt
)

fun Transaction.toCacheEntity(localId: Long): CachedTransactionEntity = CachedTransactionEntity(
    localId = localId,
    serverId = id,
    amount = amount,
    type = type,
    category = category,
    title = title,
    note = note,
    paymentMethod = paymentMethod,
    occurredAt = occurredAt,
    cityCode = cityCode,
    wishId = wishId,
    footprintId = footprintId,
    isInstallment = isInstallment ?: false,
    installmentChannelCode = installmentChannelCode,
    installmentTotalAmount = installmentTotalAmount,
    installmentMonths = installmentMonths,
    installmentMonthlyPay = installmentMonthlyPay,
    installmentRemainMonths = installmentRemainMonths,
    repaymentDay = repaymentDay,
    syncStatus = "SYNCED",
    createdAt = createdAt ?: occurredAt
)

fun Project.toCacheEntity(): CachedProjectEntity = CachedProjectEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    description = description,
    story = story,
    category = category,
    status = status,
    githubUrl = githubUrl,
    featured = featured,
    order = order,
    updatedAt = updatedAt
)

fun Wish.toCacheEntity(json: Json): CachedWishEntity = CachedWishEntity(
    id = id,
    title = title,
    kind = kind ?: "item",
    note = note,
    spec = spec,
    cityCode = cityCode,
    cityName = cityName,
    budget = budget,
    actualCost = actualCost,
    resolutionType = resolutionType,
    actualTitle = actualTitle,
    status = status,
    targetDate = targetDate,
    fulfilledAt = fulfilledAt,
    gearId = gearId,
    attachmentsJson = json.encodeToString(attachments),
    updatedAt = updatedAt
)

fun Footprint.toCacheEntity(json: Json): CachedFootprintEntity = CachedFootprintEntity(
    id = id,
    cityCode = cityCode,
    cityName = cityName,
    title = title,
    visitedAt = visitedAt,
    departedAt = departedAt,
    note = note,
    lifeEntryId = lifeEntryId,
    wishId = wishId,
    attachmentsJson = json.encodeToString(attachments),
    entriesCount = entriesCount,
    totalSpent = totalSpent,
    transactionCount = transactionCount,
    updatedAt = updatedAt
)
