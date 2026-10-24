package com.looseleaf.app.ui.stream

import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.utils.FormatUtils

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
 * 时间流行模型的**增量装配器**（AND-2）。
 *
 * 改造前每次 `loadedEntries` 变化都会整表 `mapIndexed`：
 * 翻一页 60 条也要把已有的 N 条全部重新算一遍间隔天数 / 时间戳 / 属地，
 * 而且 key 里带着全局 index —— 顶部插入一条新记录，后面所有 key 都会变，
 * LazyColumn 因此把所有行当成新项、全部重建。
 *
 * 现在的约定：
 * 1. key = `serverId ?: localId`（即 [LifeEntry.id]），重复 id 追加出现次序号；
 * 2. 追加一页只构造这一页的新行，旧行对象原样保留；
 * 3. 刷新窗口时按 key 复用：条目本身、间隔天数与上一条 key 都没变就直接复用。
 *
 * 这个类不依赖 Compose / Android，可直接在 JVM 单测里验证。
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
     * 用一页**完整窗口**替换当前内容（首屏 / 同步后刷新 / 筛选切换）。
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
        newEntries.forEachIndexed { index, entry ->
            val key = nextKey(entry.id)
            val gapDays = if (index == 0) {
                0L
            } else {
                FormatUtils.calculateDaysBetween(entry.occurredAt, newEntries[index - 1].occurredAt)
            }
            val previous = previousRows[key]
            val row = if (
                previous != null &&
                previous.entry == entry &&
                previous.gapDays == gapDays &&
                previous.previousKey == previousKey
            ) {
                reused++
                previous
            } else {
                buildRow(entry, key, gapDays, previousKey)
            }
            rows += row
            rowsByKey[key] = row
            previousKey = key
        }
        lastStats = StreamRowsBuildStats(added = newEntries.size - reused, reused = reused)
        // 返回快照而不是内部可变列表：调用方（以及单测）持有旧窗口时，
        // 下一次 replace/append 不会把它悄悄清空。
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

        more.forEach { entry ->
            val key = nextKey(entry.id)
            val gapDays = previousEntry?.let {
                FormatUtils.calculateDaysBetween(entry.occurredAt, it.occurredAt)
            } ?: 0L
            val row = buildRow(entry, key, gapDays, previousKey)
            addedRows += row
            rows += row
            rowsByKey[key] = row
            entries += entry
            previousEntry = entry
            previousKey = key
        }
        lastStats = StreamRowsBuildStats(added = addedRows.size, reused = oldRowCount)
        return addedRows
    }

    private fun nextKey(id: Long): String {
        val ordinal = occurrenceById[id] ?: 0
        occurrenceById[id] = ordinal + 1
        // 第一项维持裸 id，重复项才追加序号；序号是「同一 id 的第几次出现」，
        // 与它在整个列表中的全局下标无关，因此顶部插入不会改变旧 key。
        return if (ordinal == 0) id.toString() else "$id#$ordinal"
    }

    private fun buildRow(
        entry: LifeEntry,
        key: String,
        gapDays: Long,
        previousKey: String?
    ): StreamRowItem = StreamRowItem(
        entry = entry,
        localKey = key,
        gapDays = gapDays,
        displayTime = FormatUtils.formatDisplayDateTime(entry.occurredAt),
        displayPlace = listOfNotNull(entry.cityName, entry.location)
            .distinct()
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        previousKey = previousKey
    )
}
