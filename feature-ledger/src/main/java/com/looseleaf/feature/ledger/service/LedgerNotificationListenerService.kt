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
import java.time.LocalDateTime
import java.util.regex.Pattern

/**
 * 账本自动记账通知监听服务 (LedgerNotificationListenerService)
 * 监听微信支付、支付宝、银行等支付通知，解析金额并自动记入账本。
 */
class LedgerNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // 过滤微信、支付宝、银行及短信等
        val isTargetPkg = pkg == "com.tencent.mm" ||
                pkg == "com.eg.android.AlipayGphone" ||
                pkg.contains("bank", ignoreCase = true) ||
                pkg.contains("mms", ignoreCase = true) ||
                pkg.contains("msg", ignoreCase = true)

        if (!isTargetPkg) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text"

        val parsed = parseBillFromText(content, pkg) ?: return

        serviceScope.launch {
            try {
                LedgerRepository.createTransactionOptimistic(parsed)
                Log.i(TAG, "自动记账成功: ${parsed.title} - ${parsed.amount / 100.0}元")
            } catch (e: Exception) {
                Log.e(TAG, "自动记账失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "LedgerNotification"

        // 匹配常见支付与收款正则
        private val EXPENSE_PATTERN = Pattern.compile(
            """(?:支付|付款|消费|扣款|支出|扣除|扣款成功|转账给|扫码付款|付款给)[^\d]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?"""
        )
        private val INCOME_PATTERN = Pattern.compile(
            """(?:收款|到账|收入|存入|入账|收到转账|收款成功)[^\d]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?"""
        )
        private val YUAN_SYMBOL_PATTERN = Pattern.compile(
            """[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)"""
        )

        fun parseBillFromText(content: String, pkg: String = ""): CreateTransactionRequest? {
            var type = "expense"
            var amountCents: Long? = null

            // 1. 优先判断收入
            val incomeMatcher = INCOME_PATTERN.matcher(content)
            if (incomeMatcher.find()) {
                type = "income"
                amountCents = (incomeMatcher.group(1)?.toDoubleOrNull()?.times(100))?.toLong()
            } else {
                // 2. 支出匹配
                val expenseMatcher = EXPENSE_PATTERN.matcher(content)
                if (expenseMatcher.find()) {
                    type = "expense"
                    amountCents = (expenseMatcher.group(1)?.toDoubleOrNull()?.times(100))?.toLong()
                } else {
                    // 3. 兜底匹配货币符号
                    val symbolMatcher = YUAN_SYMBOL_PATTERN.matcher(content)
                    if (symbolMatcher.find()) {
                        amountCents = (symbolMatcher.group(1)?.toDoubleOrNull()?.times(100))?.toLong()
                    }
                }
            }

            if (amountCents == null || amountCents <= 0) return null

            val paymentMethod = when {
                pkg.contains("tencent", ignoreCase = true) || content.contains("微信") -> "微信支付"
                pkg.contains("alipay", ignoreCase = true) || content.contains("支付宝") -> "支付宝"
                pkg.contains("bank", ignoreCase = true) || content.contains("银行") -> "银行卡"
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

            return CreateTransactionRequest(
                amount = amountCents,
                type = type,
                category = category,
                title = title,
                note = "通知监听解析: $content",
                paymentMethod = paymentMethod,
                occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now())
            )
        }

        /**
         * 模拟记一笔 (用于保活检查页和单元测试)
         */
        suspend fun simulatePayment(
            title: String = "微信支付",
            text: String = "向商家付款 28.50 元",
            pkg: String = "com.tencent.mm"
        ): Long? {
            val req = parseBillFromText("$title $text", pkg) ?: return null
            return LedgerRepository.createTransactionOptimistic(req)
        }
    }
}
