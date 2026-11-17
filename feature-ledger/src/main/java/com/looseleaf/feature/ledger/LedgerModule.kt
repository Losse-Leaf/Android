package com.looseleaf.feature.ledger

import android.content.Context
import androidx.room.withTransaction
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.feature.ledger.data.LedgerDatabase
import com.looseleaf.feature.ledger.data.LedgerOutboxEventEntity
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class LedgerFailedEvent(
    val eventId: String,
    val action: String,
    val retryCount: Int,
    val lastError: String?,
    val localTargetId: Long
)

/**
 * 账本模块唯一对外暴露的服务门面 (LedgerModule)
 *
 * 密封 LedgerDatabase / LedgerDaos / LedgerRepository 内部细节，
 * 主壳模块 (:app) 仅通过本门面与 LedgerEntry Composable 协作。
 */
object LedgerModule {
    fun init(
        context: Context,
        credentialStore: CredentialStore? = null,
        apiClient: APIClient? = null,
        onScheduleSync: (() -> Unit)? = null
    ) {
        LedgerRepository.init(context, credentialStore, apiClient)
        if (onScheduleSync != null) {
            LedgerRepository.onScheduleSyncListener = onScheduleSync
        }
    }

    suspend fun pendingOutboxCount(): Int = LedgerRepository.pendingOutboxCount()

    suspend fun syncTransactions(force: Boolean = false, onlyIfPending: Boolean = false): Boolean =
        LedgerRepository.syncTransactions(force = force, onlyIfPending = onlyIfPending)

    fun observePendingCount(context: Context): Flow<Int> =
        LedgerDatabase.getInstance(context).outboxDao().getPendingEventCountFlow()

    fun observeFailedCount(context: Context): Flow<Int> =
        LedgerDatabase.getInstance(context).outboxDao().getFailedEventCountFlow()

    suspend fun getFailedEvents(context: Context): List<LedgerFailedEvent> = withContext(Dispatchers.IO) {
        LedgerDatabase.getInstance(context).outboxDao().getFailedEvents().map {
            LedgerFailedEvent(
                eventId = it.eventId,
                action = it.action,
                retryCount = it.retryCount,
                lastError = it.lastError,
                localTargetId = it.localTargetId
            )
        }
    }

    suspend fun retryFailedEvents(context: Context, eventId: String? = null) {
        withContext(Dispatchers.IO) {
            val outboxDao = LedgerDatabase.getInstance(context).outboxDao()
            if (eventId != null) {
                outboxDao.resetEventToPending(eventId)
            } else {
                outboxDao.retryAllFailed()
            }
        }
        LedgerRepository.scheduleOutboxFlush()
    }

    suspend fun discardFailedEvent(context: Context, eventId: String) = withContext(Dispatchers.IO) {
        val db = LedgerDatabase.getInstance(context)
        val evt = db.outboxDao().findEventById(eventId) ?: return@withContext
        db.withTransaction {
            discardSingleLedgerEvent(db, evt)
        }
    }

    suspend fun discardAllFailed(context: Context) = withContext(Dispatchers.IO) {
        val db = LedgerDatabase.getInstance(context)
        val failed = db.outboxDao().getFailedEvents()
        if (failed.isEmpty()) return@withContext
        db.withTransaction {
            for (evt in failed) {
                discardSingleLedgerEvent(db, evt)
            }
        }
    }

    private suspend fun discardSingleLedgerEvent(db: LedgerDatabase, evt: LedgerOutboxEventEntity) {
        db.outboxDao().deleteByEventId(evt.eventId)
        if (evt.action == "CREATE") {
            db.transactionDao().deleteByLocalId(evt.localTargetId)
        } else {
            val tx = db.transactionDao().getTransactionByLocalId(evt.localTargetId)
            if (tx != null) {
                if (tx.serverId == null) {
                    db.transactionDao().deleteByLocalId(evt.localTargetId)
                } else {
                    db.transactionDao().updateSyncStatus(evt.localTargetId, "SYNCED")
                }
            }
        }
    }

    suspend fun getUnsyncedCount(context: Context): Int = withContext(Dispatchers.IO) {
        val outboxDao = runCatching { LedgerDatabase.getInstance(context).outboxDao() }.getOrNull()
            ?: return@withContext 0
        outboxDao.countPendingEvents() + outboxDao.countFailedEvents()
    }

    suspend fun clearAllLocalData(context: Context) = withContext(Dispatchers.IO) {
        LedgerRepository.withSyncLock {
            runCatching {
                LedgerDatabase.getInstance(context).clearAllTables()
            }
            LedgerRepository.clearCursor()
            LedgerRepository.clear()
        }
    }
}
