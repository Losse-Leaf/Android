package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import com.looseleaf.app.utils.FormatUtils
import kotlinx.serialization.EncodeDefault
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
 * 生活记录一级母体 (LifeEntry) - docs/backend.md §5.2
 */
@Immutable
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
    @SerialName("project_id") val projectId: Long? = null,
    val project: Project? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    val wish: Wish? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
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
@Immutable
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
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @SerialName("installment_remain_months") val installmentRemainMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null,
    @SerialName("sync_status") val syncStatus: String? = "SYNCED",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Immutable
@Serializable
data class TransactionSummary(
    val year: Int = 0,
    val month: Int = 0,
    @SerialName("total_expense") val totalExpense: Long = 0,
    @SerialName("total_income") val totalIncome: Long = 0,
    val balance: Long = 0,
    val count: Int = 0,
    @SerialName("category_breakdown") val categoryBreakdown: Map<String, Long>? = null
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
    @SerialName("edc_gear") val edcGear: List<GearItem> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

@Immutable
@Serializable
data class GearItem(
    val id: String,
    val name: String,
    val spec: String = "",
    val category: String = "数码器材",
    val status: String = "active",
    @SerialName("price_cents") val priceCents: Long? = null,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("sold_price_cents") val soldPriceCents: Long? = null,
    @SerialName("sold_date") val soldDate: String? = null,
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
    @SerialName("curator_name") val curatorName: String = "",
    @SerialName("default_location") val defaultLocation: String = "",
    @SerialName("site_desc") val siteDesc: String = "",
    val initialized: Boolean = false,
    @SerialName("initial_liquid_balance") val initialLiquidBalance: Long = 0
)

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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
    val order: Int = 0
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
    val attachments: List<Attachment> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null
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
    // 三态：null = 不携带（服务端保持原值）；true = 明确是分期。
    // 客户端用 encodeDefaults=false，等于默认值的字段会被整个省略，
    // 所以默认值**必须**是 null，才能区分「不携带」与「明确 false」。
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
    val attachments: List<Attachment> = emptyList(),
    @SerialName("entries_count") val entriesCount: Int? = null,
    @SerialName("total_spent") val totalSpent: Long? = null,
    @SerialName("transaction_count") val transactionCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CreateFootprintRequest(
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    val title: String? = null,
    @SerialName("visited_at") val visitedAt: String,
    @SerialName("departed_at") val departedAt: String? = null,
    val note: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null
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
    // 后端省份抽屉返回的键是 province_adcode（backend footprints.go），必须显式映射，
    // 否则 ignoreUnknownKeys 会把字段静默丢弃、adcode 永远为空
    @SerialName("province_adcode") val adcode: String = "",
    @SerialName("province_name") val provinceName: String = "",
    val footprints: List<Footprint> = emptyList(),
    @SerialName("visited_cities") val visitedCities: List<ProvinceVisitedCity> = emptyList(),
    val entries: List<LifeEntry> = emptyList(),
    @SerialName("visit_stats") val visitStats: CityVisitStats = CityVisitStats(),
    @SerialName("ledger_summary") val ledgerSummary: CityDrawerLedgerSummary = CityDrawerLedgerSummary()
)

/**
 * 资产大盘与设备库 (GET /api/v1/assets/overview)
 */
@Immutable
@Serializable
data class CreditChannel(
    val id: Long = 0,
    val code: String = "",
    val name: String = "",
    @SerialName("repayment_day") val repaymentDay: Int = 10,
    @SerialName("is_builtin") val isBuiltin: Boolean = false,
    val color: String? = null
)

@Serializable
data class CreateCreditChannelRequest(
    val code: String? = null,
    val name: String,
    @SerialName("repayment_day") val repaymentDay: Int = 10,
    val color: String? = null
)

@Serializable
data class ChannelLiability(
    @SerialName("channel_code") val channelCode: String = "",
    @SerialName("channel_name") val channelName: String = "",
    @SerialName("repayment_day") val repaymentDay: Int = 10,
    val color: String = "",
    @SerialName("total_debt") val totalDebt: Long = 0,
    @SerialName("monthly_pay") val monthlyPay: Long = 0,
    @SerialName("days_until_repayment") val daysUntilRepayment: Int = 0,
    @SerialName("active_count") val activeCount: Int = 0,
    val installments: List<Transaction> = emptyList()
)

@Serializable
data class GearSummary(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("active_value") val activeValue: Long = 0,
    @SerialName("sold_count") val soldCount: Int = 0,
    @SerialName("sold_recovered_value") val soldRecoveredValue: Long = 0,
    val items: List<GearItem> = emptyList()
)

@Serializable
data class AssetsOverview(
    @SerialName("net_worth") val netWorth: Long = 0,
    @SerialName("liquid_balance") val liquidBalance: Long = 0,
    @SerialName("initial_liquid_balance") val initialLiquidBalance: Long = 0,
    @SerialName("fixed_assets") val fixedAssets: Long = 0,
    @SerialName("total_liabilities") val totalLiabilities: Long = 0,
    @SerialName("monthly_liabilities") val monthlyLiabilities: Long = 0,
    @SerialName("gear_summary") val gearSummary: GearSummary = GearSummary(),
    @SerialName("channel_liabilities") val channelLiabilities: List<ChannelLiability> = emptyList()
)

@Serializable
data class SetLiquidBalanceRequest(
    @SerialName("initial_liquid_balance") val initialLiquidBalance: Long
)

@Serializable
data class LiquidBalanceResponse(
    @SerialName("initial_liquid_balance") val initialLiquidBalance: Long = 0
)

@Serializable
data class AddGearAssetRequest(
    val name: String,
    val category: String = "数码器材",
    val spec: String = "",
    @SerialName("price_cents") val priceCents: Long = 0,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val note: String? = null,
    // MOB-P1-35：这一项**不能有默认值** —— 客户端默认 true、服务端默认 false，
    // 若省略（encodeDefaults=false 时 true 会被省掉）就会悄悄少记一笔账。
    // 去掉默认值后该字段永远随请求下发。
    @SerialName("create_transaction") val createTransaction: Boolean,
    // 同上：null = 不携带，true = 明确是分期
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null
)

@Serializable
data class SellGearRequest(
    @SerialName("sold_price_cents") val soldPriceCents: Long,
    @SerialName("sold_date") val soldDate: String? = null,
    val note: String? = null
)

/**
 * 年度生活年鉴 (GET /api/v1/almanac/summary?year=...)
 */
@Serializable
data class AlmanacPrologue(
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("total_words") val totalWords: Int = 0,
    @SerialName("active_days") val activeDays: Int = 0,
    @SerialName("first_date") val firstDate: String = "",
    @SerialName("last_date") val lastDate: String = "",
    @SerialName("wishes_fulfilled") val wishesFulfilled: Int = 0,
    @SerialName("cities_visited") val citiesVisited: Int = 0,
    @SerialName("tx_count") val txCount: Int = 0
)

@Serializable
data class AlmanacCityItem(
    @SerialName("city_code") val cityCode: String = "",
    @SerialName("city_name") val cityName: String = ""
)

@Serializable
data class AlmanacAtlas(
    @SerialName("total_cities") val totalCities: Int = 0,
    val cities: List<AlmanacCityItem> = emptyList(),
    val footprints: List<Footprint> = emptyList()
)

@Serializable
data class AlmanacWishes(
    val wishes: List<Wish> = emptyList(),
    @SerialName("fulfilled_count") val fulfilledCount: Int = 0,
    @SerialName("wanting_count") val wantingCount: Int = 0,
    @SerialName("total_budget") val totalBudget: Long = 0,
    @SerialName("total_actual") val totalActual: Long = 0
)

@Serializable
data class AlmanacHighlights(
    val spring: LifeEntry? = null,
    val summer: LifeEntry? = null,
    val autumn: LifeEntry? = null,
    val winter: LifeEntry? = null,
    val photos: List<Attachment> = emptyList()
)

@Serializable
data class AlmanacFinances(
    @SerialName("total_income") val totalIncome: Long = 0,
    @SerialName("total_expense") val totalExpense: Long = 0,
    val balance: Long = 0,
    @SerialName("wish_expenses") val wishExpenses: Long = 0,
    @SerialName("wish_spending_ratio") val wishSpendingRatio: Double = 0.0,
    val categories: List<CityDrawerCategoryStat> = emptyList()
)

@Serializable
data class AlmanacSummaryResponse(
    val year: Int = 0,
    val prologue: AlmanacPrologue = AlmanacPrologue(),
    val atlas: AlmanacAtlas = AlmanacAtlas(),
    val wishes: AlmanacWishes = AlmanacWishes(),
    val highlights: AlmanacHighlights = AlmanacHighlights(),
    val finances: AlmanacFinances = AlmanacFinances()
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
data class CategoryOption(
    val id: Long = 0,
    val scope: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_builtin") val isBuiltin: Boolean = false
)

@Serializable
data class CategoriesResponse(
    val items: List<CategoryOption> = emptyList(),
    @SerialName("by_scope") val byScope: Map<String, List<String>> = emptyMap()
)

@Serializable
data class CreateCategoryRequest(
    val scope: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null
)

@Serializable
data class StorageStatus(
    val provider: String = "local",
    val configured: Boolean = false,
    @SerialName("account_id") val accountId: String? = null,
    val bucket: String? = null,
    @SerialName("public_domain") val publicDomain: String? = null,
    val endpoint: String? = null,
    @SerialName("access_key_id") val accessKeyId: String? = null,
    @SerialName("has_secret_key") val hasSecretKey: Boolean? = false,
    @SerialName("local_file_count") val localFileCount: Int? = 0,
    @SerialName("local_total_bytes") val localTotalBytes: Long? = 0
)

@Serializable
data class StorageConfigRequest(
    @SerialName("account_id") val accountId: String = "",
    @SerialName("access_key_id") val accessKeyId: String = "",
    @SerialName("secret_access_key") val secretAccessKey: String = "",
    @SerialName("bucket_name") val bucketName: String = "",
    val endpoint: String = "",
    @SerialName("public_domain") val publicDomain: String = "",
    @SerialName("presign_expire_seconds") val presignExpireSeconds: Int = 1800
)

@Serializable
data class StorageTestResponse(
    val status: String = "ok",
    val message: String = "",
    @SerialName("latency_ms") val latencyMs: Long = 0
)

@Serializable
data class StorageMigrateResponse(
    @SerialName("uploaded_to_r2") val uploadedToR2: Int = 0,
    @SerialName("downloaded_to_local") val downloadedToLocal: Int = 0,
    val message: String = ""
)

@Immutable
@Serializable
data class BackupItem(
    val filename: String,
    val size: Long = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("storage_type") val storageType: String = "local"
)

@Serializable
data class RestoreBackupRequest(
    val filename: String
)

@Serializable
data class RestoreBackupResponse(
    val message: String = "",
    val filename: String = "",
    val size: Long = 0
)

@Serializable
data class GlobalSearchResponse(
    val query: String = "",
    val entries: List<LifeEntry> = emptyList(),
    val footprints: List<Footprint> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val projects: List<Project> = emptyList()
)

@Serializable
data class GlobalStats(
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("total_projects") val totalProjects: Int = 0,
    @SerialName("month_expense") val monthExpense: Long = 0,
    @SerialName("current_year") val currentYear: Int = 0,
    @SerialName("current_month") val currentMonth: Int = 0
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
    @SerialName("city_name") val cityName: String? = null,
    // ── 归属绑定（MOB 表单审计 #5）────────────────────────
    // EncodeDefault(ALWAYS) 让 null 也显式落进请求体（"project_id": null）——
    // 后端 UpdateLifeEntry 对「携带即写入」：显式 null = 解绑，缺省 = 保持。
    // 散页编辑把类型从造物/心愿切走时，必须靠显式 null 才能解绑旧归属，
    // 否则省略的 key 会被服务端按「保持原值」吞掉、类型与绑定矛盾落库。
    // 创建路径携带 null 与缺省等价（后端仅在非 null 时绑定），无副作用。
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("project_id") val projectId: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    // ── 契约 A:显式清空属地 ──────────────────────────────
    // 服务端对省略的 city_code 走「保持原值」;清空城市时必须携带 clear_city: true
    // (clear 优先,city_code 可不携带),否则「清空属地」会被静默忽略、下次拉取又复活。
    @SerialName("clear_city") val clearCity: Boolean = false,
    // ── 契约 B:显式解绑全部附件 ──────────────────────────
    // attachment_ids: [] 单独发送会被服务端按「保持原值」忽略(保护老客户端),
    // 删光照片时必须同时携带 clear_attachments: true。
    @SerialName("clear_attachments") val clearAttachments: Boolean = false,
    // ── 契约 C:幂等创建键 ────────────────────────────────
    // 发件箱 CREATE 事件创建时生成 UUID 并随载荷持久化,重试复用同一个;
    // 服务端按它查重,命中直接返回已有记录——worker 在「已建档、未 markSynced」
    // 窗口被杀后重试不会重复建档。
    @SerialName("client_request_id") val clientRequestId: String? = null
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
    @SerialName("city_code") val cityCode: String? = null,
    @SerialName("wish_id") val wishId: Long? = null,
    @SerialName("footprint_id") val footprintId: Long? = null,
    // ── 分期三态（后端契约）─────────────────────────────────
    //   null                    = 本次**不携带**该字段 → 服务端保持原值
    //   true                    = 明确是分期
    //   false                   = 配合 clearInstallment=true 才会真的降级
    //
    // 关键：客户端用 encodeDefaults=false，等于默认值的字段会被整个省略。
    // 原先 `isInstallment: Boolean = false` 让「取消分期」这个动作
    // 被整个省掉 ⇒ 服务端收不到 ⇒ 分期没被取消；
    // 而本地乐观写却把它写成 false ⇒ 本地显示「一次性」、服务端仍是「分期」，
    // 两端当场分叉。
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    // ClearInstallment 显式声明「本次要取消分期」
    //
    // 单发 `is_installment: false` **不足以**表达取消 —— 服务端为了保护
    // 老客户端（它们的字段不可选，必定发 false），只在携带
    // `clear_installment: true` 时才真的把分期降级并清空账期字段。
    @SerialName("clear_installment") val clearInstallment: Boolean = false,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @SerialName("installment_remain_months") val installmentRemainMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null,
    // ── 契约 A:显式清空属地(与 CreateEntryRequest 同语义)──────────
    @SerialName("clear_city") val clearCity: Boolean = false,
    // ── 契约 C:幂等创建键(发件箱 CREATE 事件生成,重试复用)────────
    @SerialName("client_request_id") val clientRequestId: String? = null
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
    val platform: String = "android"
)

@Serializable
data class TrustedDevice(
    val id: Long = 0,
    @SerialName("device_id") val deviceId: String = "",
    val name: String = "",
    val platform: String = "android",
    @SerialName("device_model") val deviceModel: String? = null,
    val status: String = "active",
    @SerialName("paired_at") val pairedAt: String = "",
    @SerialName("last_active_at") val lastActiveAt: String = ""
)

@Serializable
data class PairTicketResponse(
    val ticket: String = "",
    @SerialName("hub_url") val hubUrl: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 300,
    @SerialName("qr_payload") val qrPayload: String = "",
    @SerialName("qr_code_data_url") val qrCodeDataUrl: String = ""
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
