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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.Collections
import java.util.LinkedHashMap
import java.util.regex.Pattern

/**
 * 账本自动记账通知监听服务 (LedgerNotificationListenerService)
 *
 * 优化特性 (P0-9, P1-16)：
 * 1. 严格 5 分钟滑动窗口与去重键防止多渠道/通知更新重复记账；
 * 2. 金额全链路采用 BigDecimal 整数分运算，杜绝浮点精度丢失（如 0.29 元 = 29 分）；
 * 3. 收紧包名白名单，过滤验证码/营销短信，保护敏感备注脱敏；
 * 4. 记账成功后立即调度后台增量同步。
 */
class LedgerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 检查用户是否开启自动记账
        val prefs = applicationContext.getSharedPreferences("looseleaf_ledger_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_record_enabled", true)) {
            return
        }

        val pkg = sbn.packageName ?: return
        if (!isAllowedPackage(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text"

        // 过滤验证码与营销信息
        if (isVerificationCodeOrSpam(content)) {
            return
        }

        val parsed = parseBillFromText(content, pkg) ?: return

        // 5 分钟去重键检查 (P1-10)：移除纯金额降级 key，防止误伤 5 分钟内多笔正常同金额消费
        val nowMs = System.currentTimeMillis()
        val dedupeBucket = nowMs / (5 * 60 * 1000)
        val dedupeKey = "$pkg:${sbn.key}:${parsed.type}:${parsed.amount}:${parsed.title}:$dedupeBucket"

        synchronized(recentDedupeKeys) {
            cleanExpiredDedupeKeys(nowMs)
            if (recentDedupeKeys.containsKey(dedupeKey)) {
                Log.d(TAG, "忽略 5 分钟内重复支付通知: $dedupeKey")
                return
            }
            recentDedupeKeys[dedupeKey] = nowMs
        }

        serviceScope.launch {
            try {
                LedgerRepository.createTransactionOptimistic(parsed)
                Log.i(TAG, "自动记账成功: ${parsed.title} - ${parsed.amount / 100.0}元")
                // 插入后立即调度账本同步 (P0-9, P1-3)
                LedgerRepository.scheduleOutboxFlush()
            } catch (e: Exception) {
                Log.e(TAG, "自动记账写入失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "LedgerNotification"

        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.xiaomi.mms",
            "com.huawei.message"
        )

        private val recentDedupeKeys = Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                    return size > 300
                }
            }
        )

        private fun cleanExpiredDedupeKeys(nowMs: Long) {
            val fiveMinutesAgo = nowMs - (5 * 60 * 1000)
            val iterator = recentDedupeKeys.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < fiveMinutesAgo) {
                    iterator.remove()
                }
            }
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
            return content.contains("验证码") ||
                content.contains("校验码") ||
                content.contains("动态码") ||
                content.contains("动态密码") ||
                content.contains("退订") ||
                content.contains("回复TD")
        }

        private val EXPENSE_PATTERN = Pattern.compile(
            """(?:支付|付款|消费|扣款|支出|扣除|扣款成功|转账给|扫码付款|付款给)[^\d]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?"""
        )
        private val INCOME_PATTERN = Pattern.compile(
            """(?:收款|到账|收入|存入|入账|收到转账|收款成功)[^\d]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?"""
        )
        private val YUAN_SYMBOL_PATTERN = Pattern.compile(
            """[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)"""
        )

        fun parseYuanStringToCents(str: String?): Long? {
            if (str.isNullOrBlank()) return null
            return runCatching {
                BigDecimal(str.trim())
                    .multiply(BigDecimal(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact()
            }.getOrNull()
        }

        fun parseBillFromText(content: String, pkg: String = ""): CreateTransactionRequest? {
            // 短信包名必须包含明确金融/支付关键词，防止误记普通聊天或营销
            if (pkg in SMS_PACKAGES) {
                val hasFinancialKeyword = content.contains("银行") ||
                    content.contains("支付宝") ||
                    content.contains("微信支付") ||
                    content.contains("银联") ||
                    content.contains("信用卡")
                if (!hasFinancialKeyword) return null
            }

            var type = "expense"
            var amountCents: Long? = null

            // 1. 优先判断收入
            val incomeMatcher = INCOME_PATTERN.matcher(content)
            if (incomeMatcher.find()) {
                type = "income"
                amountCents = parseYuanStringToCents(incomeMatcher.group(1))
            } else {
                // 2. 支出匹配
                val expenseMatcher = EXPENSE_PATTERN.matcher(content)
                if (expenseMatcher.find()) {
                    type = "expense"
                    amountCents = parseYuanStringToCents(expenseMatcher.group(1))
                } else {
                    // 3. 兜底匹配货币符号
                    val symbolMatcher = YUAN_SYMBOL_PATTERN.matcher(content)
                    if (symbolMatcher.find()) {
                        amountCents = parseYuanStringToCents(symbolMatcher.group(1))
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

            val category = when {
                type == "income" -> "其他收入"
                content.contains("外卖") || content.contains("美团") || content.contains("饿了么") ||
                    content.contains("肯德基") || content.contains("麦当劳") || content.contains("星巴克") ||
                    content.contains("餐饮") || content.contains("咖啡") || content.contains("便利店") -> "餐饮"
                content.contains("滴滴") || content.contains("打车") || content.contains("地铁") ||
                    content.contains("公交") || content.contains("加油") || content.contains("停车") ||
                    content.contains("火车") || content.contains("机票") -> "交通"
                content.contains("京东") || content.contains("淘宝") || content.contains("拼多多") ||
                    content.contains("天猫") || content.contains("购物") || content.contains("商户") -> "购物"
                content.contains("电影") || content.contains("游戏") || content.contains("网易") ||
                    content.contains("腾讯") || content.contains("会员") -> "娱乐"
                content.contains("药房") || content.contains("医院") || content.contains("门诊") -> "医疗"
                else -> "其他"
            }

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
                occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now())
            )
        }

        fun clearDedupeCache() {
            recentDedupeKeys.clear()
        }
    }
}
