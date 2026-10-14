package com.looseleaf.app.utils

import java.time.Instant
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

    /**
     * 当前北京时间 ISO8601 字符串
     */
    fun beijingNowIsoString(): String {
        return LocalDateTime.now(BeijingZone)
            .atZone(BeijingZone)
            .format(IsoFormatter)
    }

    /**
     * 将整型「分」转换为「元」字符串展示 (例如 4250 -> "¥ 42.50" 或 "42.50")
     */
    fun centsToYuanString(cents: Long, includeSymbol: Boolean = true): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val remainder = absCents % 100
        val formatted = String.format(Locale.US, "%s%d.%02d", if (cents < 0) "-" else "", yuan, remainder)
        return if (includeSymbol) "¥ $formatted" else formatted
    }

    /**
     * 将用户输入的元金额安全换算为整型分 (严格执行四舍五入，杜绝浮点精度丢失)
     */
    fun yuanStringToCents(yuanString: String): Long {
        val cleaned = yuanString.trim().replace("¥", "").replace("￥", "").replace(",", "")
        if (cleaned.isBlank()) return 0L
        return try {
            val d = cleaned.toDouble()
            (d * 100.0).roundToLong()
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 弹性时间解析器 (FlexTime)：支持 ISO8601、RFC3339、HTML5、纸本点号格式
     */
    fun parseOccurredInstant(raw: String): Instant {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Instant.now()

        // 尝试以多种可能格式解析
        val patterns = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )

        for (p in patterns) {
            try {
                return if (p == DateTimeFormatter.ISO_OFFSET_DATE_TIME || p == DateTimeFormatter.ISO_INSTANT) {
                    Instant.from(p.parse(trimmed))
                } else {
                    val ldt = LocalDateTime.parse(trimmed, p)
                    ldt.atZone(BeijingZone).toInstant()
                }
            } catch (_: Exception) {
                // continue next
            }
        }
        return Instant.now()
    }

    /**
     * 格式化展示为 YYYY.MM.DD
     */
    fun formatDisplayDate(rawIso: String): String {
        return try {
            val inst = parseOccurredInstant(rawIso)
            DisplayDateFormatter.format(inst.atZone(BeijingZone))
        } catch (_: Exception) {
            rawIso
        }
    }

    /**
     * 格式化展示为 YYYY.MM.DD HH:mm
     */
    fun formatDisplayDateTime(rawIso: String): String {
        return try {
            val inst = parseOccurredInstant(rawIso)
            DisplayDateTimeFormatter.format(inst.atZone(BeijingZone))
        } catch (_: Exception) {
            rawIso
        }
    }

    /**
     * 格式化展示为 HH:mm
     */
    fun formatDisplayTime(rawIso: String): String {
        return try {
            val inst = parseOccurredInstant(rawIso)
            DisplayTimeFormatter.format(inst.atZone(BeijingZone))
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 计算两条记录相隔天数 (用于岁月留白刻度 14 天判定)
     */
    fun calculateDaysBetween(earlierIso: String, laterIso: String): Long {
        return try {
            val i1 = parseOccurredInstant(earlierIso)
            val i2 = parseOccurredInstant(laterIso)
            kotlin.math.abs(ChronoUnit.DAYS.between(i1, i2))
        } catch (_: Exception) {
            0L
        }
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
        val regex = Regex("""^([+-]?\s*\d+(?:\.\d+)?)\s*(.*)$""")
        val match = regex.find(trimmed)

        if (match != null) {
            val numStr = match.groupValues[1].replace(" ", "")
            val rest = match.groupValues[2].trim()

            val isIncome = numStr.startsWith("+")
            val cents = (kotlin.math.abs(numStr.toDoubleOrNull() ?: 0.0) * 100).roundToLong()

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
