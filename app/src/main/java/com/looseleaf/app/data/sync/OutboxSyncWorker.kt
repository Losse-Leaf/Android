package com.looseleaf.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateTransactionRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 离线优先发件箱后台同步工作器 (OutboxSyncWorker)
 * 严格遵从 docs/mobile.md §3.3 与 docs/ecosystem.md §7 离线优先规范
 */
class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "OutboxSyncWorker"
        const val WORK_NAME = "com.looseleaf.outbox_sync"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val apiClient = APIClient.getInstance(applicationContext)
        val outboxDao = database.outboxDao()
        val entryDao = database.entryDao()
        val txDao = database.transactionDao()

        val pendingEvents = outboxDao.getAllPendingEvents()
        if (pendingEvents.isEmpty()) {
            return Result.success()
        }

        Log.d(TAG, "Starting outbox flush for ${pendingEvents.size} pending events")

        for (event in pendingEvents) {
            try {
                when (event.entityType) {
                    "entry" -> {
                        when (event.action) {
                            "CREATE" -> {
                                val req = json.decodeFromString<CreateEntryRequest>(event.payload)
                                val serverEntry = apiClient.createEntry(req)
                                entryDao.markSynced(event.localTargetId, serverEntry.id)
                                outboxDao.delete(event)
                            }
                            "DELETE" -> {
                                val targetServerId = event.serverTargetId
                                if (targetServerId != null && targetServerId > 0) {
                                    apiClient.deleteEntry(targetServerId)
                                }
                                entryDao.deleteByLocalId(event.localTargetId)
                                outboxDao.delete(event)
                            }
                        }
                    }
                    "transaction" -> {
                        when (event.action) {
                            "CREATE" -> {
                                val req = json.decodeFromString<CreateTransactionRequest>(event.payload)
                                val serverTx = apiClient.createTransaction(req)
                                txDao.markSynced(event.localTargetId, serverTx.id)
                                outboxDao.delete(event)
                            }
                            "DELETE" -> {
                                val targetServerId = event.serverTargetId
                                if (targetServerId != null && targetServerId > 0) {
                                    apiClient.deleteTransaction(targetServerId)
                                }
                                txDao.deleteByLocalId(event.localTargetId)
                                outboxDao.delete(event)
                            }
                        }
                    }
                    else -> {
                        outboxDao.delete(event)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing event ${event.eventId}: ${e.message}")
                outboxDao.incrementRetry(event.eventId)
                if (event.retryCount > 10) {
                    // 超过最大重试次数，避免死循环阻塞后续队列
                    outboxDao.delete(event)
                } else {
                    return Result.retry()
                }
            }
        }

        return Result.success()
    }
}
