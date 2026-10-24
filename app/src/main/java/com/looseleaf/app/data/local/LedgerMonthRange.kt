package com.looseleaf.app.data.local

import com.looseleaf.app.utils.FormatUtils
import java.time.YearMonth

/**
 * AND-4：一个自然月在北京时间下的 ISO 半开区间 `[startIso, endIso)`。
 *
 * Room 的查询直接拿这两个字符串做范围比较：
 * - 与库里的 `occurred_at` 同为 `yyyy-MM-dd'T'HH:mm:ss+08:00` 格式；
 * - 半开区间避免「月末 23:59:59」与下月 00:00:00 的边界重复/漏算。
 */
data class MonthRange(
    val startIso: String,
    val endIso: String
)

fun monthRangeOf(yearMonth: YearMonth): MonthRange {
    val start = yearMonth.atDay(1).atStartOfDay()
    val nextMonthStart = yearMonth.plusMonths(1).atDay(1).atStartOfDay()
    return MonthRange(
        startIso = FormatUtils.toBeijingIsoString(start),
        endIso = FormatUtils.toBeijingIsoString(nextMonthStart)
    )
}
