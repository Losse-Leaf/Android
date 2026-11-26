package com.looseleaf.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.CachedEntryEntity
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class OfflineMediaFileItem(
    val filePath: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val uploadedAttachment: Attachment? = null
)

@Serializable
data class OfflineMediaEntryPayload(
    val localImageFilePath: String = "",
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val files: List<OfflineMediaFileItem> = emptyList(),
    val req: CreateEntryRequest
)

/**
 * Narrows a pending media CREATE's upload file list to the user's current selection in the edit sheet.
 *
 * Local staged photos carry negative IDs and `objectKey = local_outbox/<file.name>`.
 */
internal fun selectMediaFiles(
    files: List<OfflineMediaFileItem>,
    selected: List<Attachment>
): List<OfflineMediaFileItem> {
    val keptNames = selected
        .filter { it.id < 0 }
        .map { it.objectKey.substringAfterLast('/') }
        .toSet()
    return files.filter { File(it.filePath).name in keptNames }
}

/**
 * Coordinates offline staging, optimistic entry creation, and background upload/replay
 * for `media_entry` outbox events.
 */
internal class MediaOutboxCoordinator(
    private val context: Context,
    private val database: AppDatabase,
    private val apiClient: APIClient,
    private val json: Json,
    private val notifyLocalWrite: () -> Unit,
    private val scheduleSync: () -> Unit,
    private val itemUploader: suspend (OfflineMediaFileItem, File) -> Attachment = { item, file ->
        ImagePipeline.uploadImage(
            apiClient = apiClient,
            file = file,
            mimeType = item.mimeType,
            filename = file.name,
            width = item.width,
            height = item.height
        )
    },
    private val entryCreator: suspend (CreateEntryRequest) -> com.looseleaf.app.model.LifeEntry = { req ->
        apiClient.createEntry(req)
    }
) {

    companion object {
        private const val MAX_CONCURRENT_UPLOADS = 3
    }

    fun saveCompressedImageToOutboxDir(bytes: ByteArray): File {
        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }
        val targetFile = File(outboxDir, "img_${UUID.randomUUID()}.jpg")
        try {
            targetFile.writeBytes(bytes)
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
            throw e
        }
        return targetFile
    }

    suspend fun createMediaEntryOffline(
        localImageFilePath: String,
        mimeType: String,
        width: Int,
        height: Int,
        req: CreateEntryRequest,
        additionalMediaFiles: List<OfflineMediaFileItem> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val outboxDir = File(context.filesDir, "outbox_media").apply { mkdirs() }

        fun ensureInOutboxDir(sourcePath: String): File {
            val src = File(sourcePath)
            if (src.parentFile?.absolutePath == outboxDir.absolutePath && src.exists()) {
                return src
            }
            val dest = File(outboxDir, "img_${UUID.randomUUID()}.jpg")
            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
            }
            return dest
        }

        val allItems = mutableListOf<OfflineMediaFileItem>()
        if (localImageFilePath.isNotBlank()) {
            val savedPrimary = ensureInOutboxDir(localImageFilePath)
            allItems.add(
                OfflineMediaFileItem(
                    filePath = savedPrimary.absolutePath,
                    mimeType = mimeType,
                    width = width,
                    height = height
                )
            )
        }
        for (extra in additionalMediaFiles) {
            if (extra.filePath.isNotBlank()) {
                val savedExtra = ensureInOutboxDir(extra.filePath)
                allItems.add(extra.copy(filePath = savedExtra.absolutePath))
            }
        }

        val previewAttachments = allItems.mapIndexed { idx, item ->
            val file = File(item.filePath)
            Attachment(
                id = -(idx + 1).toLong(),
                objectKey = "local_outbox/${file.name}",
                originalFilename = file.name,
                mimeType = item.mimeType,
                size = if (file.exists()) file.length() else 0L,
                width = item.width.takeIf { it > 0 },
                height = item.height.takeIf { it > 0 },
                url = "file://${file.absolutePath}"
            )
        }

        val idempotentReq = req.copy(clientRequestId = UUID.randomUUID().toString())

        val entryEntity = CachedEntryEntity(
            localId = 0,
            serverId = null,
            type = idempotentReq.type,
            title = idempotentReq.title,
            content = idempotentReq.content,
            occurredAt = idempotentReq.occurredAt,
            location = idempotentReq.location,
            wishId = idempotentReq.wishId,
            isPrivate = idempotentReq.isPrivate == true,
            attachmentsJson = json.encodeToString(previewAttachments),
            syncStatus = SyncManager.STATUS_PENDING_CREATE,
            createdAt = FormatUtils.beijingNowIsoString(),
            category = idempotentReq.category
        )

        val localId = database.withTransaction {
            val id = database.entryDao().insert(entryEntity)
            val primaryItem = allItems.firstOrNull()
            val payloadObj = OfflineMediaEntryPayload(
                localImageFilePath = primaryItem?.filePath ?: localImageFilePath,
                mimeType = primaryItem?.mimeType ?: mimeType,
                width = primaryItem?.width ?: width,
                height = primaryItem?.height ?: height,
                files = allItems,
                req = idempotentReq
            )
            database.outboxDao().insert(
                OutboxEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    entityType = "media_entry",
                    action = "CREATE",
                    payload = json.encodeToString(payloadObj),
                    localTargetId = id,
                    serverTargetId = null
                )
            )
            id
        }

        notifyLocalWrite()
        scheduleSync()
        localId
    }

    suspend fun replayOutboxEvent(event: OutboxEventEntity) {
        if (event.action != "CREATE") {
            database.outboxDao().delete(event)
            return
        }

        val payload = json.decodeFromString<OfflineMediaEntryPayload>(event.payload)
        val itemsToUpload = if (payload.files.isNotEmpty()) {
            payload.files
        } else if (payload.localImageFilePath.isNotBlank()) {
            listOf(
                OfflineMediaFileItem(
                    filePath = payload.localImageFilePath,
                    mimeType = payload.mimeType,
                    width = payload.width,
                    height = payload.height
                )
            )
        } else {
            emptyList()
        }

        val tempFiles = Collections.synchronizedList(mutableListOf<File>())
        val newlyUploadedByPath = ConcurrentHashMap<String, Attachment>()
        val uploadSemaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

        suspend fun persistUploadedAttachments() {
            if (newlyUploadedByPath.isEmpty()) return
            val latest = database.outboxDao().findEventById(event.eventId) ?: return
            val latestPayload = runCatching {
                json.decodeFromString<OfflineMediaEntryPayload>(latest.payload)
            }.getOrNull() ?: return
            val existingItems = if (latestPayload.files.isNotEmpty()) {
                latestPayload.files
            } else if (latestPayload.localImageFilePath.isNotBlank()) {
                listOf(
                    OfflineMediaFileItem(
                        filePath = latestPayload.localImageFilePath,
                        mimeType = latestPayload.mimeType,
                        width = latestPayload.width,
                        height = latestPayload.height
                    )
                )
            } else {
                emptyList()
            }
            val patchedFiles = existingItems.map { item ->
                val uploaded = newlyUploadedByPath[item.filePath]
                if (uploaded != null && (item.uploadedAttachment == null || item.uploadedAttachment.id <= 0)) {
                    item.copy(uploadedAttachment = uploaded)
                } else {
                    item
                }
            }
            val patchedPayload = latestPayload.copy(files = patchedFiles)
            database.outboxDao().insert(latest.copy(payload = json.encodeToString(patchedPayload)))
        }

        val uploadResults: List<Attachment?> = try {
            coroutineScope {
                itemsToUpload.map { item ->
                    async(Dispatchers.IO) {
                        val cached = item.uploadedAttachment?.takeIf { it.id > 0 }
                        if (cached != null) {
                            File(item.filePath).takeIf { it.exists() }?.let { tempFiles.add(it) }
                            return@async cached
                        }
                        uploadSemaphore.withPermit {
                            val file = File(item.filePath)
                            if (file.exists()) {
                                tempFiles.add(file)
                                itemUploader(item, file).also { att ->
                                    if (att.id > 0) {
                                        newlyUploadedByPath[item.filePath] = att
                                    }
                                }
                            } else null
                        }
                    }
                }.awaitAll()
            }
        } finally {
            // 哪怕同批第 N 张失败，也立即把前 N-1 张已传成功的 Attachment 写回 Outbox 载荷，下轮重试直接复用不重传
            runCatching { persistUploadedAttachments() }
        }

        val latestMediaEvent = database.outboxDao().findEventById(event.eventId)
        if (latestMediaEvent == null) {
            for (f in tempFiles) {
                runCatching { f.delete() }
            }
            return
        }
        val latestDecoded = runCatching {
            json.decodeFromString<OfflineMediaEntryPayload>(latestMediaEvent.payload)
        }.getOrNull()
        val currentFilePaths = if (latestDecoded != null && latestDecoded.files.isNotEmpty()) {
            latestDecoded.files.map { it.filePath }.toSet()
        } else {
            itemsToUpload.map { it.filePath }.toSet()
        }
        val selectedAttachments = uploadResults.mapIndexedNotNull { idx, att ->
            att?.takeIf { itemsToUpload[idx].filePath in currentFilePaths }
        }

        val missingSelected = uploadResults.withIndex().any { (idx, att) ->
            itemsToUpload[idx].filePath in currentFilePaths && att == null
        }
        if (missingSelected) {
            throw IllegalStateException("本地相纸文件缺失，无法完成上传")
        }

        val finalReq = payload.req.copy(
            attachmentIds = (
                (payload.req.attachmentIds ?: emptyList()) +
                    selectedAttachments.map { it.id }
                ).distinct().takeIf { it.isNotEmpty() }
        )
        val serverEntry = try {
            entryCreator(finalReq)
        } catch (e: APIException) {
            if (e.code == 410) {
                SyncManager.discardGoneCreate(database, event) {
                    database.entryDao().deleteByLocalId(event.localTargetId)
                }
                for (f in tempFiles) {
                    runCatching { f.delete() }
                }
                return
            }
            throw e
        }

        if (database.outboxDao().findEventById(event.eventId) == null) {
            runCatching { apiClient.deleteEntry(serverEntry.id) }
            for (f in tempFiles) {
                runCatching { f.delete() }
            }
            return
        }

        database.withTransaction {
            val pendingUpdate = database.outboxDao().findEventByTarget(
                "entry", event.localTargetId, "UPDATE"
            )
            val localEntity = database.entryDao().getEntryByLocalId(event.localTargetId)
            if (localEntity != null) {
                val finalAttachments = if (serverEntry.attachments.isNotEmpty()) {
                    serverEntry.attachments
                } else {
                    selectedAttachments
                }
                val statusAfterCreate =
                    if (pendingUpdate != null) SyncManager.STATUS_PENDING_UPDATE else SyncManager.STATUS_SYNCED
                database.entryDao().update(
                    localEntity.copy(
                        serverId = serverEntry.id,
                        syncStatus = statusAfterCreate,
                        attachmentsJson = json.encodeToString(finalAttachments)
                    )
                )
            } else {
                database.entryDao().markSynced(event.localTargetId, serverEntry.id)
            }
            database.outboxDao().bindPendingUpdatesToServerId(
                "entry", event.localTargetId, serverEntry.id
            )
            if (pendingUpdate != null) {
                val updateReq = runCatching {
                    json.decodeFromString<CreateEntryRequest>(pendingUpdate.payload)
                }.getOrNull()
                if (updateReq != null) {
                    val finalIds = (
                        (updateReq.attachmentIds ?: emptyList()) +
                            selectedAttachments.map { it.id }
                        ).distinct()
                    database.outboxDao().insert(
                        pendingUpdate.copy(
                            payload = json.encodeToString(
                                updateReq.copy(
                                    attachmentIds = finalIds.takeIf { it.isNotEmpty() },
                                    clearAttachments = finalIds.isEmpty()
                                )
                            )
                        )
                    )
                }
            }

            database.outboxDao().delete(event)
        }
        for (f in tempFiles) {
            runCatching { f.delete() }
        }
    }
}
