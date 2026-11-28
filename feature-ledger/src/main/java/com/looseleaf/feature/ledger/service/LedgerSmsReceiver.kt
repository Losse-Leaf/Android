package com.looseleaf.feature.ledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 银行动账短信实时接收器 (LedgerSmsReceiver)
 *
 * 借鉴开源项目 SmartLedger (MIT) 与 AutoAccounting 的短信广播通道：
 * 1. 按发件人地址拼接多段长短信 PDU，避免农业银行(95599)等长动账短信在边界处被截断；
 * 2. 通过 [AutoLedgerParser.isFinancialSms] 过滤非金融/验证码短信；
 * 3. 与 [LedgerNotificationListenerService] 共用同一全局互斥锁与跨端去重槽位，
 *    当银行 App 与银行短信先后到达时自动合并补全、绝不重复入账。
 */
class LedgerSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val appCtx = context.applicationContext
        if (!AutoLedgerListenerStatus.isAutoRecordEnabled(appCtx)) {
            return
        }
        AutoLedgerDiagnostics.attachContext(appCtx)

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (messages.isNullOrEmpty()) return

        // 同发件人的长短信拆分在多个 PDU 中，必须先按 sender 拼接完整正文再解析
        val groupedBodies = linkedMapOf<String, StringBuilder>()
        var latestTimestamp = System.currentTimeMillis()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress?.trim().orEmpty()
            val body = sms.messageBody.orEmpty()
            if (body.isBlank()) continue
            groupedBodies.getOrPut(sender) { StringBuilder() }.append(body)
            if (sms.timestampMillis > 0L) {
                latestTimestamp = sms.timestampMillis
            }
        }
        if (groupedBodies.isEmpty()) return

        val pendingResult = goAsync()
        val nowMs = System.currentTimeMillis()

        receiverScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                val snapshot = LedgerRepository.snapshot.value
                val expenseKeywords = snapshot.categoryKeywordsForType("expense")
                val incomeKeywords = snapshot.categoryKeywordsForType("income")
                val learnedExpense = AutoLedgerCategoryMemory.lookupMap(appCtx, "expense")
                val learnedIncome = AutoLedgerCategoryMemory.lookupMap(appCtx, "income")
                for ((sender, sb) in groupedBodies) {
                    val fullBody = sb.toString().trim()
                    if (fullBody.isBlank()) continue
                    if (!AutoLedgerParser.isFinancialSms(sender, fullBody)) continue
                    if (AutoLedgerParser.isVerificationCodeOrSpam(fullBody)) {
                        AutoLedgerDiagnostics.record(
                            source = "银行短信",
                            outcome = "已忽略（命中过滤规则）",
                            rawDetail = fullBody,
                            isRecorded = false,
                            nowMs = nowMs
                        )
                        continue
                    }
                    val contentForParser = if (sender.isNotBlank() && !fullBody.contains(sender)) {
                        "$sender $fullBody"
                    } else {
                        fullBody
                    }
                    val decision = LedgerNotificationListenerService.processIncomingNotification(
                        context = appCtx,
                        pkg = "com.android.mms",
                        sbnKey = "sms:${sender.ifBlank { "bank" }}:${latestTimestamp / 10_000}",
                        notificationTimestampMs = latestTimestamp,
                        content = contentForParser,
                        expenseCategories = snapshot.expenseCategories,
                        incomeCategories = snapshot.incomeCategories,
                        expenseCategoryKeywords = expenseKeywords,
                        incomeCategoryKeywords = incomeKeywords,
                        learnedExpenseRules = learnedExpense,
                        learnedIncomeRules = learnedIncome,
                        nowMs = nowMs,
                        onCreate = { req -> LedgerRepository.createTransactionOptimistic(req) },
                        onEnrich = { localId, req -> LedgerRepository.updateTransactionOptimistic(-localId, req) }
                    )
                    if (decision == null) {
                        AutoLedgerDiagnostics.record(
                            source = "银行短信",
                            outcome = "已忽略（未匹配成交金额模板）",
                            rawDetail = fullBody,
                            isRecorded = false,
                            nowMs = nowMs
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "银行短信自动记账处理失败", e)
                AutoLedgerDiagnostics.record(
                    source = "银行短信",
                    outcome = "处理异常",
                    rawDetail = e.message ?: e.javaClass.simpleName,
                    isRecorded = false,
                    nowMs = nowMs,
                    context = appCtx
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LedgerSmsReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
