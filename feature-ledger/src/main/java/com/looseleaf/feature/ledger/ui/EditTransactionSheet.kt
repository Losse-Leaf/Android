package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateCreditChannelRequest
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.model.InstallmentPlanDetailResponse
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditTransactionSheet(
    tx: Transaction,
    apiClient: APIClient,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    paymentMethods: List<String>,
    creditChannels: List<CreditChannel>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onRequestDeleteRepayConfirm: (Transaction) -> Unit,
    onTransactionDeletedWithUndo: (ArchiveSnackbarData) -> Unit,
    onOpenInstallmentPlanDetail: (InstallmentPlanDetailResponse) -> Unit
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()
    val isInstallmentRepay = tx.source == "installment_repay"

    var editAmount by remember(tx.id) {
        mutableStateOf(FormatUtils.centsToYuanString(tx.amount, includeSymbol = false))
    }
    var editTitle by remember(tx.id) { mutableStateOf(tx.title) }
    var editNote by remember(tx.id) { mutableStateOf(tx.note ?: "") }
    var editCategory by remember(tx.id) { mutableStateOf(tx.category) }
    var editType by remember(tx.id) { mutableStateOf(tx.type) }
    var editPaymentMethod by remember(tx.id) { mutableStateOf(tx.paymentMethod ?: "微信支付") }
    val editCategories = if (editType == "income") incomeCategories else expenseCategories

    val initialOccurredAt = remember(tx.id) {
        FormatUtils.parseOccurredInstant(tx.occurredAt).toEpochMilli()
    }
    var txOccurredAtMillis by remember(tx.id) { mutableStateOf(initialOccurredAt) }
    val txOccurredAt = remember(txOccurredAtMillis) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(txOccurredAtMillis), FormatUtils.BeijingZone)
    }

    var isCategoriesExpanded by remember { mutableStateOf(false) }
    var isPaymentMethodsExpanded by remember { mutableStateOf(false) }

    val userChannels = remember(creditChannels) { creditChannels.userCreditChannels() }
    var editPayChoice by remember(tx.id) { mutableStateOf(if (tx.isInstallment == true) "installment" else "full") }
    var editPeriodsText by remember(tx.id) {
        mutableStateOf((tx.installmentMonths ?: 12).takeIf { it > 0 }?.toString() ?: "12")
    }
    var editChannelCode by remember(tx.id) {
        mutableStateOf(tx.installmentChannelCode ?: userChannels.firstOrNull()?.code ?: "")
    }
    val selectedChannel = remember(userChannels, editChannelCode) {
        userChannels.firstOrNull { it.code == editChannelCode } ?: userChannels.firstOrNull()
    }

    val previewCents = if (isInstallmentRepay) tx.amount else FormatUtils.yuanToCents(editAmount)

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "编辑账目",
        actionText = "保存修改",
        onAction = {
            val cents = if (isInstallmentRepay) tx.amount else FormatUtils.yuanToCents(editAmount)
            if (cents > 0) {
                val submitInstallment = !isInstallmentRepay && editType == "expense" && editPayChoice == "installment"
                val shouldClearInstallment = tx.isInstallment == true && (!submitInstallment || editType == "income")
                val effectivePeriods = (editPeriodsText.toIntOrNull() ?: 12).coerceIn(1, 60)
                scope.launch {
                    val req = CreateTransactionRequest(
                        amount = cents,
                        type = editType,
                        category = editCategory,
                        title = editTitle.trim().ifBlank { editCategory },
                        note = editNote.trim().ifBlank { null },
                        paymentMethod = if (submitInstallment) (selectedChannel?.name ?: editPaymentMethod) else editPaymentMethod,
                        occurredAt = FormatUtils.toBeijingIsoString(txOccurredAt),
                        isInstallment = submitInstallment,
                        clearInstallment = shouldClearInstallment,
                        installmentChannelCode = if (submitInstallment) (selectedChannel?.code ?: tx.installmentChannelCode) else null,
                        installmentTotalAmount = if (submitInstallment) cents else null,
                        installmentMonths = if (submitInstallment) effectivePeriods else null,
                        installmentMonthlyPay = if (submitInstallment) (cents / effectivePeriods) else null,
                        installmentRemainMonths = if (submitInstallment) {
                            if (tx.isInstallment == true) tx.installmentRemainMonths else effectivePeriods
                        } else null,
                        repaymentDay = if (submitInstallment) (selectedChannel?.repaymentDay ?: tx.repaymentDay ?: 10) else null
                    )
                    val success = LedgerRepository.updateTransactionOptimistic(tx.id, req)
                    if (success) {
                        onSaved()
                    } else {
                        ArchiveToastManager.show("保存失败，记录不存在", isError = true)
                    }
                    onDismiss()
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isInstallmentRepay) {
                Box(
                    modifier = Modifier
                        .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "支出 · 分期还款",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                        .background(colors.surfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ArchiveTypeToggleButton(
                        text = "支出",
                        isSelected = editType == "expense",
                        onClick = {
                            editType = "expense"
                            if (editCategory !in expenseCategories) {
                                editCategory = expenseCategories.firstOrNull() ?: "餐饮"
                            }
                        }
                    )
                    ArchiveTypeToggleButton(
                        text = "收入",
                        isSelected = editType == "income",
                        onClick = {
                            editType = "income"
                            if (editCategory !in incomeCategories) {
                                editCategory = incomeCategories.firstOrNull() ?: "工资收入"
                            }
                        }
                    )
                }
            }

            if (isInstallmentRepay) {
                ArchiveField(
                    value = editAmount,
                    onValueChange = {},
                    label = "金额 (元)",
                    isNumeric = true,
                    enabled = false,
                    singleLine = true
                )
                Text(
                    text = "分期还款金额由还款期次决定，不可直接修改",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                ArchiveField(
                    value = editAmount,
                    onValueChange = { editAmount = FormatUtils.sanitizeDecimalYuanInput(it) },
                    label = "金额 (元)",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            ArchiveField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "标题 / 商户",
                singleLine = true
            )

            ArchiveField(
                value = editNote,
                onValueChange = { editNote = it },
                label = "备注",
                singleLine = true
            )

            Text(
                text = "分类",
                style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            val visibleCategories = if (editCategories.size > 7 && !isCategoriesExpanded) {
                editCategories.take(7)
            } else {
                editCategories
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in visibleCategories) {
                    val dotColor = LedgerCategoryColors.colorForCategory(cat, colors.isDark)
                    ArchiveSelectableCapsule(
                        text = cat,
                        selected = cat == editCategory,
                        leadingDotColor = dotColor,
                        accentColor = if (cat == editCategory) dotColor else null,
                        onClick = { editCategory = cat }
                    )
                }
                if (editCategories.size > 7) {
                    ArchiveTextButton(
                        text = if (isCategoriesExpanded) "收起" else "更多",
                        onClick = { isCategoriesExpanded = !isCategoriesExpanded }
                    )
                }
            }

            Text(
                text = "账户 / 渠道",
                style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
            val visiblePaymentMethods = if (paymentMethods.size > 7 && !isPaymentMethodsExpanded) {
                paymentMethods.take(7)
            } else {
                paymentMethods
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (pm in visiblePaymentMethods) {
                    ArchiveSelectableCapsule(
                        text = pm,
                        selected = pm == editPaymentMethod,
                        onClick = { editPaymentMethod = pm }
                    )
                }
                if (paymentMethods.size > 7) {
                    ArchiveTextButton(
                        text = if (isPaymentMethodsExpanded) "收起" else "更多",
                        onClick = { isPaymentMethodsExpanded = !isPaymentMethodsExpanded }
                    )
                }
            }

            ArchiveDateTimeSplitRow(
                selectedDateTime = txOccurredAt,
                onDateTimeSelected = { newDt ->
                    txOccurredAtMillis = newDt.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli()
                }
            )

            if (!isInstallmentRepay && editType == "expense") {
                InstallmentConfigSection(
                    periodsText = editPeriodsText,
                    onPeriodsTextChange = { editPeriodsText = it },
                    channels = userChannels,
                    selectedChannel = selectedChannel,
                    onChannelSelected = { editChannelCode = it.code },
                    toggleLabel = "付款方式",
                    payMethodChoice = editPayChoice,
                    onPayMethodChoiceChange = { editPayChoice = it },
                    maxPeriodDigits = 2,
                    principalCents = previewCents,
                    showPreviewCard = true,
                    onCreateChannel = { name, day ->
                        runCatching {
                            val created = apiClient.createCreditChannel(
                                CreateCreditChannelRequest(name = name, repaymentDay = day)
                            )
                            LedgerRepository.refreshInstallments(apiClient)
                            created
                        }.onFailure { err ->
                            ArchiveToastManager.show("创建渠道失败: ${err.message}", isError = true)
                        }.getOrNull()
                    }
                )
            }

            tx.installmentPlanId?.let { planId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                        .background(colors.accentSoft)
                        .clickable {
                            onDismiss()
                            scope.launch {
                                runCatching {
                                    onOpenInstallmentPlanDetail(apiClient.fetchInstallmentPlanDetail(planId))
                                }.onFailure { err ->
                                    ArchiveToastManager.show("加载分期计划失败: ${err.message}", isError = true)
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已关联分期计划",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                        color = colors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "查看还款计划",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = colors.archive.accentSmallText
                        )
                        Icon(
                            imageVector = FolioGlyphs.ChevronRight,
                            contentDescription = null,
                            tint = colors.archive.accentSmallText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                ArchiveSecondaryButton(
                    text = "删除此账目",
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        if (tx.source == "installment_repay") {
                            onRequestDeleteRepayConfirm(tx)
                        } else {
                            scope.launch {
                                val ticket = LedgerRepository.deleteTransaction(tx.id)
                                if (ticket != null) {
                                    onTransactionDeletedWithUndo(
                                        ArchiveSnackbarData(
                                            message = "已删除账目",
                                            actionLabel = "撤销",
                                            onActionClick = {
                                                scope.launch {
                                                    LedgerRepository.undoDeleteTransaction(ticket)
                                                }
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
