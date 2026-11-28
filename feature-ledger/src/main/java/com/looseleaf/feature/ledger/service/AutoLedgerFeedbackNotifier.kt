package com.looseleaf.feature.ledger.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.looseleaf.app.utils.CategorizationConfidence
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.core.R as CoreR

/**
 * 静默自动记账反馈通知器 (AutoLedgerFeedbackNotifier)
 *
 * 自动记账落库后发出一条无声通知，展示「金额 + 当前分类 + 分类依据」，
 * 并附带 3 个一键改分类快捷按钮；用户点一下即可改分类并永久记住商户。
 */
internal object AutoLedgerFeedbackNotifier {

    private const val CHANNEL_ID = "ledger_auto_recorded"
    private const val CHANNEL_NAME = "自动记账入账提醒"
    private const val NOTIFICATION_BASE_ID = 4200

    private val COMMON_EXPENSE_CANDIDATES = listOf(
        "餐饮", "交通", "购物", "居家", "订阅", "娱乐", "医疗", "学习"
    )
    private val COMMON_INCOME_CANDIDATES = listOf(
        "工资", "副业", "理财", "退款"
    )

    fun notifyAutoRecorded(
        context: Context,
        localId: Long,
        bill: ParsedAutoLedgerBill,
        availableCategories: List<String>
    ) {
        if (localId <= 0L) return
        if (!AutoLedgerCategoryMemory.isFeedbackNotificationEnabled(context)) return
        if (!hasPostNotificationPermission(context)) return

        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(nm)

            val notifId = notificationIdFor(localId)
            val amountStr = FormatUtils.centsToYuanString(bill.request.amount, includeSymbol = true)
            val isUnclassified = bill.categoryConfidence == CategorizationConfidence.LOW &&
                (bill.request.category == "其他" || bill.request.category == "其他收入")
            val catBadge = if (isUnclassified) "待归类" else bill.request.category
            val title = "已自动记账 · $amountStr [$catBadge]"

            val body = listOfNotNull(
                bill.request.title.takeIf { it.isNotBlank() },
                bill.request.paymentMethod?.takeIf { it.isNotBlank() },
                bill.categoryReason.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_auto_record)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setTimeoutAfter(45_000L)

            createLaunchPendingIntent(context, notifId)?.let { builder.setContentIntent(it) }

            val quickTargets = pickQuickCategories(
                type = bill.request.type,
                currentCategory = bill.request.category,
                availableCategories = availableCategories
            )
            quickTargets.forEachIndexed { idx, targetCat ->
                val actionPi = createReclassifyPendingIntent(
                    context = context,
                    localId = localId,
                    notifId = notifId,
                    targetCategory = targetCat,
                    slotIndex = idx
                )
                builder.addAction(0, targetCat, actionPi)
            }

            nm.notify(notifId, builder.build())
        }
    }

    fun notifyReclassified(
        context: Context,
        notifId: Int,
        titleText: String,
        newCategory: String,
        learnedMerchant: String?
    ) {
        if (!hasPostNotificationPermission(context)) return
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(nm)
            val msg = if (!learnedMerchant.isNullOrBlank()) {
                "已归类为「$newCategory」并记住「$learnedMerchant」"
            } else {
                "「$titleText」已改归类为「$newCategory」"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_auto_record)
                .setContentTitle("分类已更新")
                .setContentText(msg)
                .setAutoCancel(true)
                .setSilent(true)
                .setTimeoutAfter(4_000L)
            createLaunchPendingIntent(context, notifId)?.let { builder.setContentIntent(it) }
            nm.notify(notifId, builder.build())
        }
    }

    internal fun pickQuickCategories(
        type: String,
        currentCategory: String,
        availableCategories: List<String>
    ): List<String> {
        val excluded = setOf(currentCategory, "好物购置", "利息手续费", "其他", "其他收入")
        val pool = availableCategories.map { it.trim() }.filter { it.isNotEmpty() && it !in excluded }.distinct()
        val preferred = if (type == "income") COMMON_INCOME_CANDIDATES else COMMON_EXPENSE_CANDIDATES
        val ordered = LinkedHashSet<String>()
        for (p in preferred) {
            if (p in pool) ordered.add(p)
        }
        for (c in pool) {
            ordered.add(c)
        }
        return ordered.take(3)
    }

    private fun notificationIdFor(localId: Long): Int =
        NOTIFICATION_BASE_ID + ((localId % 900L).toInt().coerceAtLeast(0))

    private fun hasPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "自动记账入账后提供一键改分类快捷提醒"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun createLaunchPendingIntent(context: Context, requestCode: Int): PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, requestCode, launchIntent, flags)
    }

    private fun createReclassifyPendingIntent(
        context: Context,
        localId: Long,
        notifId: Int,
        targetCategory: String,
        slotIndex: Int
    ): PendingIntent {
        val intent = Intent(context, AutoLedgerQuickActionReceiver::class.java).apply {
            action = AutoLedgerQuickActionReceiver.ACTION_RECLASSIFY
            putExtra(AutoLedgerQuickActionReceiver.EXTRA_LOCAL_ID, localId)
            putExtra(AutoLedgerQuickActionReceiver.EXTRA_TARGET_CATEGORY, targetCategory)
            putExtra(AutoLedgerQuickActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val reqCode = notifId * 10 + slotIndex
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }
}
