package com.looseleaf.app

import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.ui.stream.StreamRowsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-2：时间流行模型增量装配的回归。
 *
 * 核心约束：
 * 1. key 只由业务 id（重复时加出现次序号）决定，顶部插入不改变旧 key；
 * 2. 追加一页只构造这一页的新行，旧行对象原样复用；
 * 3. 刷新窗口时，未变化的行按 key 复用。
 */
class StreamRowsTest {

    private fun entry(
        id: Long,
        day: Int,
        title: String = "条目 $id"
    ) = LifeEntry(
        id = id,
        type = "life",
        title = title,
        content = "",
        occurredAt = "2026-10-${day.toString().padStart(2, '0')}T10:00:00+08:00"
    )

    @Test
    fun `顶部插入新记录后旧 key 保持不变`() {
        val builder = StreamRowsBuilder()
        val base = (1L..10L).map { entry(it, day = 20 - it.toInt()) }
        val oldRows = builder.replace(base)
        val oldKeys = oldRows.map { it.localKey }

        val inserted = listOf(entry(999, day = 21, title = "刚写下的新记录")) + base
        val refreshed = builder.replace(inserted)

        assertEquals(oldKeys, refreshed.drop(1).map { it.localKey })
        assertEquals("999", refreshed.first().localKey)
        // 旧行内容未变 —— 除了第一条的「上一条 key / 间隔天数」边界变化外，
        // 其余行应直接复用旧对象。
        assertSame(oldRows[1], refreshed[2])
    }

    @Test
    fun `重复 id 用出现次序号而不是全局下标`() {
        val builder = StreamRowsBuilder()
        val rows = builder.replace(
            listOf(
                entry(7, day = 21, title = "重复 A"),
                entry(8, day = 20),
                entry(7, day = 19, title = "重复 B")
            )
        )
        assertEquals(listOf("7", "8", "7#1"), rows.map { it.localKey })

        // 顶部插入一条别的记录，重复 id 的 key 仍按「第几次出现」计算
        val refreshed = builder.replace(
            listOf(entry(100, day = 22)) + listOf(
                entry(7, day = 21, title = "重复 A"),
                entry(8, day = 20),
                entry(7, day = 19, title = "重复 B")
            )
        )
        assertEquals(listOf("100", "7", "8", "7#1"), refreshed.map { it.localKey })
    }

    @Test
    fun `翻一页只新增一页的行`() {
        val builder = StreamRowsBuilder()
        val first = (1L..60L).map { entry(it, day = 28) }
        builder.replace(first)
        val firstRows = builder.loadedEntries.toList()
        assertEquals(60, builder.lastStats.added)

        val second = (61L..120L).map { entry(it, day = 18) }
        val added = builder.append(second)

        assertEquals(60, added.size)
        assertEquals(60, builder.lastStats.added)
        assertEquals(60, builder.lastStats.reused)
        assertEquals(120, builder.loadedEntries.size)
        // 旧页对象未被重建
        assertEquals(firstRows, builder.loadedEntries.take(60))
    }

    @Test
    fun `刷新窗口时未变行按 id 复用`() {
        val builder = StreamRowsBuilder()
        val entries = (1L..30L).map { entry(it, day = 28) }
        val first = builder.replace(entries)
        builder.replace(entries)
        assertEquals(0, builder.lastStats.added)
        assertEquals(30, builder.lastStats.reused)
        assertSame(first[10], builder.replace(entries)[10])
    }

    @Test
    fun `首条与新增页首条的间隔天数正确衔接`() {
        val builder = StreamRowsBuilder()
        builder.replace(listOf(entry(1, day = 20), entry(2, day = 18)))
        val added = builder.append(listOf(entry(3, day = 10)))
        assertEquals(8L, added.first().gapDays)
        assertTrue(added.first().previousKey == "2")
    }
}
