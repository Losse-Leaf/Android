package com.looseleaf.feature.ledger.data

import android.content.Context
import androidx.room.withTransaction
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.api.ResponseMeta
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

val DefaultExpenseCategories = listOf("餐饮", "交通", "购物", "娱乐", "住房", "学习", "医疗", "其他")
val DefaultIncomeCategories = listOf("薪资", "奖金", "二手回血", "稿费与创作", "投资收益", "其他收入")
val DefaultPaymentMethods = listOf("微信支付", "支付宝", "银行卡", "现金")

data class LedgerSnapshot(
    val expenseCategories: List<String> = DefaultExpenseCategories,
    val incomeCategories: List<String> = DefaultIncomeCategories,
    val paymentMethods: List<String> = DefaultPaymentMethods,
    val creditChannels: List<CreditChannel> = emptyList(),
    val assetsOverview: AssetsOverview? = null,
    val installmentPlans: List<InstallmentPlanItemSummary> = emptyList(),
    val installmentSummary: InstallmentSummary? = null,
    val upcomingInstallmentItems: List<UpcomingInstallmentItem> = emptyList(),
    val isLoadingAssets: Boolean = false
)

data class LedgerDeleteUndoTicket(
    val entity: CachedTransactionEntity,
    val pendingUpdatePayload: String? = null
)

/**
 * 账本核心仓储与数据管理中心 (LedgerRepository)
 * 独立管理 looseleaf_ledger.db、分类字典缓存、分期渠道、资产大盘与同步链路。
 */
object LedgerRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var customApiClient: APIClient? = null

    @Volatile
    private var customCredentialStore: CredentialStore? = null

    private const val METADATA_TTL_MS = 30_000L

    @Volatile
    private var lastMetadataRefreshTime: Long = 0L

    private val _snapshot = MutableStateFlow(LedgerSnapshot())
    val snapshot: StateFlow<LedgerSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(
        context: Context,
        credentialStore: CredentialStore? = null,
        apiClient: APIClient? = null
    ) {
        appContext = context.applicationContext
        customCredentialStore = credentialStore
        customApiClient = apiClient
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = LedgerDatabase.getInstance(context.applicationContext)
                db.transactionDao().deleteSyncedShadowsOfPending()
                db.transactionDao().deleteDuplicateServerIds()
            }
        }
    }

    private fun requireDatabase(): LedgerDatabase {
        val ctx = appContext ?: throw IllegalStateException("LedgerRepository.init(context) must be called first")
        return LedgerDatabase.getInstance(ctx)
    }

    private fun getApiClient(): APIClient {
        customApiClient?.let { return it }
        val ctx = appContext ?: throw IllegalStateException("LedgerRepository.init(context) must be called first")
        return APIClient.getInstance(ctx)
    }

    private fun getSyncPrefs(): android.content.SharedPreferences? {
        return appContext?.getSharedPreferences("looseleaf_ledger_sync_prefs", Context.MODE_PRIVATE)
    }

    private fun getCursorKey(): String {
        val baseUrl = customApiClient?.baseUrl ?: appContext?.let { APIClient.getInstance(it).baseUrl } ?: "default"
        return "cursor_" + baseUrl.hashCode()
    }

    fun getStoredCursor(): String? = getSyncPrefs()?.getString(getCursorKey(), null)

    fun saveCursor(cursor: String) {
        getSyncPrefs()?.edit()?.putString(getCursorKey(), cursor)?.apply()
    }

    fun clearCursor() {
        getSyncPrefs()?.edit()?.remove(getCursorKey())?.apply()
    }

    suspend fun <T> withSyncLock(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }

    var onScheduleSyncListener: (() -> Unit)? = null

    fun scheduleOutboxFlush() {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                pullLedgerData()
            }
        }
        runCatching {
            onScheduleSyncListener?.invoke()
        }
    }

    // ──────────────────────────── 流水查询 Flow ────────────────────────────

    fun transactionsForMonth(range: MonthRange): Flow<List<Transaction>> {
        val db = appContext?.let { LedgerDatabase.getInstance(it) } ?: return flowOf(emptyList())
        return db.transactionDao().getTransactionsInRangeFlow(range.startIso, range.endIso)
            .catch { emit(emptyList()) }
            .map { entities -> entities.map { it.toDomainModel() }.distinctBy { it.id } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun monthlyTotalsFlow(range: MonthRange): Flow<MonthlyTotalsRow> {
        val db = appContext?.let { LedgerDatabase.getInstance(it) } ?: return flowOf(MonthlyTotalsRow())
        return db.transactionDao().getMonthlyTotalsFlow(range.startIso, range.endIso)
            .catch { emit(MonthlyTotalsRow()) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun monthlyCategoryTotalsFlow(range: MonthRange): Flow<List<CategoryTotalRow>> {
        val db = appContext?.let { LedgerDatabase.getInstance(it) } ?: return flowOf(emptyList())
        return db.transactionDao().getMonthlyCategoryTotalsFlow(range.startIso, range.endIso)
            .catch { emit(emptyList()) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    // ──────────────────────────── 乐观 CUD 操作（原子事务 + 意图合并 + 幂等） ────────────────────────────

    suspend fun createTransactionOptimistic(req: CreateTransactionRequest): Long = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val clientRequestId = req.clientRequestId ?: UUID.randomUUID().toString()
        val enqueuedReq = req.copy(clientRequestId = clientRequestId)
        val nowIso = Instant.now().toString()

        db.withTransaction {
            val txEntity = CachedTransactionEntity(
                amount = enqueuedReq.amount,
                type = enqueuedReq.type,
                category = enqueuedReq.category,
                title = enqueuedReq.title,
                note = enqueuedReq.note,
                paymentMethod = enqueuedReq.paymentMethod,
                occurredAt = enqueuedReq.occurredAt,
                cityCode = enqueuedReq.cityCode,
                wishId = enqueuedReq.wishId,
                footprintId = enqueuedReq.footprintId,
                isInstallment = enqueuedReq.isInstallment ?: false,
                installmentChannelCode = enqueuedReq.installmentChannelCode,
                installmentTotalAmount = enqueuedReq.installmentTotalAmount,
                installmentMonths = enqueuedReq.installmentMonths,
                installmentMonthlyPay = enqueuedReq.installmentMonthlyPay,
                installmentRemainMonths = enqueuedReq.installmentRemainMonths,
                repaymentDay = enqueuedReq.repaymentDay,
                syncStatus = "PENDING_CREATE",
                createdAt = enqueuedReq.occurredAt
            )

            val localId = db.transactionDao().insert(txEntity)
            val sequence = db.outboxDao().getNextSequence()

            db.outboxDao().insert(
                LedgerOutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    action = "CREATE",
                    payload = json.encodeToString(enqueuedReq),
                    localTargetId = localId,
                    serverTargetId = null,
                    clientRequestId = clientRequestId,
                    sequence = sequence,
                    enqueuedAt = nowIso
                )
            )
            localId
        }.also {
            scheduleOutboxFlush()
        }
    }

    suspend fun updateTransactionOptimistic(
        txId: Long,
        req: CreateTransactionRequest
    ): Boolean = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val nowIso = Instant.now().toString()

        db.withTransaction {
            val entity = if (txId < 0) {
                db.transactionDao().getTransactionByLocalId(-txId)
            } else {
                db.transactionDao().getTransactionByServerId(txId)
                    ?: db.transactionDao().getTransactionByLocalId(txId)
            } ?: return@withTransaction false

            val existingActiveEvent = db.outboxDao().findActiveEventByLocalTarget(entity.localId)
            val isLocalPendingCreate = entity.serverId == null || existingActiveEvent?.action == "CREATE"

            val updatedEntity = entity.copy(
                amount = req.amount,
                type = req.type,
                category = req.category,
                title = req.title,
                note = req.note,
                paymentMethod = req.paymentMethod,
                occurredAt = req.occurredAt,
                cityCode = if (req.clearCity) null else req.cityCode,
                wishId = req.wishId ?: entity.wishId,
                footprintId = req.footprintId ?: entity.footprintId,
                isInstallment = if (req.clearInstallment) {
                    false
                } else {
                    req.isInstallment ?: entity.isInstallment
                },
                installmentChannelCode = if (req.clearInstallment) null else (req.installmentChannelCode ?: entity.installmentChannelCode),
                installmentTotalAmount = if (req.clearInstallment) null else (req.installmentTotalAmount ?: entity.installmentTotalAmount),
                installmentMonths = if (req.clearInstallment) null else (req.installmentMonths ?: entity.installmentMonths),
                installmentMonthlyPay = if (req.clearInstallment) null else (req.installmentMonthlyPay ?: entity.installmentMonthlyPay),
                installmentRemainMonths = if (req.clearInstallment) {
                    null
                } else {
                    val remain = req.installmentRemainMonths ?: entity.installmentRemainMonths
                    val total = req.installmentMonths ?: entity.installmentMonths
                    if (remain != null && total != null && total > 0) remain.coerceAtMost(total) else remain
                },
                repaymentDay = if (req.clearInstallment) null else (req.repaymentDay ?: entity.repaymentDay),
                syncStatus = if (isLocalPendingCreate) "PENDING_CREATE" else "PENDING_UPDATE"
            )

            db.transactionDao().update(updatedEntity)

            if (isLocalPendingCreate && existingActiveEvent != null && existingActiveEvent.action == "CREATE") {
                // 本地待同步新建条目：直接将 UPDATE 字段合并入现有 CREATE 载荷，保持幂等键不变
                val existingReq = runCatching {
                    json.decodeFromString<CreateTransactionRequest>(existingActiveEvent.payload)
                }.getOrNull()

                val mergedReq = if (existingReq != null) {
                    existingReq.copy(
                        amount = req.amount,
                        type = req.type,
                        category = req.category,
                        title = req.title,
                        note = req.note,
                        paymentMethod = req.paymentMethod,
                        occurredAt = req.occurredAt,
                        cityCode = if (req.clearCity) null else (req.cityCode ?: existingReq.cityCode),
                        wishId = req.wishId ?: existingReq.wishId,
                        footprintId = req.footprintId ?: existingReq.footprintId,
                        isInstallment = if (req.clearInstallment) false else (req.isInstallment ?: existingReq.isInstallment),
                        clearInstallment = req.clearInstallment,
                        installmentChannelCode = if (req.clearInstallment) null else (req.installmentChannelCode ?: existingReq.installmentChannelCode),
                        installmentTotalAmount = if (req.clearInstallment) null else (req.installmentTotalAmount ?: existingReq.installmentTotalAmount),
                        installmentMonths = if (req.clearInstallment) null else (req.installmentMonths ?: existingReq.installmentMonths),
                        installmentMonthlyPay = if (req.clearInstallment) null else (req.installmentMonthlyPay ?: existingReq.installmentMonthlyPay),
                        installmentRemainMonths = if (req.clearInstallment) null else (req.installmentRemainMonths ?: existingReq.installmentRemainMonths),
                        repaymentDay = if (req.clearInstallment) null else (req.repaymentDay ?: existingReq.repaymentDay),
                        clientRequestId = existingActiveEvent.clientRequestId
                    )
                } else {
                    req.copy(clientRequestId = existingActiveEvent.clientRequestId)
                }

                db.outboxDao().updatePayload(existingActiveEvent.eventId, json.encodeToString(mergedReq))
            } else {
                // 已同步上云或有独立 UPDATE 意图：按目标合并或新建 UPDATE 事件
                val existingUpdate = db.outboxDao().findEventByTarget(
                    localTargetId = entity.localId,
                    action = "UPDATE"
                )
                val clientRequestId = existingUpdate?.clientRequestId ?: UUID.randomUUID().toString()
                val updatePayload = req.copy(clientRequestId = clientRequestId)

                if (existingUpdate != null) {
                    db.outboxDao().insert(
                        existingUpdate.copy(
                            payload = json.encodeToString(updatePayload),
                            serverTargetId = entity.serverId,
                            enqueuedAt = nowIso
                        )
                    )
                } else {
                    val sequence = db.outboxDao().getNextSequence()
                    db.outboxDao().insert(
                        LedgerOutboxEventEntity(
                            eventId = UUID.randomUUID().toString(),
                            action = "UPDATE",
                            payload = json.encodeToString(updatePayload),
                            localTargetId = entity.localId,
                            serverTargetId = entity.serverId,
                            clientRequestId = clientRequestId,
                            sequence = sequence,
                            enqueuedAt = nowIso
                        )
                    )
                }
            }
            true
        }.also { success ->
            if (success) scheduleOutboxFlush()
        }
    }

    suspend fun deleteTransaction(idOrLocalId: Long): LedgerDeleteUndoTicket? = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val nowIso = Instant.now().toString()

        db.withTransaction {
            val entity = if (idOrLocalId < 0) {
                db.transactionDao().getTransactionByLocalId(-idOrLocalId)
            } else {
                db.transactionDao().getTransactionByServerId(idOrLocalId)
                    ?: db.transactionDao().getTransactionByLocalId(idOrLocalId)
            } ?: return@withTransaction null

            val existingActiveEvent = db.outboxDao().findActiveEventByLocalTarget(entity.localId)
            val isLocalOnly = entity.serverId == null || existingActiveEvent?.action == "CREATE"

            val pendingUpdate = db.outboxDao().findEventByTarget(entity.localId, "UPDATE")
            val pendingUpdatePayload = pendingUpdate?.payload

            if (!isLocalOnly && entity.serverId > 0) {
                // 清除同实体的未发 UPDATE，避免乱序更新覆盖删除
                db.outboxDao().deleteByLocalTarget(entity.localId)

                val clientRequestId = UUID.randomUUID().toString()
                val sequence = db.outboxDao().getNextSequence()
                db.outboxDao().insert(
                    LedgerOutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        action = "DELETE",
                        payload = "{}",
                        localTargetId = entity.localId,
                        serverTargetId = entity.serverId,
                        clientRequestId = clientRequestId,
                        sequence = sequence,
                        enqueuedAt = nowIso
                    )
                )
                // 标记待删除，列表不再展示；在宽限期内可直接撤销还原
                db.transactionDao().markPendingDelete(entity.localId)
            } else {
                db.outboxDao().deleteByLocalTarget(entity.localId)
                db.transactionDao().deleteByLocalId(entity.localId)
            }

            // 联动本地大盘流动资金
            if (entity.type == "expense" && !entity.isInstallment) {
                _snapshot.update { s ->
                    s.copy(assetsOverview = s.assetsOverview?.let { ov ->
                        ov.copy(liquidBalance = ov.liquidBalance + entity.amount)
                    })
                }
            } else if (entity.type == "income") {
                _snapshot.update { s ->
                    s.copy(assetsOverview = s.assetsOverview?.let { ov ->
                        ov.copy(liquidBalance = ov.liquidBalance - entity.amount)
                    })
                }
            }

            LedgerDeleteUndoTicket(entity = entity, pendingUpdatePayload = pendingUpdatePayload)
        }.also { ticket ->
            if (ticket != null) scheduleOutboxFlush()
        }
    }

    suspend fun undoDeleteTransaction(ticket: LedgerDeleteUndoTicket): Boolean = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val entity = ticket.entity
        val serverId = entity.serverId
        val nowIso = Instant.now().toString()

        db.withTransaction {
            if (serverId != null && serverId > 0) {
                val pendingDelete = db.outboxDao().findEventByTarget(entity.localId, "DELETE")
                if (pendingDelete != null) {
                    // DELETE 事件仍在发件箱中：撤销事件
                    db.outboxDao().deleteByEventId(pendingDelete.eventId)
                    if (ticket.pendingUpdatePayload != null) {
                        // P1-1: 撤销删除时保留未同步编辑，恢复为 PENDING_UPDATE 并重新入队 UPDATE 事件
                        db.transactionDao().update(entity.copy(syncStatus = "PENDING_UPDATE"))
                        val clientRequestId = UUID.randomUUID().toString()
                        val sequence = db.outboxDao().getNextSequence()
                        db.outboxDao().insert(
                            LedgerOutboxEventEntity(
                                eventId = UUID.randomUUID().toString(),
                                action = "UPDATE",
                                payload = ticket.pendingUpdatePayload,
                                localTargetId = entity.localId,
                                serverTargetId = serverId,
                                clientRequestId = clientRequestId,
                                sequence = sequence,
                                enqueuedAt = nowIso
                            )
                        )
                    } else {
                        db.transactionDao().update(entity.copy(syncStatus = "SYNCED"))
                    }
                } else {
                    // 远端已物理删除，需以 PENDING_CREATE 重新建账
                    val newLocalId = db.transactionDao().insert(
                        entity.copy(localId = 0, serverId = null, syncStatus = "PENDING_CREATE")
                    )
                    val clientRequestId = UUID.randomUUID().toString()
                    val sequence = db.outboxDao().getNextSequence()
                    val parsedUpdateReq = ticket.pendingUpdatePayload?.let {
                        runCatching { json.decodeFromString<CreateTransactionRequest>(it) }.getOrNull()
                    }
                    val createReq = parsedUpdateReq?.copy(clientRequestId = clientRequestId) ?: CreateTransactionRequest(
                        amount = entity.amount,
                        type = entity.type,
                        category = entity.category,
                        title = entity.title,
                        note = entity.note,
                        paymentMethod = entity.paymentMethod,
                        occurredAt = entity.occurredAt,
                        cityCode = entity.cityCode,
                        wishId = entity.wishId,
                        footprintId = entity.footprintId,
                        isInstallment = entity.isInstallment,
                        installmentChannelCode = entity.installmentChannelCode,
                        installmentTotalAmount = entity.installmentTotalAmount,
                        installmentMonths = entity.installmentMonths,
                        installmentMonthlyPay = entity.installmentMonthlyPay,
                        repaymentDay = entity.repaymentDay,
                        clientRequestId = clientRequestId
                    )
                    db.outboxDao().insert(
                        LedgerOutboxEventEntity(
                            eventId = UUID.randomUUID().toString(),
                            action = "CREATE",
                            payload = json.encodeToString(createReq),
                            localTargetId = newLocalId,
                            serverTargetId = null,
                            clientRequestId = clientRequestId,
                            sequence = sequence,
                            enqueuedAt = nowIso
                        )
                    )
                }
            } else {
                // 本地新增条目撤销删除：重新插回本地并入队 CREATE
                val newLocalId = db.transactionDao().insert(
                    entity.copy(localId = 0, serverId = null, syncStatus = "PENDING_CREATE")
                )
                val clientRequestId = UUID.randomUUID().toString()
                val sequence = db.outboxDao().getNextSequence()
                val parsedUpdateReq = ticket.pendingUpdatePayload?.let {
                    runCatching { json.decodeFromString<CreateTransactionRequest>(it) }.getOrNull()
                }
                val createReq = parsedUpdateReq?.copy(clientRequestId = clientRequestId) ?: CreateTransactionRequest(
                    amount = entity.amount,
                    type = entity.type,
                    category = entity.category,
                    title = entity.title,
                    note = entity.note,
                    paymentMethod = entity.paymentMethod,
                    occurredAt = entity.occurredAt,
                    cityCode = entity.cityCode,
                    wishId = entity.wishId,
                    footprintId = entity.footprintId,
                    isInstallment = entity.isInstallment,
                    installmentChannelCode = entity.installmentChannelCode,
                    installmentTotalAmount = entity.installmentTotalAmount,
                    installmentMonths = entity.installmentMonths,
                    installmentMonthlyPay = entity.installmentMonthlyPay,
                    repaymentDay = entity.repaymentDay,
                    clientRequestId = clientRequestId
                )
                db.outboxDao().insert(
                    LedgerOutboxEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        action = "CREATE",
                        payload = json.encodeToString(createReq),
                        localTargetId = newLocalId,
                        serverTargetId = null,
                        clientRequestId = clientRequestId,
                        sequence = sequence,
                        enqueuedAt = nowIso
                    )
                )
            }

            // 联动扣回流动资金
            if (entity.type == "expense" && !entity.isInstallment) {
                _snapshot.update { s ->
                    s.copy(assetsOverview = s.assetsOverview?.let { ov ->
                        ov.copy(liquidBalance = ov.liquidBalance - entity.amount)
                    })
                }
            } else if (entity.type == "income") {
                _snapshot.update { s ->
                    s.copy(assetsOverview = s.assetsOverview?.let { ov ->
                        ov.copy(liquidBalance = ov.liquidBalance + entity.amount)
                    })
                }
            }

            true
        }.also { success ->
            if (success) scheduleOutboxFlush()
        }
    }

    // ──────────────────────────── 接口并行拉取、增量游标与数据同步 ────────────────────────────

    suspend fun pullLedgerData(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            runCatching {
                val api = getApiClient()
                // 1. 刷新基础配置与大盘（P1-10：非强制刷新时遵循 30 秒缓存 TTL，避免切 Tab / 频繁打开引发 6-12 请求风暴）
                val now = System.currentTimeMillis()
                if (force || (now - lastMetadataRefreshTime) > METADATA_TTL_MS) {
                    refreshAll(api, force = true)
                    lastMetadataRefreshTime = now
                }

                val db = appContext?.let { LedgerDatabase.getInstance(it) } ?: return@runCatching

                // 2. 推送待发事件
                val hasPushed = flushOutbox(db, api)
                if (hasPushed) {
                    refreshAll(api, force = true)
                    lastMetadataRefreshTime = System.currentTimeMillis()
                }

                // 3. 增量游标拉取 (P0-1 & P0-2)
                val storedCursor = getStoredCursor()
                if (storedCursor.isNullOrBlank()) {
                    // 首次全量拉取：分页遍历直至完全同步
                    var offset = 0
                    val batchLimit = 200
                    var pages = 0
                    val maxPages = 100
                    var maxCursorCandidate: String? = null

                    while (pages < maxPages) {
                        val meta = ResponseMeta()
                        val batch = api.fetchTransactions(
                            limit = batchLimit,
                            offset = if (offset > 0) offset else null,
                            includeDeleted = true,
                            meta = meta
                        )
                        pages++
                        if (batch.isEmpty()) break

                        mergeTransactions(db, batch)

                        val serverCursor = meta.syncCursor
                        val batchMax = maxOfOrNull(
                            serverCursor,
                            batch.mapNotNull { it.updatedAt ?: it.deletedAt ?: it.createdAt }.maxOrNull()
                        )
                        if (batchMax != null && (maxCursorCandidate == null || batchMax > maxCursorCandidate)) {
                            maxCursorCandidate = batchMax
                        }

                        if (batch.size < batchLimit) break
                        offset += batch.size
                    }

                    if (pages >= maxPages && offset > 0) {
                        throw IllegalStateException("全量同步超过最大页数限制 ($maxPages)，终止游标推进以避免数据不完整")
                    }

                    if (!maxCursorCandidate.isNullOrBlank()) {
                        saveCursor(maxCursorCandidate)
                    }
                } else {
                    // 增量拉取 (P0-2)：分页遍历直至拉完全部变更，防止超过 500 条静默漏拉
                    var offset = 0
                    val batchLimit = 500
                    var pages = 0
                    val maxPages = 50
                    var maxCursorCandidate: String? = null

                    while (pages < maxPages) {
                        val meta = ResponseMeta()
                        val batch = api.fetchTransactions(
                            since = storedCursor,
                            includeDeleted = true,
                            limit = batchLimit,
                            offset = if (offset > 0) offset else null,
                            meta = meta
                        )
                        pages++
                        if (batch.isEmpty()) break

                        mergeTransactions(db, batch)

                        val serverCursor = meta.syncCursor
                        val batchMax = maxOfOrNull(
                            serverCursor,
                            batch.mapNotNull { it.updatedAt ?: it.deletedAt ?: it.createdAt }.maxOrNull()
                        )
                        if (batchMax != null && (maxCursorCandidate == null || batchMax > maxCursorCandidate)) {
                            maxCursorCandidate = batchMax
                        }

                        if (batch.size < batchLimit) break
                        offset += batch.size
                    }

                    if (pages >= maxPages) {
                        throw IllegalStateException("增量同步超过最大页数限制 ($maxPages)，终止游标推进以避免漏拉")
                    }

                    if (!maxCursorCandidate.isNullOrBlank() && maxCursorCandidate > storedCursor) {
                        saveCursor(maxCursorCandidate)
                    }
                }

                // 4. 清理超过 7 天的发件箱死信（与主库策略对齐）
                val sevenDaysAgo = Instant.now().minus(java.time.Duration.ofDays(7)).toString()
                db.outboxDao().cleanExpiredFailedEvents(sevenDaysAgo)
            }
        }
    }

    private fun maxOfOrNull(a: String?, b: String?): String? {
        if (a == null) return b
        if (b == null) return a
        return if (a >= b) a else b
    }

    /**
     * 权威 UPSERT 合并（P0-2, P1-4, P2-11）：
     * 1. 远端 Tombstone（软删除）：仅在本地无待同步修改时执行删除；
     * 2. 远端活跃行：若本地已标记为待删除 (PENDING_DELETE)，绝对不可被远端复活；
     * 3. 本地待同步新建 (PENDING_CREATE) 行通过 client_request_id 与远端对齐，原地绑定 serverId 并清理 CREATE 事件；
     * 4. 本地待同步更新优先，否则原地 UPSERT 避免重新生成 localId。
     */
    internal suspend fun mergeTransactions(db: LedgerDatabase, remote: List<Transaction>) {
        if (remote.isEmpty()) return

        val activeRemote = remote.filter { it.deletedAt.isNullOrBlank() }.distinctBy { it.id }
        val softDeleted = remote.filter { !it.deletedAt.isNullOrBlank() }.distinctBy { it.id }

        val localIndex = db.transactionDao().getTransactionSyncIndex()
            .mapNotNull { it.serverId?.let { sid -> sid to it } }
            .toMap()

        // P1-4: 建立本地 PENDING_CREATE 的 client_request_id 第二索引
        val activeCreateEvents = db.outboxDao().getActiveCreateEvents()
        val pendingCreateByCrid = activeCreateEvents.associateBy { it.clientRequestId }

        db.withTransaction {
            // 1. 处理服务端 Tombstone 软删除
            for (del in softDeleted) {
                val existing = localIndex[del.id]
                if (existing != null && existing.syncStatus != "SYNCED") {
                    // 本地有待同步修改或本地已处于 PENDING_DELETE，不打乱本地状态
                    continue
                }
                db.transactionDao().deleteByServerId(del.id)
            }

            // 2. 原地 UPSERT 活跃条目
            val toUpsert = mutableListOf<CachedTransactionEntity>()
            for (item in activeRemote) {
                val existing = localIndex[item.id]
                if (existing != null) {
                    if (existing.syncStatus == "PENDING_DELETE") {
                        // P2-11: 本地已标记待删除，严禁被远端拉取复活
                        continue
                    }
                    if (existing.syncStatus != "SYNCED") {
                        // 本地待同步行优先，避免远端旧状态覆盖
                        continue
                    }
                    toUpsert += item.toCacheEntity(localId = existing.localId)
                    continue
                }

                // P1-4: 若 server_id 未命中，检查 client_request_id 是否命中本地 PENDING_CREATE
                val crid = item.clientRequestId
                val matchedCreate = if (!crid.isNullOrBlank()) pendingCreateByCrid[crid] else null
                if (matchedCreate != null) {
                    toUpsert += item.toCacheEntity(localId = matchedCreate.localTargetId)
                    db.outboxDao().deleteByEventId(matchedCreate.eventId)
                    db.outboxDao().bindServerId(matchedCreate.localTargetId, item.id)
                    continue
                }

                // 远端全新记录
                toUpsert += item.toCacheEntity(localId = 0)
            }
            if (toUpsert.isNotEmpty()) {
                db.transactionDao().insertAll(toUpsert)
            }
        }
    }

    /**
     * 排空发件箱（P0-1, P0-4, P0-6, P1-2）：
     * 严格按入队序列 (sequence ASC) 发送；
     * 写入完成在 Room 事务内重新比对事件载荷（Check-Then-Act），杜绝同步期间编辑静默丢弃；
     * 对网络与 5xx 故障实施退避重试，4xx 错误进入死信。
     */
    internal suspend fun flushOutbox(db: LedgerDatabase, api: APIClient): Boolean {
        val events = db.outboxDao().getAllPendingEvents()
        var hasPushed = false
        val now = Instant.now()

        for (event in events) {
            try {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                        val created = api.createTransaction(req)
                        db.withTransaction {
                            val latest = db.outboxDao().findActiveEventById(event.eventId)
                            if (latest == null) {
                                // 飞行期间事件已被取消或移除，仍绑定 serverId 到本地行防孤立
                                val localEntity = db.transactionDao().getTransactionByLocalId(event.localTargetId)
                                if (localEntity != null && localEntity.syncStatus == "PENDING_DELETE") {
                                    db.transactionDao().bindServerId(event.localTargetId, created.id)
                                    db.outboxDao().bindServerId(event.localTargetId, created.id)
                                }
                                return@withTransaction
                            }
                            if (latest.payload != event.payload) {
                                // P0-1 竞态保护：飞行期间用户再次编辑了该条目
                                // 服务端已按旧载荷与 client_request_id 创建了记录 (created.id)
                                // 绑定返回的 serverId 到本地行，保持本地状态为 PENDING_UPDATE
                                db.transactionDao().bindServerId(event.localTargetId, created.id)
                                db.transactionDao().updateSyncStatus(event.localTargetId, "PENDING_UPDATE")
                                db.outboxDao().bindServerId(event.localTargetId, created.id)
                                // 将原 CREATE 事件删除，替换为一条新的 UPDATE 事件，载荷为最新编辑内容
                                db.outboxDao().deleteByEventId(event.eventId)
                                val nextSeq = db.outboxDao().getNextSequence()
                                db.outboxDao().insert(
                                    LedgerOutboxEventEntity(
                                        eventId = UUID.randomUUID().toString(),
                                        action = "UPDATE",
                                        payload = latest.payload,
                                        localTargetId = event.localTargetId,
                                        serverTargetId = created.id,
                                        clientRequestId = UUID.randomUUID().toString(),
                                        sequence = nextSeq,
                                        enqueuedAt = Instant.now().toString()
                                    )
                                )
                            } else {
                                db.transactionDao().markSynced(event.localTargetId, created.id, created.updatedAt)
                                db.outboxDao().bindServerId(event.localTargetId, created.id)
                                db.outboxDao().deleteByEventId(event.eventId)
                            }
                        }
                        hasPushed = true
                    }
                    "UPDATE" -> {
                        var serverId = event.serverTargetId
                        if (serverId == null || serverId <= 0) {
                            serverId = db.transactionDao().getTransactionByLocalId(event.localTargetId)?.serverId
                        }
                        if (serverId != null && serverId > 0) {
                            val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                            val updated = try {
                                api.updateTransaction(serverId, req)
                            } catch (e: Exception) {
                                val apiEx = e as? APIException
                                val msg = e.message ?: ""
                                if (apiEx?.code == 404 || msg.contains("404") || msg.contains("不存在")) {
                                    // P1-2: 远端该记录已不存在，清理本地行与事件避免永久卡死
                                    db.withTransaction {
                                        db.transactionDao().deleteByLocalId(event.localTargetId)
                                        db.outboxDao().deleteByEventId(event.eventId)
                                    }
                                    hasPushed = true
                                    continue
                                }
                                throw e
                            }
                            db.withTransaction {
                                val latest = db.outboxDao().findActiveEventById(event.eventId)
                                if (latest == null) {
                                    return@withTransaction
                                }
                                if (latest.payload != event.payload) {
                                    // P0-1 竞态保护：飞行期间用户再次编辑了该条目
                                    // 不 markSynced、不删事件，下一轮循环将重发最新载荷
                                    return@withTransaction
                                }
                                db.transactionDao().markSynced(event.localTargetId, serverId, updated.updatedAt)
                                db.outboxDao().deleteByEventId(event.eventId)
                            }
                            hasPushed = true
                        } else {
                            // 仍在等待 CREATE 事件完成绑定，跳过本次处理
                            continue
                        }
                    }
                    "DELETE" -> {
                        var serverId = event.serverTargetId
                        if (serverId == null || serverId <= 0) {
                            serverId = db.transactionDao().getTransactionByLocalId(event.localTargetId)?.serverId
                        }
                        if (serverId != null && serverId > 0) {
                            try {
                                api.deleteTransaction(serverId)
                            } catch (e: Exception) {
                                val msg = e.message ?: ""
                                if (msg.contains("404") || msg.contains("不存在") || msg.contains("已被删除")) {
                                    // 远端已删除或不存在，视为成功
                                } else {
                                    throw e
                                }
                            }
                            db.withTransaction {
                                db.transactionDao().deleteByServerId(serverId)
                                db.outboxDao().deleteByEventId(event.eventId)
                            }
                            hasPushed = true
                        } else {
                            db.withTransaction {
                                db.transactionDao().deleteByLocalId(event.localTargetId)
                                db.outboxDao().deleteByEventId(event.eventId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val apiEx = e as? APIException
                val code = apiEx?.code ?: -1
                val errorMsg = e.message ?: "Unknown sync error"

                when {
                    code == 401 -> {
                        // 鉴权失败：暂停本次队列处理，等待重配对
                        break
                    }
                    code in 400..499 && code != 408 && code != 429 -> {
                        // 客户端业务/参数致命错误：记录死信
                        db.outboxDao().markFailed(event.eventId, now.toString(), errorMsg)
                    }
                    else -> {
                        // 网络抖动、5xx 服务端错误或 429 限流：执行指数退避重试，不记入死信
                        val backoffSeconds = calculateExponentialBackoff(event.retryCount)
                        val nextRetryTime = now.plusSeconds(backoffSeconds).toString()
                        db.outboxDao().scheduleRetry(event.eventId, nextRetryTime, errorMsg)
                    }
                }
            }
        }
        return hasPushed
    }

    private fun calculateExponentialBackoff(retryCount: Int): Long {
        val baseSeconds = 5L
        val multiplier = 1L shl minOf(retryCount, 6)
        return minOf(baseSeconds * multiplier, 900L)
    }

    suspend fun refreshAll(
        apiClient: APIClient,
        force: Boolean = false,
        showSpinner: Boolean = false
    ) = refreshAll(
        fetchCategories = { apiClient.fetchCategories() },
        fetchCreditChannels = { apiClient.fetchCreditChannels() },
        fetchAssetsOverview = { apiClient.fetchAssetsOverview() },
        fetchInstallments = { apiClient.fetchInstallmentPlans() },
        fetchSummary = { apiClient.fetchInstallmentSummary() },
        fetchUpcoming = { apiClient.fetchUpcomingInstallmentItems() },
        force = force,
        showSpinner = showSpinner
    )

    internal suspend fun refreshAll(
        fetchCategories: suspend () -> List<CategoryOption>,
        fetchCreditChannels: suspend () -> List<CreditChannel>,
        fetchAssetsOverview: suspend () -> AssetsOverview,
        fetchInstallments: (suspend () -> List<InstallmentPlanItemSummary>)? = null,
        fetchSummary: (suspend () -> InstallmentSummary)? = null,
        fetchUpcoming: (suspend () -> List<UpcomingInstallmentItem>)? = null,
        force: Boolean = false,
        showSpinner: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (initialized && !force) {
            if (_snapshot.value.assetsOverview == null) refreshAssets(fetchAssetsOverview, fetchInstallments, fetchSummary, fetchUpcoming, showSpinner)
            return@withContext
        }

        if (showSpinner || _snapshot.value.assetsOverview == null) {
            _snapshot.update { it.copy(isLoadingAssets = true) }
        }

        coroutineScope {
            val categoriesDeferred = async { runCatching { fetchCategories() } }
            val channelsDeferred = async { runCatching { fetchCreditChannels() } }
            val assetsDeferred = async { runCatching { fetchAssetsOverview() } }
            val installmentsDeferred = async { if (fetchInstallments != null) runCatching { fetchInstallments() } else null }
            val summaryDeferred = async { if (fetchSummary != null) runCatching { fetchSummary() } else null }
            val upcomingDeferred = async { if (fetchUpcoming != null) runCatching { fetchUpcoming() } else null }

            val categoriesResult = categoriesDeferred.await()
            categoriesResult.getOrNull()?.let { allCats ->
                val expense = allCats.filter { it.scope == "expense" }.map { it.name }
                val income = allCats.filter { it.scope == "income" }.map { it.name }
                val payments = allCats.filter { it.scope == "payment" }.map { it.name }
                _snapshot.update {
                    it.copy(
                        expenseCategories = expense.ifEmpty { DefaultExpenseCategories },
                        incomeCategories = income.ifEmpty { DefaultIncomeCategories },
                        paymentMethods = payments.ifEmpty { DefaultPaymentMethods }
                    )
                }
            }

            val channelsResult = channelsDeferred.await()
            channelsResult.getOrNull()?.let { channels ->
                _snapshot.update { it.copy(creditChannels = channels) }
            }

            val assetsResult = assetsDeferred.await()
            assetsResult.getOrNull()?.let { overview ->
                _snapshot.update { it.copy(assetsOverview = overview) }
            }

            val plans = installmentsDeferred.await()?.getOrNull() ?: _snapshot.value.installmentPlans
            val summary = summaryDeferred.await()?.getOrNull() ?: _snapshot.value.installmentSummary
            val upcoming = upcomingDeferred.await()?.getOrNull() ?: _snapshot.value.upcomingInstallmentItems
            _snapshot.update {
                it.copy(
                    installmentPlans = plans,
                    installmentSummary = summary,
                    upcomingInstallmentItems = upcoming,
                    isLoadingAssets = false
                )
            }

            if (categoriesResult.isSuccess) initialized = true

            if (categoriesResult.isFailure && channelsResult.isFailure && assetsResult.isFailure) {
                throw categoriesResult.exceptionOrNull()
                    ?: channelsResult.exceptionOrNull()
                    ?: assetsResult.exceptionOrNull()
                    ?: IllegalStateException("账本数据拉取失败")
            }
        }
    }

    suspend fun refreshAssets(apiClient: APIClient, showSpinner: Boolean = false) =
        refreshAssets(
            fetchAssetsOverview = { apiClient.fetchAssetsOverview() },
            fetchInstallments = { apiClient.fetchInstallmentPlans() },
            fetchSummary = { apiClient.fetchInstallmentSummary() },
            fetchUpcoming = { apiClient.fetchUpcomingInstallmentItems() },
            showSpinner = showSpinner
        )

    internal suspend fun refreshAssets(
        fetchAssetsOverview: suspend () -> AssetsOverview,
        fetchInstallments: (suspend () -> List<InstallmentPlanItemSummary>)? = null,
        fetchSummary: (suspend () -> InstallmentSummary)? = null,
        fetchUpcoming: (suspend () -> List<UpcomingInstallmentItem>)? = null,
        showSpinner: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (showSpinner || _snapshot.value.assetsOverview == null) {
            _snapshot.update { it.copy(isLoadingAssets = true) }
        }
        val overview = runCatching { fetchAssetsOverview() }.getOrNull()
        val plans = if (fetchInstallments != null) runCatching { fetchInstallments() }.getOrDefault(emptyList()) else _snapshot.value.installmentPlans
        val summary = if (fetchSummary != null) runCatching { fetchSummary() }.getOrNull() else _snapshot.value.installmentSummary
        val upcoming = if (fetchUpcoming != null) runCatching { fetchUpcoming() }.getOrDefault(emptyList()) else _snapshot.value.upcomingInstallmentItems

        _snapshot.update {
            it.copy(
                assetsOverview = overview ?: it.assetsOverview,
                installmentPlans = plans,
                installmentSummary = summary,
                upcomingInstallmentItems = upcoming,
                isLoadingAssets = false
            )
        }
    }

    fun clear() {
        initialized = false
        _snapshot.value = LedgerSnapshot()
    }
}
