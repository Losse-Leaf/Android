package com.looseleaf.feature.ledger.service

import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDateTime
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 自动记账白名单结构化解析引擎 (AutoLedgerParser)
 *
 * 采用「辅助从句清洗 -> 强规则非成交拦截 -> 按渠道白名单模板匹配 -> 商户与分类校验」四段式管线：
 * 1. 先剥离真实成交回执末尾的括号抵扣说明（如「含红包抵扣2元」）与余额/可用额度后缀，
 *    避免真实消费被误杀或误抓余额数字；
 * 2. 严格拦截支付宝/微信/银行发出的送红包、优惠券、体验金、周期总结（如「上周总结」）、
 *    账单还款提醒、收益日报、失败/待付订单与本人账户互转；
 * 3. 动作词与金额之间只允许白名单安全连接词（SAFE_GAP），绝不允许跨过「红包」「券」「满」「立减」等词抓数；
 * 4. 仅在明确白名单渠道模板下提取金额与商户，结合服务端动态分类字典生成合法请求。
 */
internal object AutoLedgerParser {

    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.xiaomi.mms",
        "com.huawei.message",
        "com.coloros.mms",
        "com.oplus.mms",
        "com.vivo.mms",
        "com.meizu.mms",
        "com.oneplus.mms"
    )

    private val BANK_PACKAGE_PREFIXES = listOf(
        "com.icbc",
        "com.cmbchina",
        "com.chinamworld",
        "com.boc",
        "com.abchina",
        "com.bankcomm",
        "com.spdb",
        "com.cib",
        "com.cebbank",
        "com.pingan",
        "com.citicbank",
        "com.ecitic",
        "com.hxb",
        "com.cgbchina",
        "com.psbc",
        "com.cmbc",
        "com.bankofbeijing",
        "com.bosc"
    )

    // 微信仅放行官方支付/收款渠道通知，屏蔽普通好友/群聊里提到消费金额的聊天消息
    private val WECHAT_PAYMENT_CHANNEL_KEYWORDS = listOf(
        "微信支付", "微信收款助手", "微信收款商业版", "服务通知", "微信零钱", "零钱通",
        "支付凭证", "扣费凭证", "收款到账", "面对面收款", "赞赏码"
    )

    private val SMS_FINANCIAL_KEYWORDS = listOf(
        "银行", "支付宝", "微信支付", "银联", "云闪付", "信用卡", "储蓄卡", "借记卡"
    )

    // 剥离真实支付回执里括号中的抵扣/立减/红包附注（如「付款36.50元（含红包抵扣2.00元）」）
    private val PAREN_DISCOUNT_CLAUSE_REGEX = Regex(
        """[（(]\s*(?:含|已用|使用|其中|已|本次)?[^（）()]{0,28}(?:红包|立减|优惠|抵扣|积分|满减)[^（）()]{0,20}[）)]"""
    )

    // 剥离真实银行动账短信末尾带数字的余额/可用额度后缀（不剥离「余额不足」）
    private val TRAILING_BALANCE_CLAUSE_REGEX = Regex(
        """[，,。；;]?\s*(?:当前|账户|活期|卡内|剩余|可用)?(?:余额|可用额度|信用额度)\s*(?:为|人民币|RMB|CNY|¥|￥|:|：|\s)*[0-9,]+(?:\.[0-9]{1,2})?\s*元?"""
    )

    // 无论原文还是清洗后，只要出现即立刻拦截的硬性非成交关键词（验证码/总结/账单/失败/互转）
    private val HARD_RAW_BLOCK_KEYWORDS = listOf(
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
        // 还款提醒与信用额度调整通知
        "本期应还", "本月应还", "待还款", "待还金额", "还款提醒", "到期提醒", "即将到期",
        "最后还款日", "最低还款", "预约还款", "自动还款提醒", "账单提醒",
        "额度调整", "额度提升", "授信额度", "提额", "临时额度", "信用分", "芝麻信用", "芝麻分", "信用守护",
        // 收益日报与失败/待付/取消订单
        "收益日报", "昨日收益", "七日年化", "万份收益", "预期收益", "基金估值", "行情提醒",
        "涨跌幅", "付款失败", "支付失败", "扣款失败", "交易失败", "转账失败", "收款失败",
        "交易关闭", "订单已取消", "已取消订单", "余额不足", "密码错误", "待支付", "待付款",
        "即将自动扣款", "扣款预告", "续费提醒", "即将扣费",
        // 本人账户内部流转（提现/充值/余额宝零钱通互转不属于实际消费或收入）
        "零钱提现", "提现到账", "提现成功", "余额提现", "转入余额宝", "转入零钱通",
        "零钱通转入", "余额宝转入", "零钱充值", "余额充值", "本人账户互转"
    )

    // 针对清洗后正文拦截：营销、平台赠送红包、优惠券、体验金、互动小游戏等
    private val PROMO_AND_VOUCHER_KEYWORDS = listOf(
        "送你", "送您", "赠送", "赠您", "恭喜", "获得", "已发放", "发放了", "派发", "到账啦",
        "待领取", "立即领取", "点击领取", "免费领", "领红包", "扫码领", "天天领", "每天领", "最高领",
        "红包待领", "红包即将过期", "红包快过期", "红包雨", "瓜分红包", "拆红包", "开红包",
        "红包已", "个红包", "元红包", "红包金额", "无门槛", "抵扣金",
        "付款红包", "支付红包", "消费红包", "实体店红包", "线下红包", "通用红包", "到店红包",
        "现金红包", "抵扣红包", "立减红包", "随机红包", "惊喜红包", "专属红包", "专享红包",
        "体验金", "鼓励金", "奖励金", "立减金", "消费金", "福利金", "返现红包",
        "优惠券", "代金券", "消费券", "折扣券", "立减券", "满减券", "打车券", "话费券",
        "充值券", "通用券", "神券", "专享券", "满减", "卡包",
        "去使用", "立即使用", "马上用", "去核销", "有效期", "即将过期", "到期作废",
        "今日可用", "今天可用", "明日可用", "下单返",
        "支付有礼", "消费有礼", "付款有礼", "有礼", "福利", "抽奖", "签到",
        "限时免费", "限时领取", "限时抢", "活动通知", "邀请好友", "商品降价", "降价提醒",
        "免费开通", "邀你开通", "会员到期提醒", "会员即将到期", "积分到期", "积分兑换",
        "蚂蚁森林", "蚂蚁庄园", "芭芭农场", "集五福", "理财推荐", "精选基金"
    )

    private val AGGREGATE_COUNT_PATTERN = Regex(
        """(?:共|累计|合计|总计)\s*\d+\s*笔|\d+\s*笔(?:消费|支出|交易|账单|收入|付款|收款)"""
    )

    // 拦截「满10减2」「1.50元红包」「红包 ¥2.00」「3元优惠券」等任意平台赠券/赠红包表达
    private val PROMO_AMOUNT_PATTERNS = listOf(
        Regex("""满\s*\d+(?:\.\d+)?\s*元?\s*(?:减|立减|可用|抵)"""),
        Regex("""\d+(?:\.\d{1,2})?\s*元?\s*(?:的)?(?:[\u4e00-\u9fa5]{0,4})?(?:红包|优惠券|代金券|消费券|立减券|满减券|立减金|体验金|抵扣金|折扣券|通用券|充值券)"""),
        Regex("""(?:红包|优惠券|代金券|消费券|立减券|满减券|立减金|体验金|抵扣金)\s*[:：为]?\s*[¥￥]?\s*\d+(?:\.\d{1,2})?""")
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    // ── 白名单金额与安全连接词 ──
    private const val AMOUNT_NUM = """[0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?"""

    // 严格金额段：
    // 1) 带明确货币符号/币种前缀 (¥12 / RMB 88.50 / 人民币36.50元)
    // 2) 带「元/块」单位 (36.50元 / 100 元)
    // 3) 显式小数且不跟日期/笔数/折扣/百分比单位 (消费 58.00)
    private const val STRICT_AMOUNT =
        """(?:(?:¥|￥|人民币|RMB|CNY)\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])|($AMOUNT_NUM)(?![\d.])\s*[元块]|([0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{1,2}|[0-9]+\.[0-9]{1,2})(?![\d.]|\s*[月日年时分秒笔次个张件期折%％万亿]))"""

    // 白名单安全连接词：动作词与金额之间仅允许空白/冒号/「了|成功|金额」/括号渠道标(如(微信支付))/币种前缀，
    // 绝不允许跨过「红包」「券」「立减」「满」「降价提醒」等无关名词！
    private const val SAFE_GAP =
        """[\s:：]*(?:了|成功|金额)?[\s:：]*(?:\([^()¥￥元]{1,20}\)|（[^（）¥￥元]{1,20}）)?[\s:：]*(?:人民币|RMB|CNY|¥|￥)?\s*"""

    // 1. 明确收入模板（负向前瞻排除「收款方/收款人/收款商户/收款码」）
    private val INCOME_DIRECT_PATTERN = Pattern.compile(
        """(?:支付宝到账|支付宝收款|微信收款|收款成功|收款到账|扫码收款|二维码收款|面对面收款|收到转账|向你转账|向您转账|退款到账|收到退款|已退款|已收款|工资入账|代发工资|转入|入账|存入|到账|收款(?![方人账商码])|收入)$SAFE_GAP$STRICT_AMOUNT"""
    )

    private val INCOME_REVERSE_PATTERN = Pattern.compile(
        """(?:一笔|收到)\s*$STRICT_AMOUNT\s*的?(?:收款|收入|转账|退款)"""
    )

    // 2. 明确支出模板：带商户/收款人（「向星巴克付款36.50元」「付款给张三 50元」）
    private val EXPENSE_PAYEE_PATTERN = Pattern.compile(
        """(?:你已向|您已向|已向|向|付款给|转账给)\s*([^，。！？；：\n\d¥￥()（）]{1,20}?)\s*(?:付款|支付|转账|消费)?$SAFE_GAP$STRICT_AMOUNT"""
    )

    private val EXPENSE_REVERSE_PATTERN = Pattern.compile(
        """(?:一笔|成功)\s*$STRICT_AMOUNT\s*的?(?:支出|消费|付款|扣款)"""
    )

    // 3. 明确支出直出模板（排除品牌名「支付宝」与非紧邻金额的「微信支付」抬头）
    private val EXPENSE_DIRECT_PATTERN = Pattern.compile(
        """(?:(?:支付成功|付款成功|扣款成功|扣费成功|消费成功|扫码付款|扫码支付|花呗消费|花呗付款|余额宝支付|亲情卡消费|快捷支付|刷卡消费|自动扣款|网上支付|POS消费|转账支出|实付|已付|(?<!微信)支付(?!宝)|付款|消费|扣款|扣费|支出|扣除|转出)$SAFE_GAP|微信支付[\s:：]{1,2})$STRICT_AMOUNT"""
    )

    // 4. 受控兜底：仅当通知带明确成交凭证词且出现「金额/¥」字段时启用
    private val INCOME_CUE_PATTERN = Pattern.compile(
        """收款成功|到账成功|收款到账|已收款|收款通知|退款成功"""
    )
    private val EXPENSE_CUE_PATTERN = Pattern.compile(
        """支付成功|付款成功|扣款成功|扣费成功|消费成功|交易成功|已支付|已付款|已扣款|支付凭证|扣费凭证|动账通知|动账提醒"""
    )
    private val CUE_AMOUNT_PATTERN = Pattern.compile(
        """(?:[¥￥]|(?:付款|支付|交易|扣款|收款)?金额[\s:：¥￥人民币]{0,6})\s*($AMOUNT_NUM)(?![\d.])\s*[元块]?(?![万亿折%％])"""
    )

    // 商户抽取正则（按精准度排序）
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""(?:你已向|您已向|已向)\s*([^，。！？；：\n\d¥￥()（）]{1,20}?)\s*(?:付款|支付|转账|消费)"""),
        Pattern.compile("""(?:付款给|转账给)\s*([^，。！？；：\n\d¥￥()（）]{1,20}?)(?:[\s，。！？；：\d¥￥]|$)"""),
        Pattern.compile("""(?:收款方|收款商户|交易商户|消费商户|商户|商家)\s*[:：]\s*([^，。！？；\n\d¥￥()（）]{1,20})"""),
        Pattern.compile("""(?:财付通|支付宝|微信支付|美团支付|网联)[-—]\s*([^，。！？；\n()（）¥￥]{2,18})"""),
        Pattern.compile("""(?:在)\s*([^，。！？；\n\d¥￥()（）]{2,16}?)\s*(?:快捷支付|刷卡消费|消费|付款|支付)""")
    )

    // 支付中介/通道词不算具体商户名，避免跨平台去重时把「财付通」和「星巴克」误判为两家不同商户
    private val NON_MERCHANT_LABELS = setOf(
        "商户", "商家", "平台", "银行", "余额", "账户", "微信", "微信支付", "支付宝",
        "财付通", "银联", "云闪付", "网联", "花呗", "余额宝", "零钱", "零钱通", "储蓄卡", "信用卡"
    )

    fun classifyPackage(pkg: String): NotificationChannelGroup? {
        if (pkg.isBlank()) return null
        return when {
            pkg == "com.tencent.mm" -> NotificationChannelGroup.WECHAT
            pkg == "com.eg.android.AlipayGphone" -> NotificationChannelGroup.ALIPAY
            pkg == "com.unionpay" -> NotificationChannelGroup.UNIONPAY
            pkg in SMS_PACKAGES -> NotificationChannelGroup.BANK_SMS
            BANK_PACKAGE_PREFIXES.any { pkg.startsWith(it) } || pkg.contains(".bank.") ->
                NotificationChannelGroup.BANK_APP
            else -> null
        }
    }

    fun isAllowedPackage(pkg: String): Boolean = classifyPackage(pkg) != null

    fun stripAuxiliaryClauses(raw: String): String {
        var cleaned = PAREN_DISCOUNT_CLAUSE_REGEX.replace(raw, " ")
        cleaned = TRAILING_BALANCE_CLAUSE_REGEX.replace(cleaned, "")
        return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    fun isVerificationCodeOrSpam(content: String): Boolean {
        if (content.isBlank()) return true
        val rawLower = content.lowercase(Locale.ROOT)
        if (HARD_RAW_BLOCK_KEYWORDS.any { rawLower.contains(it) }) return true
        if (AGGREGATE_COUNT_PATTERN.containsMatchIn(content)) return true

        val sanitized = stripAuxiliaryClauses(content)
        val sanitizedLower = sanitized.lowercase(Locale.ROOT)
        if (PROMO_AND_VOUCHER_KEYWORDS.any { sanitizedLower.contains(it) }) return true
        if (PROMO_AMOUNT_PATTERNS.any { it.containsMatchIn(sanitized) }) return true

        return false
    }

    fun normalizeContentForDedupe(content: String): String =
        content.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

    fun parseYuanStringToCents(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        return FormatUtils.yuanStringToCents(str).takeIf { it > 0L }
    }

    fun parse(
        content: String,
        pkg: String = "",
        expenseCategories: List<String> = emptyList(),
        incomeCategories: List<String> = emptyList()
    ): ParsedAutoLedgerBill? {
        if (isVerificationCodeOrSpam(content)) return null

        val channelGroup = classifyPackage(pkg) ?: inferChannelFromContent(content)
        val sanitized = stripAuxiliaryClauses(content)
        if (sanitized.isBlank()) return null

        // 渠道前置准入守卫
        when (channelGroup) {
            NotificationChannelGroup.BANK_SMS -> {
                if (SMS_FINANCIAL_KEYWORDS.none { sanitized.contains(it) }) return null
            }
            NotificationChannelGroup.WECHAT -> {
                if (WECHAT_PAYMENT_CHANNEL_KEYWORDS.none { sanitized.contains(it) }) return null
            }
            else -> Unit
        }

        val matched = matchStructuredBill(sanitized) ?: return null
        val type = matched.first
        val amountCents = matched.second
        val payeeFromTemplate = matched.third

        if (amountCents <= 0L) return null

        val merchant = sanitizeMerchant(payeeFromTemplate) ?: extractMerchant(sanitized)

        val paymentMethod = when {
            channelGroup == NotificationChannelGroup.WECHAT || sanitized.contains("微信") -> "微信支付"
            channelGroup == NotificationChannelGroup.ALIPAY || sanitized.contains("支付宝") -> "支付宝"
            channelGroup == NotificationChannelGroup.UNIONPAY ||
                channelGroup == NotificationChannelGroup.BANK_APP ||
                sanitized.contains("银行") ||
                sanitized.contains("信用卡") ||
                sanitized.contains("储蓄卡") -> "银行卡"
            else -> "其他"
        }

        val scopedCategories = if (type == "income") incomeCategories else expenseCategories
        val categoryHintText = if (merchant != null) "$merchant $sanitized" else sanitized
        val category = FormatUtils.inferCategoryFromText(
            text = categoryHintText,
            knownCategories = scopedCategories,
            type = type
        )

        val title = when {
            type == "income" && merchant != null -> "收到款项 · $merchant"
            type == "income" -> "自动记账: 收到款项"
            merchant != null -> merchant
            paymentMethod == "微信支付" -> "微信支付自动记账"
            paymentMethod == "支付宝" -> "支付宝自动记账"
            paymentMethod == "银行卡" -> "银行卡自动记账"
            else -> "自动记账"
        }

        // 脱敏处理备注：隐藏 4 位以上连续数字（卡号/账号）
        val desensitizedContent = content.replace(Regex("""\d{4,}"""), "****")
        val sanitizedNote = "自动记账 ($paymentMethod - $category): $desensitizedContent"

        val request = CreateTransactionRequest(
            amount = amountCents,
            type = type,
            category = category,
            title = title,
            note = sanitizedNote,
            paymentMethod = paymentMethod,
            occurredAt = FormatUtils.toBeijingIsoString(LocalDateTime.now(FormatUtils.BeijingZone))
        )

        // 质量分：用于跨平台多提醒到达时的优先级仲裁（钱包 > 银行 App > 短信；有商户/具体分类加分）
        val qualityScore = channelGroup.basePriority * 10 +
            (if (merchant != null) 4 else 0) +
            (if (category != "其他" && category != "其他收入") 2 else 0)

        return ParsedAutoLedgerBill(
            request = request,
            channelGroup = channelGroup,
            merchant = merchant,
            normalizedContent = normalizeContentForDedupe(content),
            qualityScore = qualityScore
        )
    }

    private fun inferChannelFromContent(content: String): NotificationChannelGroup = when {
        content.contains("微信") -> NotificationChannelGroup.WECHAT
        content.contains("支付宝") -> NotificationChannelGroup.ALIPAY
        content.contains("云闪付") || content.contains("银联") -> NotificationChannelGroup.UNIONPAY
        content.contains("银行") || content.contains("信用卡") || content.contains("储蓄卡") ->
            NotificationChannelGroup.BANK_APP
        else -> NotificationChannelGroup.BANK_SMS
    }

    private fun matchStructuredBill(sanitized: String): Triple<String, Long, String?>? {
        // 1. 明确收入直出模板
        INCOME_DIRECT_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("income", it, null) }
            }
        }
        INCOME_REVERSE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("income", it, null) }
            }
        }

        // 2. 明确支出（带商户/收款人）模板
        EXPENSE_PAYEE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                val payee = m.group(1)?.trim()
                val isPromoPayee = payee != null && (
                    payee.contains("红包") ||
                        payee.contains("券") ||
                        payee.contains("优惠") ||
                        payee.contains("立减")
                    )
                if (!isPromoPayee) {
                    extractFirstAmount(m, startGroup = 2)?.let { return Triple("expense", it, payee) }
                }
            }
        }

        // 3. 明确支出反序模板（「你有一笔 36.50 元的支出」）
        EXPENSE_REVERSE_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 4. 明确支出直出模板
        EXPENSE_DIRECT_PATTERN.matcher(sanitized).let { m ->
            if (m.find()) {
                extractFirstAmount(m)?.let { return Triple("expense", it, null) }
            }
        }

        // 5. 受控成交凭证兜底（绝不对无成交关键词的普通通知按 ¥ 盲目记账）
        if (INCOME_CUE_PATTERN.matcher(sanitized).find()) {
            CUE_AMOUNT_PATTERN.matcher(sanitized).let { m ->
                if (m.find()) {
                    extractFirstAmount(m)?.let { return Triple("income", it, null) }
                }
            }
        }
        if (EXPENSE_CUE_PATTERN.matcher(sanitized).find()) {
            CUE_AMOUNT_PATTERN.matcher(sanitized).let { m ->
                if (m.find()) {
                    extractFirstAmount(m)?.let { return Triple("expense", it, null) }
                }
            }
        }

        return null
    }

    private fun extractFirstAmount(matcher: Matcher, startGroup: Int = 1): Long? {
        for (idx in startGroup..matcher.groupCount()) {
            val raw = matcher.group(idx)
            if (!raw.isNullOrBlank()) {
                return parseYuanStringToCents(raw)
            }
        }
        return null
    }

    internal fun extractMerchant(sanitized: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(sanitized)
            if (matcher.find()) {
                val candidate = sanitizeMerchant(matcher.group(1))
                if (candidate != null) return candidate
            }
        }
        return null
    }

    private fun sanitizeMerchant(raw: String?): String? {
        val cleaned = raw
            ?.trim()
            ?.trim('：', ':', '，', ',', '。', ' ', '-', '—', '【', '】', '(', ')', '（', '）')
            .orEmpty()
        if (cleaned.length < 2 || cleaned.length > 20) return null
        if (cleaned in NON_MERCHANT_LABELS) return null
        if (cleaned.contains("红包") || cleaned.contains("优惠") || cleaned.contains("立减") || cleaned.contains("余额")) {
            return null
        }
        if (cleaned.endsWith("卡") || cleaned.contains("尾号") || cleaned.contains("账户")) {
            return null
        }
        return cleaned
    }
}
