package com.looseleaf.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 索引与迁移的回归 (MOB-P1-71 / MOB-P1-46 / MOB-P1-47)
 *
 * 本轮新增的两条复合索引与手写的 `MIGRATION_6_7` 都是**手写 SQL** ——
 * 一旦索引名与 Room 导出的 schema 不一致，Room 会在运行时抛
 * 「Migration didn't properly handle」并让数据库整体不可用。
 * 这里用真实的 SQLite 把它们钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchemaIndexTest {

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

    @Test
    fun `发件箱按作用域查询命中索引`() {
        val cursor = db.openHelper.readableDatabase.query(
            "PRAGMA index_list('outbox_events')"
        )
        val indexes = mutableListOf<String>()
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) {
                indexes += it.getString(nameIdx)
            }
        }
        assertTrue(
            "发件箱缺少 (entity_type, local_target_id, action) 复合索引，实际有：$indexes",
            "index_outbox_events_entity_type_local_target_id_action" in indexes
        )
    }

    @Test
    fun `档案表按 sync_status 与 occurred_at 排序走复合索引`() {
        val cursor = db.openHelper.readableDatabase.query(
            "PRAGMA index_list('cached_entries')"
        )
        val indexes = mutableListOf<String>()
        cursor.use {
            val nameIdx = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) {
                indexes += it.getString(nameIdx)
            }
        }
        assertTrue(
            "档案表缺少 (sync_status, occurred_at) 复合索引，实际有：$indexes",
            "index_cached_entries_sync_status_occurred_at" in indexes
        )
    }

    @Test
    fun `按目标查找发件箱事件可命中`() {
        val dao = db.outboxDao()
        runBlocking {
            dao.insert(
                OutboxEventEntity(
                    eventId = "evt-1",
                    entityType = "entry",
                    action = "CREATE",
                    payload = "{}",
                    localTargetId = 42L
                )
            )
        }
        val found = runBlocking {
            dao.findEventByTarget("entry", 42L, "CREATE")
        }
        assertEquals("evt-1", found?.eventId)
        val missed = runBlocking {
            dao.findEventByTarget("entry", 43L, "CREATE")
        }
        assertEquals(null, missed)
    }

    @Test
    fun `同步索引投影只返回合并所需的三列`() {
        runBlocking {
            db.entryDao().insertAll(
                listOf(
                    CachedEntryEntity(
                        localId = 1,
                        serverId = 11,
                        type = "life",
                        title = "标题",
                        content = "正文内容很长很长很长很长",
                        occurredAt = "2026-01-01T00:00:00+08:00",
                        attachmentsJson = "[]",
                        syncStatus = "SYNCED"
                ),
                    CachedEntryEntity(
                        localId = 2,
                        serverId = null,
                        type = "life",
                        title = "待同步",
                        content = "",
                        occurredAt = "2026-01-02T00:00:00+08:00",
                        attachmentsJson = "[]",
                        syncStatus = "PENDING_CREATE"
                    )
                )
            )
        }
        val index = runBlocking { db.entryDao().getEntrySyncIndex() }
        assertEquals(2, index.size)
        val pending = index.first { it.syncStatus == "PENDING_CREATE" }
        assertEquals(2L, pending.localId)
        assertEquals(null, pending.serverId)
    }

    @Test
    fun `v6 到 v7 的迁移创建索引且不丢数据`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 建一张 v6 形态的表（字段与 6.json 一致）
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cached_entries` (" +
                            "`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`server_id` INTEGER, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                            "`content` TEXT NOT NULL, `occurred_at` TEXT NOT NULL, `location` TEXT, " +
                            "`city_code` TEXT, `city_name` TEXT, `featured` INTEGER, `project_id` INTEGER, " +
                            "`wish_id` INTEGER, `footprint_id` INTEGER, `attachments_json` TEXT NOT NULL, " +
                            "`sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `outbox_events` (" +
                            "`event_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `action` TEXT NOT NULL, " +
                            "`payload` TEXT NOT NULL, `local_target_id` INTEGER NOT NULL, " +
                            "`server_target_id` INTEGER, `retry_count` INTEGER NOT NULL, " +
                            "`failed_at` TEXT, `last_error` TEXT, `created_at` TEXT NOT NULL, " +
                            "PRIMARY KEY(`event_id`))"
                    )
                    db.execSQL(
                        "INSERT INTO `cached_entries` (`local_id`,`server_id`,`type`,`title`,`content`," +
                            "`occurred_at`,`attachments_json`,`sync_status`,`created_at`) VALUES " +
                            "(1, 11, 'life', '迁移前写入', '内容', '2026-01-01T00:00:00+08:00', '[]', 'SYNCED', '2026-01-01T00:00:00+08:00')"
                    )
                }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val legacy = helper.writableDatabase
        AppDatabase.MIGRATION_6_7.migrate(legacy)

        // 索引已建立
        val entryIndexes = mutableListOf<String>()
        legacy.query("PRAGMA index_list('cached_entries')").use { c ->
            val idx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) entryIndexes += c.getString(idx)
        }
        assertTrue(
            "迁移未创建 cached_entries 复合索引：$entryIndexes",
            "index_cached_entries_sync_status_occurred_at" in entryIndexes
        )
        val outboxIndexes = mutableListOf<String>()
        legacy.query("PRAGMA index_list('outbox_events')").use { c ->
            val idx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) outboxIndexes += c.getString(idx)
        }
        assertTrue(
            "迁移未创建 outbox_events 复合索引：$outboxIndexes",
            "index_outbox_events_entity_type_local_target_id_action" in outboxIndexes
        )

        // 数据未丢
        legacy.query("SELECT title FROM cached_entries WHERE local_id = 1").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("迁移前写入", c.getString(0))
        }
        helper.close()
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
