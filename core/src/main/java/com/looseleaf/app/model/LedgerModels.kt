package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import com.looseleaf.app.utils.FormatUtils
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("occurred_at") val occurredAt: String = FormatUtils.beijingNowIsoString(),
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @SerialName("installment_remain_months") val installmentRemainMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null,
    val source: String? = null,
    @SerialName("installment_plan_id") val installmentPlanId: Long? = null,
    @SerialName("installment_item_id") val installmentItemId: Long? = null,
    @SerialName("principal_amount") val principalAmount: Long = 0,
    @SerialName("fee_amount") val feeAmount: Long = 0,
    @SerialName("sync_status") val syncStatus: String? = "SYNCED",
    @SerialName("client_request_id") val clientRequestId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateTransactionRequest(
    val amount: Long,
    // type 必须始终显式下发：后端 UpdateTransaction 对空 type 按「保持原值」处理，
    // 若按默认值省略，把「收入」改回「支出」时服务端仍保持 income 并触发分类字典 400。
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "expense",
    val category: String,
    val title: String,
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("wish_id") val wishId: Long? = null,
    // 分期三态（后端契约）：null = 不携带保持原值；true = 明确分期；false 配合 clearInstallment=true 降级
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("clear_installment") val clearInstallment: Boolean = false,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_total_amount") val installmentTotalAmount: Long? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @SerialName("installment_remain_months") val installmentRemainMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null,
    @SerialName("installment_fee_mode") val installmentFeeMode: String? = null,
    @SerialName("installment_fee_value") val installmentFeeValue: Long? = null,
    @SerialName("first_due_date") val firstDueDate: String? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null
)

@Serializable
data class CategoryOption(
    val id: Long = 0,
    val scope: String,
    val name: String,
    @SerialName("group_name") val groupName: String = "",
    val keywords: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_builtin") val isBuiltin: Boolean = false,
    @SerialName("usage_count") val usageCount: Long = 0L
)

@Serializable
data class CategoryRule(
    val id: Long = 0,
    @SerialName("tx_type") val txType: String,
    @SerialName("match_key") val matchKey: String,
    @SerialName("display_name") val displayName: String,
    val category: String,
    @SerialName("hit_count") val hitCount: Int = 1,
    val source: String = "learned",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UpsertCategoryRuleRequest(
    @SerialName("tx_type") val txType: String,
    @SerialName("match_key") val matchKey: String? = null,
    @SerialName("display_name") val displayName: String,
    val category: String,
    val source: String = "learned"
)

@Serializable
data class CategoriesResponse(
    val items: List<CategoryOption> = emptyList(),
    @SerialName("by_scope") val byScope: Map<String, List<String>> = emptyMap()
)

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

// 真实分期还款计划体系契约模型 (RC-140)

@Serializable
data class InstallmentPlan(
    val id: Long = 0,
    @SerialName("channel_id") val channelId: Long = 0,
    @SerialName("channel_code") val channelCode: String = "",
    @SerialName("channel_name") val channelName: String? = null,
    val title: String = "",
    val kind: String = "expense",
    val principal: Long = 0, // 分
    val periods: Int = 3,
    @SerialName("fee_mode") val feeMode: String = "none", // "none" | "fixed_per_period" | "rate_per_period" | "total_fee"
    @SerialName("fee_value") val feeValue: Long = 0,
    @SerialName("total_fee") val totalFee: Long = 0,
    @SerialName("first_due_date") val firstDueDate: String = "",
    @SerialName("repay_day") val repayDay: Int = 10,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("purchase_entry_id") val purchaseEntryId: Long? = null,
    val status: String = "active", // "active" | "settled" | "cancelled"
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class InstallmentItem(
    val id: Long = 0,
    @SerialName("plan_id") val planId: Long = 0,
    val seq: Int = 1,
    @SerialName("due_date") val dueDate: String = "",
    @SerialName("principal_part") val principalPart: Long = 0, // 分
    @SerialName("fee_part") val feePart: Long = 0, // 分
    val status: String = "scheduled", // "scheduled" | "paid"
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("paid_amount") val paidAmount: Long = 0,
    @SerialName("pay_account") val payAccount: String? = null,
    @SerialName("transaction_id") val transactionId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class InstallmentPlanItemSummary(
    val id: Long = 0,
    @SerialName("channel_id") val channelId: Long = 0,
    @SerialName("channel_code") val channelCode: String = "",
    @SerialName("channel_name") val channelName: String? = null,
    val title: String = "",
    val kind: String = "expense",
    val principal: Long = 0,
    val periods: Int = 3,
    @SerialName("fee_mode") val feeMode: String = "none",
    @SerialName("fee_value") val feeValue: Long = 0,
    @SerialName("total_fee") val totalFee: Long = 0,
    @SerialName("first_due_date") val firstDueDate: String = "",
    @SerialName("repay_day") val repayDay: Int = 10,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val status: String = "active",
    val note: String? = null,
    @SerialName("remaining_principal") val remainingPrincipal: Long = 0,
    @SerialName("remaining_fee") val remainingFee: Long = 0,
    @SerialName("remaining_total") val remainingTotal: Long = 0,
    @SerialName("paid_principal") val paidPrincipal: Long = 0,
    @SerialName("paid_fee") val paidFee: Long = 0,
    @SerialName("paid_total") val paidTotal: Long = 0,
    @SerialName("paid_periods") val paidPeriods: Int = 0,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("next_principal") val nextPrincipal: Long = 0,
    @SerialName("next_fee") val nextFee: Long = 0,
    @SerialName("next_total") val nextTotal: Long = 0,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    @SerialName("overdue_days") val overdueDays: Int = 0
)

@Serializable
data class InstallmentPlanDetailResponse(
    val plan: InstallmentPlan,
    val items: List<InstallmentItem> = emptyList(),
    @SerialName("remaining_principal") val remainingPrincipal: Long = 0,
    @SerialName("remaining_fee") val remainingFee: Long = 0,
    @SerialName("remaining_total") val remainingTotal: Long = 0,
    @SerialName("paid_principal") val paidPrincipal: Long = 0,
    @SerialName("paid_fee") val paidFee: Long = 0,
    @SerialName("paid_total") val paidTotal: Long = 0,
    @SerialName("paid_periods") val paidPeriods: Int = 0,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("next_principal") val nextPrincipal: Long = 0,
    @SerialName("next_fee") val nextFee: Long = 0,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    @SerialName("overdue_days") val overdueDays: Int = 0
)

@Serializable
data class CreateInstallmentPlanRequest(
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("channel_code") val channelCode: String? = null,
    val title: String,
    val kind: String = "expense",
    val principal: Long,
    val periods: Int,
    @SerialName("fee_mode") val feeMode: String = "none",
    @SerialName("fee_value") val feeValue: Long = 0,
    @SerialName("first_due_date") val firstDueDate: String? = null,
    @SerialName("repay_day") val repayDay: Int? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    val note: String? = null
)

@Serializable
data class PayInstallmentItemRequest(
    @SerialName("paid_amount") val paidAmount: Long? = null,
    @SerialName("pay_account") val payAccount: String? = null,
    @SerialName("paid_at") val paidAt: String? = null
)

@Serializable
data class SettleInstallmentPlanRequest(
    @SerialName("paid_amount") val paidAmount: Long,
    @SerialName("pay_account") val payAccount: String? = null,
    @SerialName("paid_at") val paidAt: String? = null
)

@Serializable
data class InstallmentSummary(
    @SerialName("total_remaining_principal") val totalRemainingPrincipal: Long = 0,
    @SerialName("this_month_paid") val thisMonthPaid: Long = 0,
    @SerialName("this_month_scheduled") val thisMonthScheduled: Long = 0,
    @SerialName("nearest_due_date") val nearestDueDate: String? = null,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("overdue_amount") val overdueAmount: Long = 0
)

@Serializable
data class UpcomingInstallmentItem(
    @SerialName("item_id") val itemId: Long,
    @SerialName("plan_id") val planId: Long,
    @SerialName("plan_title") val planTitle: String,
    @SerialName("channel_name") val channelName: String,
    val seq: Int,
    @SerialName("total_periods") val totalPeriods: Int,
    @SerialName("due_date") val dueDate: String,
    @SerialName("principal_part") val principalPart: Long,
    @SerialName("fee_part") val feePart: Long,
    @SerialName("total_part") val totalPart: Long,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    @SerialName("overdue_days") val overdueDays: Int = 0
)
