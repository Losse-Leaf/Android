package com.looseleaf.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.EntrySyncIndex
import com.looseleaf.app.data.sync.SyncMerger
import com.looseleaf.app.model.LifeEntry
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.DeleteUndoKind
import com.looseleaf.app.data.sync.DeleteUndoTicket

/**
 * 增量 / 全量合并语义的回归 (MOB-P1-41 / MOB-P1-71)
 *
 * 合并是离线同步里最不能出错的一段：
 * 一旦覆盖了用户本地还没上传的修改，内容就会**静默丢失**。
 * 这里把三条铁律钉死：
 * 1. 待同步的本地行，正文绝不被远端覆盖；
 * 2. 服务端软删除 → 本地一并移除；
 * 3. 全量收口只删「服务端已不存在」的**已同步**行，待同步行必须保留。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncMergeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    private fun localRow(
        localId: Long,
        serverId: Long?,
        title: String,
        syncStatus: String = "SYNCED"
    ) = CachedEntryEntity(
        localId = localId,
        serverId = serverId,
        type = "life",
        title = title,
        content = "本地内容",
        occurredAt = "2026-01-01T00:00:00+08:00",
        attachmentsJson = "[]",
        syncStatus = syncStatus
    )

    private fun remote(
        id: Long,
        title: String,
        deletedAt: String? = null
    ) = LifeEntry(
        id = id,
        type = "life",
        title = title,
        content = "远端内容",
        occurredAt = "2026-01-01T00:00:00+08:00",
        deletedAt = deletedAt
    )

    private fun indexOf(vararg rows: Pair<Long, EntrySyncIndex>) = rows.toMap()

    @Test
    fun `待同步的本地行不会被远端覆盖`() {
        runBlocking {
            db.entryDao().insertAll(listOf(localRow(1, 11L, "本地未同步", syncStatus = "PENDING_CREATE")))
            val index = indexOf(
                11L to EntrySyncIndex(localId = 1, serverId = 11L, syncStatus = "PENDING_CREATE")
            )
            SyncMerger.upsertEntries(db, listOf(remote(11, "远端标题")), index, json)

            val row = db.entryDao().getEntryByLocalId(1)
            assertEquals("本地未同步", row?.title)
        }
    }

    @Test
    fun `已同步的本地行会被远端覆盖且保留 localId`() {
        runBlocking {
            db.entryDao().insertAll(listOf(localRow(1, 11L, "旧标题")))
            val index = indexOf(
                11L to EntrySyncIndex(localId = 1, serverId = 11L, syncStatus = "SYNCED")
            )
            SyncMerger.upsertEntries(db, listOf(remote(11, "新标题")), index, json)

            val row = db.entryDao().getEntryByLocalId(1)
            assertEquals("新标题", row?.title)
            assertEquals(1L, row?.localId)
        }
    }

    @Test
    fun `服务端软删除会移除本地行`() {
        runBlocking {
            db.entryDao().insertAll(listOf(localRow(1, 11L, "将被删除")))
            val index = indexOf(
                11L to EntrySyncIndex(localId = 1, serverId = 11L, syncStatus = "SYNCED")
            )
            SyncMerger.upsertEntries(
                db,
                listOf(remote(11, "已软删", deletedAt = "2026-02-01T00:00:00+08:00")),
                index,
                json
            )
            assertNull(db.entryDao().getEntryByLocalId(1))
        }
    }

    @Test
    fun `全量收口只删除服务端已不存在的已同步行`() {
        runBlocking {
            db.entryDao().insertAll(
                listOf(
                    localRow(1, 11L, "服务端还有", syncStatus = "SYNCED"),
                    localRow(2, 22L, "服务端没了", syncStatus = "SYNCED"),
                    localRow(3, 33L, "本地待推送", syncStatus = "PENDING_UPDATE")
                )
            )
            val index = indexOf(
                11L to EntrySyncIndex(localId = 1, serverId = 11L, syncStatus = "SYNCED"),
                22L to EntrySyncIndex(localId = 2, serverId = 22L, syncStatus = "SYNCED"),
                33L to EntrySyncIndex(localId = 3, serverId = 33L, syncStatus = "PENDING_UPDATE")
            )
            // 远端只带来了 11
            SyncMerger.finalizeFullEntries(db, setOf(11L), index)

            assertTrue(db.entryDao().getEntryByLocalId(1) != null)
            assertNull(db.entryDao().getEntryByLocalId(2))
            // 待同步的行必须保住（内容还没上传）
            assertTrue(db.entryDao().getEntryByLocalId(3) != null)
        }
    }

    @Test
    fun `新增的远端记录落到本地`() {
        runBlocking {
            SyncMerger.upsertEntries(db, listOf(remote(99, "全新条目")), emptyMap(), json)
            val all = db.entryDao().getAllEntries()
            assertEquals(1, all.size)
            assertEquals("全新条目", all.first().title)
        }
    }

    // ---------------- MOB-P2-47：删除撤销 ----------------

    @Test
    fun `删除事件仍在发件箱时可以撤销删除`() {
        runBlocking {
            val dao = db.entryDao()
            dao.insertAll(
                listOf(
                    CachedEntryEntity(
                        localId = 1,
                        serverId = 11L,
                        type = "life",
                        title = "将被误删",
                        content = "内容",
                        occurredAt = "2026-01-01T00:00:00+08:00",
                        attachmentsJson = "[]",
                        syncStatus = "SYNCED"
                    )
                )
            )
            val entity = dao.getEntryByLocalId(1)!!

            // 模拟删除：本地行移除 + DELETE 事件入队
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "del-1",
                    entityType = "entry",
                    action = "DELETE",
                    payload = "{}",
                    localTargetId = 1L,
                    serverTargetId = 11L
                )
            )
            dao.deleteByLocalId(1)
            assertEquals(null, dao.getEntryByLocalId(1))

            val ticket = DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.ENTRY)
            val restored = SyncMerger.undoDeleteEntry(db, ticket)

            assertTrue("删除事件仍在发件箱时应当可以撤销", restored)
            assertEquals("将被误删", dao.getEntryByLocalId(1)?.title)
            // 队列里的 DELETE 事件必须同时消失，否则会真的把记录删掉
            val stillQueued = db.outboxDao().getAllPendingEvents()
                .any { it.entityType == "entry" && it.action == "DELETE" }
            assertTrue("撤销后不得残留 DELETE 事件", !stillQueued)
        }
    }

    @Test
    fun `删除事件已上传后不允许撤销`() {
        runBlocking {
            val entity = CachedEntryEntity(
                localId = 2,
                serverId = 22L,
                type = "life",
                title = "已同步删除",
                content = "",
                occurredAt = "2026-01-01T00:00:00+08:00",
                attachmentsJson = "[]",
                syncStatus = "SYNCED"
            )
            // 发件箱里没有 DELETE 事件 ⇒ 说明已经被 Worker 上传处理
            val ticket = DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.ENTRY)
            val restored = SyncMerger.undoDeleteEntry(db, ticket)
            assertTrue("事件已上传后不得允许撤销（会造成幽灵记录）", !restored)
            assertEquals(null, db.entryDao().getEntryByLocalId(2))
        }
    }

    @Test
    fun `撤销删除只移除DELETE事件并保留待同步编辑`() {
        runBlocking {
            val entity = localRow(3, 33L, "删除前标题", syncStatus = "PENDING_UPDATE")
            db.entryDao().insertAll(listOf(entity))
            // 删除前已有一条待上传的 UPDATE：撤销必须保留它，否则恢复后的行永远停在旧内容。
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "upd-3", entityType = "entry", action = "UPDATE",
                    payload = "{}", localTargetId = 3L, serverTargetId = 33L
                )
            )
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "del-3", entityType = "entry", action = "DELETE",
                    payload = "{}", localTargetId = 3L, serverTargetId = 33L
                )
            )
            db.entryDao().deleteByLocalId(3)

            val ticket = DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.ENTRY)
            assertTrue(SyncMerger.undoDeleteEntry(db, ticket))

            val remaining = db.outboxDao().getAllPendingEvents().filter { it.localTargetId == 3L }
            assertEquals(listOf("UPDATE"), remaining.map { it.action })
            assertEquals("删除前标题", db.entryDao().getEntryByLocalId(3)?.title)
        }
    }

    @Test
    fun `失败态删除事件不允许撤销`() {
        runBlocking {
            val entity = localRow(4, 44L, "失败删除")
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "del-4", entityType = "entry", action = "DELETE",
                    payload = "{}", localTargetId = 4L, serverTargetId = 44L,
                    failedAt = "2026-10-24T00:00:00+08:00"
                )
            )
            val ticket = DeleteUndoTicket(entity = entity, kind = DeleteUndoKind.ENTRY)
            assertEquals(false, SyncMerger.undoDeleteEntry(db, ticket))
        }
    }

    @Test
    fun `解绑计数包含失败事件`() {
        runBlocking {
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "pending-1", entityType = "entry", action = "CREATE",
                    payload = "{}", localTargetId = 1L
                )
            )
            db.outboxDao().insert(
                OutboxEventEntity(
                    eventId = "failed-1", entityType = "entry", action = "CREATE",
                    payload = "{}", localTargetId = 2L,
                    failedAt = "2026-10-24T00:00:00+08:00"
                )
            )
            assertEquals(2, db.outboxDao().countAllEvents())
        }
    }

    @Test
    fun `媒体待上传文件按当前选择集收窄`() {
        val files = listOf(
            com.looseleaf.app.data.sync.OfflineMediaFileItem(filePath = "/tmp/a.jpg"),
            com.looseleaf.app.data.sync.OfflineMediaFileItem(filePath = "/tmp/b.jpg"),
            com.looseleaf.app.data.sync.OfflineMediaFileItem(filePath = "/tmp/c.jpg")
        )
        val selected = listOf(
            com.looseleaf.app.model.Attachment(
                id = -2L,
                objectKey = "local_outbox/a.jpg",
                originalFilename = "a.jpg",
                mimeType = "image/jpeg",
                size = 0,
                url = "file:///tmp/a.jpg"
            ),
            com.looseleaf.app.model.Attachment(
                id = 99L,
                objectKey = "entries/server.jpg",
                originalFilename = "server.jpg",
                mimeType = "image/jpeg",
                size = 0,
                url = "https://example.com/server.jpg"
            )
        )
        val kept = com.looseleaf.app.data.sync.selectMediaFiles(files, selected)
        assertEquals(listOf("/tmp/a.jpg"), kept.map { it.filePath })
    }

    /** 简单的协程桥接 */
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
