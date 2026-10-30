package com.looseleaf.app.data.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce

/** AND-3：同步期间的写库抖动窗口（一次全量同步逐页写入时可合并成一次刷新） */
const val REVISION_DEBOUNCE_MS = 400L

/**
 * AND-3：把一串密集的变更信号合并成「静默窗口后的一次末值」。
 *
 * - `debounce`：同步逐页写库时不让 UI 每页重载一次；
 * - `conflate`：消费端处理期间只保留最新值，绝不排长队；
 * - 末值一定会发射：同步结束后最多 400ms，界面会拿到**最后一次**变更。
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.coalesceBursts(
    debounceMs: Long = REVISION_DEBOUNCE_MS
): Flow<T> = debounce(debounceMs).conflate()
