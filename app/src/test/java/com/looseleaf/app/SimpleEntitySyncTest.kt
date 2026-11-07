package com.looseleaf.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.SimpleEntitySync
import com.looseleaf.app.data.sync.matchesServer
import com.looseleaf.app.data.sync.toOfflineEntity
import com.looseleaf.app.data.sync.withResolvedState
import com.looseleaf.app.model.CreateFootprintRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.ResolveWishRequest
import com.looseleaf.app.model.Wish
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P0-3：造物 / 心愿 / 足迹离线临时行与发件箱目标模型。
 *
 * 这些断言锁死四条不能回退的规则：
 * 1. 临时行 id < 0，CREATE 的 localTargetId 必须是这个临时 id；
 * 2. 临时行编辑直接改写 CREATE 载荷，不产生“没有服务端目标”的 UPDATE；
 * 3. 临时行删除必须撤销 CREATE，且绝不入队 DELETE；
 * 4. CREATE 成功后后续事件换绑到真实 server id。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SimpleEntitySyncTest {

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
        entityType: String = "project",
        action: String = "CREATE",
        payload: String = "{}",
        localTargetId: Long = -1L,
        serverTargetId: Long? = null,
        failedAt: String? = null,
        retryCount: Int = 0
    ) = OutboxEventEntity(
        eventId = eventId,
        entityType = entityType,
        action = action,
        payload = payload,
        localTargetId = localTargetId,
        serverTargetId = serverTargetId,
        failedAt = failedAt,
        retryCount = retryCount
    )

    @Test
    fun `临时行 CREATE 使用负数 localTargetId 且不绑定服务端 id`() = runBlocking {
        val dao = db.outboxDao()
        SimpleEntitySync.enqueueCreate(dao, SimpleEntitySync.PROJECT, "{\"title\":\"离线造物\"}", -101L)

        val events = dao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("CREATE", events[0].action)
        assertEquals(-101L, events[0].localTargetId)
        assertNull(events[0].serverTargetId)
    }

    @Test
    fun `临时行编辑原地改写 CREATE 载荷而不是新增 UPDATE`() = runBlocking {
        val dao = db.outboxDao()
        SimpleEntitySync.enqueueCreate(dao, SimpleEntitySync.WISH, "{\"title\":\"旧标题\"}", -102L)
        SimpleEntitySync.enqueueUpdate(dao, SimpleEntitySync.WISH, "{\"title\":\"新标题\"}", -102L)

        val events = dao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("CREATE", events[0].action)
        assertEquals("{\"title\":\"新标题\"}", events[0].payload)
        assertEquals(-102L, events[0].localTargetId)
        assertNull(events[0].serverTargetId)
    }

    @Test
    fun `临时行删除撤销 CREATE 且不入队 DELETE`() = runBlocking {
        val dao = db.outboxDao()
        SimpleEntitySync.enqueueCreate(dao, SimpleEntitySync.FOOTPRINT, "{}", -103L)
        SimpleEntitySync.enqueueDelete(dao, SimpleEntitySync.FOOTPRINT, -103L)

        assertTrue(dao.getAllPendingEvents().isEmpty())
    }

    @Test
    fun `临时行删除也能撤销旧版本 server_target_id 指向临时 id 的 CREATE`() = runBlocking {
        val dao = db.outboxDao()
        // 旧版本：local_target_id = -1，临时 id 落在 server_target_id。
        dao.insert(
            event(
                eventId = "legacy-create",
                entityType = "wish",
                action = "CREATE",
                localTargetId = -1L,
                serverTargetId = -103L
            )
        )
        SimpleEntitySync.enqueueDelete(dao, SimpleEntitySync.WISH, -103L)

        assertTrue(dao.getAllPendingEvents().isEmpty())
    }

    @Test
    fun `已同步行删除会取消旧 UPDATE 并入队 DELETE`() = runBlocking {
        val dao = db.outboxDao()
        SimpleEntitySync.enqueueUpdate(dao, SimpleEntitySync.PROJECT, "{\"title\":\"改过\"}", 7L)
        SimpleEntitySync.enqueueDelete(dao, SimpleEntitySync.PROJECT, 7L)

        val events = dao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("DELETE", events[0].action)
        assertEquals(7L, events[0].localTargetId)
        assertEquals(7L, events[0].serverTargetId)
    }

    @Test
    fun `CREATE 成功后后续 RESOLVE 换绑到真实 server id 并清空失败态`() = runBlocking {
        val dao = db.outboxDao()
        SimpleEntitySync.enqueueCreate(dao, SimpleEntitySync.WISH, "{\"title\":\"愿望\"}", -104L)
        SimpleEntitySync.enqueueResolve(dao, "{\"actual_cost\":100}", -104L)
        val resolve = dao.getAllPendingEvents().first { it.action == "RESOLVE" }
        dao.markFailed(resolve.eventId, "2026-11-07T10:00:00+08:00", "waiting for create")

        dao.rebindFollowUpsToServerId(SimpleEntitySync.WISH, -104L, 88L)

        val create = dao.findEventById(dao.getAllPendingEvents().first { it.action == "CREATE" }.eventId)!!
        assertEquals(-104L, create.localTargetId)
        assertNull(create.serverTargetId)

        val rebound = dao.findEventById(resolve.eventId)!!
        assertEquals(88L, rebound.localTargetId)
        assertEquals(88L, rebound.serverTargetId)
        assertNull(rebound.failedAt)
        assertEquals(0, rebound.retryCount)
    }

    @Test
    fun `旧版本事件目标迁移到新模型`() = runBlocking {
        val dao = db.outboxDao()
        dao.insert(
            event(
                eventId = "legacy-temp",
                entityType = "project",
                action = "CREATE",
                localTargetId = -1L,
                serverTargetId = -555L
            )
        )
        dao.insert(
            event(
                eventId = "legacy-server",
                entityType = "wish",
                action = "UPDATE",
                localTargetId = -1L,
                serverTargetId = 42L
            )
        )
        dao.insert(
            event(
                eventId = "entry-untouched",
                entityType = "entry",
                action = "UPDATE",
                localTargetId = -1L,
                serverTargetId = -9L
            )
        )

        val repaired = SimpleEntitySync.repairLegacyTargets(dao)
        assertEquals(2, repaired)

        val temp = dao.findEventById("legacy-temp")!!
        assertEquals(-555L, temp.localTargetId)
        assertNull(temp.serverTargetId)

        val server = dao.findEventById("legacy-server")!!
        assertEquals(42L, server.localTargetId)
        assertEquals(42L, server.serverTargetId)

        val entry = dao.findEventById("entry-untouched")!!
        assertEquals(-1L, entry.localTargetId)
        assertEquals(-9L, entry.serverTargetId)
    }

    @Test
    fun `临时行编辑会清掉旧版本遗留的同目标 UPDATE`() = runBlocking {
        val dao = db.outboxDao()
        // 旧版本：CREATE 与 UPDATE 都以 -1 为 local_target_id，临时 id 落在 server_target_id。
        dao.insert(
            event(
                eventId = "legacy-create",
                entityType = "project",
                action = "CREATE",
                localTargetId = -1L,
                serverTargetId = -301L
            )
        )
        dao.insert(
            event(
                eventId = "legacy-update",
                entityType = "project",
                action = "UPDATE",
                localTargetId = -1L,
                serverTargetId = -301L
            )
        )

        // 模拟启动迁移后，用户又编辑了一次临时行。
        SimpleEntitySync.repairLegacyTargets(dao)
        SimpleEntitySync.enqueueUpdate(
            dao, SimpleEntitySync.PROJECT, "{\"title\":\"最新内容\"}", -301L
        )

        val events = dao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("CREATE", events[0].action)
        assertEquals(-301L, events[0].localTargetId)
        assertNull(events[0].serverTargetId)
        assertEquals("{\"title\":\"最新内容\"}", events[0].payload)
    }

    @Test
    fun `迁移前编辑临时行也不会重复入队 CREATE`() = runBlocking {
        val dao = db.outboxDao()
        dao.insert(
            event(
                eventId = "legacy-create",
                entityType = "wish",
                action = "CREATE",
                localTargetId = -1L,
                serverTargetId = -302L
            )
        )

        SimpleEntitySync.enqueueUpdate(
            dao, SimpleEntitySync.WISH, "{\"title\":\"最新内容\"}", -302L
        )

        val events = dao.getAllPendingEvents()
        assertEquals(1, events.size)
        assertEquals("legacy-create", events[0].eventId)
        assertEquals(-302L, events[0].localTargetId)
        assertNull(events[0].serverTargetId)
        assertEquals("{\"title\":\"最新内容\"}", events[0].payload)
    }

    @Test
    fun `整表对账只删除远端缺失且未被发件箱保护的正数行`() {
        val stale = SimpleEntitySync.staleServerIdsToDelete(
            localIds = listOf(-101L, 1L, 2L, 3L, 4L),
            remoteIds = setOf(1L, 3L),
            protectedIds = setOf(2L)
        )
        assertEquals(listOf(4L), stale)
    }

    @Test
    fun `发件箱保护 id 同时覆盖 local 与 server 两列`() = runBlocking {
        val dao = db.outboxDao()
        dao.insert(event(eventId = "u", entityType = "project", action = "UPDATE", localTargetId = 11L, serverTargetId = 11L))
        dao.insert(event(eventId = "d", entityType = "project", action = "DELETE", localTargetId = 12L, serverTargetId = 12L))
        dao.insert(event(eventId = "t", entityType = "project", action = "CREATE", localTargetId = -5L))

        val protectedIds = dao.getProtectedServerTargetIds("project").toSet()
        assertEquals(setOf(11L, 12L), protectedIds)
        assertFalse(protectedIds.contains(-5L))
    }

    @Test
    fun `离线心愿初始状态是 wanting 且编辑保留归册状态`() {
        val created = CreateWishRequest(title = "相机").toOfflineEntity(-201L)
        assertEquals("wanting", created.status)

        val resolved = created.withResolvedState(
            ResolveWishRequest(resolutionType = "relinquished", actualCost = 999)
        )
        assertEquals("relinquished", resolved.status)
        assertEquals(0L, resolved.actualCost)

        val edited = CreateWishRequest(title = "新相机").toOfflineEntity(-201L, resolved)
        assertEquals("relinquished", edited.status)
        assertEquals("新相机", edited.title)
    }

    @Test
    fun `三类创建请求都会序列化 client_request_id`() {
        val json = Json { encodeDefaults = false }
        val project = json.encodeToString(
            CreateProjectRequest(title = "造物", clientRequestId = "cid-project")
        )
        val wish = json.encodeToString(
            CreateWishRequest(title = "心愿", clientRequestId = "cid-wish")
        )
        val footprint = json.encodeToString(
            CreateFootprintRequest(
                cityCode = "330100",
                cityName = "杭州市",
                visitedAt = "2026-11-01T10:00:00+08:00",
                clientRequestId = "cid-footprint"
            )
        )

        assertTrue(project.contains("\"client_request_id\":\"cid-project\""))
        assertTrue(wish.contains("\"client_request_id\":\"cid-wish\""))
        assertTrue(footprint.contains("\"client_request_id\":\"cid-footprint\""))
    }

    @Test
    fun `幂等命中旧记录时内容比对能识别出本地新编辑`() {
        val request = CreateProjectRequest(title = "新标题", description = "新描述")
        val stale = Project(id = 7L, title = "旧标题", description = "新描述")
        val fresh = Project(id = 7L, title = "新标题", description = "新描述")

        assertFalse(request.matchesServer(stale))
        assertTrue(request.matchesServer(fresh))
    }

    @Test
    fun `心愿目标日期的不同写法按同一时刻处理`() {
        val request = CreateWishRequest(title = "相机", targetDate = "2026-11-01")
        val sameMoment = Wish(id = 9L, title = "相机", targetDate = "2026-11-01T00:00:00+08:00")
        val differentMoment = Wish(id = 9L, title = "相机", targetDate = "2026-11-02T00:00:00+08:00")

        assertTrue(request.matchesServer(sameMoment))
        assertFalse(request.matchesServer(differentMoment))
    }

    @Test
    fun `足迹内容比对容忍城市名规范化与到访返程交换`() {
        val request = CreateFootprintRequest(
            cityCode = "330100",
            cityName = "杭州",
            title = "西湖",
            visitedAt = "2026-11-02T10:00",
            departedAt = "2026-11-01T09:00"
        )
        val normalized = Footprint(
            id = 11L,
            cityCode = "330100",
            cityName = "杭州市",
            title = "西湖",
            visitedAt = "2026-11-01T09:00:00+08:00",
            departedAt = "2026-11-02T10:00:00+08:00"
        )
        val edited = request.copy(note = "用户新写的备注")

        assertTrue(request.matchesServer(normalized))
        assertFalse(edited.matchesServer(normalized))
    }
}
