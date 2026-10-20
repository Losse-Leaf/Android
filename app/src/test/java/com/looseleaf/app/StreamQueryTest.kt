package com.looseleaf.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 时间流**分页查询**的语义回归 (MOB-P1-71)
 *
 * 这一轮把过滤从 Kotlin 内存筛选改成了 SQL 下推，
 * 「自然下渗分类」的口径（带属地归游历、带造物归造物、带心愿归心愿）
 * 从此**只有 SQL 这一份事实源** —— 一旦写错，用户就会「找不到自己的记录」。
 * 这里用 Robolectric 在 JVM 上跑真实的 SQLite，把六种筛选口径全部钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(
        id: Long,
        type: String = "life",
        occurredAt: String = "2026-03-0${id}T10:00:00+08:00",
        cityCode: String? = null,
        projectId: Long? = null,
        wishId: Long? = null,
        title: String = "条目 $id"
    ) = CachedEntryEntity(
        localId = id,
        serverId = id,
        type = type,
        title = title,
        content = "内容 $id",
        occurredAt = occurredAt,
        cityCode = cityCode,
        cityName = cityCode?.let { "城市$it" },
        projectId = projectId,
        wishId = wishId,
        attachmentsJson = "[]",
        syncStatus = "SYNCED"
    )

    private fun seed() {
        val dao = db.entryDao()
        runBlocking {
            dao.insertAll(
                listOf(
                    entry(1, type = "life", title = "纯日常"),
                    entry(2, type = "travel", title = "游历条目"),
                    entry(3, type = "life", cityCode = "330100", title = "日常但有属地"),
                    entry(4, type = "project", title = "造物条目"),
                    entry(5, type = "life", projectId = 7L, title = "日常但挂着造物"),
                    entry(6, type = "wish", title = "心愿条目"),
                    entry(7, type = "life", wishId = 9L, title = "日常但挂着心愿"),
                    entry(8, type = "daily", title = "另一种日常"),
                    entry(9, type = "life", occurredAt = "2025-05-01T10:00:00+08:00", title = "旧年份")
                )
            )
        }
    }

    @Test
    fun `all 口径返回全部并按发生时间倒序`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("all", null, 100, 0) }
        assertEquals(9, rows.size)
        // 2026 的条目应当排在 2025 之前
        assertTrue(rows.first().occurredAt > rows.last().occurredAt)
    }

    @Test
    fun `travel 口径包含游历与带属地的日常`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("travel", null, 100, 0) }
        val titles = rows.map { it.title }.toSet()
        assertTrue("游历条目" in titles)
        assertTrue("日常但有属地" in titles)
        assertTrue("纯日常" !in titles)
        assertTrue("造物条目" !in titles)
    }

    @Test
    fun `project 口径包含造物与挂着造物的日常`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("project", null, 100, 0) }
        val titles = rows.map { it.title }.toSet()
        assertTrue("造物条目" in titles)
        assertTrue("日常但挂着造物" in titles)
        assertTrue("纯日常" !in titles)
    }

    @Test
    fun `wish 口径包含心愿与挂着心愿的日常`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("wish", null, 100, 0) }
        val titles = rows.map { it.title }.toSet()
        assertTrue("心愿条目" in titles)
        assertTrue("日常但挂着心愿" in titles)
        assertTrue("纯日常" !in titles)
    }

    @Test
    fun `daily 口径只收没有任何归属的日常`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("daily", null, 100, 0) }
        val titles = rows.map { it.title }.toSet()
        // 注意：daily 口径**不区分年份**（年份是另一个独立筛选维度），
        // 因此「旧年份」这条纯日常同样命中。
        assertEquals(setOf("纯日常", "另一种日常", "旧年份"), titles)
    }

    @Test
    fun `年份筛选只返回该年`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("all", "2026", 100, 0) }
        assertEquals(8, rows.size)
        val old = runBlocking { db.entryDao().getEntriesPage("all", "2025", 100, 0) }
        assertEquals(1, old.size)
        assertEquals("旧年份", old.first().title)
    }

    @Test
    fun `分页 offset 与 limit 正确切分`() {
        seed()
        val first = runBlocking { db.entryDao().getEntriesPage("all", null, 4, 0) }
        val second = runBlocking { db.entryDao().getEntriesPage("all", null, 4, 4) }
        assertEquals(4, first.size)
        assertEquals(4, second.size)
        // 两页之间不应有重叠
        assertTrue(first.map { it.localId }.toSet().intersect(second.map { it.localId }.toSet()).isEmpty())
        val count = runBlocking { db.entryDao().countEntries("all", null) }
        assertEquals(9, count)
    }

    @Test
    fun `待同步删除的行不出现在列表里`() {
        seed()
        val dao = db.entryDao()
        runBlocking {
            dao.insertAll(
                listOf(
                    CachedEntryEntity(
                    localId = 99,
                    serverId = 99,
                    type = "life",
                    title = "已标记删除",
                    content = "",
                    occurredAt = "2026-04-01T10:00:00+08:00",
                        attachmentsJson = "[]",
                        syncStatus = "PENDING_DELETE"
                    )
                )
            )
        }
        val rows = runBlocking { db.entryDao().getEntriesPage("all", null, 100, 0) }
        assertTrue("已标记删除" !in rows.map { it.title })
    }

    /** 简单的协程桥接（测试里都是 suspend DAO 调用） */
    private fun <T> runBlocking(block: suspend () -> T): T {
        var result: T? = null
        var error: Throwable? = null
        val thread = Thread {
            try {
                result = kotlinx.coroutines.runBlocking { block() }
            } catch (t: Throwable) {
                error = t
            }
        }
        thread.start()
        thread.join()
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
