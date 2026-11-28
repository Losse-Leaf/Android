package com.looseleaf.feature.ledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.UpsertCategoryRuleRequest
import com.looseleaf.feature.ledger.data.LedgerDatabase
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 接收自动记账通知栏上的「一键改分类」快捷动作。
 */
class AutoLedgerQuickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_RECLASSIFY) return
        val localId = intent.getLongExtra(EXTRA_LOCAL_ID, -1L)
        val targetCategory = intent.getStringExtra(EXTRA_TARGET_CATEGORY)?.trim().orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 4200)
        if (localId <= 0L || targetCategory.isEmpty()) return

        val appCtx = context.applicationContext
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                val db = LedgerDatabase.getInstance(appCtx)
                val entity = db.transactionDao().getTransactionByLocalId(localId) ?: return@launch

                val updatedReq = CreateTransactionRequest(
                    amount = entity.amount,
                    type = entity.type,
                    category = targetCategory,
                    title = entity.title,
                    note = entity.note,
                    paymentMethod = entity.paymentMethod,
                    occurredAt = entity.occurredAt,
                    wishId = entity.wishId,
                    isInstallment = entity.isInstallment.takeIf { it },
                    installmentChannelCode = entity.installmentChannelCode,
                    installmentTotalAmount = entity.installmentTotalAmount,
                    installmentMonths = entity.installmentMonths,
                    installmentMonthlyPay = entity.installmentMonthlyPay,
                    installmentRemainMonths = entity.installmentRemainMonths,
                    repaymentDay = entity.repaymentDay
                )
                val updated = LedgerRepository.updateTransactionOptimistic(-localId, updatedReq)
                if (!updated) return@launch

                val learned = AutoLedgerCategoryMemory.recordLocalLearn(
                    context = appCtx,
                    type = entity.type,
                    titleOrMerchant = entity.title,
                    category = targetCategory
                )
                if (learned != null) {
                    runCatching {
                        APIClient.getInstance(appCtx).upsertCategoryRule(
                            UpsertCategoryRuleRequest(
                                txType = learned.txType,
                                matchKey = learned.matchKey,
                                displayName = learned.displayName,
                                category = learned.category,
                                source = "learned"
                            )
                        )
                    }
                }

                AutoLedgerFeedbackNotifier.notifyReclassified(
                    context = appCtx,
                    notifId = notifId,
                    titleText = entity.title,
                    newCategory = targetCategory,
                    learnedMerchant = learned?.displayName
                )
                AutoLedgerDiagnostics.record(
                    source = "快捷改类",
                    outcome = "已改归类并记忆",
                    rawDetail = "${entity.title} -> [$targetCategory]",
                    isRecorded = true,
                    context = appCtx
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RECLASSIFY = "com.looseleaf.feature.ledger.ACTION_RECLASSIFY"
        const val EXTRA_LOCAL_ID = "extra_local_id"
        const val EXTRA_TARGET_CATEGORY = "extra_target_category"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
