package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import com.looseleaf.app.utils.FormatUtils
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 生活记录一级母体 (LifeEntry)
 */
@Immutable
@Serializable
data class LifeEntry(
    val id: Long,
    val type: String = "life", // "life" | "wish"
    val title: String,
    val content: String,
    @SerialName("occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    val location: String? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    val wish: Wish? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("sync_status") val syncStatus: String? = "SYNCED" // "SYNCED", "PENDING_CREATE", "PENDING_UPDATE"
)

/**
 * 多媒体附件实体 (Attachment)
 */
@Immutable
@Serializable
data class Attachment(
    val id: Long = 0,
    @SerialName("entry_id") val entryId: Long? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("object_key") val objectKey: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("mime_type") val mimeType: String,
    val size: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val url: String,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * 那年今日 (GET /api/v1/entries/on-this-day)
 */
@Serializable
data class OnThisDayResponse(
    val month: Int = 1,
    val day: Int = 1,
    val entries: List<LifeEntry> = emptyList()
)

@Serializable
data class EntriesListResponse(
    val entries: List<LifeEntry> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    val total: Int = 0
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class CreateEntryRequest(
    val type: String = "life",
    val title: String,
    val content: String,
    @SerialName("occurred_at") val occurredAt: String,
    val location: String? = null,
    // EncodeDefault(ALWAYS) 让 null 也显式落进请求体（"wish_id": null）——
    // 后端 UpdateLifeEntry 对「携带即写入」：显式 null = 解绑，缺省 = 保持。
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    @SerialName("clear_attachments") val clearAttachments: Boolean = false,
    @SerialName("client_request_id") val clientRequestId: String? = null
)
