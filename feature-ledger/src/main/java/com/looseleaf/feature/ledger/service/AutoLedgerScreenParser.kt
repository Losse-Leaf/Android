package com.looseleaf.feature.ledger.service

import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal data class ParsedScreenBill(
    val bill: ParsedAutoLedgerBill,
    val screenEventKey: String,
    val eventTimestampMs: Long
)

/**
 * 微信 / 支付宝前台支付与转账完成页结构化解析器 (AutoLedgerScreenParser)
 *
 * 专门补齐通知栏无法覆盖的「微信零钱转账」「好友转账收款（已收钱）」与「微信前台扫码付款完成页」场景：
 * 1. 严格拦截输入页、密码/指纹确认页、未收款的「待你收款」页、普通聊天窗口与历史账单详情页；
 * 2. 兼容微信把货币符号 `￥` 与数字 `50.00` 拆成相邻两个 TextView 的布局；
 * 3. 对「已收钱」页面强制核验「收款时间」在近 5 分钟内，翻看历史聊天转账气泡绝不会误记；
 * 4. 解析结果进入与通知监听相同的 [AutoLedgerDeduplicator]，银行卡/微信通知同时到达时自动去重合并。
 */
internal object AutoLedgerScreenParser {

    internal const val RECEIPT_MAX_AGE_MS = 5 * 60 * 1000L
    private val beijingZone: ZoneId = FormatUtils.BeijingZone

    // 付款/收款尚未完成的页面特征词（出现任一即立刻放弃，绝不提前记账）
    private val PRE_PAYMENT_BLOCK_NODES = listOf(
        "确认支付", "立即支付", "请输入支付密码", "使用指纹支付", "使用面容支付",
        "验证指纹", "指纹支付", "面容支付", "确认转账", "添加转账说明",
        "待你收款", "待你确认收款", "1天内未确认", "退还给对方", "退还给你",
        "取消支付", "支付失败", "转账失败", "余额不足", "忘记密码",
        "更换支付方式", "优先使用此支付方式", "请输入金额"
    )

    // 历史账单详情 / 账单列表页特征词（防止翻看历史记录时重复记账）
    private val HISTORY_BILL_BLOCK_NODES = listOf(
        "账单详情", "全部账单", "收支统计", "交易单号", "商户单号", "转账单号",
        "对订单有疑惑", "常见问题", "定位到聊天位置", "查看往来转账", "申请电子凭证"
    )

    // 普通聊天窗口输入栏特征词（防止好友聊天发送「支付成功 ¥50 完成」伪造账目）
    private val CHAT_COMPOSER_BLOCK_NODES = listOf(
        "按住 说话", "切换到按住说话", "切换到键盘", "更多功能按钮"
    )

    // 金额前导非实付词（跳过页面底部的「立减 ￥0.50」「优惠 ￥2.00」等次要数字）
    private val DISCOUNT_PREFIX_HINTS = listOf(
        "立减", "优惠", "抵扣", "红包", "满", "已优惠", "实省", "金币", "奖励", "返现"
    )

    // 完成页上不属于商户/对方昵称的常见 UI 按钮或标题文本
    private val UI_CHROME_LABELS = setOf(
        "支付成功", "付款成功", "转账成功", "已收钱", "你已收款", "已收款", "完成", "确定", "返回",
        "返回商家", "知道了", "查看详情",
        "关闭", "零钱", "零钱通", "微信支付", "微信转账", "支付宝", "支付方式", "收款方", "收款人",
        "商户", "收款商户", "付款方", "转账时间", "收款时间", "待对方收款", "待朋友收款",
        "待确认收款", "已存入零钱", "已存入零钱，可直接消费", "已存入零钱通", "查看零钱",
        "查看账单", "支付有优惠", "微信支付有优惠", "记一笔", "消费记账", "去看看", "领取"
    )

    private val STANDALONE_CURRENCY_AMOUNT_REGEX = Regex(
        """^[¥￥]\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*元?$"""
    )
    private val PURE_NUMBER_AMOUNT_REGEX = Regex(
        """^([0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{1,2}|[0-9]+\.[0-9]{1,2}|[0-9]{1,6})$"""
    )
    private val WAIT_PAYEE_REGEX = Regex("""^待(.{1,24}?)(?:确认收款|收款)$""")
    private val TRANSFER_TO_PAYEE_REGEX = Regex("""^(?:向|给|转账给|付款给)\s*(.{1,24}?)(?:\s*(?:转账|付款))?$""")

    private val DATE_TIME_REGEX = Regex(
        """(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val CANONICAL_DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parse(
        pkg: String,
        rawTexts: List<String>,
        expenseCategories: List<String> = emptyList(),
        incomeCategories: List<String> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): ParsedScreenBill? {
        val channelGroup = when (pkg) {
            "com.tencent.mm" -> NotificationChannelGroup.WECHAT_SCREEN
            "com.eg.android.AlipayGphone" -> NotificationChannelGroup.ALIPAY_SCREEN
            else -> return null
        }

        val texts = rawTexts.map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.size < 2) return null

        // 1. 强拦截：未完成支付页 / 历史账单详情页 / 聊天输入页
        if (containsAnyHint(texts, PRE_PAYMENT_BLOCK_NODES)) return null
        if (containsAnyHint(texts, HISTORY_BILL_BLOCK_NODES)) return null
        if (containsAnyHint(texts, CHAT_COMPOSER_BLOCK_NODES)) return null

        return when (channelGroup) {
            NotificationChannelGroup.WECHAT_SCREEN -> parseWeChatScreen(
                texts = texts,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                nowMs = nowMs
            )
            NotificationChannelGroup.ALIPAY_SCREEN -> parseAlipayScreen(
                texts = texts,
                expenseCategories = expenseCategories,
                nowMs = nowMs
            )
            else -> null
        }
    }

    private fun parseWeChatScreen(
        texts: List<String>,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        nowMs: Long
    ): ParsedScreenBill? {
        // 场景 A：微信好友转账收款完成页（「已收钱」+「已存入零钱」/「收款时间」）
        val isReceivedMoneyScreen = texts.any {
            it == "已收钱" || it.startsWith("你已收款") || it == "已收款" || it.startsWith("已收钱")
        } && texts.any {
            it.contains("已存入零钱") || it.contains("存入零钱通") || it == "收款时间" || it.contains("收款时间")
        }

        if (isReceivedMoneyScreen) {
            // 强制核验「收款时间」在近 5 分钟内，杜绝用户翻看历史转账气泡触发误记
            val receiptTime = extractLabeledOrLatestTimestamp(texts, preferredLabel = "收款时间")
                ?: return null
            if (abs(nowMs - receiptTime.second) > RECEIPT_MAX_AGE_MS) {
                return null
            }

            val amountHit = extractPrimaryAmount(texts) ?: return null
            val sender = extractWeChatCounterparty(texts, amountIndex = amountHit.first, isIncome = true)
            val category = FormatUtils.inferCategoryFromText(
                text = listOfNotNull(sender, "微信转账收款").joinToString(" "),
                knownCategories = incomeCategories,
                type = "income"
            )
            val title = if (sender != null) "收到转账 · $sender" else "微信转账收款"
            val note = "自动记账 (微信支付 - $category): 微信转账已收钱（收款时间 ${receiptTime.first}）"
            val req = CreateTransactionRequest(
                amount = amountHit.second,
                type = "income",
                category = category,
                title = title,
                note = note,
                paymentMethod = "微信支付",
                occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(beijingZone))
            )
            val qualityScore = NotificationChannelGroup.WECHAT_SCREEN.basePriority * 10 +
                (if (sender != null) 4 else 0) +
                (if (category != "其他" && category != "其他收入") 2 else 0)
            val bill = ParsedAutoLedgerBill(
                request = req,
                channelGroup = NotificationChannelGroup.WECHAT_SCREEN,
                merchant = sender,
                normalizedContent = "wechat_screen:income:${amountHit.second}:${receiptTime.first}",
                qualityScore = qualityScore,
                channelHint = "微信",
                notifyOrigin = AutoLedgerParser.ORIGIN_WALLET
            )
            return ParsedScreenBill(
                bill = bill,
                screenEventKey = "wechat_screen:income:${amountHit.second}:${receiptTime.first}",
                eventTimestampMs = receiptTime.second
            )
        }

        // 场景 B：微信转账发出 / 微信付款完成页（「支付成功」/「转账成功」/「待XX收款」+「完成」）
        val hasFinishButton = texts.any { it == "完成" }
        val hasSuccessAnchor = texts.any {
            it == "支付成功" || it == "转账成功" || it.startsWith("支付成功") || it.startsWith("转账成功")
        }
        val hasPendingRecipientAnchor = texts.any {
            it == "待对方收款" || it == "待朋友收款" || it == "待确认收款" || WAIT_PAYEE_REGEX.matches(it)
        }
        if (!hasSuccessAnchor && !(hasPendingRecipientAnchor && hasFinishButton)) {
            return null
        }

        // 若页面出现明确时间戳，核验必须在近 5 分钟内
        val pageTimestamp = extractLabeledOrLatestTimestamp(texts, preferredLabel = "转账时间")
        if (pageTimestamp != null && abs(nowMs - pageTimestamp.second) > RECEIPT_MAX_AGE_MS) {
            return null
        }

        val amountHit = extractPrimaryAmount(texts) ?: return null
        val payee = extractWeChatCounterparty(texts, amountIndex = amountHit.first, isIncome = false)
        val isTransfer = hasPendingRecipientAnchor || texts.any {
            it.contains("转账") || it.contains("收款")
        }
        val hintText = listOfNotNull(payee, if (isTransfer) "微信转账" else "微信支付").joinToString(" ")
        val category = FormatUtils.inferCategoryFromText(
            text = hintText,
            knownCategories = expenseCategories,
            type = "expense"
        )
        val title = when {
            isTransfer && payee != null -> "转账 · $payee"
            payee != null -> payee
            isTransfer -> "微信转账"
            else -> "微信支付自动记账"
        }
        val summary = if (payee != null) {
            if (isTransfer) "微信转账给 $payee" else "微信支付给 $payee"
        } else {
            if (isTransfer) "微信转账成功" else "微信支付成功"
        }
        val req = CreateTransactionRequest(
            amount = amountHit.second,
            type = "expense",
            category = category,
            title = title,
            note = "自动记账 (微信支付 - $category): $summary",
            paymentMethod = "微信支付",
            occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(beijingZone))
        )
        val qualityScore = NotificationChannelGroup.WECHAT_SCREEN.basePriority * 10 +
            (if (payee != null) 4 else 0) +
            (if (category != "其他" && category != "其他收入") 2 else 0)
        val keySuffix = pageTimestamp?.first ?: (nowMs / 15_000L).toString()
        val bill = ParsedAutoLedgerBill(
            request = req,
            channelGroup = NotificationChannelGroup.WECHAT_SCREEN,
            merchant = payee,
            normalizedContent = "wechat_screen:expense:${amountHit.second}:${payee.orEmpty()}:$keySuffix",
            qualityScore = qualityScore,
            channelHint = "微信",
            notifyOrigin = AutoLedgerParser.ORIGIN_WALLET
        )
        return ParsedScreenBill(
            bill = bill,
            screenEventKey = "wechat_screen:expense:${amountHit.second}:${payee.orEmpty()}:$keySuffix",
            eventTimestampMs = pageTimestamp?.second ?: nowMs
        )
    }

    private fun parseAlipayScreen(
        texts: List<String>,
        expenseCategories: List<String>,
        nowMs: Long
    ): ParsedScreenBill? {
        val hasFinishButton = texts.any {
            it == "完成" || it == "返回商家" || it == "知道了" || it == "查看账单" || it == "查看详情"
        }
        val hasSuccessAnchor = texts.any {
            it == "支付成功" || it == "付款成功" || it == "转账成功"
        }
        if (!hasSuccessAnchor || !hasFinishButton) return null

        val amountHit = extractPrimaryAmount(texts) ?: return null
        val payee = extractWeChatCounterparty(texts, amountIndex = amountHit.first, isIncome = false)
        val isTransfer = texts.any { it.contains("转账") }
        val category = FormatUtils.inferCategoryFromText(
            text = listOfNotNull(payee, if (isTransfer) "支付宝转账" else "支付宝付款").joinToString(" "),
            knownCategories = expenseCategories,
            type = "expense"
        )
        val title = when {
            isTransfer && payee != null -> "转账 · $payee"
            payee != null -> payee
            isTransfer -> "支付宝转账"
            else -> "支付宝自动记账"
        }
        val req = CreateTransactionRequest(
            amount = amountHit.second,
            type = "expense",
            category = category,
            title = title,
            note = "自动记账 (支付宝 - $category): ${payee ?: if (isTransfer) "支付宝转账成功" else "支付宝支付成功"}",
            paymentMethod = "支付宝",
            occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(beijingZone))
        )
        val qualityScore = NotificationChannelGroup.ALIPAY_SCREEN.basePriority * 10 +
            (if (payee != null) 4 else 0) +
            (if (category != "其他" && category != "其他收入") 2 else 0)
        val keySuffix = (nowMs / 15_000L).toString()
        val bill = ParsedAutoLedgerBill(
            request = req,
            channelGroup = NotificationChannelGroup.ALIPAY_SCREEN,
            merchant = payee,
            normalizedContent = "alipay_screen:expense:${amountHit.second}:${payee.orEmpty()}:$keySuffix",
            qualityScore = qualityScore,
            channelHint = "支付宝",
            notifyOrigin = AutoLedgerParser.ORIGIN_WALLET
        )
        return ParsedScreenBill(
            bill = bill,
            screenEventKey = "alipay_screen:expense:${amountHit.second}:${payee.orEmpty()}:$keySuffix",
            eventTimestampMs = nowMs
        )
    }

    /**
     * 提取页面主实付/实收金额。
     * 支持单节点「￥50.00」及微信常见的拆分双节点「￥」+「50.00」，自动跳过「立减/优惠/红包」附注金额。
     */
    private fun extractPrimaryAmount(texts: List<String>): Pair<Int, Long>? {
        for (i in texts.indices) {
            val prev = texts.getOrNull(i - 1).orEmpty()
            if (DISCOUNT_PREFIX_HINTS.any { prev.contains(it) }) continue

            val cur = texts[i]
            STANDALONE_CURRENCY_AMOUNT_REGEX.matchEntire(cur)?.let { m ->
                AutoLedgerParser.parseYuanStringToCents(m.groupValues[1])?.let { cents ->
                    return i to cents
                }
            }

            if ((cur == "¥" || cur == "￥") && i + 1 < texts.size) {
                val next = texts[i + 1]
                PURE_NUMBER_AMOUNT_REGEX.matchEntire(next)?.let { m ->
                    AutoLedgerParser.parseYuanStringToCents(m.groupValues[1])?.let { cents ->
                        return i to cents
                    }
                }
            }
        }
        return null
    }

    private fun extractWeChatCounterparty(
        texts: List<String>,
        amountIndex: Int,
        isIncome: Boolean
    ): String? {
        // 1. 显式键值标签（「收款方」/「收款人」/「付款方」后紧邻的文本节点）
        val labelKeys = if (isIncome) {
            setOf("付款方", "转账人", "来自")
        } else {
            setOf("收款方", "收款人", "收款商户", "商户")
        }
        for (i in 0 until texts.size - 1) {
            if (texts[i] in labelKeys) {
                sanitizeScreenParty(texts[i + 1])?.let { return it }
            }
            for (key in labelKeys) {
                if (texts[i].startsWith("$key：") || texts[i].startsWith("$key:")) {
                    sanitizeScreenParty(texts[i].substring(key.length + 1))?.let { return it }
                }
            }
        }

        // 2. 「待张三确认收款」「向 张三 转账」「转账给 张三」
        if (!isIncome) {
            for (t in texts) {
                WAIT_PAYEE_REGEX.matchEntire(t)?.let { m ->
                    val name = m.groupValues[1].trim()
                    if (name !in setOf("对方", "朋友", "好友", "TA", "ta", "你", "您")) {
                        sanitizeScreenParty(name)?.let { return it }
                    }
                }
                TRANSFER_TO_PAYEE_REGEX.matchEntire(t)?.let { m ->
                    sanitizeScreenParty(m.groupValues[1])?.let { return it }
                }
            }
        }

        // 3. 金额节点前紧邻的非 UI 标签短文本（例如微信支付成功页「支付成功」->「星巴克」->「￥36.50」）
        for (idx in (amountIndex - 1) downTo maxOf(0, amountIndex - 2)) {
            sanitizeScreenParty(texts[idx])?.let { return it }
        }
        return null
    }

    private fun sanitizeScreenParty(raw: String?): String? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty() || cleaned in UI_CHROME_LABELS) return null
        if (cleaned.startsWith("¥") || cleaned.startsWith("￥")) return null
        if (cleaned.contains("已存入") || cleaned.contains("支付方式") || cleaned.contains("有优惠")) return null
        if (DATE_TIME_REGEX.containsMatchIn(cleaned)) return null
        return AutoLedgerParser.sanitizeMerchant(cleaned)
    }

    private fun extractLabeledOrLatestTimestamp(
        texts: List<String>,
        preferredLabel: String
    ): Pair<String, Long>? {
        for (i in texts.indices) {
            val cur = texts[i]
            if (cur == preferredLabel || cur.startsWith(preferredLabel)) {
                parseTimestampFromText(cur)?.let { return it }
                texts.getOrNull(i + 1)?.let { next ->
                    parseTimestampFromText(next)?.let { return it }
                }
            }
        }
        var latest: Pair<String, Long>? = null
        for (t in texts) {
            val parsed = parseTimestampFromText(t) ?: continue
            if (latest == null || parsed.second > latest.second) {
                latest = parsed
            }
        }
        return latest
    }

    private fun parseTimestampFromText(text: String): Pair<String, Long>? {
        val m = DATE_TIME_REGEX.find(text) ?: return null
        val year = m.groupValues[1].toIntOrNull() ?: return null
        val month = m.groupValues[2].toIntOrNull() ?: return null
        val day = m.groupValues[3].toIntOrNull() ?: return null
        val hour = m.groupValues[4].toIntOrNull() ?: return null
        val minute = m.groupValues[5].toIntOrNull() ?: return null
        val second = m.groupValues[6].toIntOrNull() ?: 0
        return runCatching {
            val ldt = LocalDateTime.of(year, month, day, hour, minute, second)
            val epochMs = ldt.atZone(beijingZone).toInstant().toEpochMilli()
            ldt.format(CANONICAL_DT_FORMAT) to epochMs
        }.getOrNull()
    }

    private fun containsAnyHint(texts: List<String>, hints: List<String>): Boolean =
        texts.any { node -> hints.any { hint -> node.contains(hint) } }
}
