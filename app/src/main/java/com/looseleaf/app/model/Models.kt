package com.looseleaf.app.model

import com.looseleaf.app.utils.FormatUtils
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
data class EmptyPayload(val empty: String? = null)

@Serializable
data class DeleteResponse(
    val id: String? = null,
    val deleted: Boolean? = null,
    val purged: Boolean? = null
)

/**
 * 生活记录一级母体 (LifeEntry) - docs/backend.md §5.2
 */
@Serializable
data class LifeEntry(
    val id: Long,
    val type: String = "life", // "life", "travel", "project", "wish"
    val title: String,
    val content: String,
    @SerialName("occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    val location: String? = null,
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("city_name") val cityName: String? = null,
    val camera: String? = null,
    @SerialName("project_id") val projectId: Long? = null,
    val project: Project? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    val wish: Wish? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
    val attachments: List<Attachment> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("sync_status") val syncStatus: String? = "SYNCED" // "SYNCED", "PENDING_CREATE", "PENDING_UPDATE"
)

/**
 * 多媒体附件实体 (Attachment)
 */
@Serializable
data class Attachment(
    val id: Long = 0,
    @SerialName("entry_id") val entryId: Long? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
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
 * 记账明细 (Transaction) - 金额严格为 int64 分
 */
@Serializable
data class Transaction(
    val id: Long,
    val amount: Long, // 单位：分 (4250 -> 42.50元)
    val type: String = "expense", // "expense" | "income"
    val category: String,
    val title: String,
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    @SerialName("is_installment") val isInstallment: Boolean? = false,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @SerialName("installment_remain_months") val installmentRemainMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null,
    @SerialName("sync_status") val syncStatus: String? = "SYNCED",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TransactionSummary(
    @SerialName("total_expense") val totalExpense: Long = 0,
    @SerialName("total_income") val totalIncome: Long = 0,
    val balance: Long = 0,
    @SerialName("expense_count") val expenseCount: Int = 0,
    @SerialName("income_count") val incomeCount: Int = 0,
    @SerialName("category_breakdown") val categoryBreakdown: Map<String, Long>? = null
)

/**
 * 主理人档案 (CuratorProfile)
 */
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
    @SerialName("edc_gear") val edcGear: List<GearItem> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class GearItem(
    val id: String,
    val name: String,
    val spec: String = "",
    val category: String = "数码器材",
    val status: String = "active",
    @SerialName("purchase_date") val purchaseDate: String? = null,
    val note: String? = null
)

/**
 * 站点配置 (SiteConfig)
 */
@Serializable
data class SiteConfig(
    val id: Long? = null,
    @SerialName("site_name") val siteName: String = "散页",
    @SerialName("site_subtitle") val siteSubtitle: String = "",
    @SerialName("site_desc") val siteDesc: String = "",
    val initialized: Boolean = false,
    @SerialName("initial_liquid_balance") val initialLiquidBalance: Long = 0
)

/**
 * 造物与项目 (Project)
 */
@Serializable
data class Project(
    val id: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    @SerialName("github_url") val githubUrl: String? = null,
    val status: String? = "Maintained",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * 心愿 (Wish)
 */
@Serializable
data class Wish(
    val id: Long,
    val title: String,
    val kind: String? = "item", // "item", "travel", "milestone"
    val note: String? = null,
    val budget: Long = 0,
    @SerialName("actual_cost") val actualCost: Long = 0,
    val status: String = "wanting", // "wanting", "fulfilled"
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
    @SerialName("life_entry_id") val lifeEntryId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * 足迹 (Footprint)
 */
@Serializable
data class Footprint(
    val id: Long,
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("province_adcode") val provinceAdcode: String? = null,
    @SerialName("province_name") val provinceName: String? = null,
    val year: Int? = null,
    val notes: String? = null
)

@Serializable
data class EntriesListResponse(
    val entries: List<LifeEntry> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    val total: Int = 0
)

// DTOs for requests
@Serializable
data class CreateEntryRequest(
    val type: String = "life",
    val title: String,
    val content: String,
    @SerialName("occurred_at") val occurredAt: String,
    val location: String? = null,
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null
)

@Serializable
data class CreateTransactionRequest(
    val amount: Long,
    val type: String = "expense",
    val category: String,
    val title: String,
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("city_code") val cityCode: String? = null
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
data class VerifyPasscodeRequest(
    val passcode: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("device_model") val deviceModel: String = "Android"
)

@Serializable
data class VerifyPasscodeResponse(
    val token: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class PresignUploadRequest(
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    val size: Long,
    val folder: String = "entries",
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class PresignUploadResponse(
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("object_key") val objectKey: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
data class CompleteUploadRequest(
    @SerialName("object_key") val objectKey: String,
    val filename: String,
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
