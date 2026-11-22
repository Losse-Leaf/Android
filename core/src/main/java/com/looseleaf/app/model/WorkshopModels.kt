package com.looseleaf.app.model

import androidx.compose.runtime.Immutable
import com.looseleaf.app.utils.FormatUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 心愿 (Wish)
 */
@Immutable
@Serializable
data class Wish(
    val id: Long,
    val title: String,
    val kind: String? = "item", // "item" | "travel" | "milestone"
    val note: String? = null,
    val spec: String? = null,
    val budget: Long = 0,
    @SerialName("actual_cost") val actualCost: Long = 0,
    @SerialName("resolution_type") val resolutionType: String? = null,
    @SerialName("actual_title") val actualTitle: String? = null,
    val status: String = "wanting", // "wanting", "fulfilled", "relinquished", "shelved"
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("planted_at") val plantedAt: String? = null,
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
    @SerialName("transaction_id") val transactionId: Long? = null,
    @SerialName("life_entry_id") val lifeEntryId: Long? = null,
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
    @SerialName("create_life_entry") val createLifeEntry: Boolean = false,
    @SerialName("entry_content") val entryContent: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Long>? = null,
    @SerialName("is_installment") val isInstallment: Boolean? = null,
    @SerialName("installment_channel_code") val installmentChannelCode: String? = null,
    @SerialName("installment_months") val installmentMonths: Int? = null,
    @SerialName("repayment_day") val repaymentDay: Int? = null
)
