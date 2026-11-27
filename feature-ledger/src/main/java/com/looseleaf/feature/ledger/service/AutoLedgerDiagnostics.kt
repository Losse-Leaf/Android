package com.looseleaf.feature.ledger.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 自动记账本机实时诊断记录（纯本机环形缓冲，不上传云端，自动脱敏连续 4 位以上数字）。
 * 用于在「自动记账」页面直观确认微信/支付宝/银行事件是否被捕获、合并或过滤。
 */
@Serializable
data class AutoLedgerDiagnosticEvent(
    val timestampText: String,
    val source: String,
    val outcome: String,
    val detail: String,
    val isRecorded: Boolean
)

object AutoLedgerDiagnostics {

    private const val PREFS_NAME = "looseleaf_ledger_diagnostics"
    private const val PREFS_KEY = "recent_events_v1"
    private const val MAX_EVENTS = 20
    private val beijingZone = ZoneId.of("Asia/Shanghai")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val digitsRegex = Regex("""\d{4,}""")
    private val json = Json { ignoreUnknownKeys = true }

    private val lock = Any()
    private val buffer = ArrayDeque<AutoLedgerDiagnosticEvent>(MAX_EVENTS)
    private val _events = MutableStateFlow<List<AutoLedgerDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<AutoLedgerDiagnosticEvent>> = _events.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var loadedFromPrefs = false

    fun attachContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun ensureLoaded(context: Context? = null) {
        val ctx = (context ?: appContext)?.applicationContext ?: return
        appContext = ctx
        synchronized(lock) {
            ensureLoadedLocked(ctx)
        }
    }

    fun record(
        source: String,
        outcome: String,
        rawDetail: String,
        isRecorded: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null
    ) {
        context?.applicationContext?.let { appContext = it }
        val timeStr = Instant.ofEpochMilli(nowMs)
            .atZone(beijingZone)
            .format(timeFormatter)
        val safeDetail = rawDetail
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")
            .replace(digitsRegex, "****")
            .trim()
            .take(96)
        val entry = AutoLedgerDiagnosticEvent(
            timestampText = timeStr,
            source = source,
            outcome = outcome,
            detail = safeDetail,
            isRecorded = isRecorded
        )
        synchronized(lock) {
            ensureLoadedLocked(appContext)
            buffer.addFirst(entry)
            while (buffer.size > MAX_EVENTS) {
                buffer.removeLast()
            }
            val list = buffer.toList()
            _events.value = list
            saveLocked(appContext, list)
        }
    }

    fun clear(context: Context? = null) {
        context?.applicationContext?.let { appContext = it }
        synchronized(lock) {
            buffer.clear()
            loadedFromPrefs = true
            _events.value = emptyList()
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(PREFS_KEY)
                ?.apply()
        }
    }

    private fun ensureLoadedLocked(context: Context?) {
        if (context == null || loadedFromPrefs) return
        loadedFromPrefs = true
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
        if (raw.isNullOrBlank()) return
        val restored = runCatching { json.decodeFromString<List<AutoLedgerDiagnosticEvent>>(raw) }
            .getOrDefault(emptyList())
            .take(MAX_EVENTS)
        if (restored.isNotEmpty() && buffer.isEmpty()) {
            buffer.addAll(restored)
            _events.value = buffer.toList()
        }
    }

    private fun saveLocked(context: Context?, events: List<AutoLedgerDiagnosticEvent>) {
        if (context == null) return
        val encoded = runCatching { json.encodeToString(events) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, encoded)
            .apply()
    }
}
