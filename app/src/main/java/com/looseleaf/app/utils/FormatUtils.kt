package com.looseleaf.app.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

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
     * 将任意支持的日期时间串解析为北京时间 LocalDateTime (可空容错，为空或异常时安全兜底当前北京时间)
     */
    fun parseToLocalDateTime(raw: String?): LocalDateTime {
        if (raw.isNullOrBlank()) return LocalDateTime.now(BeijingZone)
        return parseOccurredInstantOrNull(raw)?.atZone(BeijingZone)?.toLocalDateTime()
            ?: LocalDateTime.now(BeijingZone)
    }

    /**
     * 将任意支持的日期时间串解析为北京时间 LocalDateTime (若为空或解析失败返回 null)
     */
    fun parseToLocalDateTimeOrNull(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return parseOccurredInstantOrNull(raw)?.atZone(BeijingZone)?.toLocalDateTime()
    }

    /**
     * 判断给定 LocalDateTime 是否在北京时间的“今天”
     */
    fun isTodayInBeijing(dateTime: LocalDateTime): Boolean {
        val today = LocalDate.now(BeijingZone)
        return dateTime.toLocalDate() == today
    }

    /**
     * 格式化 LocalDateTime 为 YYYY.MM.DD HH:mm
     */
    fun formatLocalDateTimeDisplay(dateTime: LocalDateTime): String {
        return dateTime.format(DisplayDateTimeFormatter)
    }

    /**
     * 将整型「分」转换为「元」字符串展示 (例如 4250 -> "¥ 42.50" 或 "42.50")
     */
    fun centsToYuanString(cents: Long, includeSymbol: Boolean = true): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val remainder = absCents % 100
        val sign = if (cents < 0) "-" else ""
        val formatted = String.format(Locale.US, "%d.%02d", yuan, remainder)
        return if (includeSymbol) "${sign}¥ $formatted" else "$sign$formatted"
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

    /**
     * 当前北京时间日期字符串 (YYYY.MM.DD)
     */
    fun beijingTodayDateString(): String {
        return LocalDate.now(BeijingZone).format(DisplayDateFormatter)
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

    fun formatDate(rawIso: String?): String = formatDisplayDate(rawIso)
    fun formatDateTime(rawIso: String?): String = formatDisplayDateTime(rawIso)

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
     * 自然语言记账解析 (如输入 "42.5 午餐手冲咖啡" 自动拆解)
     */
    fun parseNaturalTransactionInput(input: String): ParsedQuickTransaction {
        val trimmed = input.trim()
        val match = NaturalInputRegex.find(trimmed)

        if (match != null) {
            val numStr = match.groupValues[1].replace(" ", "")
            val rest = match.groupValues[2].trim()

            val isIncome = numStr.startsWith("+")
            val cents = kotlin.math.abs(yuanStringToCents(numStr))

            var category = inferCategoryFromText(rest)
            val title = if (rest.isNotBlank()) rest else category

            return ParsedQuickTransaction(
                amountCents = cents,
                category = category,
                title = title,
                type = if (isIncome) "income" else "expense"
            )
        }

        return ParsedQuickTransaction(
            amountCents = 0L,
            category = "其他",
            title = trimmed
        )
    }

    private fun inferCategoryFromText(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            listOf("饭", "餐", "吃", "麦当劳", "肯德基", "咖啡", "茶", "饮", "早点", "午餐", "晚餐", "夜宵", "买菜").any { lower.contains(it) } -> "餐饮"
            listOf("地铁", "打车", "滴滴", "公交", "火车", "高铁", "机票", "加油", "停车", "过路费", "骑行").any { lower.contains(it) } -> "交通"
            listOf("买", "淘宝", "京东", "超市", "衣服", "鞋", "数码", "手机", "相机", "镜头", "电脑").any { lower.contains(it) } -> "购物"
            listOf("电影", "游戏", "steam", "聚会", "玩", "剧本杀", "门票", "展览").any { lower.contains(it) } -> "娱乐"
            listOf("房租", "水电", "物业", "燃气", "宽带", "话费", "家居").any { lower.contains(it) } -> "住房"
            listOf("书", "课", "学费", "培训", "考试", "订阅", "会员").any { lower.contains(it) } -> "学习"
            listOf("药", "医院", "门诊", "体检", "牙", "检查").any { lower.contains(it) } -> "医疗"
            listOf("工资", "奖金", "收益", "利息", "分红", "报销", "红包").any { lower.contains(it) } -> "收入"
            else -> "其他"
        }
    }
}
