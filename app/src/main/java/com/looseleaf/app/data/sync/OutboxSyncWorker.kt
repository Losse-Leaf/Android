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
        /** MOB-P1-51：单次运行最多排空几轮（防止异常情况下无限循环） */
        const val MAX_DRAIN_ROUNDS = 5

        /** MOB-P1-49：失败事件的保留天数（超过即清理） */
        const val DEAD_LETTER_RETENTION_DAYS = 7L
    }

    override suspend fun doWork(): Result {
        val syncManager = SyncManager.getInstance(applicationContext)
        return syncManager.outboxMutex.withLock {
            val database = AppDatabase.getInstance(applicationContext)
            val outboxDao = database.outboxDao()

            // MOB-P1-49：每轮排空前先清理 7 天前的死信，避免发件箱无限膨胀
            runCatching {
                val cutoff = java.time.LocalDateTime.now(FormatUtils.BeijingZone)
                    .minusDays(DEAD_LETTER_RETENTION_DAYS)
                    .let { FormatUtils.toBeijingIsoString(it) }
                val purged = outboxDao.purgeFailedEventsBefore(cutoff)
                if (purged > 0) Log.i(TAG, "Purged $purged dead-letter outbox events older than $DEAD_LETTER_RETENTION_DAYS days")
            }

            // MOB-P1-51 配套：排空循环。
            // WorkManager 的 KEEP 策略意味着「运行期间新写入的事件」不会触发新任务，
            // 因此这里循环处理，直到发件箱真的空了为止（最多 MAX_DRAIN_ROUNDS 轮，
            // 防止异常情况下无限循环）。
            var anySucceeded = false
            var anyFailed = false
            var round = 0
            // 本次运行已经尝试过的事件：失败的事件**不在同一轮里被反复重试**
            // （否则一次 WorkManager 运行就会把 retry_count 烧光、提前判死），
            // 它们要等下一次运行（带指数退避）。只有「运行期间新写入」的事件才会被继续处理。
            val attemptedEventIds = mutableSetOf<String>()

            while (true) {
            val pendingEvents = outboxDao.getAllPendingEvents()
                .filter { it.eventId !in attemptedEventIds }
                // MOB-P2-47：宽限期内的删除事件**先不上传** ——
                // 删除刚发生时发件箱 Worker 可能正好被别的事件唤醒，
                // 那样 DELETE 会立刻送到服务端，纸签上的「撤销」就永远赶不上。
                .filterNot { SyncManager.isDeleteWithinGracePeriod(it) }
            if (pendingEvents.isEmpty()) {
                if (outboxDao.getAllPendingEvents().isEmpty()) {
                    return@withLock Result.success()
                }
                // 剩下的都是本轮已试过的事件 → 留给下一次运行（带退避）
                break
            }
            attemptedEventIds += pendingEvents.map { it.eventId }

            Log.d(TAG, "Starting outbox flush for ${pendingEvents.size} pending events (round ${round + 1})")
            round++

            // MOB-P1-52：原先「单条失败即中止整队」——
            // 第 1 条事件遇到一次网络抖动，第 2..N 条全部被推迟，
            // 而 BackoffPolicy.EXPONENTIAL 从 30s 起跳（30→60→120…最长数小时）。
            // 用户明明有 9 张照片要传，却因第 1 条失败而全部延后。
            // 现在改为「逐条处理、失败不中断」，只有当**全部**事件都失败时才整体重试。
            for (event in pendingEvents) {
                try {
                    syncManager.processOutboxEvent(event)
                    anySucceeded = true
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
                        anyFailed = true
                        // MOB-P1-52：不再直接 return，继续处理后面的事件
                    }
                }
            }

            // 本轮跑完后如果发件箱还有**新写入**的事件，继续下一轮排空
            if (round >= MAX_DRAIN_ROUNDS) {
                Log.w(TAG, "Drain rounds limit reached, remaining events will be picked up by the next run")
                break
            }
            }

            // 只要还有**可重试**的失败事件就整体退避重试（WorkManager 会按指数退避再跑一轮）。
            // 下一轮里已成功的事件已从发件箱删除，只会重试剩下失败的那些；
            // 永久失败（4xx / 重试耗尽）的事件已进入 FAILED 态，不会再拖住队列。
            if (anyFailed) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
