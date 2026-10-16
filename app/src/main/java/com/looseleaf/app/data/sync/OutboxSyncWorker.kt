package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.AppDatabase
import kotlinx.coroutines.sync.withLock

/**
 * 离线优先发件箱后台同步工作器 (OutboxSyncWorker)
 * 严格遵从 docs/mobile.md §3.3 与 docs/ecosystem.md §7 离线优先规范，
 * 支持 entry (CREATE / UPDATE / DELETE)、media_entry (离线相纸上传与归档) 与 transaction
 */
class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "OutboxSyncWorker"
        const val WORK_NAME = "com.looseleaf.outbox_sync"
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
                    Log.w(TAG, "Failed syncing event ${event.eventId} (${event.entityType}/${event.action}): ${e.message}")
                    if (e is APIException && e.code == 401) {
                        return@withLock Result.failure()
                    }
                    val isPermanentClientError = e is APIException && e.code in 400..499 && e.code != 401 && e.code != 429
                    val newRetryCount = event.retryCount + 1
                    outboxDao.incrementRetry(event.eventId)

                    if (isPermanentClientError || newRetryCount >= 10) {
                        Log.e(TAG, "Discarding poisoned or exhausted event ${event.eventId} (reason: ${if (isPermanentClientError) "permanent client error ${(e as APIException).code}" else "max retries exceeded"})")
                        outboxDao.delete(event)
                        // 不中断后续其他正常事件的同步
                    } else {
                        // 遇到可恢复的临时网络波动或服务端5xx，等待退避重试
                        return@withLock Result.retry()
                    }
                }
            }

            Result.success()
        }
    }
}
