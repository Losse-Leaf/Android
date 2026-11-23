package com.looseleaf.app.data.sync

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.api.ResponseMeta
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.OutboxEventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Generic sync adapter for simple entities (currently `wish`).
 *
 * Unifies:
 * 1. Incremental (`?since=`) + full reconciliation pull with tombstone alignment & outbox protection;
 * 2. Online-first write-or-queue (`createOrQueue`, `updateOrQueue`, `deleteOrQueue`) with negative temporary IDs;
 * 3. Outbox replay (`CREATE` / `UPDATE` / `DELETE` + optional custom action such as `RESOLVE`) with
 *    idempotent replay comparison, 410 Gone cleanup, and canceled-in-flight compensation delete.
 */
internal class SimpleEntitySyncAdapter<CreateReq, UpdateReq, Domain, Entity>(
    private val entityType: String,
    private val datasetLabel: String,
    private val cursorKey: String,
    private val database: AppDatabase,
    private val syncPrefs: SharedPreferences,
    private val scheduleSync: () -> Unit,
    private val cursorForRequest: (String?) -> String?,
    private val maxUpdatedCursor: (List<String?>) -> String?,
    private val daoHasAny: suspend () -> Boolean,
    private val daoGetAllIds: suspend () -> List<Long>,
    private val daoGetById: suspend (Long) -> Entity?,
    private val daoInsert: suspend (Entity) -> Unit,
    private val daoInsertAll: suspend (List<Entity>) -> Unit,
    private val daoDeleteById: suspend (Long) -> Unit,
    private val daoDeleteByIds: suspend (List<Long>) -> Unit,
    private val domainId: (Domain) -> Long,
    private val domainUpdatedAt: (Domain) -> String?,
    private val domainDeletedAt: (Domain) -> String?,
    private val domainToEntity: (Domain) -> Entity,
    private val entityWithId: (Entity, Long) -> Entity,
    private val encodeCreateReq: (CreateReq) -> String,
    private val decodeCreateReq: (String) -> CreateReq,
    private val encodeUpdateReq: (UpdateReq) -> String,
    private val decodeUpdateReq: (String) -> UpdateReq,
    private val getCreateClientRequestId: (CreateReq) -> String?,
    private val withCreateClientRequestId: (CreateReq, String) -> CreateReq,
    private val updateReqAsCreateReq: (UpdateReq, String) -> CreateReq,
    private val createReqToOfflineEntity: (CreateReq, Long, Entity?) -> Entity,
    private val updateReqToOfflineEntity: (UpdateReq, Long, Entity?) -> Entity,
    private val matchesServer: (CreateReq, Domain) -> Boolean,
    private val fetchRemote: suspend (
        since: String?,
        recordPage: (ResponseMeta, List<Domain>) -> Unit
    ) -> List<Domain>,
    private val remoteCreate: suspend (CreateReq, ResponseMeta) -> Domain,
    private val remoteUpdate: suspend (Long, UpdateReq) -> Domain,
    private val remoteDelete: suspend (Long) -> Unit,
    private val shouldKeepLocalStateOnOnlineUpdate: suspend (Long) -> Boolean = { false },
    private val shouldKeepLocalStateAfterCreate: suspend (Long) -> Boolean = { false },
    private val extraOutboxActionHandler: (suspend SimpleEntitySyncAdapter<CreateReq, UpdateReq, Domain, Entity>.(
        event: OutboxEventEntity,
        onPayloadSuperseded: (() -> Unit)?
    ) -> Boolean)? = null
) {

    private enum class CreateOutcome { CANCELED, INSERT_SERVER, KEEP_LOCAL_AND_UPDATE }

    suspend fun pullDataset(forceFull: Boolean): String? = try {
        val cursor = syncPrefs.getString(cursorKey, null)
        val useIncremental = !forceFull && daoHasAny() && !cursor.isNullOrBlank()
        val localIdsBeforePull = if (!useIncremental) daoGetAllIds() else emptyList()
        val updatedCandidates = mutableListOf<String?>()

        val remoteItems = fetchRemote(if (useIncremental) cursorForRequest(cursor) else null) { meta, items ->
            val serverCursor = meta.syncCursor
            if (!serverCursor.isNullOrBlank()) {
                updatedCandidates += serverCursor
            } else {
                updatedCandidates += items.map(domainUpdatedAt)
                updatedCandidates += items.map(domainDeletedAt)
            }
        }

        database.withTransaction {
            val protectedIds = database.outboxDao()
                .getProtectedServerTargetIds(entityType)
                .toHashSet()

            if (useIncremental) {
                val tombstones = remoteItems.filter { domainDeletedAt(it) != null && domainId(it) !in protectedIds }
                if (tombstones.isNotEmpty()) {
                    tombstones.map(domainId).chunked(500).forEach { daoDeleteByIds(it) }
                }
                val active = remoteItems.filter { domainDeletedAt(it) == null && domainId(it) !in protectedIds }
                if (active.isNotEmpty()) {
                    daoInsertAll(active.map(domainToEntity))
                }
            } else {
                val remoteIds = remoteItems.mapTo(HashSet(), domainId)
                val toDelete = SimpleEntitySync.staleServerIdsToDelete(
                    localIdsBeforePull, remoteIds, protectedIds
                )
                if (toDelete.isNotEmpty()) {
                    toDelete.chunked(500).forEach { daoDeleteByIds(it) }
                }
                daoInsertAll(
                    remoteItems.filter { domainDeletedAt(it) == null && domainId(it) !in protectedIds }
                        .map(domainToEntity)
                )
            }
        }

        val maxUpdated = maxUpdatedCursor(updatedCandidates)
        if (maxUpdated != null) {
            syncPrefs.edit().putString(cursorKey, maxUpdated).apply()
        }
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "$datasetLabel：${e.message}"
    }

    suspend fun createOrQueue(req: CreateReq): Boolean = withContext(Dispatchers.IO) {
        val cid = getCreateClientRequestId(req) ?: SimpleEntitySync.newClientRequestId()
        val request = withCreateClientRequestId(req, cid)
        try {
            val created = remoteCreate(request, ResponseMeta())
            daoInsert(domainToEntity(created))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? APIException)?.code ?: -1
            if (code == -1 || code >= 500) {
                val tempId = SimpleEntitySync.nextTempLocalId()
                database.withTransaction {
                    daoInsert(createReqToOfflineEntity(request, tempId, null))
                    SimpleEntitySync.enqueueCreate(
                        database.outboxDao(),
                        entityType,
                        encodeCreateReq(request),
                        tempId
                    )
                }
                scheduleSync()
                true
            } else {
                throw e
            }
        }
    }

    suspend fun updateOrQueue(id: Long, req: UpdateReq): Boolean = withContext(Dispatchers.IO) {
        if (id < 0) {
            database.withTransaction {
                val existing = daoGetById(id)
                val createExists = SimpleEntitySync.hasPendingCreate(
                    database.outboxDao(), entityType, id
                )
                if (existing == null && !createExists) return@withTransaction
                val inheritedCid = pendingCreateClientRequestId(id)
                    ?: SimpleEntitySync.newClientRequestId()
                val createReq = updateReqAsCreateReq(req, inheritedCid)
                daoInsert(createReqToOfflineEntity(createReq, id, existing))
                SimpleEntitySync.enqueueUpdate(
                    database.outboxDao(),
                    entityType,
                    encodeCreateReq(createReq),
                    id
                )
            }
            scheduleSync()
            return@withContext true
        }
        try {
            val pendingUpdate = SimpleEntitySync.findPendingUpdate(
                database.outboxDao(), entityType, id
            )
            val keepLocalState = shouldKeepLocalStateOnOnlineUpdate(id)
            val updated = remoteUpdate(id, req)
            database.withTransaction {
                if (keepLocalState) {
                    val local = daoGetById(id)
                    daoInsert(updateReqToOfflineEntity(req, id, local))
                } else {
                    daoInsert(domainToEntity(updated))
                }
                SimpleEntitySync.discardSupersededUpdate(database.outboxDao(), pendingUpdate)
            }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? APIException)?.code ?: -1
            if (code == -1 || code >= 500) {
                val existing = daoGetById(id)
                database.withTransaction {
                    daoInsert(updateReqToOfflineEntity(req, id, existing))
                    SimpleEntitySync.enqueueUpdate(
                        database.outboxDao(),
                        entityType,
                        encodeUpdateReq(req),
                        id
                    )
                }
                scheduleSync()
                true
            } else {
                throw e
            }
        }
    }

    suspend fun deleteOrQueue(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (id < 0) {
            database.withTransaction {
                daoDeleteById(id)
                SimpleEntitySync.enqueueDelete(database.outboxDao(), entityType, id)
            }
            scheduleSync()
            return@withContext true
        }
        try {
            remoteDelete(id)
            database.withTransaction {
                daoDeleteById(id)
                database.outboxDao().deleteByLocalTarget(entityType, id)
            }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? APIException)?.code ?: -1
            if (code == 404) {
                database.withTransaction {
                    daoDeleteById(id)
                    database.outboxDao().deleteByLocalTarget(entityType, id)
                }
                false
            } else if (code == -1 || code >= 500) {
                database.withTransaction {
                    daoDeleteById(id)
                    SimpleEntitySync.enqueueDelete(database.outboxDao(), entityType, id)
                }
                scheduleSync()
                true
            } else {
                throw e
            }
        }
    }

    suspend fun replayOutboxEvent(
        event: OutboxEventEntity,
        onPayloadSuperseded: (() -> Unit)?
    ) {
        when (event.action) {
            "CREATE" -> replayCreate(event, onPayloadSuperseded)
            "UPDATE" -> replayTargetedMutation(event, onPayloadSuperseded) { targetServerId, payload ->
                remoteUpdate(targetServerId, decodeUpdateReq(payload))
            }
            "DELETE" -> replayDelete(event)
            else -> {
                val handled = extraOutboxActionHandler?.invoke(this, event, onPayloadSuperseded) == true
                if (!handled) {
                    database.outboxDao().delete(event)
                }
            }
        }
    }

    suspend fun replayTargetedMutation(
        event: OutboxEventEntity,
        onPayloadSuperseded: (() -> Unit)?,
        executeRemote: suspend (targetServerId: Long, payload: String) -> Domain
    ) {
        val current = database.outboxDao().findEventById(event.eventId) ?: return
        val targetServerId = current.serverTargetId ?: current.localTargetId.takeIf { it > 0 }
        if (targetServerId == null || targetServerId <= 0) {
            if (daoGetById(current.localTargetId) == null) {
                database.outboxDao().delete(current)
            }
            return
        }
        try {
            val updated = executeRemote(targetServerId, current.payload)
            database.withTransaction {
                val latest = database.outboxDao().findEventById(current.eventId)
                    ?: return@withTransaction
                if (latest.payload != current.payload) {
                    onPayloadSuperseded?.invoke()
                    return@withTransaction
                }
                daoDeleteById(current.localTargetId)
                daoInsert(domainToEntity(updated))
                database.outboxDao().delete(latest)
            }
        } catch (e: APIException) {
            if (e.code == 404) {
                database.withTransaction {
                    daoDeleteById(current.localTargetId)
                    database.outboxDao().delete(current)
                }
            } else {
                throw e
            }
        }
    }

    private suspend fun replayCreate(
        event: OutboxEventEntity,
        onPayloadSuperseded: (() -> Unit)?
    ) {
        if (event.localTargetId < -1 && daoGetById(event.localTargetId) == null) {
            database.outboxDao().delete(event)
            return
        }
        val sentPayload = event.payload
        val req = decodeCreateReq(sentPayload)
        val meta = ResponseMeta()
        val created = try {
            remoteCreate(req, meta)
        } catch (e: APIException) {
            if (e.code == 410) {
                database.withTransaction {
                    daoDeleteById(event.localTargetId)
                    database.outboxDao().delete(event)
                }
                return
            }
            throw e
        }

        if (domainDeletedAt(created) != null) {
            database.withTransaction {
                daoDeleteById(event.localTargetId)
                database.outboxDao().delete(event)
            }
            return
        }

        val serverId = domainId(created)
        val outcome = finalizeCreate(
            event = event,
            sentPayload = sentPayload,
            serverId = serverId,
            serverMatchesSentPayload = matchesServer(req, created),
            onPayloadUnchanged = {
                val keepLocal = shouldKeepLocalStateAfterCreate(serverId)
                val local = if (keepLocal) daoGetById(event.localTargetId) else null
                daoDeleteById(event.localTargetId)
                if (keepLocal && local != null) {
                    daoInsert(entityWithId(local, serverId))
                } else {
                    daoInsert(domainToEntity(created))
                }
            },
            onPayloadChanged = {
                val local = daoGetById(event.localTargetId)
                daoDeleteById(event.localTargetId)
                daoInsert(
                    entityWithId(
                        local ?: createReqToOfflineEntity(req, serverId, null),
                        serverId
                    )
                )
            }
        )
        when (outcome) {
            CreateOutcome.CANCELED -> {
                daoDeleteById(event.localTargetId)
                compensateCanceledCreate(serverId)
            }
            CreateOutcome.KEEP_LOCAL_AND_UPDATE -> onPayloadSuperseded?.invoke()
            CreateOutcome.INSERT_SERVER -> Unit
        }
    }

    private suspend fun replayDelete(event: OutboxEventEntity) {
        val current = database.outboxDao().findEventById(event.eventId) ?: return
        val targetServerId = current.serverTargetId ?: current.localTargetId.takeIf { it > 0 }
        if (targetServerId != null && targetServerId > 0) {
            try {
                remoteDelete(targetServerId)
            } catch (e: APIException) {
                if (e.code != 404) throw e
            }
        }
        database.withTransaction {
            daoDeleteById(current.localTargetId)
            if (targetServerId != null && targetServerId != current.localTargetId) {
                daoDeleteById(targetServerId)
            }
            database.outboxDao().delete(current)
        }
    }

    private suspend fun finalizeCreate(
        event: OutboxEventEntity,
        sentPayload: String,
        serverId: Long,
        serverMatchesSentPayload: Boolean,
        onPayloadUnchanged: suspend () -> Unit,
        onPayloadChanged: suspend () -> Unit
    ): CreateOutcome = database.withTransaction {
        val latest = database.outboxDao().findEventById(event.eventId)
            ?: return@withTransaction CreateOutcome.CANCELED
        val tempLocalId = event.localTargetId
        if (latest.payload != sentPayload || !serverMatchesSentPayload) {
            database.outboxDao().insert(
                latest.copy(
                    action = "UPDATE",
                    localTargetId = serverId,
                    serverTargetId = serverId,
                    failedAt = null,
                    lastError = null,
                    retryCount = 0
                )
            )
            database.outboxDao().rebindFollowUpsToServerId(
                event.entityType, tempLocalId, serverId
            )
            onPayloadChanged()
            CreateOutcome.KEEP_LOCAL_AND_UPDATE
        } else {
            database.outboxDao().delete(latest)
            database.outboxDao().rebindFollowUpsToServerId(
                event.entityType, tempLocalId, serverId
            )
            onPayloadUnchanged()
            CreateOutcome.INSERT_SERVER
        }
    }

    private suspend fun compensateCanceledCreate(serverId: Long) {
        try {
            remoteDelete(serverId)
        } catch (e: APIException) {
            if (e.code != 404) enqueueCompensationDelete(serverId)
        } catch (_: Exception) {
            enqueueCompensationDelete(serverId)
        }
    }

    private suspend fun enqueueCompensationDelete(serverId: Long) {
        database.outboxDao().insert(
            OutboxEventEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = entityType,
                action = "DELETE",
                payload = "{}",
                localTargetId = serverId,
                serverTargetId = serverId
            )
        )
    }

    private suspend fun pendingCreateClientRequestId(tempLocalId: Long): String? =
        database.outboxDao()
            .findEventByTarget(entityType, tempLocalId, "CREATE")
            ?.let { event ->
                runCatching { getCreateClientRequestId(decodeCreateReq(event.payload)) }.getOrNull()
            }
}
