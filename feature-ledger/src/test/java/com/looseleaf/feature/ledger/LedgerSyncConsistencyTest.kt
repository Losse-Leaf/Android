package com.looseleaf.feature.ledger

import androidx.room.Room
import androidx.room.withTransaction
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

    @Test
    fun `test mergeTransactions binds pending create by clientRequestId without duplicating`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        val crid = "crid-test-123"
        val localId = txDao.insert(
            CachedTransactionEntity(
                amount = 2500,
                type = "expense",
                category = "娱乐",
                title = "电影票",
                occurredAt = "2026-11-02T19:00:00+08:00",
                syncStatus = "PENDING_CREATE"
            )
        )
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_create_123",
                action = "CREATE",
                payload = "{}",
                localTargetId = localId,
                serverTargetId = null,
                clientRequestId = crid,
                sequence = 1L,
                enqueuedAt = "2026-11-02T19:00:00+08:00"
            )
        )

        // 远端返回已落库的记录，带同一 clientRequestId
        val remoteTx = Transaction(
            id = 555L,
            amount = 2500,
            category = "娱乐",
            title = "电影票",
            occurredAt = "2026-11-02T19:00:00+08:00",
            clientRequestId = crid
        )

        LedgerRepository.mergeTransactions(db, listOf(remoteTx))

        // 验证：本地行保留 localId，绑定 serverId = 555，标为 SYNCED
        val mergedLocal = txDao.getTransactionByLocalId(localId)
        assertNotNull(mergedLocal)
        assertEquals(555L, mergedLocal!!.serverId)
        assertEquals("SYNCED", mergedLocal.syncStatus)

        // 验证：对应的 CREATE 出库事件已被彻底清理
        val pendingEvents = outboxDao.getAllPendingEvents()
        assertTrue(pendingEvents.none { it.eventId == "evt_create_123" })

        // 验证：总记录数依然为 1，无任何重复行
        assertEquals(1, txDao.getAllTransactions().size)
    }

    @Test
    fun `test mergeTransactions keeps newer local pending create and promotes to update`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        val crid = "crid-race-001"
        val latestPayload =
            """{"amount":3000,"type":"expense","category":"娱乐","title":"新标题",""" +
                """"occurred_at":"2026-11-02T19:00:00+08:00","client_request_id":"$crid"}"""
        val localId = txDao.insert(
            CachedTransactionEntity(
                amount = 3000,
                type = "expense",
                category = "娱乐",
                title = "新标题",
                occurredAt = "2026-11-02T19:00:00+08:00",
                syncStatus = "PENDING_CREATE"
            )
        )
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_create_race",
                action = "CREATE",
                payload = latestPayload,
                localTargetId = localId,
                serverTargetId = null,
                clientRequestId = crid,
                sequence = 1L,
                enqueuedAt = "2026-11-02T19:00:00+08:00"
            )
        )

        // 幂等重放返回的是旧载荷落库结果：金额与标题都落后于本地最新编辑
        val staleRemote = Transaction(
            id = 777L,
            amount = 2500,
            category = "娱乐",
            title = "旧标题",
            occurredAt = "2026-11-02T19:00:00+08:00",
            clientRequestId = crid
        )

        LedgerRepository.mergeTransactions(db, listOf(staleRemote))

        // 本地最新内容必须保留，不能被远端旧值覆盖
        val local = txDao.getTransactionByLocalId(localId)
        assertNotNull(local)
        assertEquals(3000L, local!!.amount)
        assertEquals("新标题", local.title)
        assertEquals("PENDING_UPDATE", local.syncStatus)
        assertEquals(777L, local.serverId)

        // CREATE 事件必须升级为 UPDATE，并复用本地最新载荷
        val events = outboxDao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("UPDATE", events[0].action)
        assertEquals(777L, events[0].serverTargetId)
        assertEquals(latestPayload, events[0].payload)
        assertTrue(events.none { it.action == "CREATE" })

        // 不允许出现重复行
        assertEquals(1, txDao.getAllTransactions().size)
    }

    @Test
    fun `test transaction content comparison ignores server derived fields but detects stale payload`() {
        val base = Transaction(
            id = 1L,
            amount = 3000,
            category = "娱乐",
            title = "电影票",
            note = "IMAX",
            occurredAt = "2026-11-02T19:00:00+08:00",
            isInstallment = true,
            installmentChannelCode = "CMB",
            installmentTotalAmount = 3600,
            installmentMonths = 6,
            installmentMonthlyPay = 600,
            repaymentDay = 5
        )
        // 服务端派生字段不同、时间字符串时区不同，但语义内容一致
        val serverDerived = base.copy(
            id = 99L,
            occurredAt = "2026-11-02T11:00:00Z",
            installmentRemainMonths = 3,
            source = "installment_repay",
            installmentPlanId = 88L,
            installmentItemId = 99L,
            principalAmount = 3000,
            feeAmount = 600,
            updatedAt = "2026-11-03T00:00:00Z"
        )
        assertTrue(LedgerRepository.transactionsHaveSameContent(base, serverDerived))

        // 用户可编辑字段一旦不同，必须识别为旧载荷
        assertFalse(
            LedgerRepository.transactionsHaveSameContent(
                base,
                serverDerived.copy(amount = 2500)
            )
        )
        assertFalse(
            LedgerRepository.transactionsHaveSameContent(
                base,
                serverDerived.copy(title = "旧标题")
            )
        )

        // 文本仅首尾空白差异视为一致
        assertTrue(
            LedgerRepository.transactionsHaveSameContent(
                base,
                serverDerived.copy(note = "  IMAX  ")
            )
        )
    }

    @Test
    fun `test full sync reconciliation deletes synced ghosts but preserves pending rows`() = runBlocking {
        val txDao = db.transactionDao()

        suspend fun insert(serverId: Long?, title: String, status: String) = txDao.insert(
            CachedTransactionEntity(
                serverId = serverId,
                amount = 1000,
                type = "expense",
                category = "测试",
                title = title,
                occurredAt = "2026-11-02T10:00:00+08:00",
                syncStatus = status
            )
        )

        insert(1L, "远端仍在 1", "SYNCED")
        insert(2L, "远端仍在 2", "SYNCED")
        insert(3L, "远端已删除", "SYNCED")
        insert(4L, "本地待更新", "PENDING_UPDATE")
        insert(5L, "本地待删除", "PENDING_DELETE")
        insert(null, "本地待创建", "PENDING_CREATE")

        LedgerRepository.reconcileFullSync(db, setOf(1L, 2L, 4L))

        // 远端已不存在的已同步行被清理
        assertNull(txDao.getTransactionByServerId(3L))
        // 远端仍存在的已同步行保留
        assertNotNull(txDao.getTransactionByServerId(1L))
        assertNotNull(txDao.getTransactionByServerId(2L))
        // 待同步行无论远端是否存在都必须保留
        assertNotNull(txDao.getTransactionByServerId(4L))
        assertNotNull(txDao.getTransactionByServerId(5L))
        assertEquals(1, txDao.getAllTransactions().count { it.serverId == null })
    }

    @Test
    fun `test mergeTransactions does not resurrect pending delete row`() = runBlocking {
        val txDao = db.transactionDao()

        // 本地处于待删除状态 (serverId = 666, syncStatus = PENDING_DELETE)
        val localId = txDao.insert(
            CachedTransactionEntity(
                localId = 10,
                serverId = 666L,
                amount = 1200,
                type = "expense",
                category = "生活",
                title = "已删除本地账目",
                occurredAt = "2026-11-02T10:00:00+08:00",
                syncStatus = "PENDING_DELETE"
            )
        )

        // 远端全量/增量返回了活跃的 serverId = 666 记录
        val remoteTx = Transaction(
            id = 666L,
            amount = 1200,
            category = "生活",
            title = "远端仍然活跃",
            occurredAt = "2026-11-02T10:00:00+08:00"
        )

        LedgerRepository.mergeTransactions(db, listOf(remoteTx))

        // 验证：本地记录依然为 PENDING_DELETE，未被远端覆盖复活为 SYNCED
        val row = txDao.getTransactionByLocalId(localId)
        assertNotNull(row)
        assertEquals("PENDING_DELETE", row!!.syncStatus)
        assertEquals("已删除本地账目", row.title)
    }

    @Test
    fun `test concurrent edit during CREATE converts to UPDATE outbox event`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        val initialPayload = """{"amount":1000,"category":"餐饮","title":"初次创建"}"""
        val updatedPayload = """{"amount":2000,"category":"餐饮","title":"飞行期间二次编辑"}"""

        val localId = txDao.insert(
            CachedTransactionEntity(
                amount = 1000,
                category = "餐饮",
                title = "初次创建",
                occurredAt = "2026-11-02T12:00:00+08:00",
                syncStatus = "PENDING_CREATE"
            )
        )
        val eventId = "evt_race_create"
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = eventId,
                action = "CREATE",
                payload = initialPayload,
                localTargetId = localId,
                serverTargetId = null,
                clientRequestId = "crid_race",
                sequence = 1L,
                enqueuedAt = "2026-11-02T12:00:00+08:00"
            )
        )

        // 模拟飞行期间用户再次编辑：本地行与 outbox payload 更新为 updatedPayload
        outboxDao.updatePayload(eventId, updatedPayload)
        txDao.update(
            txDao.getTransactionByLocalId(localId)!!.copy(
                amount = 2000,
                title = "飞行期间二次编辑"
            )
        )

        // 模拟网络返回：服务端生成了 serverId = 777
        val serverCreatedId = 777L

        // 执行 Check-then-act 事务逻辑
        db.withTransaction {
            val latest = outboxDao.findActiveEventById(eventId)
            assertNotNull(latest)
            assertNotEquals(initialPayload, latest!!.payload)

            // 识别出竞态：绑定 serverId，本地保持 PENDING_UPDATE，将 CREATE 转为 UPDATE 事件
            txDao.bindServerId(localId, serverCreatedId)
            txDao.updateSyncStatus(localId, "PENDING_UPDATE")
            outboxDao.bindServerId(localId, serverCreatedId)
            outboxDao.deleteByEventId(eventId)

            val nextSeq = outboxDao.getNextSequence()
            outboxDao.insert(
                LedgerOutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    action = "UPDATE",
                    payload = latest.payload,
                    localTargetId = localId,
                    serverTargetId = serverCreatedId,
                    clientRequestId = UUID.randomUUID().toString(),
                    sequence = nextSeq,
                    enqueuedAt = "2026-11-02T12:00:05+08:00"
                )
            )
        }

        // 验证：本地行已绑定 serverId = 777，且处于 PENDING_UPDATE 状态
        val localRow = txDao.getTransactionByLocalId(localId)
        assertNotNull(localRow)
        assertEquals(777L, localRow!!.serverId)
        assertEquals("PENDING_UPDATE", localRow.syncStatus)
        assertEquals("飞行期间二次编辑", localRow.title)

        // 验证：outbox 中 CREATE 事件已移除，生成了新的 UPDATE 事件，载荷为最新修改
        val pendingEvents = outboxDao.getAllPendingEvents()
        assertEquals(1, pendingEvents.size)
        val updateEvent = pendingEvents.first()
        assertEquals("UPDATE", updateEvent.action)
        assertEquals(777L, updateEvent.serverTargetId)
        assertEquals(updatedPayload, updateEvent.payload)
    }

    @Test
    fun `test undo transaction deletion preserves pending update`() = runBlocking {
        val outboxDao = db.outboxDao()
        val txDao = db.transactionDao()

        // 1. 本地有一条处于待更新的记录 (serverId = 300)
        val localId = txDao.insert(
            CachedTransactionEntity(
                localId = 30,
                serverId = 300L,
                amount = 5000,
                type = "expense",
                category = "生活",
                title = "电费",
                occurredAt = "2026-11-03T10:00:00+08:00",
                syncStatus = "PENDING_UPDATE"
            )
        )
        val updatePayload = """{"amount":6000,"category":"生活","title":"电费修改"}"""
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_up_300",
                action = "UPDATE",
                payload = updatePayload,
                localTargetId = localId,
                serverTargetId = 300L,
                clientRequestId = "crid_up_300",
                sequence = 1L,
                enqueuedAt = "2026-11-03T10:05:00+08:00"
            )
        )

        // 2. 模拟 deleteTransaction 中的保存逻辑
        val pendingUpdate = outboxDao.findEventByTarget(localId, "UPDATE")
        assertNotNull(pendingUpdate)
        val ticket = LedgerDeleteUndoTicket(
            entity = txDao.getTransactionByLocalId(localId)!!,
            pendingUpdatePayload = pendingUpdate!!.payload
        )
        outboxDao.deleteByLocalTarget(localId)
        outboxDao.insert(
            LedgerOutboxEventEntity(
                eventId = "evt_del_300",
                action = "DELETE",
                payload = "{}",
                localTargetId = localId,
                serverTargetId = 300L,
                clientRequestId = "crid_del_300",
                sequence = 2L,
                enqueuedAt = "2026-11-03T10:06:00+08:00"
            )
        )
        txDao.markPendingDelete(localId)

        // 3. 模拟 undoDeleteTransaction 逻辑
        db.withTransaction {
            val pendingDel = outboxDao.findEventByTarget(ticket.entity.localId, "DELETE")
            assertNotNull(pendingDel)
            outboxDao.deleteByEventId(pendingDel!!.eventId)
            if (ticket.pendingUpdatePayload != null) {
                txDao.update(ticket.entity.copy(syncStatus = "PENDING_UPDATE"))
                outboxDao.insert(
                    LedgerOutboxEventEntity(
                        eventId = "evt_re_up_300",
                        action = "UPDATE",
                        payload = ticket.pendingUpdatePayload,
                        localTargetId = ticket.entity.localId,
                        serverTargetId = 300L,
                        clientRequestId = "crid_re_300",
                        sequence = 3L,
                        enqueuedAt = "2026-11-03T10:07:00+08:00"
                    )
                )
            }
        }

        // 验证：本地行恢复为 PENDING_UPDATE，而不是误标记为 SYNCED
        val restored = txDao.getTransactionByLocalId(localId)
        assertNotNull(restored)
        assertEquals("PENDING_UPDATE", restored!!.syncStatus)

        // 验证：发件箱中重新拥有 UPDATE 事件且载荷为用户未同步修改
        val activeEvents = outboxDao.getAllPendingEvents()
        assertEquals(1, activeEvents.size)
        val reEnqueuedUpdate = activeEvents.first()
        assertEquals("UPDATE", reEnqueuedUpdate.action)
        assertEquals(updatePayload, reEnqueuedUpdate.payload)
    }
}
