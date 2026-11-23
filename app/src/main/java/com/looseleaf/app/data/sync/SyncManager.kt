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
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.api.ResponseMeta
import com.looseleaf.app.data.local.*
import com.looseleaf.app.model.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.LedgerModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 时间流一个分页窗口的完整结果（AND-3）。
 *
 * [entries] 与 [totalCount] 在**同一次 IO** 里取回 ——
 * 原先窗口查询与 COUNT 是两次独立的 `withContext(Dispatchers.IO)`，
 * 每次刷新都要多一次线程切换与一次查询往返。
 */
data class StreamWindowPage(
    val entries: List<LifeEntry>,
    val totalCount: Int
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
class DeleteUndoTicket(
    val entity: CachedEntryEntity
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
        private const val KEY_WISHES_CURSOR = "last_wishes_sync_cursor"
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

        /**
         * 各页面「本会话已首拉」的**进程级**门禁。
         *
         * 原先各屏用 rememberSaveable 存这个标志：系统回收进程后从 savedInstanceState
         * 恢复时标志仍是 true ⇒ 这个新会话里该屏幕**永远不做首次增量拉取**，
         * 用户对着陈旧数据且无任何提示。改成进程级单例后：进程死亡重建 ⇒
         * object 重建 ⇒ 标志自动失效，恢复的屏幕会重新首拉；
         * 同进程内的旋转 / 切 Tab 仍被正确跳过。
         */
        private val sessionPullDone: MutableSet<String> =
            java.util.Collections.synchronizedSet(HashSet())

        fun markSessionPullDone(key: String) {
            sessionPullDone.add(key)
        }

        fun isSessionPullDone(key: String): Boolean = key in sessionPullDone

        /** 解绑/重新配对后必须让各页面重新首拉：旧门禁会让清空后的库一直停留在空态。 */
        fun resetSessionPulls() {
            sessionPullDone.clear()
        }

        /** MOB-P1-32：附件解码记忆化的最大条目数（超出按 LRU 淘汰） */
        private const val ATTACHMENT_MEMO_MAX = 4_000

        /**
         * OPT-08：CREATE 返回 410 Gone —— 服务端该记录已被软删后物理清理。
         * 本地临时行与对应 CREATE 事件必须一起丢弃，避免幽灵行与死信重试。
         * 放在同一事务里，避免「行删了、事件还在」或反之。
         */
        internal suspend fun discardGoneCreate(
            database: AppDatabase,
            event: OutboxEventEntity,
            deleteLocalRow: suspend () -> Unit
        ) {
            database.withTransaction {
                deleteLocalRow()
                database.outboxDao().delete(event)
            }
        }

        /**
         * 分页是否真的超限：短页（reachedEnd）说明已经到末页，
         * 即使页数恰好用满 MAX_PAGES 也不能误报。
         */
        internal fun paginationExceeded(reachedEnd: Boolean, pages: Int, maxPages: Int): Boolean =
            !reachedEnd && pages >= maxPages

        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // AND-7：这些依赖原先把 Room / OkHttp / WorkManager 的初始化全部压在
    // SyncManager 构造期（即 MainActivity.onCreate 的主线程上）。
    // 改成 lazy 后，getInstance() 只是一次对象分配；真正的重量级初始化
    // 推迟到首次实际使用（或后台 ensureStarted()）时发生。
    private val database by lazy { AppDatabase.getInstance(context) }
    private val apiClient by lazy { APIClient.getInstance(context) }
    private val workManager by lazy { WorkManager.getInstance(context) }
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

    private val mediaCoordinator by lazy {
        MediaOutboxCoordinator(
            context = context,
            database = database,
            apiClient = apiClient,
            json = json,
            notifyLocalWrite = ::notifyLocalWrite,
            scheduleSync = ::scheduleSync
        )
    }

    private val wishSync by lazy {
        SimpleEntitySyncAdapter<CreateWishRequest, CreateWishRequest, Wish, CachedWishEntity>(
            entityType = SimpleEntitySync.WISH,
            datasetLabel = "心愿",
            cursorKey = KEY_WISHES_CURSOR,
            database = database,
            syncPrefs = syncPrefs,
            scheduleSync = ::scheduleSync,
            cursorForRequest = ::cursorForRequest,
            maxUpdatedCursor = ::maxUpdatedCursor,
            daoHasAny = { database.wishDao().hasAny() },
            daoGetAllIds = { database.wishDao().getAllIds() },
            daoGetById = { database.wishDao().getById(it) },
            daoInsert = { database.wishDao().insert(it) },
            daoInsertAll = { database.wishDao().insertAll(it) },
            daoDeleteById = { database.wishDao().deleteById(it) },
            daoDeleteByIds = { database.wishDao().deleteByIds(it) },
            domainId = { it.id },
            domainUpdatedAt = { it.updatedAt },
            domainDeletedAt = { it.deletedAt },
            domainToEntity = { it.toCacheEntity(json) },
            entityWithId = { entity, id -> entity.copy(id = id) },
            encodeCreateReq = { json.encodeToString(it) },
            decodeCreateReq = { json.decodeFromString(it) },
            encodeUpdateReq = { json.encodeToString(it) },
            decodeUpdateReq = { json.decodeFromString(it) },
            getCreateClientRequestId = { it.clientRequestId },
            withCreateClientRequestId = { req, cid -> req.copy(clientRequestId = cid) },
            updateReqAsCreateReq = { req, fallbackCid -> req.copy(clientRequestId = req.clientRequestId ?: fallbackCid) },
            createReqToOfflineEntity = { req, id, existing -> req.toOfflineEntity(id, existing) },
            updateReqToOfflineEntity = { req, id, existing -> req.toOfflineEntity(id, existing) },
            matchesServer = { req, domain -> req.matchesServer(domain) },
            fetchRemote = { since, recordPage ->
                val allWishes = mutableListOf<Wish>()
                var wishPage = 1
                var pages = 0
                var reachedEnd = false
                while (pages < MAX_PAGES) {
                    val pageMeta = ResponseMeta()
                    val wishes = apiClient.fetchWishes(
                        page = wishPage,
                        pageSize = 100,
                        since = since,
                        meta = pageMeta
                    )
                    pages++
                    recordPage(pageMeta, wishes)
                    allWishes.addAll(wishes)
                    if (wishes.size < 100) {
                        reachedEnd = true
                        break
                    }
                    wishPage++
                }
                if (paginationExceeded(reachedEnd, pages, MAX_PAGES)) {
                    throw IllegalStateException("心愿分页超限")
                }
                allWishes
            },
            remoteCreate = { req, meta -> apiClient.createWish(req, meta = meta) },
            remoteUpdate = { id, req -> apiClient.updateWish(id, req) },
            remoteDelete = { id -> apiClient.deleteWish(id) },
            shouldKeepLocalStateOnOnlineUpdate = { id ->
                database.outboxDao().findEventByTarget(SimpleEntitySync.WISH, id, "RESOLVE") != null
            },
            shouldKeepLocalStateAfterCreate = { serverId ->
                database.outboxDao().findEventByTarget(SimpleEntitySync.WISH, serverId, "RESOLVE") != null
            },
            extraOutboxActionHandler = { event, onPayloadSuperseded ->
                if (event.action == "RESOLVE") {
                    replayTargetedMutation(event, onPayloadSuperseded) { targetServerId, payload ->
                        val req = json.decodeFromString<ResolveWishRequest>(payload)
                        apiClient.resolveWish(targetServerId, req)
                    }
                    true
                } else {
                    false
                }
            }
        )
    }

    /**
     * MOB-P1-86：是否已完成**首轮**同步。
     * 时间流此前用「entries 为空」同时表达「正在加载」与「本来就没有记录」，
     * 导致 App 刚打开就显示「岁月静好，暂无此分类记录」，随后记录才陆续出现 ——
     * 用户会以为数据丢了。这里用独立信号把两种状态分开。
     */
    private val _firstSyncCompleted = MutableStateFlow(false)
    val firstSyncCompletedFlow: StateFlow<Boolean> = _firstSyncCompleted.asStateFlow()

    // AND-7：待同步 / 失败计数原先用 stateIn(scope, Eagerly) —— 构造即订阅两条
    // Room Flow，冷启动首帧就要进 InvalidationTracker。现在由 ensureStarted()
    // 显式启动（Application 的 IO 协程里调用），首帧不再触碰数据库。
    private val _pendingEventCount = MutableStateFlow(0)
    val pendingEventCountFlow: StateFlow<Int> = _pendingEventCount.asStateFlow()

    private val _failedEventCount = MutableStateFlow(0)
    val failedEventCountFlow: StateFlow<Int> = _failedEventCount.asStateFlow()

    @Volatile
    private var started = false

    /**
     * AND-7：显式启动 SyncManager 的后台基础设施。
     *
     * 幂等；只应从后台协程调用（Application.onCreate 的 appScope / 首帧后的 LaunchedEffect）。
     */
    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                combine(
                    database.outboxDao().getPendingEventCountFlow().catch { emit(0) },
                    LedgerModule.observePendingCount(context).catch { emit(0) }
                ) { appPending, ledgerPending -> appPending + ledgerPending }
                    .collect { _pendingEventCount.value = it }
            }
            scope.launch {
                combine(
                    database.outboxDao().getFailedEventCountFlow().catch { emit(0) },
                    LedgerModule.observeFailedCount(context).catch { emit(0) }
                ) { appFailed, ledgerFailed -> appFailed + ledgerFailed }
                    .collect { _failedEventCount.value = it }
            }
            scope.launch { normalizeLegacyTimestamps() }
            scope.launch { repairLegacySimpleEntityTargets() }
        }
    }

    /**
     * P0-3：旧版本把 wish 的临时目标写成
     * `local_target_id = -1, server_target_id = 负数`，导致 Worker 删除 -1、
     * UPDATE / DELETE 直接消费事件，离线修改与删除静默丢失。
     *
     * 这里做一次性迁移，幂等且只跑一次；Worker 启动时也会兜底调用。
     */
    internal suspend fun repairLegacySimpleEntityTargets() = withContext(Dispatchers.IO) {
        val key = "simple_entity_targets_repaired_v1"
        if (syncPrefs.getBoolean(key, false)) return@withContext
        runCatching {
            val repaired = SimpleEntitySync.repairLegacyTargets(database.outboxDao())
            if (repaired > 0) {
                Log.i(TAG, "Repaired $repaired legacy simple-entity outbox targets")
            }
            syncPrefs.edit().putBoolean(key, true).apply()
        }
    }

    /**
     * 一次性把历史 occurred_at 归一成北京时间 ISO（`+08:00`）。
     *
     * 旧版本的本地行可能存过点号格式或带其它偏移的串；账本的月度/按年查询是
     * 字符串范围比较，混入不同偏移会把记录放进错误月份。只改格式，不改真实时刻。
     */
    private suspend fun normalizeLegacyTimestamps() = withContext(Dispatchers.IO) {
        val key = "timestamps_normalized_v1"
        if (syncPrefs.getBoolean(key, false)) return@withContext
        runCatching {
            database.entryDao().getEntryTimeRows().forEach { row ->
                val normalized = FormatUtils.normalizeBeijingIsoOrRaw(row.occurredAt)
                if (normalized != row.occurredAt) {
                    database.entryDao().updateEntryOccurredAt(row.localId, normalized)
                }
            }
            syncPrefs.edit().putBoolean(key, true).apply()
        }
    }

    /** AND-7：供 Application 在后台判断「是否确有积压」，避免无脑唤醒 WorkManager。 */
    suspend fun pendingEventCountNow(): Int = withContext(Dispatchers.IO) {
        runCatching { database.outboxDao().countPendingEvents() }.getOrDefault(0)
    }

    /** 解绑前警告用：待发与失败态事件都只存在于本机，清库后同样无法找回。 */
    suspend fun unsyncedEventCountNow(): Int = withContext(Dispatchers.IO) {
        runCatching { database.outboxDao().countAllEvents() }.getOrDefault(0)
    }

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

    /**
     * 时间流的可用年份。
     *
     * MOB-P1-32：改为 **SQL DISTINCT** —— 原先依赖 entriesFlow（把全量档案物化到内存）
     * 再遍历取年份，等于为了 10 个年份付出全表物化的代价。
     */
    val availableYearsFlow: StateFlow<List<Int>> by lazy {
        flow { emitAll(database.entryDao().getAvailableYearStringsFlow()) }
            .catch { emit(emptyList()) }
            .map { list -> list.mapNotNull { it.trim().toIntOrNull() }.sortedDescending() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /**
     * 热力图的按日计数（SQL GROUP BY）。
     *
     * MOB-P1-32：原先在 Kotlin 侧对全量档案 groupBy + 每条一次日期解析，
     * 2 万条时是 2 万次解析 + 一个巨大的 Map。
     */
    val dayCountsFlow: StateFlow<Map<String, Int>> by lazy {
        flow { emitAll(database.entryDao().getDayCountsFlow()) }
            .catch { emit(emptyList()) }
            .map { rows -> rows.associate { it.day to it.count } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

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
        // 失败必须抛出去：原先 getOrDefault(emptyList()) 之后无条件写缓存，
        // 会把「网络瞬断」缓存成「今天真的没有」，此后一整天不再请求远端。
        // 现在仅请求成功才写缓存与日期键；失败保留旧缓存，当天内可重试。
        val fresh = apiClient.fetchOnThisDay().entries
        runCatching {
            syncPrefs.edit()
                .putString(KEY_ON_THIS_DAY_DATE, todayKey)
                .putString(KEY_ON_THIS_DAY_JSON, json.encodeToString(fresh))
                .apply()
        }
        return fresh
    }

    /**
     * MOB-P1-32 / AND-3：数据变更信号（表级失效 → 重新发射）。
     * 时间流用它来感知「同步完成 / 本地乐观写入」，从而刷新当前窗口。
     *
     * AND-3：一次全量同步会逐页写入，Room 的表级失效因此会连续发射几十次。
     * 这里统一做 400ms 去抖 + conflate：同步期间只保留最后一次信号，
     * 结束后的最后一次刷新不会被吞掉（debounce 仍会在静默窗口后发射末值）。
     */
    @OptIn(FlowPreview::class)
    val entriesRevisionFlow: Flow<Long> by lazy {
        val roomRevisions = flow { emitAll(database.entryDao().getEntriesRevisionFlow()) }
            .catch { emit(0L) }
            // drop(1)：Room 的 Flow 订阅时会先发一次当前值，
            // 而首屏加载本来就会读到最新数据，这一次发射是多余的。
            .drop(1)
            .coalesceBursts()
        // 本地写入立即通知一次；Room 的批量失效仍按 400ms 合并。
        merge(roomRevisions, localWriteSignal.map { 0L })
            // 收集方在 Compose 主线程；把 Room 订阅切到 Default，避免首帧初始化数据库。
            .flowOn(Dispatchers.Default)
    }

    /**
     * 本地乐观写入的即时信号（不等 Room 的表级失效 + 400ms 去抖）。
     * 用户自己刚保存的记录应当立刻出现在时间流；同步批量写入仍走去抖管道。
     */
    private val _localWriteSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val localWriteSignal: SharedFlow<Unit> = _localWriteSignal.asSharedFlow()
    fun notifyLocalWrite() {
        _localWriteSignal.tryEmit(Unit)
    }

    /**
     * MOB-P1-32 / AND-3：时间流的**分页加载**入口。
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

    /**
     * AND-3：窗口数据 + 总数一次取回。
     *
     * 首屏 / 同步后刷新都走这里，避免「先查窗口再查 COUNT」两次 IO 调度。
     */
    suspend fun loadStreamWindow(
        filter: String,
        year: Int?,
        offset: Int,
        limit: Int
    ): StreamWindowPage = withContext(Dispatchers.IO) {
        val dao = database.entryDao()
        val entries = dao.getEntriesPage(
            filter = filter,
            year = year?.toString(),
            limit = limit,
            offset = offset
        ).map { it.toDomainModel(json) { raw -> decodeAttachments(raw) } }
        StreamWindowPage(
            entries = entries,
            totalCount = dao.countEntries(filter = filter, year = year?.toString())
        )
    }

    /** MOB-P1-32：分页列表的总数 */
    suspend fun countStreamEntries(filter: String, year: Int?): Int = withContext(Dispatchers.IO) {
        database.entryDao().countEntries(filter = filter, year = year?.toString())
    }

    /**
     * 组合筛选时间流窗口数据 + 实时总数一次取回
     */
    suspend fun loadStreamWindowFiltered(
        categories: Set<String>,
        year: Int?,
        month: Int?,
        contents: Set<String>,
        offset: Int,
        limit: Int
    ): StreamWindowPage = withContext(Dispatchers.IO) {
        val dao = database.entryDao()
        val yearStr = year?.toString()
        val monthStr = month?.let { String.format(java.util.Locale.US, "%02d", it) }
        val hasCategories = categories.isNotEmpty()
        val includeDaily = categories.contains("daily")
        val includeWish = categories.contains("wish")
        val hasImage = contents.contains("has_image")
        val isPending = contents.contains("pending")
        val isFailed = contents.contains("failed")

        val entries = dao.getEntriesPageFiltered(
            year = yearStr,
            month = monthStr,
            hasCategories = hasCategories,
            includeDaily = includeDaily,
            includeWish = includeWish,
            hasImage = hasImage,
            isPending = isPending,
            isFailed = isFailed,
            limit = limit,
            offset = offset
        ).map { it.toDomainModel(json) { raw -> decodeAttachments(raw) } }

        val totalCount = dao.countEntriesFiltered(
            year = yearStr,
            month = monthStr,
            hasCategories = hasCategories,
            includeDaily = includeDaily,
            includeWish = includeWish,
            hasImage = hasImage,
            isPending = isPending,
            isFailed = isFailed
        )

        StreamWindowPage(
            entries = entries,
            totalCount = totalCount
        )
    }

    /** 实时统计匹配筛选的记录数（毫秒级本地计算） */
    suspend fun countStreamEntriesFiltered(
        categories: Set<String>,
        year: Int?,
        month: Int?,
        contents: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        database.entryDao().countEntriesFiltered(
            year = year?.toString(),
            month = month?.let { String.format(java.util.Locale.US, "%02d", it) },
            hasCategories = categories.isNotEmpty(),
            includeDaily = categories.contains("daily"),
            includeWish = categories.contains("wish"),
            hasImage = contents.contains("has_image"),
            isPending = contents.contains("pending"),
            isFailed = contents.contains("failed")
        )
    }

    /** 查询某年份下实际有记录的月份列表 (1~12) */
    suspend fun getAvailableMonthsForYear(year: Int): List<Int> = withContext(Dispatchers.IO) {
        database.entryDao().getAvailableMonthsForYear(year.toString())
    }

    // 观察全量三维心愿 (离线缓存即时上屏)
    val wishesFlow: StateFlow<List<Wish>> by lazy {
        flow { emitAll(database.wishDao().getAllWishesFlow()) }
            .catch { emit(emptyList()) }
            .map { entities ->
                entities.map { it.toWish(json) }
            }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /**
     * 将压制后的相纸字节保存至离线发件箱目录
     */
    fun saveCompressedImageToOutboxDir(bytes: ByteArray): File =
        mediaCoordinator.saveCompressedImageToOutboxDir(bytes)

    /**
     * 离线相纸图文归册 (entityType = "media_entry")
     */
    suspend fun createMediaEntryOffline(
        localImageFilePath: String,
        mimeType: String,
        width: Int,
        height: Int,
        req: CreateEntryRequest,
        additionalMediaFiles: List<OfflineMediaFileItem> = emptyList()
    ): Long = mediaCoordinator.createMediaEntryOffline(
        localImageFilePath = localImageFilePath,
        mimeType = mimeType,
        width = width,
        height = height,
        req = req,
        additionalMediaFiles = additionalMediaFiles
    )

    /**
     * 乐观创建生活档案 (0ms 上屏)
     */
    suspend fun createEntryOptimistic(
        type: String,
        title: String,
        content: String,
        occurredAt: String = FormatUtils.beijingNowIsoString(),
        location: String? = null,
        attachmentIds: List<Long>? = null,
        attachments: List<Attachment> = emptyList(),
        wishId: Long? = null,
        isPrivate: Boolean = false
    ): Long {
        val entryEntity = CachedEntryEntity(
            localId = 0,
            serverId = null,
            type = type,
            title = title,
            content = content,
            occurredAt = occurredAt,
            location = location,
            wishId = wishId,
            isPrivate = isPrivate,
            attachmentsJson = json.encodeToString(attachments),
            syncStatus = STATUS_PENDING_CREATE,
            createdAt = FormatUtils.beijingNowIsoString()
        )
        // 契约 C：幂等创建键在事件创建时生成、随载荷持久化，重试复用（见 createMediaEntryOffline）
        val req = CreateEntryRequest(
            type = type,
            title = title,
            content = content,
            occurredAt = occurredAt,
            location = location,
            attachmentIds = attachmentIds,
            wishId = wishId,
            isPrivate = isPrivate,
            clientRequestId = UUID.randomUUID().toString()
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

        notifyLocalWrite()
        scheduleSync()
        return localId
    }

    /**
     * 乐观编辑更新生活档案 (0ms 本地更新 + 队列同步)
     *
     * @return 是否真的写入了本地行；false = 目标记录已不存在（可能已被其它端
     * 或本机删除），修订未保存，调用方应提示用户。
     */
    suspend fun updateEntryOptimistic(
        entryId: Long,
        req: CreateEntryRequest,
        /** 编辑弹窗当前选中的完整附件列表；未同步图文的本地相纸（负 id）也在此。 */
        selectedAttachments: List<Attachment>? = null
    ): Boolean {
        val entity = if (entryId < 0) {
            database.entryDao().getEntryByLocalId(-entryId)
        } else {
            database.entryDao().getEntryByServerId(entryId)
                ?: database.entryDao().getEntryByLocalId(entryId)
        } ?: return false

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
            wishId = if (req.type == "wish") (req.wishId ?: entity.wishId) else null,
            isPrivate = req.isPrivate ?: entity.isPrivate,
            // ── 契约 B 的本地落地 ──────────────────────────────
            // attachmentIds 非 null 表示「附件集合就是这份」：删掉的照片立即从本地
            // 消失（原先不重写 attachmentsJson，删掉的照片要等下一次拉取才消失，
            // 看起来像「删了又回来」）。新增的照片由 UPDATE 成功后的权威回写补齐。
            //
            // 仅对**已同步条目**生效：未同步条目（尤其 media_entry）的本地附件是
            // file:// 预览、id 为负数，不在服务端附件 id 域内——按 attachmentIds
            // 过滤会把预览全部清空，离线图文条目编辑一次照片就从时间流消失了。
            attachmentsJson = when {
                // 编辑弹窗给了完整选择集时直接以它为准：本地负数预览也能正确保留/移除。
                selectedAttachments != null -> json.encodeToString(selectedAttachments)
                // 删光全部照片：无论是否同步过都必须立即清空本地预览。
                req.clearAttachments -> "[]"
                // 已同步条目按服务端附件 id 过滤；未同步条目的本地预览是负数/本地 id，
                // 不能按服务端 id 域过滤，否则会把预览整片清空。
                req.attachmentIds != null && entity.serverId != null -> {
                    val targetIds = req.attachmentIds ?: emptyList()
                    json.encodeToString(
                        decodeAttachments(entity.attachmentsJson).filter { it.id in targetIds }
                    )
                }
                else -> entity.attachmentsJson
            },
            syncStatus = newSyncStatus
        )
        val filesToDelete = mutableListOf<File>()
        database.withTransaction {
            database.entryDao().update(updatedEntity)

            // 未同步图文：把媒体 CREATE 的待上传文件收窄到当前选择，
            // 避免已删照片仍被 Worker 上传；内容与幂等键保持原样，交给 UPDATE 补发。
            if (selectedAttachments != null) {
                val mediaCreate = database.outboxDao().findEventByTarget(
                    entityType = "media_entry",
                    localTargetId = entity.localId,
                    action = "CREATE"
                )
                if (mediaCreate != null) {
                    val mediaPayload = runCatching {
                        json.decodeFromString<OfflineMediaEntryPayload>(mediaCreate.payload)
                    }.getOrNull()
                    if (mediaPayload != null) {
                        val keptFiles = selectMediaFiles(mediaPayload.files, selectedAttachments)
                        val keptPaths = keptFiles.map { it.filePath }.toSet()
                        filesToDelete += mediaPayload.files
                            .filter { it.filePath !in keptPaths }
                            .map { File(it.filePath) }
                        val patched = mediaPayload.copy(
                            files = keptFiles,
                            localImageFilePath = keptFiles.firstOrNull()?.filePath ?: "",
                            mimeType = keptFiles.firstOrNull()?.mimeType ?: mediaPayload.mimeType,
                            width = keptFiles.firstOrNull()?.width ?: 0,
                            height = keptFiles.firstOrNull()?.height ?: 0
                        )
                        // 收窄后的载荷要让事件**离开失败态**：只 copy(payload) 会保留
                        // failedAt，事件停在失败态永远不会自动复活。与下方 entry UPDATE
                        // 路径的语义一致，一并清空失败标记与重试计数。
                        database.outboxDao().insert(
                            mediaCreate.copy(
                                payload = json.encodeToString(patched),
                                failedAt = null,
                                lastError = null,
                                retryCount = 0
                            )
                        )
                    }
                }
            }

            // 编辑不覆盖已投递的 CREATE（可能服务端已按旧内容建档、只是响应丢失）。
            // 保持 CREATE 的原始载荷与幂等键，另起一条 UPDATE 携带最新内容：
            // CREATE 成功后把 server_target_id 绑定到 UPDATE，再按序补发。
            val updatePayload = req.copy(
                clientRequestId = null,
                attachmentIds = selectedAttachments
                    ?.mapNotNull { it.id.takeIf { id -> id > 0 } }
                    ?: req.attachmentIds,
                clearAttachments = selectedAttachments?.isEmpty() ?: req.clearAttachments
            )
            val existingUpdate = database.outboxDao().findEventByTarget(
                entityType = "entry",
                localTargetId = entity.localId,
                action = "UPDATE"
            )
            if (existingUpdate != null) {
                database.outboxDao().insert(
                    existingUpdate.copy(
                        payload = json.encodeToString(updatePayload),
                        failedAt = null,
                        lastError = null,
                        retryCount = 0
                    )
                )
            } else {
                database.outboxDao().insert(
                    OutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        entityType = "entry",
                        action = "UPDATE",
                        payload = json.encodeToString(updatePayload),
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId?.takeIf { it > 0 }
                    )
                )
            }
        }

        // 事务成功后才删除被移除的相纸文件；事务回滚时文件仍在，CREATE 仍可正常上传。
        filesToDelete.forEach { runCatching { it.delete() } }

        notifyLocalWrite()
        scheduleSync()
        return true
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
        notifyLocalWrite()
        // MOB-P2-47：给「撤销」留出宽限期（立即上传的话撤销就永远赶不上）
        scheduleDelayedSync(DELETE_GRACE_PERIOD_MS)

        // 已同步的条目：删除事件仍在发件箱里（尚未上传）⇒ 可以撤销
        val undoable = entity.serverId != null && entity.serverId > 0 && orphanMediaPaths.isEmpty()
        return if (undoable) DeleteUndoTicket(entity = entity) else null
    }

    /**
     * MOB-P2-47：撤销一次删除（实现见 [SyncMerger]）。
     *
     * 必须在 [outboxMutex] 临界区内执行：原先「查 DELETE 事件仍在 → 删事件 + 回插行」
     * 与 worker 的取事件存在 check-then-act 竞态——若 worker 恰在两步之间取走该 DELETE
     * 并完成服务端删除，它随后的 deleteByLocalId 会把刚恢复的行再删掉，
     * 而用户已经看到「已恢复该条记录」。worker 的整个 doWork 都持同一把锁，
     * 这里加锁后要么 undo 先完成（事件已删，worker 拿不到）、要么 worker 先完成
     * （undo 查不到事件，如实返回 false 提示「已同步，无法撤销」）。
     */
    suspend fun undoDeleteEntry(ticket: DeleteUndoTicket): Boolean =
        outboxMutex.withLock { SyncMerger.undoDeleteEntry(database, ticket) }

    /**
     * 清空本地缓存与待发队列 (设备解绑时调用)
     *
     * 必须在 outboxMutex + pullMutex 双临界区内执行：cancelScheduledSync 只取消
     * 后续调度，拦不住**正在运行**的 Worker 与在途拉取——清库后它们的成功回调
     * （如离线 CREATE 的缓存回插、整表重灌）会把旧中枢的数据重新写回空库，
     * 「解除并清除本机数据」变得不彻底。双锁顺序 outbox → pull 与既有用法
     * （两把锁各自单独使用、从不同时嵌套）不构成反向等待，无死锁风险。
     */
    suspend fun clearAllLocalData() = outboxMutex.withLock {
        pullMutex.withLock {
            resetSessionPulls()
            database.withTransaction {
                database.entryDao().clearAll()
                database.outboxDao().clearAll()
                database.wishDao().clearAll()
            }
            syncPrefs.edit().clear().apply()
            runCatching {
                File(context.filesDir, "outbox_media").deleteRecursively()
            }
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
     * MOB-P1-37：可按数据集独立拉取（散页与心愿按页面按需同步）。
     */
    enum class SyncDataset {
        ENTRIES,
        WISHES
    }

    suspend fun pullRemoteData(
        forceFull: Boolean = false,
        datasets: Set<SyncDataset> = setOf(SyncDataset.ENTRIES, SyncDataset.WISHES)
    ): Result<Unit> = pullMutex.withLock {
        withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val failures = mutableListOf<String>()

            if (SyncDataset.ENTRIES in datasets) {
                pullEntriesDataset(forceFull)?.let { failures += it }
            }
            if (SyncDataset.WISHES in datasets) {
                pullWishesDataset(forceFull)?.let { failures += it }
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
        // 收口历史重复行（必须在 upsert 之前，否则重复行会被再插回来）；
        // 先删「已同步旧行」，保证同 id 的 pending 本地编辑不会被 MIN(local_id) 误删。
        database.entryDao().deleteSyncedShadowsOfPending()
        if (!useIncremental) {
            database.entryDao().deleteDuplicateServerIds()
        }

        // MOB-P1-41：本地索引**只建一次**（逐页合并时复用，避免每页重建全表索引）
        val localIndex = database.entryDao().getEntrySyncIndex()
            .mapNotNull { index -> index.serverId?.let { it to index } }
            .toMap()

        try {
            var page = 1
            var pages = 0
            var reachedEnd = false
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
                if (resp.entries.size < 100) {
                    reachedEnd = true
                    break
                }
                page++
            }
            // 只有「没看到短页且已经用满页数」才是真的超限；
            // 第 MAX_PAGES 页恰好是最后一页（短页）时不能误报。
            if (paginationExceeded(reachedEnd, pages, MAX_PAGES)) {
                throw IllegalStateException("分页超过 $MAX_PAGES 页，本次拉取已中止")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "生活档案：${e.message}"
        }

        if (!useIncremental) {
            SyncMerger.finalizeFullEntries(database, remoteIds, localIndex)
        }
        // 分页重叠/排序抖动同样可能给档案留下重复 server_id（Lazy 列表会 key 冲突）。
        database.entryDao().deleteSyncedShadowsOfPending()
        database.entryDao().deleteDuplicateServerIds()

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

    private suspend fun pullWishesDataset(forceFull: Boolean): String? =
        wishSync.pullDataset(forceFull)

    /** MOB-P1-37：时间流页面专用拉取（只拉生活档案） */
    suspend fun pullStreamData(forceFull: Boolean = false): Result<Unit> =
        pullRemoteData(forceFull, setOf(SyncDataset.ENTRIES))

    /** 心愿单页面专用拉取 */
    suspend fun pullWishesData(forceFull: Boolean = false): Result<Unit> =
        pullRemoteData(forceFull, setOf(SyncDataset.WISHES))


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
        // 不能只靠进程内的 scope.delay：进程被杀后没人再排空，删除事件会永远卡在
        // 宽限期里（Worker 下次启动又会把它当成「还在宽限期」直接跳过）。
        // 交给 WorkManager 持久化的 initialDelay，进程重启也仍然有效。
        scheduleGraceSync(delayMs)
    }

    /**
     * 为「宽限期内的删除事件」安排一次延迟排空。
     * 使用独立的唯一任务名：主同步任务可能正在运行（KEEP 会忽略新请求）。
     */
    fun scheduleGraceSync(delayMs: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMs.coerceAtLeast(0), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            OutboxSyncWorker.GRACE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
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

    suspend fun createWishOrQueue(req: CreateWishRequest): Boolean =
        wishSync.createOrQueue(req)

    suspend fun updateWishOrQueue(id: Long, req: CreateWishRequest): Boolean =
        wishSync.updateOrQueue(id, req)

    suspend fun deleteWishOrQueue(id: Long): Boolean =
        wishSync.deleteOrQueue(id)

    suspend fun resolveWishOrQueue(id: Long, req: ResolveWishRequest): Boolean = withContext(Dispatchers.IO) {
        if (id < 0) {
            val existing = database.wishDao().getById(id)
            database.withTransaction {
                if (existing != null) {
                    database.wishDao().insert(existing.withResolvedState(req))
                    if (!SimpleEntitySync.hasPendingCreate(database.outboxDao(), SimpleEntitySync.WISH, id)) {
                        SimpleEntitySync.enqueueCreate(
                            database.outboxDao(),
                            SimpleEntitySync.WISH,
                            json.encodeToString(existing.toCreateWishRequest()),
                            id
                        )
                    }
                }
                SimpleEntitySync.enqueueResolve(database.outboxDao(), json.encodeToString(req), id)
            }
            scheduleSync()
            return@withContext true
        }
        try {
            val updated = apiClient.resolveWish(id, req)
            database.wishDao().insert(updated.toCacheEntity(json))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? APIException)?.code ?: -1
            if (code == -1 || code >= 500) {
                val existing = database.wishDao().getById(id)
                database.withTransaction {
                    if (existing != null) {
                        database.wishDao().insert(existing.withResolvedState(req))
                    }
                    SimpleEntitySync.enqueueResolve(database.outboxDao(), json.encodeToString(req), id)
                }
                scheduleSync()
                true
            } else {
                throw e
            }
        }
    }

    /**
     * 执行单条 Outbox 事件的远端投递
     *
     * @param onPayloadSuperseded 载荷在投递期间被 UI 编辑替换时的回调
     *   （竞态收口跳过回写与删事件，把事件留给下轮按新载荷重发）；
     *   Worker 收到后把该 eventId 移出本轮已尝试集合，避免投递长期停滞。
     */
    suspend fun processOutboxEvent(
        event: OutboxEventEntity,
        onPayloadSuperseded: (() -> Unit)? = null
    ) {
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
                            val serverEntry = try {
                                apiClient.createEntry(req)
                            } catch (e: APIException) {
                                if (e.code == 410) {
                                    // OPT-08：服务端该记录已被软删后物理清理（purge）。
                                    // 删除本地临时行并消费 CREATE，避免死信反复重试。
                                    discardGoneCreate(database, event) {
                                        database.entryDao().deleteByLocalId(event.localTargetId)
                                    }
                                    return
                                }
                                throw e
                            }
                            database.entryDao().markSynced(event.localTargetId, serverEntry.id)
                            // CREATE 响应可能是幂等命中的旧记录；把编辑期间排队的 UPDATE
                            // 绑定到真实 id，随后补发最新内容。
                            database.outboxDao().bindPendingUpdatesToServerId(
                                "entry", event.localTargetId, serverEntry.id
                            )
                        }
                        database.outboxDao().delete(event)
                    }
                    "UPDATE" -> {
                        // 同一轮 flush 内事件可能已被合并编辑 / 补绑服务端 id，
                        // 不能继续使用 Worker 启动时的旧快照载荷。
                        val current = database.outboxDao().findEventById(event.eventId) ?: return
                        val local = database.entryDao().getEntryByLocalId(current.localTargetId)
                        val targetId = current.serverTargetId ?: local?.serverId
                        if (targetId == null || targetId <= 0) {
                            // CREATE 尚未完成（响应丢失/仍在队列）：保留 UPDATE，
                            // 等 CREATE 成功后由 bindPendingUpdatesToServerId 补绑再发。
                            if (local == null) database.outboxDao().delete(current)
                            return
                        }
                        try {
                            val req = json.decodeFromString<CreateEntryRequest>(current.payload)
                            val serverEntry = apiClient.updateEntry(targetId, req)
                            // ── check-then-act 收口（P1）───────────────────────
                            // UI 的 updateEntryOptimistic 不持 outboxMutex，API 往返期间
                            // 完全可能又落了一版编辑（换掉本事件的载荷、改写本地行）。
                            // 回写与删事件放进同一事务：先按 eventId 重读事件比对载荷——
                            // - 载荷已被换成新内容 → 既不回写也不删事件，留给下一轮按新载荷重发；
                            // - 事件已不在（被撤销/清队）→ 同样不动本地行；
                            // - 载荷一致 → 只做**定向回写**（server_id / sync_status / attachments_json），
                            //   绝不整行覆盖——local 是 API 调用前的旧快照，整行回写会把
                            //   编辑期间的新内容静默冲掉。
                            database.withTransaction {
                                val latest = database.outboxDao().findEventById(current.eventId)
                                    ?: return@withTransaction
                                if (latest.payload != current.payload) {
                                    // 载荷已被并发编辑替换：本次不回写不删事件。
                                    onPayloadSuperseded?.invoke()
                                    return@withTransaction
                                }
                                database.entryDao().markSynced(current.localTargetId, serverEntry.id)
                                // 附件是服务端权威字段（编辑时新增的照片只存在于服务端响应里），
                                // 但只允许落在不再待发的行上，双保险防止覆盖并发编辑。
                                database.entryDao().updateAttachmentsIfNotPending(
                                    current.localTargetId,
                                    json.encodeToString(serverEntry.attachments)
                                )
                                database.outboxDao().delete(latest)
                            }
                        } catch (e: APIException) {
                            // 服务端已删（网页/其他设备）：本地修改已无处可去，
                            // 与 DELETE 同样宽容地消费事件，而不是反复重试进失败态
                            if (e.code == 404) {
                                database.entryDao().deleteByLocalId(current.localTargetId)
                                database.outboxDao().delete(current)
                            } else {
                                throw e
                            }
                        }
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
            "media_entry" -> mediaCoordinator.replayOutboxEvent(event)
            SimpleEntitySync.WISH -> wishSync.replayOutboxEvent(event, onPayloadSuperseded)
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
        wishId = wishId,
        isPrivate = isPrivate,
        attachments = atts,
        syncStatus = syncStatus,
        createdAt = createdAt
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
        budget = budget,
        actualCost = actualCost,
        resolutionType = resolutionType,
        actualTitle = actualTitle,
        status = status,
        targetDate = targetDate,
        fulfilledAt = fulfilledAt,
        isPrivate = isPrivate,
        attachments = atts,
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
    // 统一成北京时间 ISO：SQL 的年份/日期范围比较是字符串比较，
    // 混入 "Z"/其它偏移会让同一天的记录落到错误月份。
    occurredAt = FormatUtils.normalizeBeijingIsoOrRaw(occurredAt),
    location = location,
    wishId = wishId,
    isPrivate = isPrivate,
    attachmentsJson = json.encodeToString(attachments),
    syncStatus = SyncManager.STATUS_SYNCED,
    createdAt = createdAt ?: occurredAt
)

fun Wish.toCacheEntity(json: Json): CachedWishEntity = CachedWishEntity(
    id = id,
    title = title,
    kind = kind ?: "item",
    note = note,
    spec = spec,
    budget = budget,
    actualCost = actualCost,
    resolutionType = resolutionType,
    actualTitle = actualTitle,
    status = status,
    targetDate = targetDate,
    fulfilledAt = fulfilledAt,
    isPrivate = isPrivate,
    attachmentsJson = json.encodeToString(attachments),
    updatedAt = updatedAt
)
