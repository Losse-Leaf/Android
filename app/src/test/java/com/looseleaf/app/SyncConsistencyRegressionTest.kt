package com.looseleaf.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.OutboxSyncWorker
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 同步一致性复核的回归测试：
 * - CREATE 返回 410 时，本地临时行与 CREATE 事件必须一起丢弃；
 * - 发件箱达到单次运行上限后，只有「还有未尝试事件且无重试失败」才续排；
 * - 分页恰好用满上限、最后一页是短页时不能被误判为超限。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncConsistencyRegressionTest {

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

    private fun event(
        eventId: String,
        entityType: String = "entry",
        action: String = "CREATE",
        localTargetId: Long = -1L,
        createdAt: String = FormatUtils.beijingNowIsoString()
    ) = OutboxEventEntity(
        eventId = eventId,
        entityType = entityType,
        action = action,
        payload = "{}",
        localTargetId = localTargetId,
        createdAt = createdAt
    )

    @Test
    fun `410 清理会同时删除本地临时散页与 CREATE 事件`() = runBlocking {
        val tempId = -401L
        db.entryDao().insert(
            CachedEntryEntity(
                localId = tempId,
                title = "待丢弃散页",
                content = "服务端已 purge",
                syncStatus = "PENDING_CREATE"
            )
        )
        val create = event(eventId = "ev-entry-410", localTargetId = tempId)
        db.outboxDao().insert(create)

        SyncManager.discardGoneCreate(db, create) {
            db.entryDao().deleteByLocalId(tempId)
        }

        assertNull("本地临时散页必须删除", db.entryDao().getEntryByLocalId(tempId))
        assertNull("CREATE 事件必须消费掉，避免死信反复重试", db.outboxDao().findEventById(create.eventId))
    }

    @Test
    fun `发件箱续排只统计未尝试且不在宽限期的待发事件`() = runBlocking {
        val old = FormatUtils.toBeijingIsoString(
            Instant.now().minus(1, ChronoUnit.HOURS)
        )
        val attempted = event(
            eventId = "ev-attempted",
            action = "UPDATE",
            localTargetId = 1L,
            createdAt = old
        )
        val unattempted = event(
            eventId = "ev-unattempted",
            action = "UPDATE",
            localTargetId = 2L,
            createdAt = old
        )
        db.outboxDao().insert(attempted)
        db.outboxDao().insert(unattempted)

        assertTrue(
            "排除本轮已尝试事件后仍有待发事件，应续排",
            OutboxSyncWorker.hasUnattemptedPending(db.outboxDao(), setOf(attempted.eventId))
        )
        assertFalse(
            "全部事件都已尝试过，不应再续排",
            OutboxSyncWorker.hasUnattemptedPending(
                db.outboxDao(),
                setOf(attempted.eventId, unattempted.eventId)
            )
        )

        // 只剩宽限期内的 DELETE：必须先等撤销窗口，不能立即续排。
        db.outboxDao().delete(attempted)
        db.outboxDao().delete(unattempted)
        db.outboxDao().insert(
            event(
                eventId = "ev-grace-delete",
                action = "DELETE",
                localTargetId = 3L
            )
        )
        assertFalse(
            "宽限期内的删除事件不算可续排的待发事件",
            OutboxSyncWorker.hasUnattemptedPending(db.outboxDao(), emptySet())
        )
    }

    @Test
    fun `发件箱续排决策：有可重试失败时交给退避重试`() {
        assertTrue(OutboxSyncWorker.shouldScheduleContinuation(anyFailed = false, hasUnattempted = true))
        assertFalse(
            "有失败事件时整体退避重试，避免续排把重试次数烧光",
            OutboxSyncWorker.shouldScheduleContinuation(anyFailed = true, hasUnattempted = true)
        )
        assertFalse(OutboxSyncWorker.shouldScheduleContinuation(anyFailed = false, hasUnattempted = false))
    }

    @Test
    fun `分页恰好用满上限且最后一页是短页时不算超限`() {
        assertFalse(
            "第 MAX_PAGES 页是短页说明已到末页，不能误报超限",
            SyncManager.paginationExceeded(reachedEnd = true, pages = 200, maxPages = 200)
        )
        assertFalse(
            "还没用满页数且已到末页，更不算超限",
            SyncManager.paginationExceeded(reachedEnd = true, pages = 1, maxPages = 200)
        )
    }

    @Test
    fun `分页用满上限且没有短页时判定超限`() {
        assertTrue(
            "每页都是满页且用满 200 页，说明数据没拉完",
            SyncManager.paginationExceeded(reachedEnd = false, pages = 200, maxPages = 200)
        )
        assertFalse(
            "未用满页数时仍可继续翻页",
            SyncManager.paginationExceeded(reachedEnd = false, pages = 199, maxPages = 200)
        )
    }
}
