package com.looseleaf.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.looseleaf.app.MainActivity
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.UpcomingInstallmentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 分期还款到期提醒工作器 (InstallmentReminderWorker)
 * 每日检查一次，或在 App 打开时补检查。
 * 到期前一天与当天上午提醒："明天花呗还款 ¥812（2 笔）" / "今天花呗还款 ¥812（2 笔）"。
 */
class InstallmentReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                checkAndNotify(applicationContext)
                Result.success()
            } catch (e: Exception) {
                Log.w(TAG, "Installment reminder check failed", e)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "InstallmentReminder"
        const val WORK_NAME = "installment_reminder_worker"
        const val CHANNEL_ID = "installment_reminders"

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<InstallmentReminderWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        suspend fun checkAndNotify(context: Context) = withContext(Dispatchers.IO) {
            val credStore = CredentialStore.getInstance(context)
            if (!credStore.isInstallmentReminderEnabled) {
                return@withContext
            }

            // Android 13+ 检查通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext
                }
            }

            try {
                val apiClient = APIClient.getInstance(context)
                val items = apiClient.fetchUpcomingInstallmentItems()
                if (items.isEmpty()) return@withContext

                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val todayStr = today.toString()
                val tomorrowStr = tomorrow.toString()

                // 按到期日筛选待还期次
                val tomorrowItems = items.filter { it.dueDate == tomorrowStr }
                val todayItems = items.filter { it.dueDate == todayStr }

                createNotificationChannel(context)

                notifyGrouped(context, tomorrowItems, isTomorrow = true, today)
                notifyGrouped(context, todayItems, isTomorrow = false, today)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check installment reminders", e)
            }
        }

        private fun notifyGrouped(
            context: Context,
            items: List<UpcomingInstallmentItem>,
            isTomorrow: Boolean,
            today: LocalDate
        ) {
            if (items.isEmpty()) return

            val prefs = context.getSharedPreferences("installment_reminder_cache", Context.MODE_PRIVATE)
            val byChannel = items.groupBy { it.channelName.ifBlank { "信贷渠道" } }

            byChannel.forEach { (channel, channelItems) ->
                val count = channelItems.size
                val totalCents = channelItems.sumOf { it.totalPart }
                val totalYuanStr = if (totalCents % 100 == 0L) {
                    "${totalCents / 100}"
                } else {
                    String.format("%.2f", totalCents / 100.0)
                }

                val dayPrefix = if (isTomorrow) "明天" else "今天"
                val contentText = "${dayPrefix}${channel}还款 ¥$totalYuanStr（$count 笔）"

                val reminderKey = "notified_${today}_${if (isTomorrow) "tomorrow" else "today"}_$channel"
                if (prefs.getBoolean(reminderKey, false)) {
                    return@forEach
                }

                showNotification(context, contentText, channel.hashCode() + if (isTomorrow) 10000 else 20000)
                prefs.edit().putBoolean(reminderKey, true).apply()
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "分期还款提醒"
                val descriptionText = "分期计划到期前一天与当天还款提醒"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun showNotification(context: Context, contentText: String, notificationId: Int) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("shortcut_route", "ledger")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("散页 · 分期还款提醒")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission missing", e)
            }
        }
    }
}
