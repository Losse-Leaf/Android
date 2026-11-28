package com.looseleaf.feature.ledger.service

import android.content.Context
import com.looseleaf.app.model.CategoryRule
import com.looseleaf.app.utils.TransactionCategorizer
import com.looseleaf.feature.ledger.data.CachedTransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LearnedCategoryRule(
    val txType: String,
    val matchKey: String,
    val displayName: String,
    val category: String,
    val hitCount: Int = 1,
    val source: String = "learned",
    val serverId: Long? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Serializable
private data class MemoryStoreState(
    val expenseRules: Map<String, LearnedCategoryRule> = emptyMap(),
    val incomeRules: Map<String, LearnedCategoryRule> = emptyMap(),
    val warmupDone: Boolean = false
)

/**
 * 本地持久化商户分类记忆与通知反馈开关 (AutoLedgerCategoryMemory)
 *
 * - 当用户在手机端修改自动记账分类或点击通知栏快捷改类按钮时，立即记住 `商户名 -> 分类`；
 * - 支持与后端 `/api/v1/category-rules` 双向合并，实现多端记忆共享；
 * - 首次冷启动记忆为空时，仅从非自动记账历史流水中预热高频手动规则，绝不吸收老版自动记账脏数据。
 */
internal object AutoLedgerCategoryMemory {

    private const val PREFS_NAME = "looseleaf_ledger_category_memory_v1"
    private const val KEY_STATE = "memory_state"
    private const val KEY_FEEDBACK_NOTIF_ENABLED = "feedback_notif_enabled"
    private const val MAX_RULES_PER_TYPE = 300

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var loadedFromPrefs = false
    private val expenseRules = LinkedHashMap<String, LearnedCategoryRule>()
    private val incomeRules = LinkedHashMap<String, LearnedCategoryRule>()
    private var warmupDone = false

    private val _rulesFlow = MutableStateFlow<List<LearnedCategoryRule>>(emptyList())
    val rulesFlow: StateFlow<List<LearnedCategoryRule>> = _rulesFlow.asStateFlow()

    fun lookupMap(context: Context?, type: String): Map<String, String> {
        synchronized(lock) {
            ensureLoadedLocked(context)
            val target = if (type == "income") incomeRules else expenseRules
            return target.mapValues { it.value.category }
        }
    }

    fun getAllRules(context: Context?): List<LearnedCategoryRule> {
        synchronized(lock) {
            ensureLoadedLocked(context)
            return snapshotSortedLocked()
        }
    }

    fun recordLocalLearn(
        context: Context?,
        type: String,
        titleOrMerchant: String,
        category: String,
        nowMs: Long = System.currentTimeMillis()
    ): LearnedCategoryRule? {
        val normType = if (type == "income") "income" else "expense"
        val cleanCat = category.trim()
        if (cleanCat.isEmpty() || cleanCat == "其他" || cleanCat == "其他收入") return null
        val (matchKey, displayName) = TransactionCategorizer.normalizeMerchantKey(titleOrMerchant) ?: return null
        if (matchKey.equals(cleanCat, ignoreCase = true)) return null

        synchronized(lock) {
            ensureLoadedLocked(context)
            val map = if (normType == "income") incomeRules else expenseRules
            val existing = map[matchKey]
            val updated = LearnedCategoryRule(
                txType = normType,
                matchKey = matchKey,
                displayName = displayName,
                category = cleanCat,
                hitCount = (existing?.hitCount ?: 0) + 1,
                source = "learned",
                serverId = existing?.serverId,
                updatedAtMs = nowMs
            )
            map[matchKey] = updated
            trimCapLocked(map)
            saveAndEmitLocked(context)
            return updated
        }
    }

    fun mergeRemoteRules(context: Context?, remoteRules: List<CategoryRule>) {
        if (remoteRules.isEmpty()) return
        synchronized(lock) {
            ensureLoadedLocked(context)
            for (r in remoteRules) {
                val normType = if (r.txType == "income") "income" else "expense"
                val key = r.matchKey.trim().lowercase()
                if (key.isEmpty() || r.category.isBlank()) continue
                val map = if (normType == "income") incomeRules else expenseRules
                val local = map[key]
                val remoteMillis = parseRemoteMillis(r.updatedAt)
                if (local == null || remoteMillis >= local.updatedAtMs || local.serverId == null) {
                    map[key] = LearnedCategoryRule(
                        txType = normType,
                        matchKey = key,
                        displayName = r.displayName.ifBlank { key },
                        category = r.category.trim(),
                        hitCount = maxOf(r.hitCount, local?.hitCount ?: 1),
                        source = r.source,
                        serverId = r.id.takeIf { it > 0 } ?: local?.serverId,
                        updatedAtMs = maxOf(remoteMillis, local?.updatedAtMs ?: 0L)
                    )
                }
            }
            trimCapLocked(expenseRules)
            trimCapLocked(incomeRules)
            saveAndEmitLocked(context)
        }
    }

    fun removeRule(context: Context?, type: String, matchKey: String): LearnedCategoryRule? {
        val normType = if (type == "income") "income" else "expense"
        val key = matchKey.trim().lowercase()
        synchronized(lock) {
            ensureLoadedLocked(context)
            val map = if (normType == "income") incomeRules else expenseRules
            val removed = map.remove(key)
            if (removed != null) {
                saveAndEmitLocked(context)
            }
            return removed
        }
    }

    /**
     * 冷启动历史预热：仅在尚未预热且本地规则为空时执行一次。
     * 只吸收「非自动记账来源」的手动/已校验账目，彻底隔离旧版自动记账的误判脏数据。
     */
    fun warmupFromManualHistoryIfEmpty(
        context: Context?,
        transactions: List<CachedTransactionEntity>
    ) {
        if (transactions.isEmpty()) return
        synchronized(lock) {
            ensureLoadedLocked(context)
            if (warmupDone || expenseRules.isNotEmpty() || incomeRules.isNotEmpty()) {
                if (!warmupDone) {
                    warmupDone = true
                    saveAndEmitLocked(context)
                }
                return
            }

            // key = (type, matchKey) -> map<category, count + latestDisplayName>
            val freqByMerchant = LinkedHashMap<Pair<String, String>, MutableMap<String, Int>>()
            val displayByKey = LinkedHashMap<Pair<String, String>, String>()

            for (tx in transactions) {
                if (tx.deletedAt != null) continue
                if (tx.note?.trim()?.startsWith("自动记账 (") == true) continue
                val normType = if (tx.type == "income") "income" else "expense"
                val cat = tx.category.trim()
                if (cat.isEmpty() || cat == "其他" || cat == "其他收入" || cat == "好物购置" || cat == "利息手续费") {
                    continue
                }
                val (matchKey, displayName) = TransactionCategorizer.normalizeMerchantKey(tx.title) ?: continue
                if (matchKey.equals(cat, ignoreCase = true)) continue

                val composite = normType to matchKey
                val catCounts = freqByMerchant.getOrPut(composite) { LinkedHashMap() }
                catCounts[cat] = (catCounts[cat] ?: 0) + 1
                displayByKey.putIfAbsent(composite, displayName)
            }

            val nowMs = System.currentTimeMillis()
            for ((composite, catCounts) in freqByMerchant) {
                val best = catCounts.maxByOrNull { it.value } ?: continue
                val (normType, matchKey) = composite
                val map = if (normType == "income") incomeRules else expenseRules
                map[matchKey] = LearnedCategoryRule(
                    txType = normType,
                    matchKey = matchKey,
                    displayName = displayByKey[composite] ?: matchKey,
                    category = best.key,
                    hitCount = best.value,
                    source = "learned",
                    updatedAtMs = nowMs
                )
            }

            trimCapLocked(expenseRules)
            trimCapLocked(incomeRules)
            warmupDone = true
            saveAndEmitLocked(context)
        }
    }

    fun isFeedbackNotificationEnabled(context: Context?): Boolean {
        val ctx = context ?: return false
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FEEDBACK_NOTIF_ENABLED, true)
    }

    fun setFeedbackNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FEEDBACK_NOTIF_ENABLED, enabled)
            .apply()
    }

    fun clear(context: Context? = null) {
        synchronized(lock) {
            expenseRules.clear()
            incomeRules.clear()
            warmupDone = false
            loadedFromPrefs = false
            _rulesFlow.value = emptyList()
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_STATE)
                ?.apply()
        }
    }

    internal fun resetInMemoryForTest() {
        synchronized(lock) {
            expenseRules.clear()
            incomeRules.clear()
            warmupDone = false
            loadedFromPrefs = false
            _rulesFlow.value = emptyList()
        }
    }

    private fun parseRemoteMillis(updatedAt: String?): Long {
        if (updatedAt.isNullOrBlank()) return 0L
        return runCatching { java.time.Instant.parse(updatedAt).toEpochMilli() }
            .getOrElse {
                runCatching { java.time.OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli() }
                    .getOrDefault(0L)
            }
    }

    private fun snapshotSortedLocked(): List<LearnedCategoryRule> =
        (expenseRules.values + incomeRules.values)
            .sortedWith(compareByDescending<LearnedCategoryRule> { it.updatedAtMs }.thenByDescending { it.hitCount })

    private fun trimCapLocked(map: LinkedHashMap<String, LearnedCategoryRule>) {
        if (map.size <= MAX_RULES_PER_TYPE) return
        val keep = map.entries
            .sortedWith(compareByDescending<Map.Entry<String, LearnedCategoryRule>> { it.value.updatedAtMs }
                .thenByDescending { it.value.hitCount })
            .take(MAX_RULES_PER_TYPE)
        map.clear()
        keep.forEach { map[it.key] = it.value }
    }

    private fun ensureLoadedLocked(context: Context?) {
        if (context == null || loadedFromPrefs) return
        loadedFromPrefs = true
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
        if (raw.isNullOrBlank()) return
        val restored = runCatching { json.decodeFromString<MemoryStoreState>(raw) }.getOrNull() ?: return
        expenseRules.clear()
        expenseRules.putAll(restored.expenseRules)
        incomeRules.clear()
        incomeRules.putAll(restored.incomeRules)
        warmupDone = restored.warmupDone
        _rulesFlow.value = snapshotSortedLocked()
    }

    private fun saveAndEmitLocked(context: Context?) {
        val sorted = snapshotSortedLocked()
        _rulesFlow.value = sorted
        if (context == null) return
        val state = MemoryStoreState(
            expenseRules = expenseRules.toMap(),
            incomeRules = incomeRules.toMap(),
            warmupDone = warmupDone
        )
        val encoded = runCatching { json.encodeToString(state) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, encoded)
            .apply()
    }
}
