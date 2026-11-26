package com.looseleaf.feature.ledger.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
import kotlin.math.abs

/**
 * 账本自动记账通知监听服务 (LedgerNotificationListenerService)
 *
 * 结合开源项目 `huanghhcri/SmartLedger` (MIT) 与 `AutoAccounting` 非 Root 多渠道机制：
 * 1. 系统级通知过滤：忽略常驻/促销通知与超期重投通知，支持对白名单支付/银行通道的折叠摘要自动补全子通知；
 * 2. 多形态通知正文抽取：同时兼容单条通知、MessagingStyle (EXTRA_MESSAGES)、
 *    InboxStyle (EXTRA_TEXT_LINES) 与 tickerText，并在通知摘要刷新时使用稳定事件时间戳；
 * 3. 白名单解析引擎 ([AutoLedgerParser]) 与四段式规则库；
 * 4. 持久化去重与跨平台补全 ([AutoLedgerDeduplicator])：
 *    与 [LedgerSmsReceiver]、[LedgerAccessibilityService] 共用同一全局互斥锁与去重槽位。
 */
class LedgerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            requestRebind(ComponentName(this, LedgerNotificationListenerService::class.java))
        }.onFailure {
            Log.w(TAG, "请求重新绑定通知监听服务失败: ${it.message}")
        }
    }

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
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (shouldIgnoreNotification(notification, sbn.postTime, nowMs, allowGroupSummary = true)) return

        val candidates = LinkedHashSet<String>()
        val rawCandidates = extractCandidateContents(notification)
        var effectiveTimestampMs = stableEventTime(sbn.postTime, notification.`when`, nowMs)
        var adoptedChildKey: String? = null

        val rawCombined = rawCandidates.firstOrNull().orEmpty()
        val looksGrouped = isGroupSummary || AutoLedgerParser.looksLikeGroupedSummary(rawCombined)
        val needGroupEnrich = looksGrouped &&
            !AutoLedgerParser.hasPayableAmountSignal(rawCombined) &&
            !AutoLedgerParser.hasBankLedgerSignal(rawCombined)
        if (needGroupEnrich) {
            val activeList = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
            val enriched = enrichFromGroupChildren(
                summary = sbn,
                active = activeList,
                nowMs = nowMs
            )
            if (enriched != null) {
                candidates.add(enriched.combinedText)
                effectiveTimestampMs = enriched.eventTimeMs
                adoptedChildKey = enriched.childKey
            }
        }
        candidates.addAll(rawCandidates)
        if (candidates.isEmpty()) return

        if (isGroupSummary && candidates.none { AutoLedgerParser.looksLikeLedgerChild(it) }) {
            return
        }

        val sbnKey = adoptedChildKey ?: sbn.key.orEmpty()

        // 通过全局处理互斥锁串行化多平台并发通知、短信广播与前台完成页事件，
        // 确保先到达者落库并注册跨平台去重槽位后，后到达者才能准确去重或原地补全。
        serviceScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                val snapshot = LedgerRepository.snapshot.value
                var matchedDecision: AutoLedgerDedupeDecision? = null
                for (candidate in candidates) {
                    if (isVerificationCodeOrSpam(candidate)) continue
                    val decision = processIncomingNotification(
                        context = appCtx,
                        pkg = pkg,
                        sbnKey = sbnKey,
                        notificationTimestampMs = effectiveTimestampMs,
                        content = candidate,
                        expenseCategories = snapshot.expenseCategories,
                        incomeCategories = snapshot.incomeCategories,
                        nowMs = nowMs,
                        onCreate = { req -> LedgerRepository.createTransactionOptimistic(req) },
                        onEnrich = { localId, req -> LedgerRepository.updateTransactionOptimistic(-localId, req) }
                    )
                    if (decision != null) {
                        matchedDecision = decision
                        break
                    }
                }
                if (matchedDecision == null && candidates.isNotEmpty()) {
                    val primary = candidates.first()
                    val isWeChatNonChannel = pkg == "com.tencent.mm" &&
                        !primary.contains("微信支付") && !primary.contains("转账") &&
                        !primary.contains("收款") && !primary.contains("付款")
                    val hasLedgerHint = AutoLedgerParser.hasPayableAmountSignal(primary) ||
                        AutoLedgerParser.hasBankLedgerSignal(primary) ||
                        primary.contains("转账") || primary.contains("收款") || primary.contains("付款")
                    if (!isWeChatNonChannel && hasLedgerHint) {
                        val reason = if (isVerificationCodeOrSpam(primary)) "命中过滤规则" else "未匹配成交金额模板"
                        AutoLedgerDiagnostics.record(
                            source = sourceLabelForPackage(pkg),
                            outcome = "已忽略（$reason）",
                            rawDetail = primary,
                            isRecorded = false,
                            nowMs = nowMs
                        )
                    }
                }
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
        ): AutoLedgerDedupeDecision? {
            val bill = AutoLedgerParser.parse(
                content = content,
                pkg = pkg,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories
            ) ?: return null
            return processParsedBill(
                context = context,
                pkg = pkg,
                sbnKey = sbnKey,
                notificationTimestampMs = notificationTimestampMs,
                bill = bill,
                nowMs = nowMs,
                onCreate = onCreate,
                onEnrich = onEnrich
            )
        }

        internal suspend fun processParsedBill(
            context: Context?,
            pkg: String,
            sbnKey: String,
            notificationTimestampMs: Long,
            bill: ParsedAutoLedgerBill,
            nowMs: Long = System.currentTimeMillis(),
            onCreate: suspend (CreateTransactionRequest) -> Long,
            onEnrich: suspend (Long, CreateTransactionRequest) -> Boolean
        ): AutoLedgerDedupeDecision = processingMutex.withLock {
            val decision = AutoLedgerDeduplicator.evaluate(
                context = context,
                pkg = pkg,
                sbnKey = sbnKey,
                notificationTimestampMs = notificationTimestampMs,
                bill = bill,
                nowMs = nowMs
            )

            val sourceLabel = when (bill.channelGroup) {
                NotificationChannelGroup.WECHAT_SCREEN -> "微信页面"
                NotificationChannelGroup.ALIPAY_SCREEN -> "支付宝页面"
                else -> sourceLabelForPackage(pkg)
            }
            val amountYuan = "%.2f".format(bill.request.amount / 100.0)
            val directionLabel = if (bill.request.type == "income") "收入" else "支出"

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
                    AutoLedgerDiagnostics.record(
                        source = sourceLabel,
                        outcome = "已自动记账",
                        rawDetail = "${bill.request.title} · $directionLabel ¥$amountYuan [${bill.request.category}]",
                        isRecorded = true,
                        nowMs = nowMs
                    )
                    Log.i(
                        TAG,
                        "自动记账成功: ${bill.request.title} [${bill.request.type}/${bill.request.category}] - ${amountYuan}元"
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
                        AutoLedgerDiagnostics.record(
                            source = sourceLabel,
                            outcome = "跨端合并补全",
                            rawDetail = "${bill.request.title} · $directionLabel ¥$amountYuan",
                            isRecorded = true,
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
                    AutoLedgerDiagnostics.record(
                        source = sourceLabel,
                        outcome = "重复提醒已去重",
                        rawDetail = "${bill.request.title} · $directionLabel ¥$amountYuan",
                        isRecorded = false,
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

        private fun sourceLabelForPackage(pkg: String): String = when (AutoLedgerParser.classifyPackage(pkg)) {
            NotificationChannelGroup.WECHAT -> "微信通知"
            NotificationChannelGroup.ALIPAY -> "支付宝通知"
            NotificationChannelGroup.UNIONPAY -> "云闪付通知"
            NotificationChannelGroup.BANK_APP -> "银行 App"
            NotificationChannelGroup.E_COMMERCE -> "消费平台"
            NotificationChannelGroup.BANK_SMS -> "银行短信"
            else -> "支付通知"
        }

        internal data class EnrichedGroupChild(
            val combinedText: String,
            val eventTimeMs: Long,
            val childKey: String
        )

        internal fun stableEventTime(posted: Long, notificationWhen: Long, now: Long): Long {
            if (notificationWhen <= 0L) return posted
            if (notificationWhen > now + 60_000L) return posted
            if (now - notificationWhen > 18L * 60 * 60 * 1000L) return posted
            val postIsFresh = abs(now - posted) <= 2 * 60_000L
            val whenIsEarlier = posted - notificationWhen >= 2 * 60_000L
            return if (postIsFresh && whenIsEarlier) notificationWhen else posted
        }

        internal fun enrichFromGroupChildren(
            summary: StatusBarNotification,
            active: List<StatusBarNotification>,
            nowMs: Long = System.currentTimeMillis()
        ): EnrichedGroupChild? {
            val summaryGroup = summary.groupKey
            val summaryEvent = stableEventTime(summary.postTime, summary.notification.`when`, nowMs)
            val candidates = active
                .asSequence()
                .filter { child ->
                    child.packageName == summary.packageName &&
                        child.key != summary.key &&
                        (child.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                        (summaryGroup.isNullOrBlank() || child.groupKey == summaryGroup || child.groupKey.isNullOrBlank())
                }
                .sortedByDescending { it.postTime }

            for (child in candidates) {
                val extracted = extractCandidateContents(child.notification).firstOrNull() ?: continue
                if (!AutoLedgerParser.looksLikeLedgerChild(extracted)) continue
                val childEvent = stableEventTime(child.postTime, child.notification.`when`, nowMs)
                val close = abs(childEvent - summaryEvent) <= AutoLedgerDeduplicator.CROSS_PLATFORM_WINDOW_MS ||
                    abs(child.postTime - summary.postTime) <= AutoLedgerDeduplicator.CROSS_PLATFORM_WINDOW_MS
                if (!close) continue
                return EnrichedGroupChild(
                    combinedText = extracted,
                    eventTimeMs = childEvent,
                    childKey = child.key.orEmpty()
                )
            }
            return null
        }

        internal fun shouldIgnoreNotification(
            notification: Notification,
            postTimeMs: Long,
            nowMs: Long = System.currentTimeMillis(),
            allowGroupSummary: Boolean = false
        ): Boolean {
            val flags = notification.flags
            if (!allowGroupSummary && (flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return true
            if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
            if (notification.category in IGNORED_NOTIFICATION_CATEGORIES) return true
            if (postTimeMs > 0L && nowMs - postTimeMs > STALE_NOTIFICATION_MS) return true
            return false
        }

        /**
         * 从 Android 通知中按优先级抽取候选正文列表：
         * 1. 若通知为多行堆叠（MessagingStyle / InboxStyle），优先抽取最新一条消息，防止历史消息干扰；
         * 2. 常规单条通知使用完整「标题 + 子标题 + 正文 + 长正文」，保证拦截逻辑覆盖 bigText；
         * 3. 最后补充 tickerText 兜底（部分 ROM 下通知栏摘要会被折叠为「2条新消息」）。
         */
        @Suppress("DEPRECATION")
        internal fun extractCandidateContents(notification: Notification): List<String> {
            val extras = notification.extras ?: return emptyList()
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            val ticker = notification.tickerText?.toString()?.trim().orEmpty()

            val results = LinkedHashSet<String>()

            // 1. MessagingStyle (EXTRA_MESSAGES)：优先最新一条
            val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!rawMessages.isNullOrEmpty()) {
                val latestBundle = rawMessages.lastOrNull() as? Bundle
                val msgText = latestBundle?.getCharSequence("text")?.toString()?.trim().orEmpty()
                val msgSender = latestBundle?.getCharSequence("sender")?.toString()?.trim().orEmpty()
                if (msgText.isNotEmpty()) {
                    val line = buildList {
                        if (title.isNotEmpty()) add(title)
                        if (msgSender.isNotEmpty() && !title.contains(msgSender) && !msgText.contains(msgSender)) {
                            add(msgSender)
                        }
                        add(msgText)
                    }.joinToString(" ")
                    results.add(line)
                }
            }

            // 2. InboxStyle (EXTRA_TEXT_LINES)：当包含多行时，优先最后一条新到达消息
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            if (textLines.isNotEmpty()) {
                val latestLine = textLines.last()
                val combinedLine = if (title.isNotEmpty() && !latestLine.contains(title)) {
                    "$title $latestLine"
                } else {
                    latestLine
                }
                results.add(combinedLine)
            }

            // 3. 标准组合正文（title + subText + text + bigText）
            val combined = extractNotificationContent(
                title = title,
                text = text,
                bigText = bigText,
                subText = subText
            )
            if (combined.isNotEmpty()) {
                results.add(combined)
            }

            // 4. tickerText 兜底（当正文被系统或微信折叠为「[N条]新消息」时）
            if (ticker.isNotEmpty()) {
                val tickerLine = if (title.isNotEmpty() && !ticker.contains(title)) {
                    "$title $ticker"
                } else {
                    ticker
                }
                results.add(tickerLine)
            }

            return results.toList()
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
