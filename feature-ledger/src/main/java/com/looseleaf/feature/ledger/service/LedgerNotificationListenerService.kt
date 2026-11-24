package com.looseleaf.feature.ledger.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 账本自动记账通知监听服务 (LedgerNotificationListenerService)
 *
 * 核心保障：
 * 1. 严格过滤分组摘要通知 (FLAG_GROUP_SUMMARY)、常驻/进度通知与过旧重投通知；
 * 2. 屏蔽周报/月报/上周总结、出账还款提醒、营销红包/优惠券、收益日报与失败/待支付订单；
 * 3. 分类严格绑定服务端动态分类字典并按收支类型隔离，杜绝「分类不存在于分类字典」400 同步失败；
 * 4. 持久化三层滑动窗口去重（精确通知身份 24h + 归一化内容指纹 5min + 同通知原地更新 30s），
 *    消除跨 5 分钟整点分桶漏去重与服务重启重投导致的重复记账；
 * 5. 金额全链路采用整数分解析，杜绝浮点精度丢失，并对敏感长数字串脱敏。
 */
class LedgerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val appCtx = applicationContext
        // 检查用户是否开启自动记账
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
        if (content.isBlank()) return

        // 过滤验证码、周期总结（如支付宝「上周总结」）、账单还款提醒与营销通知
        if (isVerificationCodeOrSpam(content)) {
            return
        }

        // 确保冷启动后台唤醒时仓库与持久化的服务端分类字典已就绪
        LedgerRepository.ensureInitialized(appCtx)
        val snapshot = LedgerRepository.snapshot.value

        val parsed = parseBillFromText(
            content = content,
            pkg = pkg,
            expenseCategories = snapshot.expenseCategories,
            incomeCategories = snapshot.incomeCategories
        ) ?: return

        val notificationTimestampMs = notification.`when`.takeIf { it > 0L } ?: sbn.postTime
        val isDuplicate = checkAndRecordDuplicate(
            context = appCtx,
            pkg = pkg,
            sbnKey = sbn.key.orEmpty(),
            notificationTimestampMs = notificationTimestampMs,
            type = parsed.type,
            amountCents = parsed.amount,
            normalizedContent = normalizeContentForDedupe(content),
            nowMs = nowMs
        )
        if (isDuplicate) {
            Log.d(TAG, "忽略重复支付通知: pkg=$pkg type=${parsed.type} amount=${parsed.amount}")
            return
        }

        serviceScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                // createTransactionOptimistic 内部已自动调用 scheduleOutboxFlush()
                LedgerRepository.createTransactionOptimistic(parsed)
                Log.i(TAG, "自动记账成功: ${parsed.title} [${parsed.type}/${parsed.category}] - ${parsed.amount / 100.0}元")
            } catch (e: Exception) {
                Log.e(TAG, "自动记账写入失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "LedgerNotification"
        private const val DEDUPE_PREFS_NAME = "looseleaf_ledger_notif_dedupe"
        private const val DEDUPE_PREFS_KEY = "recent_fingerprints_v2"
        private const val MAX_DEDUPE_ENTRIES = 300

        internal const val STALE_NOTIFICATION_MS = 10 * 60 * 1000L
        internal const val IDENTITY_DEDUPE_WINDOW_MS = 24 * 60 * 60 * 1000L
        internal const val CONTENT_DEDUPE_WINDOW_MS = 5 * 60 * 1000L
        internal const val KEY_UPDATE_DEDUPE_WINDOW_MS = 30 * 1000L

        private val dedupeJson = Json { ignoreUnknownKeys = true }
        private val dedupeLock = Any()
        private var dedupeLoadedFromPrefs = false
        private val recentDedupeExpiries = LinkedHashMap<String, Long>(128, 0.75f, false)

        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.xiaomi.mms",
            "com.huawei.message"
        )

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

        // 验证码、周期账单总结（如支付宝「上周总结」）、还款提醒、营销与非成交通知屏蔽词
        private val NON_TRANSACTION_KEYWORDS = listOf(
            // 验证码与垃圾退订
            "验证码", "校验码", "动态码", "动态密码", "确认码", "安全码", "退订", "回复td", "回t",
            // 周期总结、汇总与分析报告（支付宝上周总结、月账单、账单小助手等）
            "上周", "本周", "上月", "本月", "上季度", "本季度",
            "年度账单", "年度总结", "年度报告", "年度消费", "年度支出", "年终总结", "年终账单",
            "近7天", "近30天", "近一周", "近一月", "最近一周", "最近一月", "过去一周", "过去一月",
            "昨日消费", "昨日支出", "昨日账单", "今日汇总", "今日消费汇总",
            "总结", "汇总", "周报", "月报", "年报", "季报", "报告",
            "周账单", "月账单", "年账单", "账单总结", "账单汇总", "账单报告", "账单小助手",
            "账单助手", "账单分析", "收支分析", "消费分析", "消费概况", "收支概况", "记账本",
            "总支出", "总消费", "总收入", "共支出", "共消费", "共收入", "共计支出", "共计消费",
            "共计收入", "共计", "累计支出", "累计消费", "累计收入", "合计支出", "合计消费", "合计收入",
            "总计支出", "总计消费", "总计收入", "较上周", "比上周", "较上月", "比上月", "环比", "同比",
            "排行榜", "账单已出", "已出账", "本期账单", "出账通知", "出账提醒",
            "花呗账单", "白条账单", "信用卡账单", "电子账单",
            // 还款提醒与信用额度调整通知（保留真实刷卡短信末尾的「可用额度」不被误杀）
            "本期应还", "本月应还", "待还款", "待还金额", "还款提醒", "到期提醒", "即将到期",
            "最后还款日", "最低还款", "预约还款", "自动还款提醒", "账单提醒",
            "额度调整", "额度提升", "授信额度", "提额", "临时额度", "信用分", "芝麻信用", "芝麻分", "信用守护",
            // 营销、未领红包/优惠券与互动小游戏（保留真实支付里的「红包抵扣/已立减/开通会员」不被误杀）
            "待领取", "立即领取", "点击领取", "免费领", "领红包", "红包待领", "红包即将过期",
            "红包雨", "瓜分红包", "拆红包", "优惠券", "代金券", "满减活动", "立减金",
            "折扣券", "抽奖", "签到", "限时免费", "限时领取", "限时抢", "活动通知", "邀请好友",
            "免费开通", "邀你开通", "会员到期提醒", "会员即将到期", "积分到期", "积分兑换",
            "蚂蚁森林", "蚂蚁庄园", "芭芭农场", "集五福", "理财推荐", "精选基金",
            // 收益日报与失败/待付/取消订单
            "收益日报", "昨日收益", "七日年化", "万份收益", "预期收益", "基金估值", "行情提醒",
            "涨跌幅", "付款失败", "支付失败", "扣款失败", "交易失败", "转账失败", "收款失败",
            "交易关闭", "订单已取消", "已取消订单", "余额不足", "密码错误", "待支付", "待付款",
            "即将自动扣款", "扣款预告", "续费提醒", "即将扣费"
        )

        private val AGGREGATE_COUNT_PATTERN = Regex(
            """(?:共|累计|合计|总计)\s*\d+\s*笔|\d+\s*笔(?:消费|支出|交易|账单|收入|付款|收款)"""
        )

        // 微信仅放行官方支付/收款渠道通知，屏蔽普通好友/群聊提到消费金额的聊天消息
        private val WECHAT_PAYMENT_CHANNEL_KEYWORDS = listOf(
            "微信支付", "微信收款助手", "微信收款商业版", "服务通知", "微信零钱", "零钱通",
            "支付凭证", "扣费凭证", "收款到账", "面对面收款", "赞赏码"
        )

        private val WHITESPACE_REGEX = Regex("""\s+""")

        private const val AMOUNT_NUM = """[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?"""
        // 严格金额段：
        // 1) 带明确货币符号/币种前缀 (¥12 / RMB 88.50 / 人民币36.50元)
        // 2) 带「元/块」单位 (36.50元 / 100 元)
        // 3) 显式小数且不跟日期/笔数/折扣/百分比单位 (消费 58.00)
        private const val STRICT_AMOUNT =
            """(?:(?:¥|￥|人民币|RMB|CNY)\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])|($AMOUNT_NUM)(?![\d.])\s*[元块]|([0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{1,2}|[0-9]+\.[0-9]{1,2})(?![\d.]|\s*[月日年时分秒笔次个张件期折%％万亿]))"""

        // 动作词与金额之间限制在 12 个非分句字符内，避免跨句抓取日期、笔数或卡号尾号；
        // 特别排除品牌名「支付宝」(支付(?!宝)) 与非紧邻金额的「微信支付」抬头，防止任意支付宝/微信通知误命中「支付」二字
        private const val VERB_GAP = """[^\d。！？；，,\n]{0,12}"""

        private val EXPENSE_PATTERN = Pattern.compile(
            """(?:(?:支付成功|付款成功|扣款成功|消费成功|扫码付款|扫码支付|快捷支付|刷卡消费|自动扣款|付款给|转账给|实付|已付|(?<!微信)支付(?!宝)|付款|消费|扣款|支出|扣除|转出)$VERB_GAP|微信支付[\s:：]{0,2})$STRICT_AMOUNT"""
        )
        // 负向前瞻排除「收款方/收款人/收款商户/收款账户/收款码」，防止支出通知里的收款方信息被误判为收入
        private val INCOME_PATTERN = Pattern.compile(
            """(?:收款成功|收款到账|扫码收款|二维码收款|面对面收款|收到转账|收到红包|退款到账|转入|入账|存入|到账|收款(?![方人账商码])|收入)$VERB_GAP$STRICT_AMOUNT"""
        )

        private val EXPENSE_FALLBACK_CUE_PATTERN = Pattern.compile(
            """支付成功|付款成功|扣款成功|消费成功|交易成功|已支付|已付款|已扣款|支付凭证|扣费凭证|动账通知|动账提醒"""
        )
        private val INCOME_FALLBACK_CUE_PATTERN = Pattern.compile(
            """收款成功|到账成功|收款到账|已收款|收款通知"""
        )
        private val AMOUNT_SYMBOL_FALLBACK_PATTERN = Pattern.compile(
            """(?:[¥￥]|金额[\s:：¥￥人民币]{0,6})\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])"""
        )

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
            // 服务重启/系统重绑时，状态栏滞留超过 10 分钟的旧通知会被系统重新投递，直接忽略
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

        internal fun normalizeContentForDedupe(content: String): String =
            content.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

        private fun sha256Short(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

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
            synchronized(dedupeLock) {
                if (context != null && !dedupeLoadedFromPrefs) {
                    loadDedupeFromPrefsLocked(context, nowMs)
                    dedupeLoadedFromPrefs = true
                }
                pruneExpiredLocked(nowMs)

                val identityKey = if (notificationTimestampMs > 0L && sbnKey.isNotBlank()) {
                    "id:$pkg:$sbnKey:$notificationTimestampMs:$type:$amountCents"
                } else {
                    null
                }
                val contentKey = "fp:$pkg:$type:$amountCents:${sha256Short(normalizedContent)}"
                val keyAmountKey = if (sbnKey.isNotBlank()) {
                    "ka:$pkg:$sbnKey:$type:$amountCents"
                } else {
                    null
                }

                val duplicate = (identityKey != null && (recentDedupeExpiries[identityKey] ?: 0L) > nowMs) ||
                    (recentDedupeExpiries[contentKey] ?: 0L) > nowMs ||
                    (keyAmountKey != null && (recentDedupeExpiries[keyAmountKey] ?: 0L) > nowMs)

                if (duplicate) {
                    return true
                }

                if (identityKey != null) {
                    recentDedupeExpiries[identityKey] = nowMs + IDENTITY_DEDUPE_WINDOW_MS
                }
                recentDedupeExpiries[contentKey] = nowMs + CONTENT_DEDUPE_WINDOW_MS
                if (keyAmountKey != null) {
                    recentDedupeExpiries[keyAmountKey] = nowMs + KEY_UPDATE_DEDUPE_WINDOW_MS
                }

                trimDedupeCapLocked()
                if (context != null) {
                    saveDedupeToPrefsLocked(context)
                }
                return false
            }
        }

        private fun pruneExpiredLocked(nowMs: Long) {
            val iterator = recentDedupeExpiries.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value <= nowMs) {
                    iterator.remove()
                }
            }
        }

        private fun trimDedupeCapLocked() {
            if (recentDedupeExpiries.size <= MAX_DEDUPE_ENTRIES) return
            val keep = recentDedupeExpiries.entries
                .sortedByDescending { it.value }
                .take(MAX_DEDUPE_ENTRIES)
            recentDedupeExpiries.clear()
            keep.forEach { recentDedupeExpiries[it.key] = it.value }
        }

        private fun loadDedupeFromPrefsLocked(context: Context, nowMs: Long) {
            val raw = context.getSharedPreferences(DEDUPE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(DEDUPE_PREFS_KEY, null)
                ?: return
            val stored = runCatching {
                dedupeJson.decodeFromString<Map<String, Long>>(raw)
            }.getOrDefault(emptyMap())
            stored.forEach { (k, expiry) ->
                if (expiry > nowMs) {
                    recentDedupeExpiries[k] = expiry
                }
            }
        }

        private fun saveDedupeToPrefsLocked(context: Context) {
            val encoded = runCatching {
                dedupeJson.encodeToString(recentDedupeExpiries.toMap())
            }.getOrNull() ?: return
            context.getSharedPreferences(DEDUPE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(DEDUPE_PREFS_KEY, encoded)
                .apply()
        }

        fun isAllowedPackage(pkg: String): Boolean {
            return pkg == "com.tencent.mm" ||
                pkg == "com.eg.android.AlipayGphone" ||
                pkg == "com.unionpay" ||
                pkg.startsWith("com.icbc") ||
                pkg.startsWith("com.cmbchina") ||
                pkg.startsWith("com.chinamworld") ||
                pkg.startsWith("com.boc") ||
                pkg.startsWith("com.abchina") ||
                pkg.startsWith("com.bankcomm") ||
                pkg.startsWith("com.spdb") ||
                pkg.startsWith("com.cib") ||
                pkg.startsWith("com.cebbank") ||
                pkg.startsWith("com.pingan") ||
                pkg.contains(".bank.") ||
                pkg in SMS_PACKAGES
        }

        fun isVerificationCodeOrSpam(content: String): Boolean {
            if (content.isBlank()) return true
            val lower = content.lowercase(Locale.ROOT)
            if (NON_TRANSACTION_KEYWORDS.any { lower.contains(it) }) {
                return true
            }
            if (AGGREGATE_COUNT_PATTERN.containsMatchIn(content)) {
                return true
            }
            return false
        }

        fun parseYuanStringToCents(str: String?): Long? {
            if (str.isNullOrBlank()) return null
            return FormatUtils.yuanStringToCents(str).takeIf { it > 0L }
        }

        private fun extractMatchedAmountCents(matcher: Matcher): Long? {
            for (groupIndex in 1..matcher.groupCount()) {
                val value = matcher.group(groupIndex)
                if (!value.isNullOrBlank()) {
                    return parseYuanStringToCents(value)
                }
            }
            return null
        }

        private fun inferCategory(
            content: String,
            type: String,
            expenseCategories: List<String>,
            incomeCategories: List<String>
        ): String {
            val known = if (type == "income") incomeCategories else expenseCategories
            return FormatUtils.inferCategoryFromText(
                text = content,
                knownCategories = known,
                type = type
            )
        }

        fun parseBillFromText(
            content: String,
            pkg: String = "",
            expenseCategories: List<String> = emptyList(),
            incomeCategories: List<String> = emptyList()
        ): CreateTransactionRequest? {
            if (isVerificationCodeOrSpam(content)) return null

            // 短信包名必须包含明确金融/支付关键词，防止误记普通聊天或营销
            if (pkg in SMS_PACKAGES) {
                val hasFinancialKeyword = content.contains("银行") ||
                    content.contains("支付宝") ||
                    content.contains("微信支付") ||
                    content.contains("银联") ||
                    content.contains("云闪付") ||
                    content.contains("信用卡") ||
                    content.contains("储蓄卡") ||
                    content.contains("借记卡")
                if (!hasFinancialKeyword) return null
            }

            // 微信必须来自官方支付/收款渠道，屏蔽好友或群聊里提到消费金额的聊天消息
            if (pkg == "com.tencent.mm") {
                val isOfficialPaymentChannel = WECHAT_PAYMENT_CHANNEL_KEYWORDS.any { content.contains(it) }
                if (!isOfficialPaymentChannel) return null
            }

            var type = "expense"
            var amountCents: Long? = null

            // 1. 优先判断收入（已通过负向前瞻排除「收款方/收款人/收款商户/收款码」）
            val incomeMatcher = INCOME_PATTERN.matcher(content)
            if (incomeMatcher.find()) {
                type = "income"
                amountCents = extractMatchedAmountCents(incomeMatcher)
            } else {
                // 2. 支出匹配
                val expenseMatcher = EXPENSE_PATTERN.matcher(content)
                if (expenseMatcher.find()) {
                    type = "expense"
                    amountCents = extractMatchedAmountCents(expenseMatcher)
                } else if (INCOME_FALLBACK_CUE_PATTERN.matcher(content).find()) {
                    // 3a. 仅当带有明确收款成交词时，才允许按 ¥/金额 兜底提取收入金额
                    val symbolMatcher = AMOUNT_SYMBOL_FALLBACK_PATTERN.matcher(content)
                    if (symbolMatcher.find()) {
                        type = "income"
                        amountCents = extractMatchedAmountCents(symbolMatcher)
                    }
                } else if (EXPENSE_FALLBACK_CUE_PATTERN.matcher(content).find()) {
                    // 3b. 仅当带有明确支付成交词时，才允许按 ¥/金额 兜底提取支出金额
                    val symbolMatcher = AMOUNT_SYMBOL_FALLBACK_PATTERN.matcher(content)
                    if (symbolMatcher.find()) {
                        type = "expense"
                        amountCents = extractMatchedAmountCents(symbolMatcher)
                    }
                }
            }

            if (amountCents == null || amountCents <= 0) return null

            val paymentMethod = when {
                pkg.contains("tencent", ignoreCase = true) || content.contains("微信") -> "微信支付"
                pkg.contains("alipay", ignoreCase = true) || content.contains("支付宝") -> "支付宝"
                pkg.contains("bank", ignoreCase = true) || content.contains("银行") || content.contains("信用卡") -> "银行卡"
                else -> "其他"
            }

            val category = inferCategory(content, type, expenseCategories, incomeCategories)

            val title = when {
                type == "income" -> "自动记账: 收到款项"
                paymentMethod == "微信支付" -> "微信支付自动记账"
                paymentMethod == "支付宝" -> "支付宝自动记账"
                else -> "自动记账"
            }

            // 脱敏处理备注 (P1-16)：隐藏 4 位以上连续数字（卡号/账号），不明文存储敏感通知全文
            val desensitizedContent = content.replace(Regex("""\d{4,}"""), "****")
            val sanitizedNote = "自动记账 ($paymentMethod - $category): $desensitizedContent"

            return CreateTransactionRequest(
                amount = amountCents,
                type = type,
                category = category,
                title = title,
                note = sanitizedNote,
                paymentMethod = paymentMethod,
                occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(FormatUtils.BeijingZone))
            )
        }

        fun clearDedupeCache(context: Context? = null) {
            synchronized(dedupeLock) {
                recentDedupeExpiries.clear()
                dedupeLoadedFromPrefs = false
                context?.getSharedPreferences(DEDUPE_PREFS_NAME, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.clear()
                    ?.apply()
            }
        }

        internal fun resetInMemoryDedupeCacheForTest() {
            synchronized(dedupeLock) {
                recentDedupeExpiries.clear()
                dedupeLoadedFromPrefs = false
            }
        }
    }
}
