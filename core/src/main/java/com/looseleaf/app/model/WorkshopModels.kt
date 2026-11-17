package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import com.looseleaf.app.utils.FormatUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 造物与项目 (Project)
 */
@Immutable
@Serializable
data class Project(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val story: String? = null,
    val category: String? = null,
    @SerialName("github_url") val githubUrl: String? = null,
    val status: String? = "进行中",
    val order: Int? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CreateProjectRequest(
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val story: String? = null,
    val category: String? = null,
    val status: String? = "进行中",
    @SerialName("github_url") val githubUrl: String? = null,
    val order: Int = 0,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null
)

/**
 * 心愿 (Wish)
 */
@Immutable
@Serializable
data class Wish(
    val id: Long,
    val title: String,
    val kind: String? = "item", // "item", "travel", "milestone"
    val note: String? = null,
    val spec: String? = null,
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("city_name") val cityName: String? = null,
    val budget: Long = 0,
    @SerialName("actual_cost") val actualCost: Long = 0,
    @SerialName("resolution_type") val resolutionType: String? = null,
    @SerialName("actual_title") val actualTitle: String? = null,
    val status: String = "wanting", // "wanting", "fulfilled", "relinquished", "shelved"
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
    @SerialName("transaction_id") val transactionId: Long? = null,
    @SerialName("life_entry_id") val lifeEntryId: Long? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
    @SerialName("gear_id") val gearId: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Immutable
@Serializable
data class WishesListResponse(
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    val wishes: List<Wish> = emptyList()
)

@Serializable
data class CreateWishRequest(
    val title: String,
    val kind: String = "item", // "item" | "travel" | "milestone"
    val note: String? = null,
    val spec: String? = null,
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("city_name") val cityName: String? = null,
    val budget: Long = 0,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null
)

@Serializable
data class ResolveWishRequest(
    @SerialName("fulfilled_at") val fulfilledAt: String = FormatUtils.beijingNowIsoString(),
    @SerialName("actual_cost") val actualCost: Long = 0,
    @SerialName("resolution_type") val resolutionType: String = "exact", // "exact" | "altered" | "relinquished"
    @SerialName("actual_title") val actualTitle: String? = null,
    @SerialName("create_transaction") val createTransaction: Boolean = false,
    @SerialName("create_gear_asset") val createGearAsset: Boolean = false,
    @SerialName("create_life_entry") val createLifeEntry: Boolean = false,
    @SerialName("create_footprint") val createFootprint: Boolean = false,
    @SerialName("entry_content") val entryContent: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null
)

/**
 * 足迹 (Footprint) - 严格对齐 backend/internal/models/models.go
 */
@Immutable
@Serializable
data class Footprint(
    val id: Long,
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    val title: String? = null,
    @SerialName("visited_at") val visitedAt: String? = null,
    @SerialName("departed_at") val departedAt: String? = null,
    val note: String? = null,
    @SerialName("life_entry_id") val lifeEntryId: Long? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    @SerialName("entries_count") val entriesCount: Int? = null,
    @SerialName("total_spent") val totalSpent: Long? = null,
    @SerialName("transaction_count") val transactionCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CreateFootprintRequest(
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    val title: String? = null,
    @SerialName("visited_at") val visitedAt: String,
    @SerialName("departed_at") val departedAt: String? = null,
    val note: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null
)

/**
 * 城市记忆抽屉 (GET /api/v1/footprints/cities/:city_code/drawer)
 */
@Immutable
@Serializable
data class CityVisitStats(
    @SerialName("total_visits") val totalVisits: Int = 0,
    @SerialName("first_visited_at") val firstVisitedAt: String = "",
    @SerialName("latest_visited_at") val latestVisitedAt: String = ""
)

@Serializable
data class CityDrawerCategoryStat(
    val name: String = "",
    val amount: Long = 0
)

@Immutable
@Serializable
data class CityDrawerLedgerSummary(
    @SerialName("total_spent") val totalSpent: Long = 0,
    @SerialName("transaction_count") val transactionCount: Int = 0,
    val categories: List<CityDrawerCategoryStat> = emptyList()
)

@Immutable
@Serializable
data class CityMemoryDrawerResponse(
    @SerialName("city_code") val cityCode: String = "",
    @SerialName("city_name") val cityName: String = "",
    @SerialName("visit_stats") val visitStats: CityVisitStats = CityVisitStats(),
    val footprints: List<Footprint> = emptyList(),
    val entries: List<LifeEntry> = emptyList(),
    @SerialName("ledger_summary") val ledgerSummary: CityDrawerLedgerSummary = CityDrawerLedgerSummary()
)

@Immutable
@Serializable
data class ProvinceVisitedCity(
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("visit_count") val visitCount: Int = 1
)

@Immutable
@Serializable
data class ProvinceMemoryDrawerResponse(
    @SerialName("province_adcode") val adcode: String = "",
    @SerialName("province_name") val provinceName: String = "",
    val footprints: List<Footprint> = emptyList(),
    @SerialName("visited_cities") val visitedCities: List<ProvinceVisitedCity> = emptyList(),
    val entries: List<LifeEntry> = emptyList(),
    @SerialName("visit_stats") val visitStats: CityVisitStats = CityVisitStats(),
    @SerialName("ledger_summary") val ledgerSummary: CityDrawerLedgerSummary = CityDrawerLedgerSummary()
)
