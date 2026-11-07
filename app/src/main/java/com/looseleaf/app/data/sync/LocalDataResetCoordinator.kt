package com.looseleaf.app.data.sync

import android.content.Context
import androidx.work.WorkManager
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.ProfileRepository
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.reminder.InstallmentReminderWorker
import com.looseleaf.feature.ledger.data.LedgerDatabase
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ResetReason {
    UNPAIR,
    SWITCH_HUB,
    USER_MANUAL_RESET
}

/**
 * 本地数据统一重置协调器 (P0-8)
 *
 * 保证解绑或切换数据中枢时：
 * 1. 同步锁保护，防止在途拉取将旧中枢数据写回；
 * 2. 彻底抹除主库、独立账本库、所有待发与死信事件、缓存媒体、表单草稿与会话门禁；
 * 3. 统计解绑警告时，真实合并主库与账本库的未同步记录数。
 */
object LocalDataResetCoordinator {

    suspend fun getUnsyncedCount(context: Context): Int = withContext(Dispatchers.IO) {
        val mainCount = runCatching { AppDatabase.getInstance(context).outboxDao().countAllEvents() }.getOrDefault(0)
        val ledgerDb = runCatching { LedgerDatabase.getInstance(context) }.getOrNull()
        val ledgerPending = ledgerDb?.outboxDao()?.countPendingEvents() ?: 0
        val ledgerFailed = ledgerDb?.outboxDao()?.countFailedEvents() ?: 0
        mainCount + ledgerPending + ledgerFailed
    }

    suspend fun resetAll(
        context: Context,
        syncManager: SyncManager,
        reason: ResetReason
    ) = withContext(Dispatchers.IO) {
        // 1. 中止后台在途同步任务并重置提醒开关
        runCatching {
            syncManager.cancelScheduledSync()
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(OutboxSyncWorker.WORK_NAME)
            wm.cancelUniqueWork(OutboxSyncWorker.GRACE_WORK_NAME)
            InstallmentReminderWorker.cancelWork(context)
            CredentialStore.getInstance(context).isInstallmentReminderEnabled = false
        }

        // 2. 获取账本同步互斥锁，确保无在途 pullLedgerData 竞争
        LedgerRepository.withSyncLock {
            // 3. 清理主数据库（生活档案、造物、心愿、足迹、主库 outbox、媒体暂存）
            syncManager.clearAllLocalData()

            // 4. 清理独立账本数据库 (looseleaf_ledger.db) 全部表与游标
            runCatching {
                val ledgerDb = LedgerDatabase.getInstance(context)
                ledgerDb.clearAllTables()
            }
            LedgerRepository.clearCursor()
            LedgerRepository.clear()

            // 5. 清理主理人档案与快照
            ProfileRepository.clearCache(context)

            // 6. 清理本地暂存相纸、缓存缩略图与下载目录
            runCatching {
                File(context.filesDir, "outbox_media").deleteRecursively()
                File(context.cacheDir, "image_cache").deleteRecursively()
                context.getExternalFilesDir(null)?.let { dir ->
                    File(dir, "Download").deleteRecursively()
                }
            }

            // 7. 清除两份表单草稿
            runCatching {
                context.getSharedPreferences("looseleaf_form_drafts", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("quick_note_draft", Context.MODE_PRIVATE).edit().clear().apply()
            }

            // 8. 若为解绑场景，彻底清除 KeyStore 凭据
            if (reason == ResetReason.UNPAIR) {
                CredentialStore.getInstance(context).clearCredentials()
            }

            // 9. 重置同一进程内的首拉门禁
            SyncManager.resetSessionPulls()
        }
    }
}
