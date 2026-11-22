package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 标准 RESTful API v1 统一信封
 */
@Serializable
data class APIEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)

@Serializable
data class DeleteResponse(
    val deleted: Boolean? = null,
    val purged: Boolean? = null
)

/**
 * 主理人档案 (CuratorProfile)
 */
@Immutable
@Serializable
data class CuratorProfile(
    val id: Long? = null,
    val name: String = "",
    val title: String = "",
    val bio: String = "",
    val location: String = "",
    @SerialName("current_city") val currentCity: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("avatar_key") val avatarKey: String? = null,
    val motto: String = "",
    val manifesto: String = "",
    val email: String? = null,
    val github: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val title: String? = null,
    val bio: String? = null,
    val location: String? = null,
    @SerialName("current_city") val currentCity: String? = null,
    @SerialName("avatar_key") val avatarKey: String? = null,
    val motto: String? = null,
    val manifesto: String? = null,
    val email: String? = null,
    val github: String? = null
)

@Serializable
data class GlobalSearchResponse(
    val query: String = "",
    val entries: List<LifeEntry> = emptyList(),
    val wishes: List<Wish> = emptyList()
)

@Serializable
data class GlobalStats(
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("month_expense") val monthExpense: Long = 0,
    @SerialName("current_year") val currentYear: Int = 0,
    @SerialName("current_month") val currentMonth: Int = 0
)

@Serializable
data class ConfirmPairingRequest(
    val ticket: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("device_model") val deviceModel: String
)

@Serializable
data class ConfirmPairingResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("hub_url") val hubUrl: String? = null
)

@Serializable
data class UpdateDeviceNameRequest(
    val name: String
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: String = "",
    val name: String = "",
    val platform: String = "android",
    @SerialName("is_current") val isCurrent: Boolean = false
)

@Serializable
data class PresignUploadRequest(
    val filename: String,
    @SerialName("content_type") val contentType: String,
    val size: Long
)

@Serializable
data class PresignUploadResponse(
    @SerialName("upload_id") val uploadId: String = "",
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("object_key") val objectKey: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
data class CompleteUploadRequest(
    @SerialName("upload_id") val uploadId: String = "",
    @SerialName("object_key") val objectKey: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("mime_type") val mimeType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class UnifiedUploadResponse(
    @SerialName("attachment_id") val attachmentId: Long,
    @SerialName("object_key") val objectKey: String,
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val url: String
)
