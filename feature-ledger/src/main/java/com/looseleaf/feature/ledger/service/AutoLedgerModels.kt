package com.looseleaf.feature.ledger.service

import com.looseleaf.app.model.CreateTransactionRequest

/**
 * 自动记账通知渠道分组与信息丰富度优先级。
 *
 * 参考 SmartLedger (MIT) 与 AutoAccounting 多渠道架构：
 * 一次真实消费/转账可能同时触发「前台完成页辅助识别 + 支付钱包/电商通知 + 银行 App + 银行短信 / 云闪付」多端提醒：
 * - 前台完成页（WECHAT_SCREEN / ALIPAY_SCREEN）可捕获微信零钱转账与好友收款，并含精准金额与对象，优先级最高；
 * - 支付钱包通知（WECHAT / ALIPAY）通常带有完整商户名与支付方式，次之；
 * - 银行 App / 云闪付 / 电商收银台再次之；
 * - 银行短信通常只有卡尾号与金额，优先级最低，但可作为银行 App 免通知或被系统折叠时的可靠兜底。
 */
enum class NotificationChannelGroup(val basePriority: Int) {
    WECHAT_SCREEN(4),
    ALIPAY_SCREEN(4),
    WECHAT(3),
    ALIPAY(3),
    UNIONPAY(2),
    BANK_APP(2),
    E_COMMERCE(2),
    BANK_SMS(1);

    /**
     * 判断两个渠道组是否可能对应同一笔底层资金扣款/入账。
     *
     * 例如：微信内绑银行卡转账时，可能同时触发 WECHAT_SCREEN + WECHAT + BANK_APP + BANK_SMS + UNIONPAY；
     * 但单笔支付不会同时经过微信和支付宝两个独立钱包。
     */
    fun correlatesWith(other: NotificationChannelGroup): Boolean {
        if (this == other) return true
        return when (this) {
            WECHAT_SCREEN -> other == WECHAT || other == BANK_APP || other == BANK_SMS || other == UNIONPAY || other == E_COMMERCE
            ALIPAY_SCREEN -> other == ALIPAY || other == BANK_APP || other == BANK_SMS || other == UNIONPAY || other == E_COMMERCE
            WECHAT -> other == WECHAT_SCREEN || other == BANK_APP || other == BANK_SMS || other == UNIONPAY || other == E_COMMERCE
            ALIPAY -> other == ALIPAY_SCREEN || other == BANK_APP || other == BANK_SMS || other == UNIONPAY || other == E_COMMERCE
            E_COMMERCE -> other != E_COMMERCE
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
    val qualityScore: Int,
    val channelHint: String? = null,
    val notifyOrigin: String? = null
)

internal sealed class AutoLedgerDedupeDecision {
    /** 全新交易，应写入一条本地账目 */
    data object RecordNew : AutoLedgerDedupeDecision()

    /** 跨端/同端重复提醒，但新通知携带更丰富的商户/分类/支付方式，应原地补全已有条目 */
    data class EnrichExisting(val existingLocalId: Long, val reason: String) : AutoLedgerDedupeDecision()

    /** 确认为重复通知，直接丢弃 */
    data class DropDuplicate(val reason: String) : AutoLedgerDedupeDecision()
}
