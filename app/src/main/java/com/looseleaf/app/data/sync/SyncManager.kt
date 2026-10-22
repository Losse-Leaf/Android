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
import com.looseleaf.app.data.api.ResponseMeta
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.*
import com.looseleaf.app.model.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
/**
 * MOB-P2-47：删除撤销票据。
 * 记录被删对象，用于在「已删除」提示上挂一个「撤销」动作。
 */
enum class DeleteUndoKind { ENTRY, TRANSACTION }

class DeleteUndoTicket(
    val entity: Any,
    val kind: DeleteUndoKind
)

class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"

        /**
         * MOB-P2-37：发件箱同步状态的**唯一事实源**。
         * 原先这些值是散落 10+ 处的裸字符串（"SYNCED" / "PENDING_CREATE" …），
         * 任何一处拼写错误都会让合并逻辑**静默走错分支**（例如把「新建」当成「更新」）。
         * 这里集中为常量；数据库列仍是 TEXT，因此**无需 schema 迁移**。
         */
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_PENDING_CREATE = "PENDING_CREATE"
        const val STATUS_PENDING_UPDATE = "PENDING_UPDATE"
        const val STATUS_PENDING_DELETE = "PENDING_DELETE"
        private const val SYNC_PREFS_NAME = "looseleaf_sync_prefs"
        private const val KEY_ENTRIES_CURSOR = "last_entries_sync_cursor"
        private const val KEY_TX_CURSOR = "last_tx_sync_cursor"
        private const val KEY_ON_THIS_DAY_JSON = "on_this_day_json"
        private const val KEY_ON_THIS_DAY_DATE = "on_this_day_date"

        /** 单次拉取的分页上限。200 页 ≈ 2 万条记录，足够覆盖多年档案，同时仍能防止死循环 */
        private const val MAX_PAGES = 200

        /** 增量游标回退窗口，避免「同一秒内多条更新」被 since 过滤掉（重复行由 upsert 吸收） */
        private const val CURSOR_OVERLAP_SECONDS = 120L

        /**
         * MOB-P2-47：删除事件的**宽限期**（毫秒）。
         *
         * 删除若立即上传，「撤销」就永远来不及 —— 发件箱 Worker 1 秒内就把 DELETE 送出去了，
         * 用户点「撤销」只会得到「已同步到服务端，无法撤销」（实测如此）。
         * 现在删除事件先在发件箱里**压 8 秒**再上传，纸签上的「撤销」按钮 6 秒内有效 ⇒ 撤销真正可用。
         */
        const val DELETE_GRACE_PERIOD_MS = 8_000L

        /**
         * MOB-P2-47：删除事件是否还在宽限期内（期内不上传，给「撤销」留机会）。
         *
         * 以事件的入队时间为基准 —— 不需要改表结构。
         */
        fun isDeleteWithinGracePeriod(event: OutboxEventEntity): Boolean {
            if (event.action != "DELETE") return false
            val created = FormatUtils.parseOccurredInstantOrNull(event.createdAt) ?: return false
            val elapsed = (System.currentTimeMillis() - created.toEpochMilli())
            return elapsed < DELETE_GRACE_PERIOD_MS
        }

        /** project/wish 这类没有本地行的事件用 -1 哨兵，绝不能用 0（会误删全表同类事件） */
        const val LOCAL_TARGET_ID_NONE = -1L

        /** MOB-P1-32：附件解码记忆化的最大条目数（超出按 LRU 淘汰） */
        private const val ATTACHMENT_MEMO_MAX = 4_000

        /** MOB-P0-03：媒体直传并发上限（超过会占满移动上行带宽并放大内存峰值） */
        private const val MAX_CONCURRENT_UPLOADS = 3

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
    // MOB-P1-35：省略等于默认值的字段 —— 发件箱里存的就是这份请求体，
    // 省下的 null 字段既瘦身请求体，也瘦身本地数据库。
    // 服务端已配套实现「省略即默认」（entry.type / transaction.type / wish.kind 等）。
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    val outboxMutex = Mutex()

    /** 拉取互斥：连续下拉/切 Tab 不会并发清库或重复插入 */
    private val pullMutex = Mutex()

    private val syncPrefs by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 观察待同步队列总数 (数据量极小，保持 Eagerly 让徽标即时生效)
    /**
     * MOB-P1-86：是否已完成**首轮**同步。
     * 时间流此前用「entries 为空」同时表达「正在加载」与「本来就没有记录」，
     * 导致 App 刚打开就显示「岁月静好，暂无此分类记录」，随后记录才陆续出现 ——
     * 用户会以为数据丢了。这里用独立信号把两种状态分开。
     */
    private val _firstSyncCompleted = MutableStateFlow(false)
    val firstSyncCompletedFlow: StateFlow<Boolean> = _firstSyncCompleted.asStateFlow()

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
    /**
     * MOB-P1-32 / MOB-P1-48：附件 JSON 的**记忆化解码**。
     *
     * Room 的 InvalidationTracker 是**表级**失效：任意一行写入（哪怕只是一次
     * `markSynced`）都会让 `getAllEntriesFlow()` 重新发射**整表**，
     * 于是每一行的 attachments_json 都被重新解析一遍 ——
     * 2 万条记录 = 2 万次 JSON 反序列化 + 2 万个异常兜底对象，全部发生在后台，
     * 但依然会把 CPU 与内存搅得翻江倒海。
     *
     * 这里用「JSON 原文 → 已解析列表」的 LRU 记忆化：
     * 未变更的行几乎 100% 命中缓存 ⇒ 重解码成本降到接近零。
     */
    private val attachmentMemo: MutableMap<String, List<com.looseleaf.app.model.Attachment>> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, List<com.looseleaf.app.model.Attachment>>(
                256, 0.75f, true
            ) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<com.looseleaf.app.model.Attachment>>?
                ): Boolean = size > ATTACHMENT_MEMO_MAX
            }
        )

    private fun decodeAttachments(raw: String): List<com.looseleaf.app.model.Attachment> {
        if (raw.isBlank()) return emptyList()
        attachmentMemo[raw]?.let { return it }
        val decoded = try {
            json.decodeFromString<List<com.looseleaf.app.model.Attachment>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
        attachmentMemo[raw] = decoded
        return decoded
    }

    // 观察全量生活档案 (合并本地乐观与云端权威，后台 Default 线程反序列化，0ms 即刻上屏杜绝白屏闪烁)
    // MOB-P1-48：distinctUntilChanged 抑制「表级失效 → 全表重发射」的下游抖动：
    // 内容没变就不通知 UI，避免列表无谓重组。
    val entriesFlow: StateFlow<List<LifeEntry>> = database.entryDao().getAllEntriesFlow()
        .catch { emit(emptyList()) }
        .map { entities ->
            entities.map { it.toDomainModel(json) { raw -> decodeAttachments(raw) } }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 时间流的可用年份。
     *
     * MOB-P1-32：改为 **SQL DISTINCT** —— 原先依赖 entriesFlow（把全量档案物化到内存）
     * 再遍历取年份，等于为了 10 个年份付出全表物化的代价。
     */
    val availableYearsFlow: StateFlow<List<Int>> = database.entryDao().getAvailableYearStringsFlow()
        .catch { emit(emptyList()) }
        .map { list -> list.mapNotNull { it.trim().toIntOrNull() }.sortedDescending() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 热力图的按日计数（SQL GROUP BY）。
     *
     * MOB-P1-32：原先在 Kotlin 侧对全量档案 groupBy + 每条一次日期解析，
     * 2 万条时是 2 万次解析 + 一个巨大的 Map。
     */
    val dayCountsFlow: StateFlow<Map<String, Int>> = database.entryDao().getDayCountsFlow()
        .catch { emit(emptyList()) }
        .map { rows -> rows.associate { it.day to it.count } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 往年同月同日的本地条目（不含今年），作为「那年今日」服务端结果缺失时的本地兜底。
     *
     * formatDisplayDate 需要解析 ISO 时间串，必须离开主线程批量执行（AOPT-03c）。
     */
    val localOnThisDayFlow: StateFlow<List<LifeEntry>> = flow {
        val today = java.time.LocalDate.now(FormatUtils.BeijingZone)
        val monthDay = String.format(
            java.util.Locale.US,
            "%02d-%02d",
            today.monthValue,
            today.dayOfMonth
        )
        emitAll(
            database.entryDao().getLocalOnThisDayFlow(monthDay, today.year.toString())
                .map { list -> list.map { it.toDomainModel(json) { raw -> decodeAttachments(raw) } } }
        )
    }
        .catch { emit(emptyList()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「那年今日」远端结果 —— **按天缓存**。
     *
     * MOB-P2-35：此前每次进入时间流都会请求一次 /entries/on-this-day，
     * 一天里反复切 Tab 就反复打网络。现在同一天只取一次。
     */
    suspend fun fetchOnThisDayCached(): List<LifeEntry> {
        val todayKey = FormatUtils.beijingNowIsoString().take(10)
        val cached = syncPrefs.getString(KEY_ON_THIS_DAY_JSON, null)
        if (syncPrefs.getString(KEY_ON_THIS_DAY_DATE, null) == todayKey && cached != null) {
            return runCatching {
                json.decodeFromString<List<LifeEntry>>(cached)
            }.getOrDefault(emptyList())
        }
        val fresh = runCatching { apiClient.fetchOnThisDay().entries }.getOrDefault(emptyList())
        runCatching {
            syncPrefs.edit()
                .putString(KEY_ON_THIS_DAY_DATE, todayKey)
                .putString(KEY_ON_THIS_DAY_JSON, json.encodeToString(fresh))
                .apply()
        }
        return fresh
    }

    /**
     * MOB-P1-32：数据变更信号（表级失效 → 重新发射）。
     * 时间流用它来感知「同步完成 / 本地乐观写入」，从而刷新当前窗口。
     */
    val entriesRevisionFlow: Flow<Long> = database.entryDao().getEntriesRevisionFlow()
        .catch { emit(0L) }
        // drop(1)：Room 的 Flow 订阅时会先发一次当前值，
        // 而首屏加载本来就会读到最新数据，这一次发射是多余的。
        .drop(1)

    /**
     * MOB-P1-32：时间流的**分页加载**入口。
     *
     * 列表不再把全量档案物化进内存，只按需取当前窗口；
     * 过滤（分类 / 年份）下沉到 SQL，避免在内存里做全表筛选。
     */
    suspend fun loadStreamPage(
        filter: String,
        year: Int?,
        offset: Int,
        limit: Int
    ): List<LifeEntry> = withContext(Dispatchers.IO) {
        database.entryDao().getEntriesPage(
            filter = filter,
            year = year?.toString(),
            limit = limit,
            offset = offset
        ).map { it.toDomainModel(json) { raw -> decodeAttachments(raw) } }
    }

    /** MOB-P1-32：分页列表的总数 */
    suspend fun countStreamEntries(filter: String, year: Int?): Int = withContext(Dispatchers.IO) {
        database.entryDao().countEntries(filter = filter, year = year?.toString())
    }

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
            projectId = req.projectId,
            wishId = req.wishId,
            footprintId = req.footprintId,
            attachmentsJson = json.encodeToString(previewAttachments),
            syncStatus = STATUS_PENDING_CREATE,
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
            projectId = projectId,
            wishId = wishId,
            footprintId = footprintId,
            attachmentsJson = json.encodeToString(attachments),
            syncStatus = STATUS_PENDING_CREATE,
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
            STATUS_PENDING_UPDATE
        } else {
            STATUS_PENDING_CREATE
        }

        val updatedEntity = entity.copy(
            type = req.type,
            title = req.title,
            content = req.content,
            occurredAt = req.occurredAt,
            location = req.location,
            cityCode = req.cityCode,
            cityName = req.cityName ?: entity.cityName,
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
            syncStatus = STATUS_PENDING_CREATE,
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
            // 新建时只有「是分期」与「不是分期」两种真实语义，
            // 发 false 没有意义（服务端新建默认就是非分期），
            // 因此只在是分期时携带 true。
            isInstallment = isInstallment.takeIf { it },
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
            STATUS_PENDING_UPDATE
        } else {
            STATUS_PENDING_CREATE
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
            // ── 分期三态的本地落地 ──────────────────────────────
            // 关键：req.isInstallment 为 null 表示「本次不携带该字段」，
            // 服务端会**保持原值** —— 那本地也必须保持原值，
            // 不能乐观写成 false（否则本地显示「一次性」、服务端仍是「分期」，
            // 两端当场分叉，下一次拉取又跳回去）。
            //
            // 只有明确带了 clear_installment（= 显式取消分期）时，
            // 才真的把分期降级并清空账期字段 —— 与服务端行为完全对齐。
            isInstallment = if (req.clearInstallment) {
                false
            } else {
                req.isInstallment ?: entity.isInstallment
            },
            installmentChannelCode = if (req.clearInstallment) null else (req.installmentChannelCode ?: entity.installmentChannelCode),
            installmentTotalAmount = if (req.clearInstallment) null else (req.installmentTotalAmount ?: entity.installmentTotalAmount),
            installmentMonths = if (req.clearInstallment) null else (req.installmentMonths ?: entity.installmentMonths),
            installmentMonthlyPay = if (req.clearInstallment) null else (req.installmentMonthlyPay ?: entity.installmentMonthlyPay),
            installmentRemainMonths = if (req.clearInstallment) null else (req.installmentRemainMonths ?: entity.installmentRemainMonths),
            repaymentDay = if (req.clearInstallment) null else (req.repaymentDay ?: entity.repaymentDay),
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
    /**
     * 删除一条生活档案。
     *
     * MOB-P2-47：返回**撤销票据**（已同步且删除事件尚未上传时才可撤销）。
     * 未同步的图文条目会把本地相纸文件一并清理，照片已不可恢复 ⇒ 不提供撤销。
     */
    suspend fun deleteEntry(idOrLocalId: Long): DeleteUndoTicket? {
        val entity = if (idOrLocalId < 0) {
            database.entryDao().getEntryByLocalId(-idOrLocalId)
        } else {
            database.entryDao().getEntryByServerId(idOrLocalId)
                ?: database.entryDao().getEntryByLocalId(idOrLocalId)
        } ?: return null

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
        // MOB-P2-47：给「撤销」留出宽限期（立即上传的话撤销就永远赶不上）
        scheduleDelayedSync(DELETE_GRACE_PERIOD_MS)

        // 已同步的条目：删除事件仍在发件箱里（尚未上传）⇒ 可以撤销
        val undoable = entity.serverId != null && entity.serverId > 0 && orphanMediaPaths.isEmpty()
        return if (undoable) DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.ENTRY) else null
    }

    /** MOB-P2-47：撤销一次删除（实现见 [SyncMerger]） */
    suspend fun undoDeleteEntry(ticket: DeleteUndoTicket): Boolean =
        SyncMerger.undoDeleteEntry(database, ticket)

    /**
     * 删除单笔记账 (支持已同步正数 serverId 与未同步负数 -localId)
     */
    /** MOB-P2-47：删除一笔账目并返回撤销票据（已同步且删除事件尚未上传时可撤销） */
    suspend fun deleteTransaction(idOrLocalId: Long): DeleteUndoTicket? {
        val entity = if (idOrLocalId < 0) {
            database.transactionDao().getTransactionByLocalId(-idOrLocalId)
        } else {
            database.transactionDao().getTransactionByServerId(idOrLocalId)
                ?: database.transactionDao().getTransactionByLocalId(idOrLocalId)
        } ?: return null

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
        // MOB-P2-47：同 deleteEntry，删除事件先压一会儿再上传
        scheduleDelayedSync(DELETE_GRACE_PERIOD_MS)

        val undoable = entity.serverId != null && entity.serverId > 0
        return if (undoable) DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.TRANSACTION) else null
    }

    /** MOB-P2-47：撤销一次账目删除（实现见 [SyncMerger]） */
    suspend fun undoDeleteTransaction(ticket: DeleteUndoTicket): Boolean =
        SyncMerger.undoDeleteTransaction(database, ticket)

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
    /**
     * MOB-P1-37：可按数据集独立拉取。
     *
     * 原先一次 pullRemoteData 会把 **五类数据** 全部拉一遍 ——
     * 于是「切到造物页」也会连带把用户根本看不到的生活档案与账目流水重拉一遍，
     * 流量与耗时都是白给。现在每个页面只拉自己渲染的那一类。
     */
    enum class SyncDataset { ENTRIES, TRANSACTIONS, PROJECTS, WISHES, FOOTPRINTS }

    suspend fun pullRemoteData(
        forceFull: Boolean = false,
        datasets: Set<SyncDataset> = SyncDataset.entries.toSet()
    ): Result<Unit> = pullMutex.withLock {
        // MOB-P0-04：调用方（Compose 的 rememberCoroutineScope）可能在主线程发起拉取，
        // 而函数体内的游标计算、对象映射、合并都是纯 CPU 的重活：
        // 上万条记录时 maxUpdatedCursor 要做上万个 DateTimeFormatter.parse，
        // 全部落在主线程 ⇒ 下拉刷新时界面直接冻结数百毫秒。
        // 这里整体切到 IO 调度器，调用方无需关心线程。
        withContext(Dispatchers.IO) {
        kotlin.runCatching {
            // 不在这里内联 flush：多图上传可能要几十秒，会把「下拉刷新」卡死在发件箱上。
            // 每次入队都已 scheduleSync()，Worker 受网络约束自会跑；此处只做拉取。
            val failures = mutableListOf<String>()

            if (SyncDataset.ENTRIES in datasets) {
                pullEntriesDataset(forceFull)?.let { failures += it }
            }
            if (SyncDataset.TRANSACTIONS in datasets) {
                pullTransactionsDataset(forceFull)?.let { failures += it }
            }
            if (SyncDataset.PROJECTS in datasets) {
                pullProjectsDataset()?.let { failures += it }
            }
            if (SyncDataset.WISHES in datasets) {
                pullWishesDataset()?.let { failures += it }
            }
            if (SyncDataset.FOOTPRINTS in datasets) {
                pullFootprintsDataset()?.let { failures += it }
            }

            if (failures.isNotEmpty()) {
                val authExpired = failures.any { it.contains("401") }
                val suffix = if (authExpired) "（设备凭据已失效，请重新扫码配对）" else ""
                throw IllegalStateException(failures.joinToString("；") + suffix)
            }
            // MOB-P1-86：首轮拉取成功 —— 此后 UI 才能区分「真的没有记录」与「还在加载」
            _firstSyncCompleted.value = true
        }.also { result ->
            // runCatching 会把 CancellationException 转成 Result.failure 吞掉，
            // 违背结构化并发——用户离开页面导致的取消必须继续向上传播
            val ex = result.exceptionOrNull()
            if (ex is CancellationException) throw ex
        }
        }
    }

    /** MOB-P1-37 / MOB-P1-41：生活档案拉取。增量模式下**逐页合并并释放**。 */
    private suspend fun pullEntriesDataset(forceFull: Boolean): String? {
        val entriesCursor = syncPrefs.getString(KEY_ENTRIES_CURSOR, null)
        val useIncremental =
            !forceFull && database.entryDao().hasAny() && !entriesCursor.isNullOrBlank()

        // MOB-P1-41：只保留「远端 ID 集合」用于全量收口，
        // 远端记录本身逐页合并后立即释放 —— 不再把上万条记录堆在内存里。
        val remoteIds = mutableSetOf<Long>()
        val updatedCandidates = mutableListOf<String?>()

        // MOB-P1-xx：全量拉取时**先收口历史重复行，再建本地索引**。
        //
        // 顺序反了会出事：deleteDuplicateServerIds() 保留的是 MIN(local_id) 那条，
        // 而若索引是在去重之前建的，toMap() 对重复 server_id 保留的是**最后一条**
        // （通常是 local_id 最大的那条）—— 也就是刚被删掉的那条。
        // 随后 upsertEntries 用这个已被删除的 local_id 做 insertAll(REPLACE)，
        // 重复行就复活了，去重每轮都被抵消。
        if (!useIncremental) {
            // 全量拉取：先收口历史重复行（必须在 upsert 之前，否则重复行会被再插回来）
            database.entryDao().deleteDuplicateServerIds()
        }

        // MOB-P1-41：本地索引**只建一次**（逐页合并时复用，避免每页重建全表索引）
        val localIndex = database.entryDao().getEntrySyncIndex()
            .mapNotNull { index -> index.serverId?.let { it to index } }
            .toMap()

        try {
            var page = 1
            var pages = 0
            while (pages < MAX_PAGES) {
                val pageMeta = ResponseMeta()
                val resp = apiClient.fetchEntries(
                    page = page,
                    pageSize = 100,
                    since = if (useIncremental) cursorForRequest(entriesCursor) else null,
                    meta = pageMeta
                )
                pages++
                // MOB-P1-36：服务端回了权威游标就直接采纳 ——
                // 过去这里要把这一页**每一条记录**的 updated_at / deleted_at 都塞进列表，
                // 收尾时再逐个 DateTimeFormatter 解析一遍求最大值（上万条就是上万个解析）。
                // 现在一页只要解析 1 个值，老服务端没给这个头时才回落到旧路径。
                val serverCursor = pageMeta.syncCursor
                if (!serverCursor.isNullOrBlank()) {
                    updatedCandidates += serverCursor
                } else {
                    updatedCandidates += resp.entries.map { it.updatedAt }
                    updatedCandidates += resp.entries.map { it.deletedAt }
                }
                if (!useIncremental) {
                    resp.entries.forEach { remoteIds += it.id }
                }
                // MOB-P1-41：逐页合并（无论增量还是全量），远端对象随即被释放
                SyncMerger.upsertEntries(database, resp.entries, localIndex, json)
                // 只用「不满页」做终止条件：resp.total 是 COUNT 快照，
                // 并发写入会偏小，导致 size >= total 提前 break 漏拉后续页
                if (resp.entries.size < 100) break
                page++
            }
            if (pages >= MAX_PAGES) {
                throw IllegalStateException("分页超过 $MAX_PAGES 页，本次拉取已中止")
            }
        } catch (e: Exception) {
            return "生活档案：${e.message}"
        }

        if (!useIncremental) {
            SyncMerger.finalizeFullEntries(database, remoteIds, localIndex)
        }

        val maxEntryUpdated = maxUpdatedCursor(updatedCandidates)
        if (maxEntryUpdated != null) {
            syncPrefs.edit().putString(KEY_ENTRIES_CURSOR, maxEntryUpdated).apply()
        }
        // MOB-P1-xx：**不要**用手机当前时间当游标。
        //
        // 原先这里还有一条兜底：拉取没拿到任何记录时，把游标写成 beijingNowIsoString()（= 手机时钟）。
        // 若**服务器时钟比手机慢**（超过 cursorForRequest 的 120 秒回退窗口），
        // 此后增量拉取用的游标就永远比服务器上所有记录还新 ⇒
        // 服务器上后来新增的记录**永远拉不到**（静默漏拉，且没有任何报错）。
        //
        // 正确做法：拿不到游标就**不写**。下一次拉取仍是全量（entriesCursor 为空 ⇒ useIncremental=false），
        // 代价只是多拉一次，收益是不会把游标推到未来。
        return null
    }

    /** MOB-P1-37 / MOB-P1-41：记账流水拉取（同样是增量逐页合并）。 */
    private suspend fun pullTransactionsDataset(forceFull: Boolean): String? {
        val txCursor = syncPrefs.getString(KEY_TX_CURSOR, null)
        val useIncremental =
            !forceFull && database.transactionDao().hasAny() && !txCursor.isNullOrBlank()

        val remoteIds = mutableSetOf<Long>()
        val updatedCandidates = mutableListOf<String?>()

        // MOB-P1-41：本地索引只建一次，逐页复用
        val localIndex = database.transactionDao().getTransactionSyncIndex()
            .mapNotNull { index -> index.serverId?.let { it to index } }
            .toMap()

        try {
            var offset = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                val batchMeta = ResponseMeta()
                val batch = apiClient.fetchTransactions(
                    since = if (useIncremental) cursorForRequest(txCursor) else null,
                    limit = 200,
                    offset = if (offset > 0) offset else null,
                    meta = batchMeta
                )
                pages++
                // MOB-P1-36：优先采用服务端权威游标（见 pullEntriesDataset 的说明）
                val serverCursor = batchMeta.syncCursor
                if (!serverCursor.isNullOrBlank()) {
                    updatedCandidates += serverCursor
                } else {
                    updatedCandidates += batch.map { it.updatedAt }
                    updatedCandidates += batch.map { it.deletedAt }
                }
                if (!useIncremental) {
                    batch.forEach { remoteIds += it.id }
                }
                // MOB-P1-41：逐页合并并释放
                SyncMerger.upsertTransactions(database, batch, localIndex)
                if (batch.size < 200) break
                offset += batch.size
            }
            if (pages >= MAX_PAGES) {
                throw IllegalStateException("分页超过 $MAX_PAGES 页，本次拉取已中止")
            }
        } catch (e: Exception) {
            return "记账流水：${e.message}"
        }

        if (!useIncremental) {
            SyncMerger.finalizeFullTransactions(database, remoteIds, localIndex)
        }

        val maxTxUpdated = maxUpdatedCursor(updatedCandidates)
        if (maxTxUpdated != null) {
            syncPrefs.edit().putString(KEY_TX_CURSOR, maxTxUpdated).apply()
        }
        // MOB-P1-xx：同生活档案 —— 不拿不到游标就拿手机时钟顶上（服务器时钟慢于手机时会静默漏拉）
        return null
    }

    /** MOB-P1-37：造物项目（只读缓存，整表替换） */
    private suspend fun pullProjectsDataset(): String? = try {
        val remoteProjects = apiClient.fetchProjects()
        database.withTransaction {
            database.projectDao().clearAll()
            database.projectDao().insertAll(remoteProjects.map { it.toCacheEntity() })
        }
        null
    } catch (e: Exception) {
        "造物：${e.message}"
    }

    /** MOB-P1-37：三维心愿（分页拉取，整表替换） */
    private suspend fun pullWishesDataset(): String? = try {
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
        null
    } catch (e: Exception) {
        "心愿：${e.message}"
    }

    /** MOB-P1-37：山川足迹（只读缓存，整表替换） */
    private suspend fun pullFootprintsDataset(): String? = try {
        val remoteFootprints = apiClient.fetchFootprints()
        database.withTransaction {
            database.footprintDao().clearAll()
            database.footprintDao().insertAll(remoteFootprints.map { it.toCacheEntity(json) })
        }
        null
    } catch (e: Exception) {
        "足迹：${e.message}"
    }

    /** MOB-P1-37：时间流页面专用拉取（只拉生活档案） */
    suspend fun pullStreamData(forceFull: Boolean = false): Result<Unit> =
        pullRemoteData(forceFull, setOf(SyncDataset.ENTRIES))

    /** MOB-P1-37：账本页面专用拉取（只拉记账流水；资产由 LedgerRepository 单独刷新） */
    suspend fun pullLedgerData(forceFull: Boolean = false): Result<Unit> =
        pullRemoteData(forceFull, setOf(SyncDataset.TRANSACTIONS))

    /** MOB-P1-37：造物 / 心愿 / 足迹页面专用拉取 */
    suspend fun pullProjectsWishesData(forceFull: Boolean = false): Result<Unit> =
        pullRemoteData(
            forceFull,
            setOf(SyncDataset.PROJECTS, SyncDataset.WISHES, SyncDataset.FOOTPRINTS)
        )


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
     * MOB-P2-47：延迟一段时间后再排空发件箱（用于删除事件的宽限期）。
     */
    private fun scheduleDelayedSync(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            scheduleSync()
        }
    }

    /**
     * 立即调度发件箱同步。
     *
     * 用 APPEND_OR_REPLACE 而不是 REPLACE：连续编辑不会取消正在上传的 worker，
     * 避免「相纸已传到 R2、却来不及建档」留下孤儿对象。
     */
    /**
     * 调度后台发件箱同步。
     *
     * MOB-P1-51：原先用 APPEND_OR_REPLACE —— 每一次乐观写入（记账 / 改随笔 / 删条目）
     * 都会**链式追加**一条 WorkManager 任务，连续记 20 笔就是 20 条排队任务，
     * 每条都要重读整个发件箱、重抢 outboxMutex、再写一次 WorkManager 自己的 SQLite，
     * 同步延迟被指数级放大。
     *
     * 现在改为 **KEEP + 立即入队**：
     * - KEEP 保证「有在跑/排队的就不重复排」，连续写入只折叠成一条任务；
     * - 立即入队（而不是延迟去抖）保证 WorkManager 的 SQLite 里**马上**有持久化记录，
     *   即使用户保存后立刻杀掉进程，待同步事件也不会「没人认领」；
     * - 「运行期间新写入的事件会不会被漏掉」由 OutboxSyncWorker 的**排空循环**兜底
     *   （见 OutboxSyncWorker.doWork）。
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // MOB-P1-53：低电量时不启动媒体上传，避免在用户不知情时耗尽电量
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            OutboxSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
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
                    // MOB-P0-03：原先每张照片都被 readBytes() 整份读进堆内存，
                    // 而且是「无限并发」上传 —— N 张照片的峰值内存 = 2 × Σ文件大小，
                    // 同时还会占满上行带宽把其它请求饿死。
                    // 现在改为「最多 3 张并发 + 文件流式上传」，峰值内存恒定。
                    val uploadSemaphore = Semaphore(MAX_CONCURRENT_UPLOADS)
                    val uploadedAttachments = coroutineScope {
                        itemsToUpload.map { item ->
                            async(Dispatchers.IO) {
                                uploadSemaphore.withPermit {
                                    val file = File(item.filePath)
                                    if (file.exists()) {
                                        tempFiles.add(file)
                                        ImagePipeline.uploadImage(
                                            apiClient = apiClient,
                                            file = file,
                                            mimeType = item.mimeType,
                                            filename = file.name,
                                            width = item.width,
                                            height = item.height
                                        )
                                    } else null
                                }
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
                                syncStatus = STATUS_SYNCED,
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
// MOB-P1-32：附件解码器可注入（生产环境走 SyncManager 的 LRU 记忆化缓存）
fun CachedEntryEntity.toDomainModel(
    json: Json,
    attachmentDecoder: (String) -> List<com.looseleaf.app.model.Attachment> = { raw ->
        try {
            json.decodeFromString<List<com.looseleaf.app.model.Attachment>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
): LifeEntry {
    val atts = attachmentDecoder(attachmentsJson)
    return LifeEntry(
        id = serverId ?: -localId,
        type = type,
        title = title,
        content = content,
        occurredAt = occurredAt,
        location = location,
        cityCode = cityCode,
        cityName = cityName,
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
    projectId = projectId,
    wishId = wishId,
    footprintId = footprintId,
    attachmentsJson = json.encodeToString(attachments),
    syncStatus = SyncManager.STATUS_SYNCED,
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
    syncStatus = SyncManager.STATUS_SYNCED,
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
