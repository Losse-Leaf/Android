package com.looseleaf.feature.ledger.service

import com.looseleaf.app.model.CreateTransactionRequest

/**
 * 自动记账通知渠道分组与信息丰富度优先级。
 *
 * 一次真实消费常同时触发「支付钱包 + 银行 App + 银行短信 / 云闪付」多端提醒：
 * - 支付钱包（微信 / 支付宝）通常带有最完整的商户名与支付方式，优先级最高；
 * - 银行 App / 云闪付次之；
 * - 银行短信通常只有卡尾号与金额，优先级最低。
 */
enum class NotificationChannelGroup(val basePriority: Int) {
    WECHAT(3),
    ALIPAY(3),
    UNIONPAY(2),
    BANK_APP(2),
    BANK_SMS(1);

    /**
     * 判断两个渠道组是否可能对应同一笔底层资金扣款/入账。
     *
     * 例如：微信支付绑银行卡消费时，可能同时触发 WECHAT + BANK_APP + BANK_SMS + UNIONPAY；
     * 但单笔支付不会同时经过微信支付和支付宝两个独立钱包。
     */
    fun correlatesWith(other: NotificationChannelGroup): Boolean {
        if (this == other) return true
        return when (this) {
            WECHAT -> other == BANK_APP || other == BANK_SMS || other == UNIONPAY
            ALIPAY -> other == BANK_APP || other == BANK_SMS || other == UNIONPAY
            UNIONPAY -> true
            BANK_APP -> true
            BANK_SMS -> true
        }
    }
}

internal data class ParsedAutoLedgerBill(
    val request: CreateTransactionRequest,
    val channelGroup: NotificationChannelGroup,
    val merchant: String?,
    val normalizedContent: String,
    val qualityScore: Int
)

internal sealed class AutoLedgerDedupeDecision {
    /** 全新交易，应写入一条本地账目 */
    data object RecordNew : AutoLedgerDedupeDecision()

    /** 跨端/同端重复提醒，但新通知携带更丰富的商户/分类/支付方式，应原地补全已有条目 */
    data class EnrichExisting(val existingLocalId: Long, val reason: String) : AutoLedgerDedupeDecision()

    /** 确认为重复通知，直接丢弃 */
    data class DropDuplicate(val reason: String) : AutoLedgerDedupeDecision()
}
