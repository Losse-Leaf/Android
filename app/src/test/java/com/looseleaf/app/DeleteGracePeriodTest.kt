package com.looseleaf.app

import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.utils.FormatUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * MOB-P2-47：删除事件的**宽限期**。
 *
 * 这条测试是为了守住一个实测踩到的坑：
 * 删除若立即上传，发件箱 Worker 1 秒内就把 DELETE 送到了服务端，
 * 纸签上的「撤销」按钮永远只会得到「已同步到服务端，无法撤销」——
 * 功能等于没有。
 *
 * 现在删除事件先压 8 秒再上传，撤销才有意义。
 */
class DeleteGracePeriodTest {

    private fun event(createdAt: Instant, action: String = "DELETE") = OutboxEventEntity(
        eventId = "e-1",
        entityType = "entry",
        action = action,
        payload = "{}",
        localTargetId = 1L,
        serverTargetId = 1L,
        createdAt = FormatUtils.toBeijingIsoString(createdAt)
    )

    @Test
    fun `刚入队的删除事件处于宽限期内`() {
        val justNow = Instant.now()
        assertTrue(
            "刚创建的删除事件必须处于宽限期内，否则撤销永远赶不上上传",
            SyncManager.isDeleteWithinGracePeriod(event(justNow))
        )
    }

    @Test
    fun `超过宽限期的删除事件不再受保护`() {
        val longAgo = Instant.now().minus(SyncManager.DELETE_GRACE_PERIOD_MS + 2_000L, ChronoUnit.MILLIS)
        assertFalse(
            "宽限期一过就应当照常上传",
            SyncManager.isDeleteWithinGracePeriod(event(longAgo))
        )
    }

    @Test
    fun `非删除事件不受宽限期影响`() {
        val justNow = Instant.now()
        assertFalse(
            "CREATE / UPDATE 事件必须立即上传，不能被宽限期拖住",
            SyncManager.isDeleteWithinGracePeriod(event(justNow, action = "CREATE"))
        )
        assertFalse(
            SyncManager.isDeleteWithinGracePeriod(event(justNow, action = "UPDATE"))
        )
    }
}
