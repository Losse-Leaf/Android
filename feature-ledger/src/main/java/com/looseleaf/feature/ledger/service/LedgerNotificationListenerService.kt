package com.looseleaf.feature.ledger.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 账本自动记账通知监听服务 (LedgerNotificationListenerService)
 *
 * 协调管线：
 * 1. 系统级通知过滤：忽略分组摘要 (FLAG_GROUP_SUMMARY)、常驻/促销通知与超期重投通知；
 * 2. 白名单解析引擎 ([AutoLedgerParser])：先剥离真实回执括号附注与余额后缀，
 *    严格拦截送红包/优惠券/周期总结/还款提醒/非成交通知，仅对明确渠道白名单模板提取金额与商户；
 * 3. 五层持久化去重与跨平台补全 ([AutoLedgerDeduplicator])：
 *    串行化处理并发到达的多端通知（微信/支付宝 + 银行 App + 银行短信 + 云闪付），
 *    杜绝一笔支付多次记账；当低信息量短信先到、高信息量钱包后到时，原地补全商户与分类。
 */
class LedgerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val appCtx = applicationContext
        val prefs = appCtx.getSharedPreferences("looseleaf_ledger_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_record_enabled", true)) {
            return
        }

        val pkg = sbn.packageName ?: return
        if (!isAllowedPackage(pkg)) return

        val notification = sbn.notification ?: return
        val nowMs = System.currentTimeMillis()
        if (shouldIgnoreNotification(notification, sbn.postTime, nowMs)) return

        val extras = notification.extras ?: return
        val content = extractNotificationContent(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        )
        if (content.isBlank() || isVerificationCodeOrSpam(content)) return

        val sbnKey = sbn.key.orEmpty()
        val notificationTimestampMs = notification.`when`.takeIf { it > 0L } ?: sbn.postTime

        // 通过全局处理互斥锁串行化多平台并发通知（例如微信与招行 App 相隔 50ms 同时到达），
        // 确保先到达者落库并注册跨平台去重槽位后，后到达者才能准确去重或原地补全。
        serviceScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                val snapshot = LedgerRepository.snapshot.value
                processIncomingNotification(
                    context = appCtx,
                    pkg = pkg,
                    sbnKey = sbnKey,
                    notificationTimestampMs = notificationTimestampMs,
                    content = content,
                    expenseCategories = snapshot.expenseCategories,
                    incomeCategories = snapshot.incomeCategories,
                    nowMs = nowMs,
                    onCreate = { req -> LedgerRepository.createTransactionOptimistic(req) },
                    onEnrich = { localId, req -> LedgerRepository.updateTransactionOptimistic(-localId, req) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "自动记账处理失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "LedgerNotification"
        private val processingMutex = Mutex()

        internal const val STALE_NOTIFICATION_MS = 10 * 60 * 1000L

        private val IGNORED_NOTIFICATION_CATEGORIES = setOf(
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_PROMO,
            Notification.CATEGORY_RECOMMENDATION,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_TRANSPORT
        )

        internal suspend fun processIncomingNotification(
            context: Context?,
            pkg: String,
            sbnKey: String,
            notificationTimestampMs: Long,
            content: String,
            expenseCategories: List<String> = emptyList(),
            incomeCategories: List<String> = emptyList(),
            nowMs: Long = System.currentTimeMillis(),
            onCreate: suspend (CreateTransactionRequest) -> Long,
            onEnrich: suspend (Long, CreateTransactionRequest) -> Boolean
        ): AutoLedgerDedupeDecision? = processingMutex.withLock {
            val bill = AutoLedgerParser.parse(
                content = content,
                pkg = pkg,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories
            ) ?: return@withLock null

            val decision = AutoLedgerDeduplicator.evaluate(
                context = context,
                pkg = pkg,
                sbnKey = sbnKey,
                notificationTimestampMs = notificationTimestampMs,
                bill = bill,
                nowMs = nowMs
            )

            when (decision) {
                is AutoLedgerDedupeDecision.RecordNew -> {
                    val localId = onCreate(bill.request)
                    AutoLedgerDeduplicator.commitRecorded(
                        context = context,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = notificationTimestampMs,
                        bill = bill,
                        localId = localId,
                        nowMs = nowMs
                    )
                    Log.i(
                        TAG,
                        "自动记账成功: ${bill.request.title} [${bill.request.type}/${bill.request.category}] - ${bill.request.amount / 100.0}元"
                    )
                }

                is AutoLedgerDedupeDecision.EnrichExisting -> {
                    val updated = onEnrich(decision.existingLocalId, bill.request)
                    if (updated) {
                        AutoLedgerDeduplicator.commitEnriched(
                            context = context,
                            pkg = pkg,
                            sbnKey = sbnKey,
                            notificationTimestampMs = notificationTimestampMs,
                            bill = bill,
                            existingLocalId = decision.existingLocalId,
                            nowMs = nowMs
                        )
                        Log.i(
                            TAG,
                            "跨平台提醒合并补全 (${decision.reason}): localId=${decision.existingLocalId} -> ${bill.request.title} [${bill.request.category}]"
                        )
                    } else {
                        AutoLedgerDeduplicator.commitDroppedFingerprints(
                            context = context,
                            pkg = pkg,
                            sbnKey = sbnKey,
                            notificationTimestampMs = notificationTimestampMs,
                            bill = bill,
                            nowMs = nowMs
                        )
                    }
                }

                is AutoLedgerDedupeDecision.DropDuplicate -> {
                    AutoLedgerDeduplicator.commitDroppedFingerprints(
                        context = context,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = notificationTimestampMs,
                        bill = bill,
                        nowMs = nowMs
                    )
                    Log.d(
                        TAG,
                        "忽略重复提醒 (${decision.reason}): pkg=$pkg type=${bill.request.type} amount=${bill.request.amount}"
                    )
                }
            }
            decision
        }

        internal fun shouldIgnoreNotification(
            notification: Notification,
            postTimeMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            val flags = notification.flags
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return true
            if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
            if (notification.category in IGNORED_NOTIFICATION_CATEGORIES) return true
            if (postTimeMs > 0L && nowMs - postTimeMs > STALE_NOTIFICATION_MS) return true
            return false
        }

        internal fun extractNotificationContent(
            title: String?,
            text: String?,
            bigText: String?,
            subText: String? = null
        ): String {
            val cleanTitle = title?.trim().orEmpty()
            val cleanText = text?.trim().orEmpty()
            val cleanBigText = bigText?.trim().orEmpty()
            val cleanSubText = subText?.trim().orEmpty()

            val body = when {
                cleanBigText.isEmpty() -> cleanText
                cleanText.isEmpty() -> cleanBigText
                cleanBigText.contains(cleanText) -> cleanBigText
                cleanText.contains(cleanBigText) -> cleanText
                else -> "$cleanText $cleanBigText"
            }

            return buildList {
                if (cleanTitle.isNotEmpty()) add(cleanTitle)
                if (cleanSubText.isNotEmpty() && !cleanTitle.contains(cleanSubText) && !body.contains(cleanSubText)) {
                    add(cleanSubText)
                }
                if (body.isNotEmpty() && body != cleanTitle) add(body)
            }.joinToString(" ")
        }

        fun isAllowedPackage(pkg: String): Boolean = AutoLedgerParser.isAllowedPackage(pkg)

        fun isVerificationCodeOrSpam(content: String): Boolean =
            AutoLedgerParser.isVerificationCodeOrSpam(content)

        fun parseYuanStringToCents(str: String?): Long? =
            AutoLedgerParser.parseYuanStringToCents(str)

        fun normalizeContentForDedupe(content: String): String =
            AutoLedgerParser.normalizeContentForDedupe(content)

        fun parseBillFromText(
            content: String,
            pkg: String = "",
            expenseCategories: List<String> = emptyList(),
            incomeCategories: List<String> = emptyList()
        ): CreateTransactionRequest? = AutoLedgerParser.parse(
            content = content,
            pkg = pkg,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories
        )?.request

        internal fun checkAndRecordDuplicate(
            context: Context?,
            pkg: String,
            sbnKey: String,
            notificationTimestampMs: Long,
            type: String,
            amountCents: Long,
            normalizedContent: String,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            val channelGroup = AutoLedgerParser.classifyPackage(pkg) ?: NotificationChannelGroup.BANK_SMS
            val syntheticBill = ParsedAutoLedgerBill(
                request = CreateTransactionRequest(
                    amount = amountCents,
                    type = type,
                    category = if (type == "income") "其他收入" else "其他",
                    title = "自动记账",
                    occurredAt = ""
                ),
                channelGroup = channelGroup,
                merchant = AutoLedgerParser.extractMerchant(normalizedContent),
                normalizedContent = normalizedContent,
                qualityScore = channelGroup.basePriority * 10
            )
            val decision = AutoLedgerDeduplicator.evaluate(
                context = context,
                pkg = pkg,
                sbnKey = sbnKey,
                notificationTimestampMs = notificationTimestampMs,
                bill = syntheticBill,
                nowMs = nowMs
            )
            return when (decision) {
                is AutoLedgerDedupeDecision.RecordNew -> {
                    AutoLedgerDeduplicator.commitRecorded(
                        context = context,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = notificationTimestampMs,
                        bill = syntheticBill,
                        localId = null,
                        nowMs = nowMs
                    )
                    false
                }
                is AutoLedgerDedupeDecision.EnrichExisting -> {
                    AutoLedgerDeduplicator.commitEnriched(
                        context = context,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = notificationTimestampMs,
                        bill = syntheticBill,
                        existingLocalId = decision.existingLocalId,
                        nowMs = nowMs
                    )
                    true
                }
                is AutoLedgerDedupeDecision.DropDuplicate -> {
                    AutoLedgerDeduplicator.commitDroppedFingerprints(
                        context = context,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = notificationTimestampMs,
                        bill = syntheticBill,
                        nowMs = nowMs
                    )
                    true
                }
            }
        }

        fun clearDedupeCache(context: Context? = null) = AutoLedgerDeduplicator.clear(context)

        internal fun resetInMemoryDedupeCacheForTest() = AutoLedgerDeduplicator.resetInMemoryForTest()
    }
}
