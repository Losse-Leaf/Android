package com.looseleaf.app.data.local

import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.AssetsOverview
import com.looseleaf.app.model.CreditChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) = withContext(Dispatchers.IO) {
        if (initialized && !force) {
            // 数据已经在内存里：只在从未加载过资产时补一次
            if (_snapshot.value.assetsOverview == null) refreshAssets(apiClient, showSpinner)
            return@withContext
        }

        var categoriesLoadedNow = false
        runCatching {
            val allCats = apiClient.fetchCategories()
            val expense = allCats.filter { it.scope == "expense" }.map { it.name }
            val income = allCats.filter { it.scope == "income" }.map { it.name }
            val payments = allCats.filter { it.scope == "payment" }.map { it.name }
            _snapshot.value = _snapshot.value.copy(
                expenseCategories = expense.ifEmpty { DefaultExpenseCategories },
                incomeCategories = income.ifEmpty { DefaultIncomeCategories },
                paymentMethods = payments.ifEmpty { DefaultPaymentMethods }
            )
            categoriesLoadedNow = true
        }

        runCatching {
            _snapshot.value = _snapshot.value.copy(creditChannels = apiClient.fetchCreditChannels())
        }

        refreshAssets(apiClient, showSpinner)
        // 只有分类真的取到了才标记完成，否则下次进入账本仍会重试（离线启动时不至于永远用默认值）
        if (categoriesLoadedNow) initialized = true
    }

    suspend fun refreshAssets(apiClient: APIClient, showSpinner: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (showSpinner || _snapshot.value.assetsOverview == null) {
                _snapshot.value = _snapshot.value.copy(isLoadingAssets = true)
            }
            runCatching { apiClient.fetchAssetsOverview() }
                .onSuccess { overview ->
                    _snapshot.value = _snapshot.value.copy(assetsOverview = overview, isLoadingAssets = false)
                }
                .onFailure {
                    _snapshot.value = _snapshot.value.copy(isLoadingAssets = false)
                }
        }

    /** 解绑设备时清空，避免下一个账号看到上一个中枢的资产 */
    fun clear() {
        initialized = false
        _snapshot.value = LedgerSnapshot()
    }
}
