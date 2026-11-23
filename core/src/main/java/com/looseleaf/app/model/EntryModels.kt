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
    @SerialName("sync_status") val syncStatus: String? = "SYNCED", // "SYNCED", "PENDING_CREATE", "PENDING_UPDATE"
    /**
     * 与 Vesper Space 共享的原生分类（方式 A）。
     * 合法取值见 [EntryCategory]：空串为默认日常，后端会按地点 / 心愿对带图散页自动兜底投射。
     */
    val category: String = ""
)

/**
 * 散页原生分类（与 Vesper Space 画廊 1:1 投影）。
 * [code] 为后端 JSON 取值，[label] 为界面中文名。
 */
enum class EntryCategory(val code: String, val label: String) {
    DAILY("", "日常"),
    PHOTOGRAPHY("Photography", "摄影"),
    TRAVEL("Travel", "旅途"),
    STILL_LIFE("Still Life", "静物"),
    MOTION("Motion", "影像");

    companion object {
        /** 未知或历史取值一律回落为日常，保证界面永远有可读标签 */
        fun fromCode(code: String?): EntryCategory =
            entries.firstOrNull { it.code == (code ?: "") } ?: DAILY

        /** 画廊分类（不含日常），供筛选与选择器使用 */
        val gallery: List<EntryCategory> get() = listOf(PHOTOGRAPHY, TRAVEL, STILL_LIFE, MOTION)
    }
}

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
    @SerialName("client_request_id") val clientRequestId: String? = null,
    // 分类必须始终显式下发：后端对 UPDATE 是「携带即写入」，
    // 若按默认值省略，把「摄影」改回「日常」（空串）时服务端会保持旧值。
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val category: String = ""
)
