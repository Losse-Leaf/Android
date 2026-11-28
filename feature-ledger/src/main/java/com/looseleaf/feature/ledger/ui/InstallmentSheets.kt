package com.looseleaf.feature.ledger.ui

import com.looseleaf.app.ui.components.*
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.theme.LedgerTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * 阶段 2：新建分期 LedgerSheet
 * - 不考虑手续费（有就计入商品总额/本金中）
 * - 首期时间绑定渠道还款日，仅选月份
 * - 还款渠道仅保留用户自建 + 提供新建入口
 * - 本金与期数直接调用手机系统原生键盘，双列紧凑并排
 * - 所有文字单行截断（maxLines = 1, overflow = Ellipsis）
 */
@Composable
fun CreateInstallmentSheet(
    creditChannels: List<CreditChannel>,
    expenseCategories: List<String>,
    onDismissRequest: () -> Unit,
    onSubmit: suspend (CreateInstallmentPlanRequest) -> Boolean,
    initialTitle: String = "",
    initialPriceYuan: String = "",
    onCreateChannel: (suspend (name: String, repaymentDay: Int) -> CreditChannel?)? = null
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialTitle) }
    var principalYuan by remember { mutableStateOf(initialPriceYuan) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 常用期数直接填入，留一个空让用户填
    var periodsText by remember { mutableStateOf("12") }
    val effectivePeriods = periodsText.toIntOrNull() ?: 12

    val userChannels = remember(creditChannels) { creditChannels.userCreditChannels() }
    var selectedChannel by remember(userChannels) { mutableStateOf(userChannels.firstOrNull()) }

    var firstDueYearMonth by remember(selectedChannel) {
        mutableStateOf(defaultFirstDueYearMonth(selectedChannel?.repaymentDay ?: 10))
    }

    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull { it == "购物" } ?: expenseCategories.firstOrNull() ?: "购物") }
    var note by remember { mutableStateOf("") }

    val principalCents = FormatUtils.yuanToCents(principalYuan)
    val canSubmit = title.isNotBlank() && principalCents > 0 && effectivePeriods in 1..120

    ArchiveSheet(
        onDismissRequest = { if (!isSubmitting) onDismissRequest() },
        title = "新建分期",
        actionText = if (isSubmitting) "创建中…" else "创建",
        isActionEnabled = canSubmit && !isSubmitting,
        onAction = {
            if (canSubmit && !isSubmitting) {
                val firstDueDateStr = formatFirstDueDate(firstDueYearMonth, selectedChannel?.repaymentDay ?: 10)
                val req = CreateInstallmentPlanRequest(
                    channelId = selectedChannel?.id,
                    channelCode = selectedChannel?.code,
                    title = title.trim(),
                    kind = "expense",
                    principal = principalCents,
                    periods = effectivePeriods,
                    feeMode = "none",
                    feeValue = 0L,
                    firstDueDate = firstDueDateStr,
                    repayDay = selectedChannel?.repaymentDay ?: 10,
                    categoryName = selectedCategory,
                    note = note.trim().ifEmpty { null }
                )
                isSubmitting = true
                scope.launch {
                    val ok = onSubmit(req)
                    isSubmitting = false
                    if (ok) {
                        onDismissRequest()
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArchiveField(
                value = title,
                onValueChange = { title = it },
                label = "计划名称",
                placeholder = "如 iPhone 16 Pro 分期",
                singleLine = true
            )

            InstallmentConfigSection(
                periodsText = periodsText,
                onPeriodsTextChange = { periodsText = it },
                channels = userChannels,
                selectedChannel = selectedChannel,
                onChannelSelected = { selectedChannel = it },
                showPayMethodToggle = false,
                leadingAmountField = {
                    ArchiveField(
                        value = principalYuan,
                        onValueChange = { principalYuan = FormatUtils.sanitizeDecimalYuanInput(it) },
                        label = "分期总额",
                        placeholder = "0.00",
                        trailingText = "元",
                        isNumeric = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                },
                firstDueYearMonth = firstDueYearMonth,
                onFirstDueYearMonthChange = { firstDueYearMonth = it },
                principalCents = principalCents,
                emptyPreviewHint = "输入金额与期数后将实时试算各期还款额",
                onCreateChannel = onCreateChannel,
                installmentExtraContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "消费分类",
                            style = LedgerTheme.typography.caption,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expenseCategories.forEach { cat ->
                                ArchiveSelectableCapsule(
                                    text = cat,
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat }
                                )
                            }
                        }
                    }

                    ArchiveField(
                        value = note,
                        onValueChange = { note = it },
                        label = "备注",
                        placeholder = "选填",
                        singleLine = true
                    )
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * 阶段 3：计划详情全高 LedgerSheet
 */
@Composable
fun InstallmentPlanDetailSheet(
    detail: InstallmentPlanDetailResponse,
    onDismissRequest: () -> Unit,
    onPayItemClick: (InstallmentItem) -> Unit,
    onUnpayItemClick: (InstallmentItem) -> Unit,
    onSettleClick: () -> Unit,
    onCancelPlanClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val colors = LedgerTheme.colors
    val plan = detail.plan
    val items = detail.items
    val hasPaid = detail.paidPeriods > 0

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = plan.title,
        cancelText = "关闭",
        actionText = "编辑",
        isActionEnabled = true,
        onAction = onEditClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            val isSettled = plan.status == "settled"
            val channelMeta = buildString {
                append(plan.channelName ?: plan.channelCode)
                plan.categoryName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            }

            // 总览卡：剩余本金英雄数字 + 进度条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.xl)
                    .background(colors.surface)
                    .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.xl)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channelMeta,
                        style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSettled) {
                        Box(
                            modifier = Modifier
                                .clip(ArchiveShapes.full)
                                .background(colors.incomeSoft)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "已结清",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.income
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "剩余待还本金",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = FormatUtils.formatMoney(detail.remainingPrincipal),
                    style = com.looseleaf.app.theme.ArchiveTextStyles.amountHero.copy(fontSize = 38.sp),
                    color = if (detail.remainingPrincipal > 0) colors.expense else colors.income,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(16.dp))
                val progress = if (plan.periods > 0) detail.paidPeriods.toFloat() / plan.periods else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ArchiveShapes.full)
                        .background(colors.surfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(ArchiveShapes.full)
                            .background(colors.income)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "已还 ${detail.paidPeriods}/${plan.periods} 期",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (detail.paidFee > 0) {
                            "应还 ${FormatUtils.formatMoney(detail.remainingTotal)} · 手续费 ${FormatUtils.formatMoney(detail.paidFee)}"
                        } else {
                            "剩 ${plan.periods - detail.paidPeriods} 期"
                        },
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "还款计划",
                style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // 期次时间轴：左侧节点 + 竖线，点击未还期次标记还款，点击已还期次撤销
            val todayStr = LocalDate.now(FormatUtils.BeijingZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            items.forEachIndexed { index, itm ->
                val isPaid = itm.status == "paid"
                val isOverdue = !isPaid && itm.dueDate < todayStr
                val isDueThisMonth = !isPaid && !isOverdue && itm.dueDate.startsWith(todayStr.take(7))
                val nodeColor = when {
                    isPaid -> colors.income
                    isOverdue -> colors.alertRed
                    isDueThisMonth -> colors.accent
                    else -> colors.border
                }
                val statusText = when {
                    isPaid -> "已还"
                    isOverdue -> "逾期"
                    isDueThisMonth -> "本月待还"
                    else -> "未到期"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(ArchiveShapes.md)
                        .clickable {
                            if (isPaid) onUnpayItemClick(itm) else onPayItemClick(itm)
                        }
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 节点列
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .weight(1f)
                                    .background(if (index == 0) androidx.compose.ui.graphics.Color.Transparent else colors.borderSubtle)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .weight(1f)
                                    .background(if (index == items.lastIndex) androidx.compose.ui.graphics.Color.Transparent else colors.borderSubtle)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isPaid) 18.dp else 12.dp)
                                .clip(CircleShape)
                                .background(if (isPaid) nodeColor else colors.background)
                                .border(if (isPaid) 0.dp else 2.dp, nodeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPaid) {
                                Icon(
                                    imageVector = FolioGlyphs.Check,
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "第 ${itm.seq} 期",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                            color = if (isPaid) colors.textSecondary else colors.textPrimary
                        )
                        Text(
                            text = if (itm.feePart > 0) {
                                "${itm.dueDate} · 本金 ${FormatUtils.formatMoney(itm.principalPart)} + 手续费 ${FormatUtils.formatMoney(itm.feePart)}"
                            } else {
                                itm.dueDate
                            },
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = FormatUtils.formatMoney(itm.principalPart + itm.FeePartSafe()),
                            style = com.looseleaf.app.theme.ArchiveTextStyles.amount.copy(fontSize = 15.sp),
                            color = if (isPaid) colors.textMuted else colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = statusText,
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            fontWeight = if (isOverdue || isDueThisMonth) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isPaid -> colors.income
                                isOverdue -> colors.alertRed
                                isDueThisMonth -> colors.archive.accentSmallText
                                else -> colors.textMuted
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArchiveSecondaryButton(
                    text = "取消计划",
                    isDestructive = true,
                    onClick = onCancelPlanClick,
                    enabled = !hasPaid && !isSettled,
                    modifier = Modifier.weight(1f)
                )
                ArchivePrimaryButton(
                    text = "提前结清",
                    onClick = onSettleClick,
                    enabled = !isSettled && detail.remainingPrincipal > 0,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "点按未还期次标记还款，点按已还期次可撤销",
                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                color = colors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// 辅助扩展：兜底 feePart 防止空
private fun InstallmentItem.FeePartSafe(): Long = this.feePart

/**
 * 阶段 4：标记已还 LedgerSheet
 */
@Composable
fun PayInstallmentSheet(
    item: InstallmentItem,
    planTitle: String,
    paymentMethods: List<String>,
    onDismissRequest: () -> Unit,
    onConfirmPay: (paidAmount: Long, payAccount: String, paidAt: String) -> Unit
) {
    val colors = LedgerTheme.colors

    val expectedCents = item.principalPart + item.feePart
    var paidAmountYuan by remember { mutableStateOf(FormatUtils.centsToYuanString(expectedCents, includeSymbol = false)) }
    var selectedAccount by remember { mutableStateOf(item.payAccount?.ifBlank { null } ?: paymentMethods.firstOrNull() ?: "银行卡") }
    var paidDate by remember { mutableStateOf(LocalDate.now(FormatUtils.BeijingZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var showPaidDatePicker by remember { mutableStateOf(false) }

    val actualCents = FormatUtils.yuanStringToCents(paidAmountYuan)
    val isValid = actualCents >= item.principalPart

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = "标记已还 · 第 ${item.seq} 期",
        actionText = "确认",
        isActionEnabled = isValid,
        onAction = {
            if (isValid) {
                onConfirmPay(actualCents, selectedAccount, paidDate)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$planTitle · 应还本金 ${FormatUtils.formatMoney(item.principalPart)}${if (item.feePart > 0) " + 手续费 " + FormatUtils.formatMoney(item.feePart) else ""}",
                style = LedgerTheme.typography.caption,
                color = colors.textSecondary
            )

            // 实付金额
            ArchiveField(
                value = paidAmountYuan,
                onValueChange = { paidAmountYuan = it },
                label = "实付金额",
                isNumeric = true,
                trailingText = "元",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            if (!isValid) {
                Text(
                    text = "实付金额不得低于本期本金 ${FormatUtils.formatMoney(item.principalPart)}",
                    style = LedgerTheme.typography.caption,
                    color = colors.alertRed
                )
            }

            // 付款账户
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "付款账户",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentMethods.forEach { acc ->
                        ArchiveSelectableCapsule(
                            text = acc,
                            selected = selectedAccount == acc,
                            onClick = { selectedAccount = acc }
                        )
                    }
                }
            }

            // 还款日期
            ArchiveField(
                value = paidDate,
                onValueChange = {},
                label = "还款日期",
                readOnly = true,
                singleLine = true,
                onClick = {
                    showPaidDatePicker = true
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showPaidDatePicker) {
        val initialDate = runCatching { LocalDate.parse(paidDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now(FormatUtils.BeijingZone))
        ArchiveDatePickerSheet(
            selectedDate = initialDate,
            onDateSelected = { newDate ->
                paidDate = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
            },
            onDismissRequest = { showPaidDatePicker = false }
        )
    }
}

/**
 * 阶段 5：提前结清 LedgerSheet
 */
@Composable
fun SettleInstallmentSheet(
    plan: InstallmentPlan,
    remainingPrincipal: Long,
    paymentMethods: List<String>,
    onDismissRequest: () -> Unit,
    onConfirmSettle: (paidAmount: Long, payAccount: String, paidAt: String) -> Unit
) {
    val colors = LedgerTheme.colors

    var paidAmountYuan by remember { mutableStateOf(FormatUtils.centsToYuanString(remainingPrincipal, includeSymbol = false)) }
    var selectedAccount by remember { mutableStateOf(paymentMethods.firstOrNull() ?: "银行卡") }
    var paidDate by remember { mutableStateOf(LocalDate.now(FormatUtils.BeijingZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var showPaidDatePicker by remember { mutableStateOf(false) }

    val actualCents = FormatUtils.yuanStringToCents(paidAmountYuan)
    val isValid = actualCents >= remainingPrincipal
    val feeDiff = actualCents - remainingPrincipal

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = "提前结清 · ${plan.title}",
        actionText = "结清",
        isActionEnabled = isValid,
        onAction = {
            if (isValid) {
                onConfirmSettle(actualCents, selectedAccount, paidDate)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "剩余待还本金总额：${FormatUtils.formatMoney(remainingPrincipal)}",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = ArchiveTypeScale.body,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            ArchiveField(
                value = paidAmountYuan,
                onValueChange = { paidAmountYuan = it },
                label = "实付结清总额",
                isNumeric = true,
                trailingText = "元",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (!isValid) {
                Text(
                    text = "实付金额不得低于剩余本金 ${FormatUtils.formatMoney(remainingPrincipal)}",
                    style = LedgerTheme.typography.caption,
                    color = colors.alertRed
                )
            } else {
                Text(
                    text = "超出剩余本金的差额 ${FormatUtils.formatMoney(feeDiff)} 将记为手续费支出",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "付款账户",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentMethods.forEach { acc ->
                        ArchiveSelectableCapsule(
                            text = acc,
                            selected = selectedAccount == acc,
                            onClick = { selectedAccount = acc }
                        )
                    }
                }
            }

            ArchiveField(
                value = paidDate,
                onValueChange = {},
                label = "结清日期",
                readOnly = true,
                singleLine = true,
                onClick = {
                    showPaidDatePicker = true
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showPaidDatePicker) {
        val initialDate = runCatching { LocalDate.parse(paidDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now(FormatUtils.BeijingZone))
        ArchiveDatePickerSheet(
            selectedDate = initialDate,
            onDateSelected = { newDate ->
                paidDate = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
            },
            onDismissRequest = { showPaidDatePicker = false }
        )
    }
}

/**
 * 阶段 4：本月待还期次列表弹层 (首页点击入口)
 */
@Composable
fun UpcomingInstallmentsSheet(
    items: List<UpcomingInstallmentItem>,
    onDismissRequest: () -> Unit,
    onQuickPayItem: (UpcomingInstallmentItem) -> Unit
) {
    val colors = LedgerTheme.colors

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = "本月待还",
        cancelText = "关闭"
    ) {
        if (items.isEmpty()) {
            ArchiveEmptyState(
                title = "本月没有待还分期",
                subtitle = "到期前一天与当天会提醒你",
                imageVector = FolioGlyphs.Check
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.itemId }) { itm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.leaf)
                            .background(colors.surface)
                            .border(
                                ArchiveLines.hairline,
                                if (itm.isOverdue) colors.alertRed.copy(alpha = 0.4f) else colors.archive.borderCard,
                                ArchiveShapes.leaf
                            )
                            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(ArchiveShapes.full)
                                    .background(if (itm.isOverdue) colors.archive.negativeTint else colors.accentSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (itm.isOverdue) "已逾期 ${itm.overdueDays} 天" else "到期 ${itm.dueDate}",
                                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                    fontWeight = FontWeight.Medium,
                                    color = if (itm.isOverdue) colors.alertRed else colors.archive.accentSmallText
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = itm.planTitle,
                                style = com.looseleaf.app.theme.ArchiveTextStyles.rowTitle,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${itm.channelName} · 第 ${itm.seq}/${itm.totalPeriods} 期",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = FormatUtils.formatMoney(itm.totalPart),
                                style = com.looseleaf.app.theme.ArchiveTextStyles.amount,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(ArchiveShapes.full)
                                    .background(colors.inverseSurface)
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onQuickPayItem(itm) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "已还",
                                    style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inverseInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 阶段 3：编辑分期计划弹层 (RC-140)
 * 允许修改：
 * - 计划名称 (title)
 * - 备注 (note)
 * - 单期到期日 (点击未还期次弹出日期选择器)
 * 注：已有已还记录后，本金与期数不可改
 */
@Composable
fun EditInstallmentPlanSheet(
    detail: InstallmentPlanDetailResponse,
    onDismissRequest: () -> Unit,
    onSaveSuccess: (title: String, note: String, modifiedDates: Map<Long, String>) -> Unit
) {
    val colors = LedgerTheme.colors
    val plan = detail.plan
    val items = detail.items
    val hasPaid = detail.paidPeriods > 0

    var editTitle by remember(plan.id) { mutableStateOf(plan.title) }
    var editNote by remember(plan.id) { mutableStateOf(plan.note ?: "") }
    val modifiedDates = remember(plan.id) { mutableStateMapOf<Long, String>() }
    var adjustingItem by remember { mutableStateOf<InstallmentItem?>(null) }

    val canSave = editTitle.isNotBlank()

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = "编辑分期计划",
        actionText = "保存",
        isActionEnabled = canSave,
        onAction = {
            onSaveSuccess(editTitle.trim(), editNote.trim(), modifiedDates)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasPaid) {
                Text(
                    text = "该计划已有还款记录，本金与期数已锁定，仅支持修改名称、备注与未还期次到期日",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary
                )
            }

            ArchiveField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "计划名称",
                singleLine = true
            )

            ArchiveField(
                value = editNote,
                onValueChange = { editNote = it },
                label = "备注",
                placeholder = "选填",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "期次到期日（点击未还期次可调整）",
                style = LedgerTheme.typography.caption,
                fontFamily = LedgerTheme.typography.serif,
                color = colors.textSecondary
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (itm in items) {
                    val isPaid = itm.status == "paid"
                    val currentDue = modifiedDates[itm.id] ?: itm.dueDate

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.small)
                            .clickable(enabled = !isPaid) {
                                adjustingItem = itm
                            }
                            .background(colors.surfaceElevated)
                            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "第 ${itm.seq}/${plan.periods} 期",
                                style = LedgerTheme.typography.body,
                                fontFamily = LedgerTheme.typography.serif,
                                color = if (isPaid) colors.textMuted else colors.textPrimary
                            )
                            Text(
                                text = if (isPaid) "已于 ${itm.paidAt?.take(10) ?: ""} 还款" else "点击修改到期日",
                                style = LedgerTheme.typography.caption,
                                color = colors.textSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentDue,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.body,
                                color = if (isPaid) colors.textMuted else colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            if (!isPaid) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = FolioGlyphs.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    adjustingItem?.let { itm ->
        val currentDue = modifiedDates[itm.id] ?: itm.dueDate
        val initialDate = runCatching { LocalDate.parse(currentDue, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now(FormatUtils.BeijingZone))
        ArchiveDatePickerSheet(
            selectedDate = initialDate,
            onDateSelected = { newDate ->
                val newDateStr = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
                modifiedDates[itm.id] = newDateStr
            },
            onDismissRequest = { adjustingItem = null }
        )
    }
}

/**
 * 分期计划总览列表抽屉（通过账本顶栏更多菜单访问，确保分期计划不因移除资产大盘而孤立）
 */
@Composable
fun InstallmentPlansSheet(
    plans: List<InstallmentPlanItemSummary>,
    isPrivacyMasked: Boolean,
    onDismissRequest: () -> Unit,
    onNewInstallmentClick: () -> Unit,
    onPlanClick: (InstallmentPlanItemSummary) -> Unit
) {
    val colors = LedgerTheme.colors
    val activePlans = remember(plans) { plans.filter { it.status == "active" } }
    val settledPlans = remember(plans) { plans.filter { it.status != "active" } }

    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        title = "分期计划",
        cancelText = "关闭",
        actionText = "新建",
        isActionEnabled = true,
        onAction = {
            onDismissRequest()
            onNewInstallmentClick()
        }
    ) {
        if (plans.isEmpty()) {
            ArchiveEmptyState(
                title = "还没有分期计划",
                subtitle = "大件支出可以拆成若干期，到期自动提醒",
                actionText = "新建分期",
                onActionClick = {
                    onDismissRequest()
                    onNewInstallmentClick()
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activePlans.isNotEmpty()) {
                    item(key = "hdr_active_plans") {
                        PlanGroupLabel(text = "进行中", count = activePlans.size)
                    }
                    items(activePlans, key = { "plan_${it.id}" }) { plan ->
                        InstallmentPlanSummaryRow(
                            plan = plan,
                            isPrivacyMasked = isPrivacyMasked,
                            onClick = { onPlanClick(plan) }
                        )
                    }
                }
                if (settledPlans.isNotEmpty()) {
                    item(key = "hdr_settled_plans") {
                        PlanGroupLabel(text = "已结清", count = settledPlans.size)
                    }
                    items(settledPlans, key = { "plan_done_${it.id}" }) { plan ->
                        InstallmentPlanSummaryRow(
                            plan = plan,
                            isPrivacyMasked = isPrivacyMasked,
                            onClick = { onPlanClick(plan) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanGroupLabel(text: String, count: Int) {
    val colors = LedgerTheme.colors
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = com.looseleaf.app.theme.ArchiveTextStyles.overline, color = colors.textMuted)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun InstallmentPlanSummaryRow(
    plan: InstallmentPlanItemSummary,
    isPrivacyMasked: Boolean,
    onClick: () -> Unit
) {
    val colors = LedgerTheme.colors
    val isSettled = plan.status == "settled"
    val progress = if (plan.periods > 0) plan.paidPeriods.toFloat() / plan.periods else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(
                ArchiveLines.hairline,
                if (plan.isOverdue) colors.alertRed.copy(alpha = 0.4f) else colors.archive.borderCard,
                ArchiveShapes.leaf
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = com.looseleaf.app.theme.ArchiveTextStyles.rowTitle,
                    color = if (isSettled) colors.textSecondary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = plan.channelName ?: plan.channelCode,
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPrivacyMasked) "¥ ••••" else FormatUtils.formatMoney(plan.remainingPrincipal),
                    style = com.looseleaf.app.theme.ArchiveTextStyles.amount,
                    color = if (plan.remainingPrincipal > 0) colors.expense else colors.income
                )
                Text(
                    text = if (isSettled) "已结清" else if (plan.isOverdue) "已逾期" else "待还本金",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    fontWeight = if (plan.isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (plan.isOverdue) colors.alertRed else if (isSettled) colors.income else colors.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.surfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(ArchiveShapes.full)
                        .background(if (plan.isOverdue) colors.alertRed else colors.income)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${plan.paidPeriods}/${plan.periods} 期",
                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                color = colors.textSecondary
            )
        }
    }
}
