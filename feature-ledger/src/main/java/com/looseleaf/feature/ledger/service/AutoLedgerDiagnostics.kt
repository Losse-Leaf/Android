package com.looseleaf.feature.ledger.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 自动记账本机实时诊断记录（纯内存环形缓冲，不上传云端，自动脱敏连续 4 位以上数字）。
 * 用于在「自动记账」页面直观确认微信/支付宝/银行事件是否被捕获、合并或过滤。
 */
data class AutoLedgerDiagnosticEvent(
    val timestampText: String,
    val source: String,
    val outcome: String,
    val detail: String,
    val isRecorded: Boolean
)

object AutoLedgerDiagnostics {

    private const val MAX_EVENTS = 20
    private val beijingZone = ZoneId.of("Asia/Shanghai")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val digitsRegex = Regex("""\d{4,}""")

    private val lock = Any()
    private val buffer = ArrayDeque<AutoLedgerDiagnosticEvent>(MAX_EVENTS)
    private val _events = MutableStateFlow<List<AutoLedgerDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<AutoLedgerDiagnosticEvent>> = _events.asStateFlow()

    fun record(
        source: String,
        outcome: String,
        rawDetail: String,
        isRecorded: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
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
            buffer.addFirst(entry)
            while (buffer.size > MAX_EVENTS) {
                buffer.removeLast()
            }
            _events.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _events.value = emptyList()
        }
    }
}
