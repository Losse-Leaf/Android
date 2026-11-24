package com.looseleaf.app.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 散页核心格式化工具 (FormatUtils)
 * 严格遵从 docs/ecosystem.md §5.1 (分位整型标准) 与 docs/backend.md §一.2 (UTC+8 北京时间标准)
 */
object FormatUtils {

    // 唯一权威时区：中国北京时间 UTC+8 (RC-134)
    val BeijingZone: ZoneId = ZoneOffset.ofHours(8)

    private val IsoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val DisplayDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val DisplayDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    private val DisplayTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 预编译时间解析器与自然语言正则，避免在列表高频渲染时重复分配对象
    private val OffsetFormatters = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT
    )
    private val DateTimePatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    )
    private val DateOnlyPatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )
    private val NaturalInputRegex = Regex("""^([+-]?\s*\d+(?:\.\d+)?)\s*(.*)$""")

    /**
     * 当前北京时间 ISO8601 字符串
     */
    fun beijingNowIsoString(): String {
        return LocalDateTime.now(BeijingZone)
            .atZone(BeijingZone)
            .format(IsoFormatter)
    }

    /**
     * 将 LocalDateTime 锚定在北京时间转换为带 +08:00 偏移的标准 ISO8601 串
     */
    fun toBeijingIsoString(dateTime: LocalDateTime): String {
        return dateTime.atZone(BeijingZone).format(IsoFormatter)
    }

    /**
     * 将 Instant 锚定在北京时间转换为带 +08:00 偏移的标准 ISO8601 串
     */
    fun toBeijingIsoString(instant: Instant): String {
        return instant.atZone(BeijingZone).format(IsoFormatter)
    }

    /**
     * 把任意可解析时间串统一成 `yyyy-MM-dd'T'HH:mm:ss+08:00`。
     * 解析失败时**原样返回**（宁可这条记录暂时查不到，也不静默改成现在）。
     */
    fun normalizeBeijingIsoOrRaw(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return beijingNowIsoString()
        return parseOccurredInstantOrNull(trimmed)
            ?.let { toBeijingIsoString(it) }
            ?: trimmed
    }

    /**
     * 将任意支持的日期时间串解析为北京时间 LocalDateTime (可空容错，为空或异常时安全兜底当前北京时间)
     */
    fun parseToLocalDateTime(raw: String?): LocalDateTime {
        if (raw.isNullOrBlank()) return LocalDateTime.now(BeijingZone)
        return parseOccurredInstantOrNull(raw)?.atZone(BeijingZone)?.toLocalDateTime()
            ?: LocalDateTime.now(BeijingZone)
    }

    /**
     * 等宽数字金额格式化（支持千分位、保留两位小数与货币符号）
     */
    fun formatMoney(cents: Long, includeSymbol: Boolean = true): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val remainder = absCents % 100
        val sign = if (cents < 0) "-" else ""
        val formattedYuan = String.format(Locale.CHINA, "%,d", yuan)
        val numStr = "$formattedYuan.${remainder.toString().padStart(2, '0')}"
        return if (includeSymbol) "${sign}¥$numStr" else "$sign$numStr"
    }

    /**
     * 将整型「分」转换为「元」字符串展示 (例如 4250 -> "¥42.50" 或 "42.50")
     */
    fun centsToYuanString(cents: Long, includeSymbol: Boolean = true): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val remainder = absCents % 100
        val sign = if (cents < 0) "-" else ""
        // MOB-P1-31：原先用 String.format —— 每次调用都要新建 Formatter + StringBuilder + String。
        // 该方法在列表每行、每帧都会被调用，改为手写拼接省掉全部中间分配。
        val formatted = "$yuan.${remainder.toString().padStart(2, '0')}"
        return if (includeSymbol) "${sign}¥$formatted" else "$sign$formatted"
    }

    /**
     * 将用户输入的元金额安全换算为整型分。
     * 全程按字符串解析，不经过 Double：避免 "4.35 * 100 = 434.999…" 这类浮点尾差，
     * 也避免超大金额在 2^53 之后丢精度。第三位小数按四舍五入处理。
     */
    fun yuanStringToCents(yuanString: String): Long {
        var cleaned = yuanString.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return 0L

        var negative = false
        if (cleaned.startsWith("-")) {
            negative = true
            cleaned = cleaned.substring(1)
        } else if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }
        if (cleaned.isEmpty()) return 0L

        val parts = cleaned.split(".")
        if (parts.size > 2) return 0L
        val integerPart = parts[0].ifEmpty { "0" }
        val fractionRaw = parts.getOrElse(1) { "" }
        if (integerPart.length > 15) return 0L
        if (integerPart.any { !it.isDigit() }) return 0L
        if (fractionRaw.any { !it.isDigit() }) return 0L

        var cents = (integerPart.toLongOrNull() ?: return 0L) * 100
        val head = fractionRaw.take(2).padEnd(2, '0')
        var fraction = head.toLongOrNull() ?: 0L
        if (fractionRaw.length >= 3 && fractionRaw[2] >= '5') fraction += 1
        if (fraction >= 100) {
            cents += 100
            fraction -= 100
        }
        val total = cents + fraction
        return if (negative) -total else total
    }

    fun yuanToCents(yuanString: String): Long = yuanStringToCents(yuanString)

    /**
     * Sanitizes system-keyboard decimal yuan text input: digits + at most one '.' + at most 2 fractional digits.
     */
    fun sanitizeDecimalYuanInput(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        val dotIdx = filtered.indexOf('.')
        if (dotIdx == -1) return filtered
        val integerPart = filtered.substring(0, dotIdx)
        val fractionalPart = filtered.substring(dotIdx + 1).filter { it.isDigit() }.take(2)
        return "$integerPart.$fractionalPart"
    }

    /**
     * 自定义数字键盘的金额输入唯一入口。
     *
     * 金额行是纯展示（不再挂可编辑输入框），系统键盘与剪贴板无法直接写入金额串；
     * 所有按键、草稿恢复都经由这里的纯字符串变换，保证落库前不会出现多个小数点、
     * 超 2 位小数或超长整数 —— yuanStringToCents 因此不会静默归零。
     * 纯函数，便于回归测试直接断言。
     */
    object AmountInput {
        const val EMPTY = "0"
        private const val MAX_SEGMENT_DIGITS = 8
        private const val MAX_DECIMAL_DIGITS = 2

        private fun lastSegment(current: String): String = current.substringAfterLast("+")

        fun digit(current: String, digit: String): String {
            if (digit.length != 1 || !digit[0].isDigit()) return current
            val base = if (current == EMPTY) "" else current
            val segment = lastSegment(base)
            return when {
                segment.contains('.') -> {
                    val decimals = segment.substringAfter('.')
                    if (decimals.length < MAX_DECIMAL_DIGITS) base + digit else current
                }
                segment.length < MAX_SEGMENT_DIGITS -> base + digit
                else -> current
            }
        }

        fun dot(current: String): String {
            val segment = lastSegment(current)
            return if (segment.contains('.') || segment.isEmpty()) current else "$current."
        }

        fun plus(current: String): String =
            if (current != EMPTY && !current.endsWith("+")) "$current+" else current

        fun backspace(current: String): String {
            if (current.length <= 1) return EMPTY
            return current.dropLast(1).ifEmpty { EMPTY }
        }

        fun clear(): String = EMPTY

        /** 任意外部串（草稿 / 历史值 / 粘贴）都按按键规则重放，产出合法金额串。 */
        fun sanitize(raw: String): String = raw.fold(EMPTY) { acc, c ->
            when {
                c.isDigit() -> digit(acc, c.toString())
                c == '.' -> dot(acc)
                c == '+' -> plus(acc)
                else -> acc
            }
        }

        /** 金额串 → 分。含「+」时按段求和，与自定义键盘的累加语义一致。 */
        fun toCents(current: String): Long = if (current.contains("+")) {
            current.split("+").sumOf { FormatUtils.yuanStringToCents(it.trim()) }
        } else {
            FormatUtils.yuanStringToCents(current)
        }
    }

    /**
     * 弹性时间解析器 (FlexTime)：支持 ISO8601、RFC3339、HTML5、纸本点号格式与纯日期格式。
     * 解析失败返回 null，由调用方决定「显示原文」还是「回退当前时间」，
     * 绝不在解析层默默把一条旧记录改成今天。
     */
    fun parseOccurredInstantOrNull(raw: String?): Instant? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // 1. 带时区偏移的标准 ISO 格式
        for (p in OffsetFormatters) {
            try {
                return Instant.from(p.parse(trimmed))
            } catch (_: Exception) {
                // continue
            }
        }

        // 2. 无时区后缀的日期+时间串，一律按北京时间 (UTC+8) 解释
        for (p in DateTimePatterns) {
            try {
                val ldt = LocalDateTime.parse(trimmed, p)
                return ldt.atZone(BeijingZone).toInstant()
            } catch (_: Exception) {
                // continue
            }
        }

        // 3. 纯日期串 (YYYY.MM.DD / YYYY-MM-DD)，按北京时间当日零点解释
        for (p in DateOnlyPatterns) {
            try {
                val ld = LocalDate.parse(trimmed, p)
                return ld.atStartOfDay(BeijingZone).toInstant()
            } catch (_: Exception) {
                // continue
            }
        }

        return null
    }

    /**
     * 弹性时间解析器（兜底版）：无法解析时返回当前时刻。
     * 仅用于「确实需要一个时间」的场景（如排序键），展示与编辑请用 OrNull 版本。
     */
    fun parseOccurredInstant(raw: String): Instant =
        parseOccurredInstantOrNull(raw) ?: Instant.now()

    /**
     * 格式化展示为 YYYY.MM.DD
     * 解析失败时原样返回，绝不把一条脏数据悄悄显示成「今天」。
     */
    fun formatDisplayDate(rawIso: String?): String {
        if (rawIso.isNullOrBlank()) return ""
        val inst = parseOccurredInstantOrNull(rawIso) ?: return rawIso
        return DisplayDateFormatter.format(inst.atZone(BeijingZone))
    }

    /**
     * 格式化展示为 YYYY.MM.DD HH:mm
     */
    fun formatDisplayDateTime(rawIso: String?): String {
        if (rawIso.isNullOrBlank()) return ""
        val inst = parseOccurredInstantOrNull(rawIso) ?: return rawIso
        return DisplayDateTimeFormatter.format(inst.atZone(BeijingZone))
    }

    /**
     * 格式化展示为 HH:mm
     */
    fun formatDisplayTime(rawIso: String?): String {
        if (rawIso.isNullOrBlank()) return ""
        val inst = parseOccurredInstantOrNull(rawIso) ?: return ""
        return DisplayTimeFormatter.format(inst.atZone(BeijingZone))
    }

    /**
     * 计算两条记录在北京时间下的自然日相隔天数 (用于岁月留白刻度 14 天判定)
     */
    fun calculateDaysBetween(earlierIso: String, laterIso: String): Long {
        val e = parseOccurredInstantOrNull(earlierIso) ?: return 0L
        val l = parseOccurredInstantOrNull(laterIso) ?: return 0L
        val d1 = e.atZone(BeijingZone).toLocalDate()
        val d2 = l.atZone(BeijingZone).toLocalDate()
        return kotlin.math.abs(ChronoUnit.DAYS.between(d1, d2))
    }

    data class ParsedQuickTransaction(
        val amountCents: Long,
        val category: String,
        val title: String,
        val type: String = "expense"
    )

    /**
     * 速记提交金额的唯一裁决规则（RG-27 回归护栏）。
     *
     * 手输金额优先：用户一旦敲了数字键盘，就表示已明确表达金额，备注里解析出的
     * 金额让位，避免「我明明输了 30，却被 42.5 覆盖」。只有手输为空时才采纳语义
     * 解析结果；两者都空或无效则返回 0（不可提交）。
     *
     * 按钮 enabled、分期试算、提交金额必须全部读这个返回值 —— 此前三者各算各的，
     * 导致「备注写 42.5 午餐」时界面提示「点记下自动采纳」、按钮却是灰的。
     */
    fun resolveQuickRecordCents(inputCents: Long, parsedCents: Long?): Long = when {
        inputCents > 0 -> inputCents
        parsedCents != null && parsedCents > 0 -> parsedCents
        else -> 0L
    }

    /**
     * 自然语言记账解析 (如输入 "42.5 午餐手冲咖啡" 自动拆解)
     *
     * @param knownCategories 支出动态分类字典；命中时优先返回字典里的原名
     * @param incomeCategories 收入动态分类字典；解析到 "+金额" 时只从收入分类字典中匹配
     */
    fun parseNaturalTransactionInput(
        input: String,
        knownCategories: List<String> = emptyList(),
        incomeCategories: List<String> = emptyList()
    ): ParsedQuickTransaction {
        val trimmed = input.trim()
        val match = NaturalInputRegex.find(trimmed)

        if (match != null) {
            val numStr = match.groupValues[1].replace(" ", "")
            val rest = match.groupValues[2].trim()

            val isIncome = numStr.startsWith("+")
            val txType = if (isIncome) "income" else "expense"
            val cents = kotlin.math.abs(yuanStringToCents(numStr))

            val scopedCategories = if (isIncome) incomeCategories else knownCategories
            val category = inferCategoryFromText(rest, scopedCategories, type = txType)
            val title = if (rest.isNotBlank()) rest else category

            return ParsedQuickTransaction(
                amountCents = cents,
                category = category,
                title = title,
                type = txType
            )
        }

        return ParsedQuickTransaction(
            amountCents = 0L,
            category = inferCategoryFromText(trimmed, knownCategories, type = "expense"),
            title = trimmed
        )
    }

    /**
     * 根据描述文本与交易类型推断分类。
     *
     * 关键约束：
     * 1. 严格按 [type] ("expense" | "income") 隔离关键词规则，杜绝支出账单被误判成收入分类（例如含「红包」「利息」的支出通知）；
     * 2. 当调用方传入非空 [knownCategories] 时，返回值保证一定落在 [knownCategories] 内，避免后端校验 400（分类不存在于分类字典）。
     */
    fun inferCategoryFromText(
        text: String,
        knownCategories: List<String> = emptyList(),
        type: String = "expense"
    ): String {
        val normalizedType = if (type == "income") "income" else "expense"
        val validKnown = knownCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val lower = text.lowercase(Locale.ROOT)

        // 1. 动态分类字典优先：用户自定义/改名的具体分类直接命中（优先长名称，避免「其他」抢匹配）
        if (validKnown.isNotEmpty()) {
            val directHit = validKnown
                .filterNot { it == "其他" || it == "其他收入" }
                .sortedByDescending { it.length }
                .firstOrNull { text.contains(it, ignoreCase = true) }
            if (directHit != null) return directHit
        }

        // 2. 按收支类型严格隔离关键词，并与后端 DefaultCategoriesMap 对齐
        val candidates: List<String> = if (normalizedType == "income") {
            when {
                listOf("工资", "薪资", "发薪", "薪水", "奖金", "年终奖", "绩效", "津贴", "补贴").any { lower.contains(it) } ->
                    listOf("工资收入", "薪资", "奖金")
                listOf("二手", "闲鱼", "转转", "回血", "出售").any { lower.contains(it) } ->
                    listOf("二手回血")
                listOf("稿费", "稿酬", "设计", "创作", "约稿", "版权").any { lower.contains(it) } ->
                    listOf("稿酬设计", "稿费与创作")
                listOf("理财", "基金", "股票", "股息", "分红", "利息", "收益", "余额宝").any { lower.contains(it) } ->
                    listOf("投资理财", "投资收益")
                listOf("红包", "礼金", "压岁钱", "份子钱").any { lower.contains(it) } ->
                    listOf("礼金红包")
                listOf("退款", "报销", "返现", "退税", "赔付", "退回").any { lower.contains(it) } ->
                    listOf("退款报销")
                else -> listOf("其他收入", "其他")
            }
        } else {
            when {
                listOf("饭", "餐", "吃", "外卖", "美团", "饿了么", "麦当劳", "肯德基", "星巴克", "瑞幸", "喜茶", "奈雪", "霸王茶姬", "咖啡", "茶", "饮", "早点", "早餐", "午餐", "晚餐", "夜宵", "买菜", "便利店", "罗森", "全家", "盒马", "叮咚").any { lower.contains(it) } ->
                    listOf("餐饮")
                listOf("地铁", "打车", "滴滴", "高德", "公交", "火车", "高铁", "12306", "机票", "航空", "加油", "充电", "停车", "过路费", "高速", "骑行", "哈啰", "青桔").any { lower.contains(it) } ->
                    listOf("交通")
                listOf("酒店", "民宿", "景点", "旅游", "度假", "携程", "飞猪", "去哪儿").any { lower.contains(it) } ->
                    listOf("旅行度假", "交通", "娱乐")
                listOf("数码", "手机", "相机", "镜头", "电脑", "耳机", "键盘", "apple", "苹果", "华为", "小米").any { lower.contains(it) } ->
                    listOf("数码器材", "购物")
                listOf("手续费", "利息", "滞纳金", "年费", "分期费").any { lower.contains(it) } ->
                    listOf("利息手续费", "其他")
                listOf("买", "淘宝", "天猫", "京东", "拼多多", "抖音商城", "超市", "沃尔玛", "山姆", "商户", "衣服", "鞋", "护肤", "化妆品", "快递").any { lower.contains(it) } ->
                    listOf("购物")
                listOf("电影", "游戏", "steam", "网易", "腾讯", "聚会", "玩", "剧本杀", "门票", "展览", "演出", "音乐", "bilibili", "b站").any { lower.contains(it) } ->
                    listOf("娱乐")
                listOf("房租", "水电", "电费", "水费", "物业", "燃气", "宽带", "话费", "流量", "充值", "家居", "宜家").any { lower.contains(it) } ->
                    listOf("住房")
                listOf("书", "课", "学费", "培训", "考试", "订阅", "会员", "知识").any { lower.contains(it) } ->
                    listOf("学习")
                listOf("药", "药房", "医院", "门诊", "挂号", "体检", "牙", "检查", "诊所").any { lower.contains(it) } ->
                    listOf("医疗")
                else -> listOf("其他")
            }
        }

        // 3. 若有动态分类字典，确保最终分类 100% 落在有效字典中
        if (validKnown.isNotEmpty()) {
            candidates.firstOrNull { it in validKnown }?.let { return it }
            val fallbackOrder = if (normalizedType == "income") {
                listOf("其他收入", "其他")
            } else {
                listOf("其他")
            }
            fallbackOrder.firstOrNull { it in validKnown }?.let { return it }
            return validKnown.first()
        }

        return candidates.first()
    }
}
