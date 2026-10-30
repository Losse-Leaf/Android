package com.looseleaf.app.ui.stream

import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.utils.FormatUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 一次行模型装配的增量统计（便于回归与日志观察）。
 *
 * - [added]：本次真正重新构造的行数；
 * - [reused]：直接复用旧行对象的行数。
 */
internal data class StreamRowsBuildStats(
    val added: Int = 0,
    val reused: Int = 0
)

/**
 * 时间流行模型的增量装配器（AND-2）。
 *
 * 1. key = `serverId ?: localId`（即 [LifeEntry.id]），重复 id 追加出现次序号；
 * 2. 追加一页只构造这一页的新行，旧行对象原样保留；
 * 3. 刷新窗口时按 key 复用：条目本身、间隔天数与上一条 key 都没变就直接复用。
 * 4. 预先解析并计算首月与首日标记，支持按天/按月的大时间分割渲染。
 */
internal class StreamRowsBuilder {

    private val entries = ArrayList<LifeEntry>()
    private val rowsByKey = HashMap<String, StreamRowItem>()
    private val occurrenceById = HashMap<Long, Int>()

    /** 当前窗口内的条目（按列表顺序） */
    val loadedEntries: List<LifeEntry> get() = entries

    /** 当前窗口内的行（按列表顺序） */
    private val rows = ArrayList<StreamRowItem>()

    var lastStats: StreamRowsBuildStats = StreamRowsBuildStats()
        private set

    fun clear() {
        entries.clear()
        rows.clear()
        rowsByKey.clear()
        occurrenceById.clear()
        lastStats = StreamRowsBuildStats()
    }

    /**
     * 用一页完整窗口替换当前内容（首屏 / 同步后刷新 / 筛选切换）。
     *
     * 返回值与 [loadedEntries] 一一对应；未变化的行为旧对象。
     */
    fun replace(newEntries: List<LifeEntry>): List<StreamRowItem> {
        val previousRows = HashMap(rowsByKey)
        entries.clear()
        entries.addAll(newEntries)
        rows.clear()
        rowsByKey.clear()
        occurrenceById.clear()

        var reused = 0
        var previousKey: String? = null
        var previousDate: LocalDate? = null
        var previousYearMonth: YearMonth? = null

        newEntries.forEachIndexed { index, entry ->
            val key = nextKey(entry.id)
            val gapDays = if (index == 0) {
                0L
            } else {
                FormatUtils.calculateDaysBetween(entry.occurredAt, newEntries[index - 1].occurredAt)
            }
            val ldt = FormatUtils.parseToLocalDateTime(entry.occurredAt)
            val date = ldt.toLocalDate()
            val ym = YearMonth.from(date)
            val isFirstOfDay = (previousDate == null || date != previousDate)
            val isFirstOfMonth = (previousYearMonth == null || ym != previousYearMonth)

            val previous = previousRows[key]
            val row = if (
                previous != null &&
                previous.entry == entry &&
                previous.gapDays == gapDays &&
                previous.previousKey == previousKey &&
                previous.isFirstOfDay == isFirstOfDay &&
                previous.isFirstOfMonth == isFirstOfMonth
            ) {
                reused++
                previous
            } else {
                buildRow(entry, key, gapDays, previousKey, isFirstOfDay, isFirstOfMonth, date, ldt)
            }
            rows += row
            rowsByKey[key] = row
            previousKey = key
            previousDate = date
            previousYearMonth = ym
        }
        lastStats = StreamRowsBuildStats(added = newEntries.size - reused, reused = reused)
        return rows.toList()
    }

    /**
     * 追加一页（滚动到底部）。
     *
     * 只构造 [more] 对应的新行并返回；旧行不会被复制或重建，
     * 新页第一行的间隔天数会与旧窗口最后一条正确衔接。
     */
    fun append(more: List<LifeEntry>): List<StreamRowItem> {
        if (more.isEmpty()) {
            lastStats = StreamRowsBuildStats(added = 0, reused = rows.size)
            return emptyList()
        }
        val oldRowCount = rows.size
        val addedRows = ArrayList<StreamRowItem>(more.size)
        var previousEntry = entries.lastOrNull()
        var previousKey = rows.lastOrNull()?.localKey
        var previousDate = rows.lastOrNull()?.let {
            FormatUtils.parseToLocalDateTime(it.entry.occurredAt).toLocalDate()
        }
        var previousYearMonth = previousDate?.let { YearMonth.from(it) }

        more.forEach { entry ->
            val key = nextKey(entry.id)
            val gapDays = previousEntry?.let {
                FormatUtils.calculateDaysBetween(entry.occurredAt, it.occurredAt)
            } ?: 0L
            val ldt = FormatUtils.parseToLocalDateTime(entry.occurredAt)
            val date = ldt.toLocalDate()
            val ym = YearMonth.from(date)
            val isFirstOfDay = (previousDate == null || date != previousDate)
            val isFirstOfMonth = (previousYearMonth == null || ym != previousYearMonth)

            val row = buildRow(entry, key, gapDays, previousKey, isFirstOfDay, isFirstOfMonth, date, ldt)
            addedRows += row
            rows += row
            rowsByKey[key] = row
            entries += entry
            previousEntry = entry
            previousKey = key
            previousDate = date
            previousYearMonth = ym
        }
        lastStats = StreamRowsBuildStats(added = addedRows.size, reused = oldRowCount)
        return addedRows
    }

    private fun nextKey(id: Long): String {
        val ordinal = occurrenceById[id] ?: 0
        occurrenceById[id] = ordinal + 1
        return if (ordinal == 0) id.toString() else "$id#$ordinal"
    }

    private fun buildRow(
        entry: LifeEntry,
        key: String,
        gapDays: Long,
        previousKey: String?,
        isFirstOfDay: Boolean = false,
        isFirstOfMonth: Boolean = false,
        date: LocalDate = FormatUtils.parseToLocalDateTime(entry.occurredAt).toLocalDate(),
        ldt: LocalDateTime = FormatUtils.parseToLocalDateTime(entry.occurredAt)
    ): StreamRowItem {
        val today = LocalDate.now(FormatUtils.BeijingZone)
        val diff = ChronoUnit.DAYS.between(date, today)
        val relativeDay = when (diff) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> null
        }
        val weekdayName = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
            else -> ""
        }
        val timeDisplay = String.format(Locale.US, "%02d:%02d", ldt.hour, ldt.minute)
        val monthDisplay = String.format(Locale.US, "%04d 年 %d 月", date.year, date.monthValue)

        return StreamRowItem(
            entry = entry,
            localKey = key,
            gapDays = gapDays,
            displayTime = FormatUtils.formatDisplayDateTime(entry.occurredAt),
            displayPlace = listOfNotNull(entry.cityName, entry.location)
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            previousKey = previousKey,
            isFirstOfMonth = isFirstOfMonth,
            isFirstOfDay = isFirstOfDay,
            year = date.year,
            monthValue = date.monthValue,
            dayOfMonth = date.dayOfMonth,
            weekdayName = weekdayName,
            relativeDay = relativeDay,
            timeDisplay = timeDisplay,
            monthDisplay = monthDisplay
        )
    }
}
