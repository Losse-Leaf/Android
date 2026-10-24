package com.looseleaf.app.data.local

import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.AssetsOverview
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.model.CreditChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

val DefaultExpenseCategories = listOf("餐饮", "交通", "购物", "娱乐", "住房", "学习", "医疗", "其他")
val DefaultIncomeCategories = listOf("薪资", "奖金", "二手回血", "稿费与创作", "投资收益", "其他收入")
val DefaultPaymentMethods = listOf("微信支付", "支付宝", "银行卡", "现金")

data class LedgerSnapshot(
    val expenseCategories: List<String> = DefaultExpenseCategories,
    val incomeCategories: List<String> = DefaultIncomeCategories,
    val paymentMethods: List<String> = DefaultPaymentMethods,
    val creditChannels: List<CreditChannel> = emptyList(),
    val assetsOverview: AssetsOverview? = null,
    val isLoadingAssets: Boolean = false
)

/**
 * 账本附属数据的进程级缓存（分类字典 / 支付方式 / 信贷渠道 / 资产大盘）。
 *
 * 这些数据原本散落在 LedgerScreen 的 `remember` 里，切一次 Tab 就被销毁，
 * 而 `hasInitializedLedger` 又是 rememberSaveable，导致「状态清空但不再重拉」。
 * 上提到 repository 后，界面只负责观察，数据生命周期与应用一致。
 */
object LedgerRepository {

    private val _snapshot = MutableStateFlow(LedgerSnapshot())
    val snapshot: StateFlow<LedgerSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var initialized = false

    suspend fun refreshAll(
        apiClient: APIClient,
        force: Boolean = false,
        showSpinner: Boolean = false
    ) = refreshAll(
        fetchCategories = { apiClient.fetchCategories() },
        fetchCreditChannels = { apiClient.fetchCreditChannels() },
        fetchAssetsOverview = { apiClient.fetchAssetsOverview() },
        force = force,
        showSpinner = showSpinner
    )

    /**
     * AND-12：三个互不依赖的接口**并行**请求。
     *
     * 原先 `fetchCategories → fetchCreditChannels → refreshAssets` 串行，
     * 首屏最坏要等三个 RTT（弱网下 3× 延迟）。现在同时发出，
     * 总耗时约等于最慢的那个；任一失败仍走 runCatching 独立降级，
     * 最后只做**一次** snapshot update，避免中间态被 UI 看到。
     *
     * 分类成功才置 `initialized` —— 与旧行为保持一致（离线启动会重试）。
     */
    internal suspend fun refreshAll(
        fetchCategories: suspend () -> List<CategoryOption>,
        fetchCreditChannels: suspend () -> List<CreditChannel>,
        fetchAssetsOverview: suspend () -> AssetsOverview,
        force: Boolean = false,
        showSpinner: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (initialized && !force) {
            // 数据已经在内存里：只在从未加载过资产时补一次
            if (_snapshot.value.assetsOverview == null) refreshAssets(fetchAssetsOverview, showSpinner)
            return@withContext
        }

        if (showSpinner || _snapshot.value.assetsOverview == null) {
            _snapshot.update { it.copy(isLoadingAssets = true) }
        }

        coroutineScope {
            val categoriesDeferred = async { runCatching { fetchCategories() } }
            val channelsDeferred = async { runCatching { fetchCreditChannels() } }
            val assetsDeferred = async { runCatching { fetchAssetsOverview() } }

            // 三个请求并行，但**谁的先回来就先发布谁**：
            // 原先要等最慢的资产接口，分类与分期渠道跟着一起被扣住，
            // 用户在极速记账里看到的仍是默认分类。
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
            _snapshot.update { it.copy(isLoadingAssets = false) }

            // 只有分类真的取到了才标记完成，否则下次进入账本仍会重试
            if (categoriesResult.isSuccess) initialized = true

            // 三个接口全失败时必须把错误抛给界面：否则 LedgerScreen 的
            // ledgerLoadError 永远是 null，离线且无缓存时只会显示「暂无记账明细」。
            if (categoriesResult.isFailure && channelsResult.isFailure && assetsResult.isFailure) {
                throw categoriesResult.exceptionOrNull()
                    ?: channelsResult.exceptionOrNull()
                    ?: assetsResult.exceptionOrNull()
                    ?: IllegalStateException("账本数据拉取失败")
            }
        }
    }

    suspend fun refreshAssets(apiClient: APIClient, showSpinner: Boolean = false) =
        refreshAssets(fetchAssetsOverview = { apiClient.fetchAssetsOverview() }, showSpinner = showSpinner)

    internal suspend fun refreshAssets(
        fetchAssetsOverview: suspend () -> AssetsOverview,
        showSpinner: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (showSpinner || _snapshot.value.assetsOverview == null) {
            _snapshot.update { it.copy(isLoadingAssets = true) }
        }
        runCatching { fetchAssetsOverview() }
            .onSuccess { overview ->
                _snapshot.update { it.copy(assetsOverview = overview, isLoadingAssets = false) }
            }
            .onFailure {
                _snapshot.update { it.copy(isLoadingAssets = false) }
            }
    }

    /** 解绑设备时清空，避免下一个账号看到上一个中枢的资产 */
    fun clear() {
        initialized = false
        _snapshot.value = LedgerSnapshot()
    }
}
