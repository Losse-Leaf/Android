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
 * 「自然下渗分类」的口径（带心愿归心愿、其余归日常）
 * 从此**只有 SQL 这一份事实源** —— 一旦写错，用户就会「找不到自己的记录」。
 * 这里用 Robolectric 在 JVM 上跑真实的 SQLite，把筛选口径全部钉死。
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
        wishId: Long? = null,
        title: String = "条目 $id"
    ) = CachedEntryEntity(
        localId = id,
        serverId = id,
        type = type,
        title = title,
        content = "内容 $id",
        occurredAt = occurredAt,
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
                    entry(2, type = "wish", title = "心愿条目"),
                    entry(3, type = "life", wishId = 9L, title = "日常但挂着心愿"),
                    entry(4, type = "daily", title = "另一种日常"),
                    entry(5, type = "life", occurredAt = "2025-05-01T10:00:00+08:00", title = "旧年份")
                )
            )
        }
    }

    @Test
    fun `all 口径返回全部并按发生时间倒序`() {
        seed()
        val rows = runBlocking { db.entryDao().getEntriesPage("all", null, 100, 0) }
        assertEquals(5, rows.size)
        // 2026 的条目应当排在 2025 之前
        assertTrue(rows.first().occurredAt > rows.last().occurredAt)
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
        assertEquals(4, rows.size)
        val old = runBlocking { db.entryDao().getEntriesPage("all", "2025", 100, 0) }
        assertEquals(1, old.size)
        assertEquals("旧年份", old.first().title)
    }

    @Test
    fun `分页 offset 与 limit 正确切分`() {
        seed()
        val first = runBlocking { db.entryDao().getEntriesPage("all", null, 2, 0) }
        val second = runBlocking { db.entryDao().getEntriesPage("all", null, 2, 2) }
        assertEquals(2, first.size)
        assertEquals(2, second.size)
        // 两页之间不应有重叠
        assertTrue(first.map { it.localId }.toSet().intersect(second.map { it.localId }.toSet()).isEmpty())
        val count = runBlocking { db.entryDao().countEntries("all", null) }
        assertEquals(5, count)
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

    @Test
    fun `组合筛选：仅日常且指定2026年`() {
        seed()
        val dao = db.entryDao()
        val rows = runBlocking {
            dao.getEntriesPageFiltered(
                year = "2026",
                month = null,
                hasCategories = true,
                includeDaily = true,
                includeWish = false,
                hasImage = false,
                isPending = false,
                isFailed = false,
                limit = 100,
                offset = 0
            )
        }
        val count = runBlocking {
            dao.countEntriesFiltered(
                year = "2026",
                month = null,
                hasCategories = true,
                includeDaily = true,
                includeWish = false,
                hasImage = false,
                isPending = false,
                isFailed = false
            )
        }
        val titles = rows.map { it.title }.toSet()
        assertEquals(count, rows.size)
        assertEquals(setOf("纯日常", "另一种日常"), titles)
    }

    @Test
    fun `月份查询：返回某年份下实际有记录的月份`() {
        seed()
        val dao = db.entryDao()
        val months = runBlocking { dao.getAvailableMonthsForYear("2026") }
        assertEquals(listOf(3), months) // seed 中的 2026 条目发生在 03 月
        val months2025 = runBlocking { dao.getAvailableMonthsForYear("2025") }
        assertEquals(listOf(5), months2025) // seed 中的 2025 条目发生在 05 月
    }

    @Test
    fun `内容属性筛选：有图与待放行状态过滤`() {
        seed()
        val dao = db.entryDao()
        runBlocking {
            dao.insertAll(
                listOf(
                    CachedEntryEntity(
                        localId = 101,
                        serverId = 101,
                        type = "life",
                        title = "带图条目",
                        content = "",
                        occurredAt = "2026-03-20T10:00:00+08:00",
                        attachmentsJson = "[{\"id\":\"att1\",\"url\":\"https://example.com/1.jpg\"}]",
                        syncStatus = "SYNCED"
                    ),
                    CachedEntryEntity(
                        localId = 102,
                        serverId = 102,
                        type = "life",
                        title = "待放行条目",
                        content = "",
                        occurredAt = "2026-03-21T10:00:00+08:00",
                        attachmentsJson = "[]",
                        syncStatus = "PENDING_CREATE"
                    )
                )
            )
        }
        val imageRows = runBlocking {
            dao.getEntriesPageFiltered(
                year = null,
                month = null,
                hasCategories = false,
                includeDaily = false,
                includeWish = false,
                hasImage = true,
                isPending = false,
                isFailed = false,
                limit = 100,
                offset = 0
            )
        }
        assertEquals(1, imageRows.size)
        assertEquals("带图条目", imageRows.first().title)

        val pendingRows = runBlocking {
            dao.getEntriesPageFiltered(
                year = null,
                month = null,
                hasCategories = false,
                includeDaily = false,
                includeWish = false,
                hasImage = false,
                isPending = true,
                isFailed = false,
                limit = 100,
                offset = 0
            )
        }
        assertEquals(1, pendingRows.size)
        assertEquals("待放行条目", pendingRows.first().title)
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
