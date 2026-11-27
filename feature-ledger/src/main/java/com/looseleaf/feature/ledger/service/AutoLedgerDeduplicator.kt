package com.looseleaf.feature.ledger.service

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

@Serializable
internal data class RecentTransactionSlot(
    val pkg: String,
    val channelGroup: String,
    val type: String,
    val amountCents: Long,
    val merchant: String? = null,
    val qualityScore: Int,
    val localId: Long? = null,
    val recordedAtMs: Long,
    val channelHint: String? = null,
    val notifyOrigin: String? = null,
    val alreadyPaired: Boolean = false
)

@Serializable
internal data class DedupePersistedState(
    val fingerprintExpiries: Map<String, Long> = emptyMap(),
    val recentTransactions: List<RecentTransactionSlot> = emptyList()
)

/**
 * 自动记账五层持久化去重与跨平台补全引擎 (AutoLedgerDeduplicator)
 *
 * 1. 精确通知身份（24h）：拦截系统重启/重绑服务后重投的状态栏历史通知；
 * 2. 同 sbnKey 原地更新（30s）：拦截通知原地展开或二次刷新；
 * 3. 同包名归一化正文指纹（5min 真滑动窗口）：消除固定整点分桶边界漏去重；
 * 4. 同渠道多通知合并（30s，如支付宝「支付助手 + 花呗/余额宝」、微信「微信支付 + 零钱通」）；
 * 5. 跨平台关联扣款去重与信息补全（120s，如微信/支付宝 + 银行 App + 银行短信 + 云闪付）：
 *    - 钱包先到、银行后到 → 直接去重丢弃；
 *    - 银行短信先到、钱包后到 → 原地升级已有账目的商户/分类/支付方式，不产生重复记录。
 */
internal object AutoLedgerDeduplicator {

    private const val PREFS_NAME = "looseleaf_ledger_notif_dedupe"
    private const val PREFS_KEY = "dedupe_state_v3"
    private const val MAX_FINGERPRINTS = 300
    private const val MAX_RECENT_SLOTS = 80

    internal const val IDENTITY_WINDOW_MS = 24 * 60 * 60 * 1000L
    internal const val CONTENT_WINDOW_MS = 5 * 60 * 1000L
    internal const val KEY_UPDATE_WINDOW_MS = 30 * 1000L
    internal const val SAME_CHANNEL_MULTI_ALERT_WINDOW_MS = 30 * 1000L
    internal const val PAIRED_WALLET_REPLAY_WINDOW_MS = 15 * 1000L
    internal const val CROSS_PLATFORM_WINDOW_MS = 120 * 1000L
    internal const val OPPOSITE_DIRECTION_CORRELATION_WINDOW_MS = 30 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var loadedFromPrefs = false
    private val fingerprintExpiries = LinkedHashMap<String, Long>(128, 0.75f, false)
    private val recentSlots = ArrayList<RecentTransactionSlot>(32)

    private val MERCHANT_NOISE_REGEX = Regex("""[\s\-—·.,，。()（）\[\]【】]+""")

    fun evaluate(
        context: Context?,
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        bill: ParsedAutoLedgerBill,
        nowMs: Long = System.currentTimeMillis()
    ): AutoLedgerDedupeDecision {
        synchronized(lock) {
            ensureLoadedLocked(context, nowMs)
            pruneExpiredLocked(nowMs)

            val type = bill.request.type
            val amount = bill.request.amount

            // 第 1 层：精确通知身份（24h，防服务重启重投）
            val identityKey = identityKey(pkg, sbnKey, notificationTimestampMs, type, amount)
            if (identityKey != null && (fingerprintExpiries[identityKey] ?: 0L) > nowMs) {
                return AutoLedgerDedupeDecision.DropDuplicate("exact_identity")
            }

            // 第 2 层：同 sbnKey 原地刷新（30s）
            val keyAmountKey = keyAmountKey(pkg, sbnKey, type, amount)
            if (keyAmountKey != null && (fingerprintExpiries[keyAmountKey] ?: 0L) > nowMs) {
                val matchingSlot = findRecentSlotLocked(
                    pkg = pkg,
                    bill = bill,
                    nowMs = nowMs
                )
                if (matchingSlot != null &&
                    matchingSlot.localId != null &&
                    bill.qualityScore > matchingSlot.qualityScore
                ) {
                    return AutoLedgerDedupeDecision.EnrichExisting(
                        existingLocalId = matchingSlot.localId,
                        reason = "same_key_enrich"
                    )
                }
                return AutoLedgerDedupeDecision.DropDuplicate("same_key_update")
            }

            // 第 3 层：同包名归一化正文指纹（5min 真滑动窗口）
            val contentKey = contentKey(pkg, type, amount, bill.normalizedContent)
            if ((fingerprintExpiries[contentKey] ?: 0L) > nowMs) {
                return AutoLedgerDedupeDecision.DropDuplicate("same_content")
            }

            // 第 4 & 5 层：同渠道多提醒（30s）与跨平台关联扣款提醒（120s，含收银台线索冲突与已配对保护）
            val correlated = findRecentSlotLocked(
                pkg = pkg,
                bill = bill,
                nowMs = nowMs
            )
            if (correlated != null) {
                val isCrossPlatform = correlated.channelGroup != bill.channelGroup.name || correlated.pkg != pkg
                val reason = if (isCrossPlatform) "cross_platform_correlated" else "same_channel_multi_alert"
                return if (correlated.localId != null && bill.qualityScore > correlated.qualityScore) {
                    AutoLedgerDedupeDecision.EnrichExisting(
                        existingLocalId = correlated.localId,
                        reason = "${reason}_enrich"
                    )
                } else {
                    AutoLedgerDedupeDecision.DropDuplicate(reason)
                }
            }

            return AutoLedgerDedupeDecision.RecordNew
        }
    }

    fun commitRecorded(
        context: Context?,
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        bill: ParsedAutoLedgerBill,
        localId: Long?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            ensureLoadedLocked(context, nowMs)
            pruneExpiredLocked(nowMs)
            putFingerprintsLocked(pkg, sbnKey, notificationTimestampMs, bill, nowMs)
            recentSlots.add(
                RecentTransactionSlot(
                    pkg = pkg,
                    channelGroup = bill.channelGroup.name,
                    type = bill.request.type,
                    amountCents = bill.request.amount,
                    merchant = bill.merchant,
                    qualityScore = bill.qualityScore,
                    localId = localId,
                    recordedAtMs = nowMs,
                    channelHint = bill.channelHint,
                    notifyOrigin = bill.notifyOrigin,
                    alreadyPaired = false
                )
            )
            trimCapsLocked()
            saveLocked(context)
        }
    }

    fun commitEnriched(
        context: Context?,
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        bill: ParsedAutoLedgerBill,
        existingLocalId: Long,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            ensureLoadedLocked(context, nowMs)
            pruneExpiredLocked(nowMs)
            putFingerprintsLocked(pkg, sbnKey, notificationTimestampMs, bill, nowMs)

            val idx = recentSlots.indexOfLast { it.localId == existingLocalId }
            if (idx >= 0) {
                val old = recentSlots[idx]
                val pairedNow = old.alreadyPaired || (
                    old.notifyOrigin != null &&
                        bill.notifyOrigin != null &&
                        old.notifyOrigin != bill.notifyOrigin
                    )
                recentSlots[idx] = old.copy(
                    pkg = pkg,
                    channelGroup = bill.channelGroup.name,
                    type = bill.request.type,
                    merchant = bill.merchant ?: old.merchant,
                    qualityScore = maxOf(old.qualityScore, bill.qualityScore),
                    channelHint = bill.channelHint ?: old.channelHint,
                    notifyOrigin = bill.notifyOrigin ?: old.notifyOrigin,
                    alreadyPaired = pairedNow
                )
            }
            trimCapsLocked()
            saveLocked(context)
        }
    }

    fun commitDroppedFingerprints(
        context: Context?,
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        bill: ParsedAutoLedgerBill,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            ensureLoadedLocked(context, nowMs)
            pruneExpiredLocked(nowMs)
            putFingerprintsLocked(pkg, sbnKey, notificationTimestampMs, bill, nowMs)
            // 若是 bank/wallet 跨端关联被丢弃，在最近槽位标上 alreadyPaired=true（SmartLedger 双边已配对保护），
            // 防止随后紧接着的一笔同金额消费再次被吸进第一笔里。
            val idx = recentSlots.indexOfLast { slot ->
                val elapsed = nowMs - slot.recordedAtMs
                val typeMatchesOrOppositeRecent = slot.type == bill.request.type ||
                    elapsed in 0L..OPPOSITE_DIRECTION_CORRELATION_WINDOW_MS
                typeMatchesOrOppositeRecent &&
                    slot.amountCents == bill.request.amount &&
                    elapsed in 0L..CROSS_PLATFORM_WINDOW_MS &&
                    slot.notifyOrigin != null &&
                    bill.notifyOrigin != null &&
                    slot.notifyOrigin != bill.notifyOrigin &&
                    merchantsCompatible(slot.merchant, bill.merchant)
            }
            if (idx >= 0 && !recentSlots[idx].alreadyPaired) {
                recentSlots[idx] = recentSlots[idx].copy(alreadyPaired = true)
            }
            trimCapsLocked()
            saveLocked(context)
        }
    }

    internal fun merchantsCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        val normA = normalizeMerchant(a)
        val normB = normalizeMerchant(b)
        if (normA.isEmpty() || normB.isEmpty()) return true
        return normA == normB || normA.contains(normB) || normB.contains(normA)
    }

    private fun normalizeMerchant(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(MERCHANT_NOISE_REGEX, "")

    private fun hintsConflict(slot: RecentTransactionSlot, incoming: ParsedAutoLedgerBill): Boolean {
        val hintA = slot.channelHint ?: return false
        val hintB = incoming.channelHint ?: return false
        return hintA != hintB
    }

    private fun findRecentSlotLocked(
        pkg: String,
        bill: ParsedAutoLedgerBill,
        nowMs: Long
    ): RecentTransactionSlot? {
        val channelGroup = bill.channelGroup
        val type = bill.request.type
        val amount = bill.request.amount
        val merchant = bill.merchant
        for (i in recentSlots.lastIndex downTo 0) {
            val slot = recentSlots[i]
            if (slot.type != type || slot.amountCents != amount) continue

            val elapsed = nowMs - slot.recordedAtMs
            if (elapsed < 0L || elapsed > CROSS_PLATFORM_WINDOW_MS) continue

            // 银行明确写了「支付宝」，新来的是「微信」，绝不合并（SmartLedger 收银台线索冲突保护）
            if (hintsConflict(slot, bill)) continue

            val slotGroup = runCatching { NotificationChannelGroup.valueOf(slot.channelGroup) }
                .getOrNull() ?: continue

            val isCrossPlatform = slotGroup != channelGroup ||
                (slotGroup == NotificationChannelGroup.BANK_APP && slot.pkg != pkg)

            // 该笔交易已经配齐「收银台 + 银行」双端通知后，不再吞并后续独立消费，只保留 15s 重投窗口
            val withinWindow = if (slot.alreadyPaired) {
                val limit = if (bill.notifyOrigin == AutoLedgerParser.ORIGIN_BANK) {
                    SAME_CHANNEL_MULTI_ALERT_WINDOW_MS
                } else {
                    PAIRED_WALLET_REPLAY_WINDOW_MS
                }
                elapsed <= limit
            } else if (isCrossPlatform) {
                slotGroup.correlatesWith(channelGroup) && elapsed <= CROSS_PLATFORM_WINDOW_MS
            } else {
                elapsed <= SAME_CHANNEL_MULTI_ALERT_WINDOW_MS
            }
            if (!withinWindow) continue

            if (!merchantsCompatible(slot.merchant, merchant)) continue

            return slot
        }

        // 第 2 轮（跨端反方向幽灵保护）：高可信钱包已记为「收入/支出」后，30s 内银行短信/App 同金额反方向到达时
        // 视作同一笔交易被银行端无符号文案误判：高质端已先记则丢弃银行端；银行端先记则由高质端原地纠偏为正确方向。
        for (i in recentSlots.lastIndex downTo 0) {
            val slot = recentSlots[i]
            if (slot.type == type || slot.amountCents != amount) continue

            val elapsed = nowMs - slot.recordedAtMs
            if (elapsed < 0L || elapsed > OPPOSITE_DIRECTION_CORRELATION_WINDOW_MS) continue
            if (slot.alreadyPaired) continue
            if (slot.notifyOrigin == null || bill.notifyOrigin == null || slot.notifyOrigin == bill.notifyOrigin) continue
            if (hintsConflict(slot, bill)) continue

            val slotGroup = runCatching { NotificationChannelGroup.valueOf(slot.channelGroup) }
                .getOrNull() ?: continue
            if (!slotGroup.correlatesWith(channelGroup)) continue
            if (!merchantsCompatible(slot.merchant, merchant)) continue

            return slot
        }
        return null
    }

    private fun putFingerprintsLocked(
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        bill: ParsedAutoLedgerBill,
        nowMs: Long
    ) {
        val type = bill.request.type
        val amount = bill.request.amount
        identityKey(pkg, sbnKey, notificationTimestampMs, type, amount)?.let {
            fingerprintExpiries[it] = nowMs + IDENTITY_WINDOW_MS
        }
        keyAmountKey(pkg, sbnKey, type, amount)?.let {
            fingerprintExpiries[it] = nowMs + KEY_UPDATE_WINDOW_MS
        }
        fingerprintExpiries[contentKey(pkg, type, amount, bill.normalizedContent)] =
            nowMs + CONTENT_WINDOW_MS
    }

    private fun identityKey(
        pkg: String,
        sbnKey: String,
        notificationTimestampMs: Long,
        type: String,
        amount: Long
    ): String? {
        if (notificationTimestampMs <= 0L || sbnKey.isBlank()) return null
        return "id:$pkg:$sbnKey:$notificationTimestampMs:$type:$amount"
    }

    private fun keyAmountKey(pkg: String, sbnKey: String, type: String, amount: Long): String? {
        if (sbnKey.isBlank()) return null
        return "ka:$pkg:$sbnKey:$type:$amount"
    }

    private fun contentKey(pkg: String, type: String, amount: Long, normalizedContent: String): String =
        "fp:$pkg:$type:$amount:${sha256Short(normalizedContent)}"

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val fpIter = fingerprintExpiries.entries.iterator()
        while (fpIter.hasNext()) {
            if (fpIter.next().value <= nowMs) {
                fpIter.remove()
            }
        }
        recentSlots.removeAll { slot ->
            val age = nowMs - slot.recordedAtMs
            age > CROSS_PLATFORM_WINDOW_MS || age < -CROSS_PLATFORM_WINDOW_MS
        }
    }

    private fun trimCapsLocked() {
        if (fingerprintExpiries.size > MAX_FINGERPRINTS) {
            val keep = fingerprintExpiries.entries
                .sortedByDescending { it.value }
                .take(MAX_FINGERPRINTS)
            fingerprintExpiries.clear()
            keep.forEach { fingerprintExpiries[it.key] = it.value }
        }
        if (recentSlots.size > MAX_RECENT_SLOTS) {
            val dropCount = recentSlots.size - MAX_RECENT_SLOTS
            repeat(dropCount) { recentSlots.removeAt(0) }
        }
    }

    private fun ensureLoadedLocked(context: Context?, nowMs: Long) {
        if (context == null || loadedFromPrefs) return
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
        loadedFromPrefs = true
        if (raw.isNullOrBlank()) return

        val state = runCatching {
            json.decodeFromString<DedupePersistedState>(raw)
        }.getOrNull() ?: return

        state.fingerprintExpiries.forEach { (k, expiry) ->
            if (expiry > nowMs) fingerprintExpiries[k] = expiry
        }
        state.recentTransactions.forEach { slot ->
            val age = nowMs - slot.recordedAtMs
            if (age in 0L..CROSS_PLATFORM_WINDOW_MS) {
                recentSlots.add(slot)
            }
        }
    }

    private fun saveLocked(context: Context?) {
        if (context == null) return
        val state = DedupePersistedState(
            fingerprintExpiries = fingerprintExpiries.toMap(),
            recentTransactions = recentSlots.toList()
        )
        val encoded = runCatching { json.encodeToString(state) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, encoded)
            .apply()
    }

    fun clear(context: Context? = null) {
        synchronized(lock) {
            fingerprintExpiries.clear()
            recentSlots.clear()
            loadedFromPrefs = false
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.apply()
        }
    }

    internal fun resetInMemoryForTest() {
        synchronized(lock) {
            fingerprintExpiries.clear()
            recentSlots.clear()
            loadedFromPrefs = false
        }
    }
}
