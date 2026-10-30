package com.looseleaf.feature.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.looseleaf.app.model.Transaction

/**
 * 账本独立本地缓存表 (CachedTransaction)
 * 隶属于 :feature-ledger 独立 Room 数据库 (looseleaf_ledger.db)，不与散页主库共表
 */
@Entity(
    tableName = "cached_transactions",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["server_id"]),
        Index(value = ["sync_status"])
    ]
)
data class CachedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id") val localId: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "amount") val amount: Long, // 单位：分
    @ColumnInfo(name = "type") val type: String = "expense", // 'expense' | 'income'
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: String, // ISO-8601
    @ColumnInfo(name = "city_code") val cityCode: String? = null,
    @ColumnInfo(name = "wish_id") val wishId: Long? = null,
    @ColumnInfo(name = "footprint_id") val footprintId: Long? = null,
    @ColumnInfo(name = "is_installment") val isInstallment: Boolean = false,
    @ColumnInfo(name = "installment_channel_code") val installmentChannelCode: String? = null,
    @ColumnInfo(name = "installment_total_amount") val installmentTotalAmount: Long? = null,
    @ColumnInfo(name = "installment_months") val installmentMonths: Int? = null,
    @ColumnInfo(name = "installment_monthly_pay") val installmentMonthlyPay: Long? = null,
    @ColumnInfo(name = "installment_remain_months") val installmentRemainMonths: Int? = null,
    @ColumnInfo(name = "repayment_day") val repaymentDay: Int? = null,
    @ColumnInfo(name = "source") val source: String? = null,
    @ColumnInfo(name = "installment_plan_id") val installmentPlanId: Long? = null,
    @ColumnInfo(name = "installment_item_id") val installmentItemId: Long? = null,
    @ColumnInfo(name = "principal_amount") val principalAmount: Long = 0,
    @ColumnInfo(name = "fee_amount") val feeAmount: Long = 0,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "SYNCED",
    @ColumnInfo(name = "created_at") val createdAt: String = occurredAt
)

fun CachedTransactionEntity.toDomainModel(): Transaction = Transaction(
    id = serverId ?: -localId,
    amount = amount,
    type = type,
    category = category,
    title = title,
    note = note,
    paymentMethod = paymentMethod,
    occurredAt = occurredAt,
    cityCode = cityCode,
    wishId = wishId,
    footprintId = footprintId,
    isInstallment = isInstallment,
    installmentChannelCode = installmentChannelCode,
    installmentTotalAmount = installmentTotalAmount,
    installmentMonths = installmentMonths,
    installmentMonthlyPay = installmentMonthlyPay,
    installmentRemainMonths = installmentRemainMonths,
    repaymentDay = repaymentDay,
    source = source,
    installmentPlanId = installmentPlanId,
    installmentItemId = installmentItemId,
    principalAmount = principalAmount,
    feeAmount = feeAmount,
    syncStatus = syncStatus,
    createdAt = createdAt
)

fun Transaction.toCacheEntity(localId: Long = 0): CachedTransactionEntity = CachedTransactionEntity(
    localId = localId,
    serverId = if (id > 0) id else null,
    amount = amount,
    type = type,
    category = category,
    title = title,
    note = note,
    paymentMethod = paymentMethod,
    occurredAt = occurredAt,
    cityCode = cityCode,
    wishId = wishId,
    footprintId = footprintId,
    isInstallment = isInstallment ?: false,
    installmentChannelCode = installmentChannelCode,
    installmentTotalAmount = installmentTotalAmount,
    installmentMonths = installmentMonths,
    installmentMonthlyPay = installmentMonthlyPay,
    installmentRemainMonths = installmentRemainMonths,
    repaymentDay = repaymentDay,
    source = source,
    installmentPlanId = installmentPlanId,
    installmentItemId = installmentItemId,
    principalAmount = principalAmount,
    feeAmount = feeAmount,
    syncStatus = syncStatus ?: "SYNCED",
    createdAt = createdAt ?: occurredAt
)

/**
 * 账本独立发件箱事件实体
 */
@Entity(tableName = "ledger_outbox_events")
data class LedgerOutboxEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "action") val action: String, // "CREATE" | "UPDATE" | "DELETE"
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "local_target_id") val localTargetId: Long,
    @ColumnInfo(name = "server_target_id") val serverTargetId: Long? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "failed_at") val failedAt: String? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)
