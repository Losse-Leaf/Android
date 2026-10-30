package com.looseleaf.feature.ledger.data

import android.content.Context
import com.looseleaf.app.data.api.APIClient
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
    val entity: CachedTransactionEntity
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

    // ──────────────────────────── 乐观 CUD 操作 ────────────────────────────

    suspend fun createTransactionOptimistic(req: CreateTransactionRequest): Long = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val txEntity = CachedTransactionEntity(
            amount = req.amount,
            type = req.type,
            category = req.category,
            title = req.title,
            note = req.note,
            paymentMethod = req.paymentMethod,
            occurredAt = req.occurredAt,
            cityCode = req.cityCode,
            wishId = req.wishId,
            footprintId = req.footprintId,
            isInstallment = req.isInstallment ?: false,
            installmentChannelCode = req.installmentChannelCode,
            installmentTotalAmount = req.installmentTotalAmount,
            installmentMonths = req.installmentMonths,
            installmentMonthlyPay = req.installmentMonthlyPay,
            installmentRemainMonths = req.installmentRemainMonths,
            repaymentDay = req.repaymentDay,
            syncStatus = "PENDING_CREATE"
        )

        val localId = db.transactionDao().insert(txEntity)
        db.outboxDao().insert(
            LedgerOutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                action = "CREATE",
                payload = json.encodeToString(req),
                localTargetId = localId,
                serverTargetId = null,
                createdAt = req.occurredAt
            )
        )
        localId
    }

    suspend fun updateTransactionOptimistic(
        txId: Long,
        req: CreateTransactionRequest
    ): Boolean = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val entity = if (txId < 0) {
            db.transactionDao().getTransactionByLocalId(-txId)
        } else {
            db.transactionDao().getTransactionByServerId(txId)
                ?: db.transactionDao().getTransactionByLocalId(txId)
        } ?: return@withContext false

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
            syncStatus = newSyncStatus
        )

        db.transactionDao().update(updatedEntity)

        val updatePayload = req.copy(clientRequestId = null)
        val existingUpdate = db.outboxDao().findEventByTarget(
            localTargetId = entity.localId,
            action = "UPDATE"
        )
        if (existingUpdate != null) {
            db.outboxDao().insert(
                existingUpdate.copy(
                    payload = json.encodeToString(updatePayload),
                    serverTargetId = entity.serverId
                )
            )
        } else {
            db.outboxDao().insert(
                LedgerOutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    action = "UPDATE",
                    payload = json.encodeToString(updatePayload),
                    localTargetId = entity.localId,
                    serverTargetId = entity.serverId,
                    createdAt = req.occurredAt
                )
            )
        }
        true
    }

    suspend fun deleteTransaction(idOrLocalId: Long): LedgerDeleteUndoTicket? = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val entity = if (idOrLocalId < 0) {
            db.transactionDao().getTransactionByLocalId(-idOrLocalId)
        } else {
            db.transactionDao().getTransactionByServerId(idOrLocalId)
                ?: db.transactionDao().getTransactionByLocalId(idOrLocalId)
        } ?: return@withContext null

        if (entity.serverId != null && entity.serverId > 0) {
            db.outboxDao().insert(
                LedgerOutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    action = "DELETE",
                    payload = "{}",
                    localTargetId = entity.localId,
                    serverTargetId = entity.serverId,
                    createdAt = entity.occurredAt
                )
            )
            db.transactionDao().deleteByServerId(entity.serverId)
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

        LedgerDeleteUndoTicket(entity = entity)
    }

    suspend fun undoDeleteTransaction(ticket: LedgerDeleteUndoTicket): Boolean = withContext(Dispatchers.IO) {
        val db = requireDatabase()
        val entity = ticket.entity
        val serverId = entity.serverId

        val wasPendingDelete = if (serverId != null && serverId > 0) {
            val deleteEvents = db.outboxDao().getAllPendingEvents()
                .filter { it.action == "DELETE" && it.serverTargetId == serverId }
            if (deleteEvents.isNotEmpty()) {
                deleteEvents.forEach { db.outboxDao().deleteByEventId(it.eventId) }
                true
            } else {
                false
            }
        } else {
            true
        }

        if (!wasPendingDelete && serverId != null && serverId > 0) {
            // 远端已物理删除，需重新作为待同步新增条目提交建账
            val newLocalId = db.transactionDao().insert(
                entity.copy(localId = 0, serverId = null, syncStatus = "PENDING_CREATE")
            )
            val createReq = CreateTransactionRequest(
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
                repaymentDay = entity.repaymentDay
            )
            db.outboxDao().insert(
                LedgerOutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    action = "CREATE",
                    payload = json.encodeToString(createReq),
                    localTargetId = newLocalId,
                    serverTargetId = null,
                    createdAt = entity.occurredAt
                )
            )
        } else {
            // 重新插回本地表
            db.transactionDao().insert(entity.copy(localId = 0))
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
    }

    // ──────────────────────────── 接口并行拉取与数据同步 ────────────────────────────

    suspend fun pullLedgerData(): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            runCatching {
                val api = getApiClient()
                // 1. 刷新基础配置与大盘
                refreshAll(api, force = true)

                val db = appContext?.let { LedgerDatabase.getInstance(it) }
                if (db != null) {
                    // 2. 推送待发事件
                    val hasPushed = flushOutbox(db, api)
                    if (hasPushed) {
                        refreshAll(api, force = true)
                    }

                    // 3. 拉取远端交易记录增量
                    val remoteTx = api.fetchTransactions(limit = 200)
                    if (remoteTx.isNotEmpty()) {
                        val entities = remoteTx.map { it.toCacheEntity() }
                        db.transactionDao().insertAll(entities)
                        db.transactionDao().deleteDuplicateServerIds()
                    }
                }
            }
        }
    }

    private suspend fun flushOutbox(db: LedgerDatabase, api: APIClient): Boolean {
        val events = db.outboxDao().getAllPendingEvents()
        var hasPushed = false
        for (event in events) {
            try {
                when (event.action) {
                    "CREATE" -> {
                        val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                        val created = api.createTransaction(req)
                        db.transactionDao().markSynced(event.localTargetId, created.id)
                        db.outboxDao().deleteByEventId(event.eventId)
                        hasPushed = true
                    }
                    "UPDATE" -> {
                        val serverId = event.serverTargetId
                        if (serverId != null && serverId > 0) {
                            val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                            api.updateTransaction(serverId, req)
                            db.outboxDao().deleteByEventId(event.eventId)
                            hasPushed = true
                        } else {
                            db.outboxDao().deleteByEventId(event.eventId)
                        }
                    }
                    "DELETE" -> {
                        val serverId = event.serverTargetId
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
                            hasPushed = true
                        }
                        db.outboxDao().deleteByEventId(event.eventId)
                    }
                }
            } catch (e: Exception) {
                db.outboxDao().recordFailure(event.eventId, e.message ?: "Unknown sync error")
            }
        }
        return hasPushed
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
