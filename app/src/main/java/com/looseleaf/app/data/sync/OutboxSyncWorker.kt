package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.sync.withLock

/**
 * 离线优先发件箱后台同步工作器 (OutboxSyncWorker)
 * 严格遵从 docs/mobile.md §3.3 与 docs/ecosystem.md §7 离线优先规范，
 * 支持 entry (CREATE / UPDATE / DELETE)、media_entry (离线相纸上传与归档) 与 transaction
 *
 * 失败策略：
 * - 永久 4xx / 重试超限 → 事件进入 FAILED 态并保留，**不再静默删除**；
 * - 401 → 整个队列暂停，等待用户重新配对；
 * - 其余（网络、5xx、429）→ 退避重试。
 */
class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "OutboxSyncWorker"
        const val WORK_NAME = "com.looseleaf.outbox_sync"
        const val MAX_RETRY = 10
    }

    override suspend fun doWork(): Result {
        val syncManager = SyncManager.getInstance(applicationContext)
        return syncManager.outboxMutex.withLock {
            val database = AppDatabase.getInstance(applicationContext)
            val outboxDao = database.outboxDao()

            val pendingEvents = outboxDao.getAllPendingEvents()
            if (pendingEvents.isEmpty()) {
                return@withLock Result.success()
            }

            Log.d(TAG, "Starting outbox flush for ${pendingEvents.size} pending events")

            for (event in pendingEvents) {
                try {
                    syncManager.processOutboxEvent(event)
                } catch (e: Exception) {
                    val code = (e as? APIException)?.code
                    Log.w(TAG, "Failed syncing event ${event.eventId} (${event.entityType}/${event.action}): ${e.message}")

                    if (code == 401) {
                        // 凭据失效：继续跑只会让后续事件全部失败
                        return@withLock Result.failure()
                    }

                    val isPermanentClientError = code != null && code in 400..499 && code != 429
                    val exhausted = event.retryCount + 1 >= MAX_RETRY

                    if (isPermanentClientError || exhausted) {
                        outboxDao.markFailed(
                            event.eventId,
                            FormatUtils.beijingNowIsoString(),
                            buildString {
                                if (code != null) append("[$code] ")
                                append(e.message ?: "同步失败")
                                if (exhausted && !isPermanentClientError) append("（已重试 $MAX_RETRY 次）")
                            }
                        )
                        Log.e(TAG, "Event ${event.eventId} marked FAILED, user action required")
                        // 不中断后续事件
                    } else {
                        outboxDao.incrementRetry(event.eventId)
                        return@withLock Result.retry()
                    }
                }
            }

            Result.success()
        }
    }
}
