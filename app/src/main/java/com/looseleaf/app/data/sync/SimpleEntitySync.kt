package com.looseleaf.app.data.sync

import com.looseleaf.app.data.local.CachedFootprintEntity
import com.looseleaf.app.data.local.CachedProjectEntity
import com.looseleaf.app.data.local.CachedWishEntity
import com.looseleaf.app.data.local.OutboxDao
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.model.CreateFootprintRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.ResolveWishRequest
import com.looseleaf.app.model.Wish
import com.looseleaf.app.utils.FormatUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * P0-3：造物 / 心愿 / 足迹的离线临时行与发件箱目标模型。
 *
 * 统一约定：
 * - 未同步临时行 `id < 0`；
 * - CREATE 事件 `localTargetId = 临时 id`、`serverTargetId = null`；
 * - 临时行编辑直接改写 CREATE 载荷（不再另起一条没有服务端目标的 UPDATE）；
 * - 临时行删除取消该目标的全部事件（绝不把 CREATE 补发到服务端）；
 * - CREATE 成功后由 Worker 把后续 UPDATE / RESOLVE 换绑到真实 server id。
 */
internal object SimpleEntitySync {
    const val PROJECT = "project"
    const val WISH = "wish"
    const val FOOTPRINT = "footprint"

    /**
     * 进程内单调递减的临时 id。
     *
     * 原先用 `-System.currentTimeMillis()`：同一毫秒内连续创建两条会撞 id，
     * 第二条会覆盖第一条的本地行，两条事件还会共用同一个目标。
     */
    private val tempIdSeed = AtomicLong(-System.currentTimeMillis())

    fun nextTempLocalId(): Long = tempIdSeed.getAndDecrement()

    /** P1-2：为一次创建生成稳定的幂等键；重试必须复用同一个值。 */
    fun newClientRequestId(): String = UUID.randomUUID().toString()

    suspend fun enqueueCreate(
        outboxDao: OutboxDao,
        entityType: String,
        payload: String,
        tempLocalId: Long
    ) {
        require(tempLocalId < 0) { "CREATE 必须使用负数临时 id" }
        outboxDao.insert(
            OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = entityType,
                action = "CREATE",
                payload = payload,
                localTargetId = tempLocalId,
                serverTargetId = null
            )
        )
    }

    suspend fun enqueueUpdate(
        outboxDao: OutboxDao,
        entityType: String,
        payload: String,
        localTargetId: Long
    ) {
        if (localTargetId < 0) {
            // 旧版本离线编辑可能留下同一临时目标的 UPDATE；CREATE 载荷即将被
            // 改写成最新内容，旧 UPDATE 若在它之后重发只会把旧内容覆盖回来。
            outboxDao.deleteUpdatesForTempTarget(entityType, localTargetId)
            // 兼容旧版本 `local_target_id = -1, server_target_id = 临时 id` 的写法：
            // 迁移与编辑可能并发，两种写法都要命中，避免重复建一条 CREATE。
            val create = outboxDao.findEventByLocalOrServerTarget(entityType, localTargetId, "CREATE")
            if (create != null) {
                // 离线创建尚未上传：原地改写 CREATE 载荷（保留 eventId / createdAt），
                // 避免“先建旧内容、再补一条 UPDATE”的两段同步。
                outboxDao.insert(
                    create.copy(
                        payload = payload,
                        // 兼容旧写法命中时，local_target_id 可能还是 -1；
                        // 统一改成真实临时 id，后续对账/删除才能按目标定位。
                        localTargetId = localTargetId,
                        serverTargetId = null,
                        failedAt = null,
                        lastError = null,
                        retryCount = 0
                    )
                )
                return
            }
            // 兜底：临时行还在但 CREATE 事件丢了（旧版本清库等），按最新内容重新建档。
            enqueueCreate(outboxDao, entityType, payload, localTargetId)
            return
        }

        val existing = outboxDao.findEventByTarget(entityType, localTargetId, "UPDATE")
        if (existing != null) {
            outboxDao.insert(
                existing.copy(
                    payload = payload,
                    serverTargetId = localTargetId,
                    failedAt = null,
                    lastError = null,
                    retryCount = 0
                )
            )
        } else {
            outboxDao.insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = entityType,
                    action = "UPDATE",
                    payload = payload,
                    localTargetId = localTargetId,
                    serverTargetId = localTargetId
                )
            )
        }
    }

    suspend fun enqueueResolve(outboxDao: OutboxDao, payload: String, localTargetId: Long) {
        val existing = outboxDao.findEventByLocalOrServerTarget(WISH, localTargetId, "RESOLVE")
        val event = existing?.copy(
            payload = payload,
            // 旧写法命中时 local_target_id 可能还是 -1，统一改成真实目标。
            localTargetId = localTargetId,
            serverTargetId = localTargetId.takeIf { it > 0 },
            failedAt = null,
            lastError = null,
            retryCount = 0
        ) ?: OutboxEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = WISH,
            action = "RESOLVE",
            payload = payload,
            localTargetId = localTargetId,
            serverTargetId = localTargetId.takeIf { it > 0 }
        )
        outboxDao.insert(event)
    }

    /**
     * 删除本地目标。
     *
     * 先取消该目标的全部待发事件：临时行删除必须连同 CREATE 一起撤销，
     * 否则 Worker 补发成功后会把用户已删除的条目重新建到服务端。
     * 只有正数 id（已同步实体）才需要入队 DELETE。
     */
    suspend fun enqueueDelete(outboxDao: OutboxDao, entityType: String, localTargetId: Long) {
        outboxDao.deleteByLocalOrServerTarget(entityType, localTargetId)
        if (localTargetId > 0) {
            outboxDao.insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = entityType,
                    action = "DELETE",
                    payload = "{}",
                    localTargetId = localTargetId,
                    serverTargetId = localTargetId
                )
            )
        }
    }

    /**
     * 整表拉取对账：只返回「远端完整列表里不存在」且未被发件箱保护的正数行。
     * 负数临时行不在远端 id 域内，由调用方的插入过滤与删除条件天然保留。
     */
    fun staleServerIdsToDelete(
        localIds: Collection<Long>,
        remoteIds: Set<Long>,
        protectedIds: Set<Long>
    ): List<Long> = localIds.filter { it > 0 && it !in remoteIds && it !in protectedIds }

    suspend fun repairLegacyTargets(outboxDao: OutboxDao): Int {
        val negative = outboxDao.repairLegacyNegativeSimpleEntityTargets()
        val positive = outboxDao.repairLegacyPositiveSimpleEntityTargets()
        return negative + positive
    }

    /** 在线更新前记录待发 UPDATE；成功后再决定是否删除（见 [discardSupersededUpdate]）。 */
    suspend fun findPendingUpdate(
        outboxDao: OutboxDao,
        entityType: String,
        localTargetId: Long
    ): OutboxEventEntity? =
        if (localTargetId > 0) outboxDao.findEventByTarget(entityType, localTargetId, "UPDATE") else null

    /**
     * 在线更新已成功：只有载荷仍与请求前完全一致时才删除待发 UPDATE。
     * 若请求期间用户又改了内容（载荷已被改写），保留事件按新载荷重发。
     */
    suspend fun discardSupersededUpdate(outboxDao: OutboxDao, captured: OutboxEventEntity?) {
        if (captured == null) return
        outboxDao.deleteIfPayloadUnchanged(captured.eventId, captured.payload)
    }

    suspend fun hasPendingCreate(
        outboxDao: OutboxDao,
        entityType: String,
        localTargetId: Long
    ): Boolean = outboxDao.findEventByTarget(entityType, localTargetId, "CREATE") != null
}

// ---------- 请求模型 → 本地临时行（离线创建与 CREATE 升级回退共用） ----------

internal fun CreateProjectRequest.toOfflineEntity(
    id: Long,
    updatedAt: String = FormatUtils.beijingNowIsoString()
): CachedProjectEntity = CachedProjectEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    description = description,
    story = story,
    category = category,
    status = status ?: "进行中",
    githubUrl = githubUrl,
    order = order,
    updatedAt = updatedAt
)

internal fun CreateWishRequest.toOfflineEntity(
    id: Long,
    existing: CachedWishEntity? = null,
    updatedAt: String = FormatUtils.beijingNowIsoString()
): CachedWishEntity = CachedWishEntity(
    id = id,
    title = title,
    kind = kind,
    note = note,
    spec = spec,
    cityCode = cityCode,
    cityName = cityName,
    budget = budget,
    actualCost = existing?.actualCost ?: 0,
    resolutionType = existing?.resolutionType,
    actualTitle = existing?.actualTitle,
    // 服务端新建心愿的初始状态是 wanting；原先离线临时行写成 pending，
    // 心愿列表按 wanting / fulfilled / relinquished 分组时完全看不到它。
    status = existing?.status ?: "wanting",
    targetDate = targetDate,
    fulfilledAt = existing?.fulfilledAt,
    gearId = existing?.gearId,
    attachmentsJson = existing?.attachmentsJson ?: "[]",
    updatedAt = updatedAt
)

internal fun CreateFootprintRequest.toOfflineEntity(
    id: Long,
    existing: CachedFootprintEntity? = null,
    updatedAt: String = FormatUtils.beijingNowIsoString()
): CachedFootprintEntity = CachedFootprintEntity(
    id = id,
    cityCode = cityCode,
    cityName = cityName,
    title = title ?: existing?.title,
    visitedAt = visitedAt,
    departedAt = departedAt ?: existing?.departedAt,
    note = note ?: existing?.note,
    lifeEntryId = existing?.lifeEntryId,
    wishId = existing?.wishId,
    attachmentsJson = existing?.attachmentsJson ?: "[]",
    entriesCount = existing?.entriesCount ?: 0,
    totalSpent = existing?.totalSpent ?: 0,
    transactionCount = existing?.transactionCount ?: 0,
    updatedAt = updatedAt
)

/** 极端情况下临时行还在、CREATE 事件却丢了：用本地行重建一份最小创建请求。 */
internal fun CachedWishEntity.toCreateWishRequest(): CreateWishRequest = CreateWishRequest(
    title = title,
    kind = kind,
    note = note,
    spec = spec,
    cityCode = cityCode,
    cityName = cityName,
    budget = budget,
    targetDate = targetDate,
    clientRequestId = SimpleEntitySync.newClientRequestId()
)

/** 离线归册：本地状态必须与服务端一致（relinquished 不产生实际花费）。 */
internal fun CachedWishEntity.withResolvedState(req: ResolveWishRequest): CachedWishEntity {
    val relinquished = req.resolutionType == "relinquished"
    return copy(
        status = if (relinquished) "relinquished" else "fulfilled",
        actualCost = if (relinquished) 0 else req.actualCost,
        resolutionType = req.resolutionType,
        actualTitle = req.actualTitle ?: actualTitle,
        fulfilledAt = req.fulfilledAt,
        updatedAt = FormatUtils.beijingNowIsoString()
    )
}

// ---------- P1-2：幂等命中判定 ----------
//
// 服务端按 client_request_id 幂等返回的是**首次落库的旧记录**。若创建请求其实
// 已经成功、响应丢失后用户又编辑了临时行，重试会命中旧记录；此时若直接采用
// 服务端响应，用户的新内容会被静默盖掉。这里比对响应与本次发送载荷的关键字段，
// 不一致就说明响应已过期，调用方应保留本地内容并把 CREATE 升级为 UPDATE。

private fun normalizedText(value: String?): String = value?.trim().orEmpty()

private fun sameInstantOrText(sent: String?, server: String?): Boolean {
    val sentInstant = FormatUtils.parseOccurredInstantOrNull(sent)
    val serverInstant = FormatUtils.parseOccurredInstantOrNull(server)
    return if (sentInstant != null && serverInstant != null) {
        sentInstant == serverInstant
    } else {
        normalizedText(sent) == normalizedText(server)
    }
}

internal fun CreateProjectRequest.matchesServer(project: Project): Boolean =
    normalizedText(title) == normalizedText(project.title) &&
        normalizedText(subtitle) == normalizedText(project.subtitle) &&
        normalizedText(description) == normalizedText(project.description) &&
        normalizedText(story) == normalizedText(project.story) &&
        normalizedText(category) == normalizedText(project.category) &&
        normalizedText(githubUrl) == normalizedText(project.githubUrl) &&
        normalizedText(status).ifEmpty { "进行中" } ==
        normalizedText(project.status).ifEmpty { "进行中" } &&
        order == (project.order ?: 0)

internal fun CreateWishRequest.matchesServer(wish: Wish): Boolean =
    normalizedText(title) == normalizedText(wish.title) &&
        normalizedText(kind).ifEmpty { "item" } ==
        normalizedText(wish.kind).ifEmpty { "item" } &&
        normalizedText(note) == normalizedText(wish.note) &&
        normalizedText(spec) == normalizedText(wish.spec) &&
        normalizedText(cityCode) == normalizedText(wish.cityCode) &&
        normalizedText(cityName) == normalizedText(wish.cityName) &&
        budget == wish.budget &&
        sameInstantOrText(targetDate, wish.targetDate)

/**
 * 足迹的城市名会被后端按国标字典规范化、到访/返程倒挂会被自动交换，
 * 因此只比对城市代码与时间集合，避免把服务端的正常规范化误判成旧记录。
 */
internal fun CreateFootprintRequest.matchesServer(footprint: Footprint): Boolean {
    val sentTimes = listOfNotNull(
        FormatUtils.parseOccurredInstantOrNull(visitedAt),
        FormatUtils.parseOccurredInstantOrNull(departedAt)
    ).sorted()
    val serverTimes = listOfNotNull(
        FormatUtils.parseOccurredInstantOrNull(footprint.visitedAt),
        FormatUtils.parseOccurredInstantOrNull(footprint.departedAt)
    ).sorted()
    val timesMatch = if (sentTimes.isEmpty() || serverTimes.isEmpty()) {
        normalizedText(visitedAt) == normalizedText(footprint.visitedAt) &&
            normalizedText(departedAt) == normalizedText(footprint.departedAt)
    } else {
        sentTimes == serverTimes
    }
    return normalizedText(cityCode) == normalizedText(footprint.cityCode) &&
        normalizedText(title) == normalizedText(footprint.title) &&
        normalizedText(note) == normalizedText(footprint.note) &&
        timesMatch
}
