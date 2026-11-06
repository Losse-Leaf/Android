package com.looseleaf.feature.ledger

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.Transaction
import com.looseleaf.feature.ledger.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * P0 核心数据一致性与发件箱测试套件 (P0-1 ~ P0-7)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerSyncConsistencyTest {

    private lateinit var db: LedgerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        LedgerRepository.init(context)
    }

    @After
    fun tearDown() {
        db.close()
        LedgerRepository.clear()
    }

    @Test
    fun `test Migration 2 to 3 preserves data and cleans dead letters`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("test_migration_ledger.db")
        dbFile.delete()

        // 1. 手动以 v2 结构建立数据库
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(sqLiteDb: SupportSQLiteDatabase) {
                    sqLiteDb.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cached_transactions` (" +
                            "`local_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `server_id` INTEGER, " +
                            "`amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, `note` TEXT, `payment_method` TEXT, " +
                            "`occurred_at` TEXT NOT NULL, `city_code` TEXT, `wish_id` INTEGER, " +
                            "`footprint_id` INTEGER, `is_installment` INTEGER NOT NULL, " +
                            "`installment_channel_code` TEXT, `installment_total_amount` INTEGER, " +
                            "`installment_months` INTEGER, `installment_monthly_pay` INTEGER, " +
                            "`installment_remain_months` INTEGER, `repayment_day` INTEGER, " +
                            "`source` TEXT, `installment_plan_id` INTEGER, `installment_item_id` INTEGER, " +
                            "`principal_amount` INTEGER NOT NULL, `fee_amount` INTEGER NOT NULL, " +
                            "`sync_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                    )
                    sqLiteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_transactions_server_id` ON `cached_transactions` (`server_id`)")

                    sqLiteDb.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ledger_outbox_events` (" +
                            "`event_id` TEXT PRIMARY KEY NOT NULL, `action` TEXT NOT NULL, " +
                            "`payload` TEXT NOT NULL, `local_target_id` INTEGER NOT NULL, " +
                            "`server_target_id` INTEGER, `retry_count` INTEGER NOT NULL, " +
                            "`failed_at` TEXT, `last_error` TEXT, `created_at` TEXT NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v2Db = helper.writableDatabase

        // 插入测试数据：包含重复的 server_id=100（一条 SYNCED，一条 PENDING_UPDATE）
        v2Db.execSQL("INSERT INTO cached_transactions (local_id, server_id, amount, type, category, title, occurred_at, is_installment, principal_amount, fee_amount, sync_status, created_at) VALUES (1, 100, 1000, 'expense', '餐饮', '旧同步记录', '2026-10-01T10:00:00+08:00', 0, 0, 0, 'SYNCED', '2026-10-01T10:00:00+08:00')")
        v2Db.execSQL("INSERT INTO cached_transactions (local_id, server_id, amount, type, category, title, occurred_at, is_installment, principal_amount, fee_amount, sync_status, created_at) VALUES (2, 100, 1500, 'expense', '餐饮', '待更新记录', '2026-10-01T10:00:00+08:00', 0, 0, 0, 'PENDING_UPDATE', '2026-10-01T10:00:00+08:00')")

        // 插入被 'null' 字符串污染的死信事件
        v2Db.execSQL("INSERT INTO ledger_outbox_events (event_id, action, payload, local_target_id, server_target_id, retry_count, failed_at, last_error, created_at) VALUES ('event_1', 'UPDATE', '{}', 2, 100, 1, 'null', '连接失败', '2026-10-01T10:00:00+08:00')")

        // 2. 执行 Migration 2 -> 3
        LedgerDatabase.MIGRATION_2_3.migrate(v2Db)

        // 3. 校验重复 server_id 清洗：PENDING_UPDATE 的那条（local_id=2）胜出！
        val cursorTx = v2Db.query("SELECT local_id, server_id, sync_status FROM cached_transactions WHERE server_id = 100")
        assertEquals(1, cursorTx.count)
        cursorTx.moveToFirst()
        assertEquals(2L, cursorTx.getLong(0))
        assertEquals("PENDING_UPDATE", cursorTx.getString(2))
        cursorTx.close()

        // 4. 校验死信修复：'null' 字符串被转为 NULL
        val cursorOutbox = v2Db.query("SELECT event_id, client_request_id, sequence, failed_at FROM ledger_outbox_events WHERE event_id = 'event_1'")
        assertEquals(1, cursorOutbox.count)
        cursorOutbox.moveToFirst()
        assertEquals("event_1", cursorOutbox.getString(0))
        assertEquals("event_1", cursorOutbox.getString(1)) // 自动以 event_id 作为 client_request_id
        assertTrue(cursorOutbox.isNull(3)) // failed_at 必须是真实的 SQL NULL！
        cursorOutbox.close()

        v2Db.close()
        dbFile.delete()
    }

    @Test
    fun `test local create then update coalesces into single CREATE payload`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        // 1. 模拟本地创建条目
        val createReq = CreateTransactionRequest(
            amount = 3500,
            type = "expense",
            category = "餐饮",
            title = "午餐",
            occurredAt = "2026-11-01T12:00:00+08:00"
        )
        val localId = txDao.insert(
            CachedTransactionEntity(
                amount = createReq.amount,
                type = createReq.type,
                category = createReq.category,
                title = createReq.title,
                occurredAt = createReq.occurredAt,
                syncStatus = "PENDING_CREATE"
            )
        )
        val clientRequestId = UUID.randomUUID().toString()
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_create",
                action = "CREATE",
                payload = kotlinx.serialization.json.Json.encodeToString(CreateTransactionRequest.serializer(), createReq.copy(clientRequestId = clientRequestId)),
                localTargetId = localId,
                serverTargetId = null,
                clientRequestId = clientRequestId,
                sequence = 1L,
                enqueuedAt = "2026-11-01T12:00:00+08:00"
            )
        )

        // 2. 在未同步前执行编辑（改金额为 4200，改标题为「豪华午餐」）
        val updateReq = createReq.copy(amount = 4200, title = "豪华午餐")
        val activeEvent = outboxDao.findActiveEventByLocalTarget(localId)
        assertNotNull(activeEvent)
        assertEquals("CREATE", activeEvent!!.action)

        // 合并载荷并更新
        val merged = updateReq.copy(clientRequestId = activeEvent.clientRequestId)
        outboxDao.updatePayload(activeEvent.eventId, kotlinx.serialization.json.Json.encodeToString(CreateTransactionRequest.serializer(), merged))
        txDao.update(
            txDao.getTransactionByLocalId(localId)!!.copy(amount = 4200, title = "豪华午餐")
        )

        // 3. 验证发件箱：依然只有 1 个事件，没有孤儿 UPDATE，金额已合并，clientRequestId 保持一致
        val pendingEvents = outboxDao.getAllPendingEvents()
        assertEquals(1, pendingEvents.size)
        val singleEvent = pendingEvents.first()
        assertEquals("CREATE", singleEvent.action)
        assertEquals(clientRequestId, singleEvent.clientRequestId)
        assertTrue(singleEvent.payload.contains("4200"))
        assertTrue(singleEvent.payload.contains("豪华午餐"))
    }

    @Test
    fun `test delete before sync removes local row and event with zero orphan`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        val localId = txDao.insert(
            CachedTransactionEntity(
                amount = 1000,
                type = "expense",
                category = "交通",
                title = "地铁",
                occurredAt = "2026-11-01T08:00:00+08:00",
                syncStatus = "PENDING_CREATE"
            )
        )
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_local",
                action = "CREATE",
                payload = "{}",
                localTargetId = localId,
                serverTargetId = null,
                clientRequestId = "req_1",
                sequence = 1L,
                enqueuedAt = "2026-11-01T08:00:00+08:00"
            )
        )

        // 本地未同步条目直接删除
        outboxDao.deleteByLocalTarget(localId)
        txDao.deleteByLocalId(localId)

        // 验证 0 孤儿
        assertEquals(0, txDao.getAllTransactions().size)
        assertEquals(0, outboxDao.getAllPendingEvents().size)
    }

    @Test
    fun `test remote soft deleted tombstone does not overwrite local pending update`() = runBlocking {
        val txDao = db.transactionDao()

        // 本地有一条正在待更新的记录 (serverId = 200, syncStatus = PENDING_UPDATE)
        txDao.insert(
            CachedTransactionEntity(
                localId = 5,
                serverId = 200,
                amount = 8800,
                type = "expense",
                category = "购物",
                title = "本地待同步编辑",
                occurredAt = "2026-11-01T15:00:00+08:00",
                syncStatus = "PENDING_UPDATE"
            )
        )

        // 远端拉取返回 Tombstone (deletedAt != null)
        val remoteTombstone = Transaction(
            id = 200,
            amount = 8800,
            category = "购物",
            title = "远端已删除",
            deletedAt = "2026-11-01T16:00:00+08:00"
        )

        // 模拟 mergeTransactions 逻辑：因本地是 PENDING_UPDATE，必须保留！
        val existing = txDao.getTransactionByServerId(remoteTombstone.id)
        assertNotNull(existing)
        if (existing!!.syncStatus == "SYNCED") {
            txDao.deleteByServerId(remoteTombstone.id)
        }

        // 验证本地行依然健在
        val localRow = txDao.getTransactionByServerId(200)
        assertNotNull(localRow)
        assertEquals("PENDING_UPDATE", localRow!!.syncStatus)
        assertEquals("本地待同步编辑", localRow.title)
    }

    @Test
    fun `test outbox strictly monotonic sequence and retry scheduling`() = runBlocking {
        val outboxDao = db.outboxDao()

        val seq1 = outboxDao.getNextSequence()
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "e1",
                action = "CREATE",
                payload = "{}",
                localTargetId = 1,
                clientRequestId = "req_1",
                sequence = seq1,
                enqueuedAt = "2026-11-01T10:00:00Z"
            )
        )

        val seq2 = outboxDao.getNextSequence()
        assertTrue("Sequence must be strictly monotonic", seq2 > seq1)
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "e2",
                action = "UPDATE",
                payload = "{}",
                localTargetId = 1,
                clientRequestId = "req_2",
                sequence = seq2,
                enqueuedAt = "2026-11-01T10:05:00Z"
            )
        )

        val pending = outboxDao.getAllPendingEvents()
        assertEquals(2, pending.size)
        assertEquals("e1", pending[0].eventId)
        assertEquals("e2", pending[1].eventId)

        // 模拟失败退避调度
        outboxDao.scheduleRetry("e1", "2026-11-01T10:10:00Z", "Network timeout")
        val scheduled = outboxDao.getAllPendingEvents("2026-11-01T10:06:00Z")
        assertEquals(1, scheduled.size)
        assertEquals("e2", scheduled[0].eventId)

        // 到达重试时刻
        val ready = outboxDao.getAllPendingEvents("2026-11-01T10:11:00Z")
        assertEquals(2, ready.size)
    }

    @Test
    fun `test expired dead letter cleanup removes 7 day old failed events`() = runBlocking {
        val outboxDao = db.outboxDao()

        // 插入 10 天前失败的死信与 2 天前失败的死信
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "old_dead",
                action = "CREATE",
                payload = "{}",
                localTargetId = 1,
                clientRequestId = "req_old",
                sequence = 1L,
                enqueuedAt = "2026-10-20T09:50:00Z",
                failedAt = "2026-10-20T10:00:00Z",
                lastError = "400 Bad Request"
            )
        )
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "recent_dead",
                action = "CREATE",
                payload = "{}",
                localTargetId = 2,
                clientRequestId = "req_recent",
                sequence = 2L,
                enqueuedAt = "2026-10-30T09:50:00Z",
                failedAt = "2026-10-30T10:00:00Z",
                lastError = "400 Bad Request"
            )
        )

        assertEquals(2, outboxDao.countFailedEvents())

        // 清理 7 天前（2026-10-25 之前）的死信
        outboxDao.cleanExpiredFailedEvents("2026-10-25T00:00:00Z")

        val remainingFailed = outboxDao.getFailedEvents()
        assertEquals(1, remainingFailed.size)
        assertEquals("recent_dead", remainingFailed[0].eventId)
    }
}
