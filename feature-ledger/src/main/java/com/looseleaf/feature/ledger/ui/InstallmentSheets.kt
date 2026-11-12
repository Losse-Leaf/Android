package com.looseleaf.feature.ledger.ui

import com.looseleaf.app.ui.components.ArchiveDatePickerSheet
import com.looseleaf.app.ui.components.ArchiveSelectableCapsule
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
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.theme.LedgerFormat
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
    existingGears: List<GearItem>,
    expenseCategories: List<String>,
    onDismissRequest: () -> Unit,
    onSubmit: suspend (CreateInstallmentPlanRequest) -> Boolean,
    initialTitle: String = "",
    initialPriceYuan: String = "",
    initialGearCategory: String = "数码器材",
    isFromGear: Boolean = false,
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

    // 仅保留用户自建还款渠道
    val userChannels = remember(creditChannels) {
        creditChannels.filterNot { it.isBuiltin || it.code in BUILTIN_CHANNEL_CODES }
    }
    var selectedChannel by remember(userChannels) {
        mutableStateOf(userChannels.firstOrNull())
    }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showChannelSelectDialog by remember { mutableStateOf(false) }

    // 首期月份推算（还款日绑定在渠道中，只需选择月份）
    var firstDueYearMonth by remember(selectedChannel) {
        val now = LocalDate.now()
        val targetDay = selectedChannel?.repaymentDay ?: 10
        val ym = if (now.dayOfMonth <= targetDay) YearMonth.from(now) else YearMonth.from(now).plusMonths(1)
        mutableStateOf(ym)
    }
    var showMonthPicker by remember { mutableStateOf(false) }

    // 分期类型：asset | expense
    var selectedKind by remember { mutableStateOf(if (isFromGear) "asset" else "asset") }
    var selectedGearId by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull { it == "购物" || it == "数码器材" } ?: expenseCategories.firstOrNull() ?: "购物") }
    var note by remember { mutableStateOf("") }

    // 实时试算（无手续费模式，每期本金均摊）
    val principalCents = FormatUtils.yuanStringToCents(principalYuan)
    val approxMonthly = if (effectivePeriods > 0) principalCents / effectivePeriods else 0L
    val endYm = runCatching {
        firstDueYearMonth.plusMonths((effectivePeriods - 1).toLong().coerceAtLeast(0L)).toString()
    }.getOrDefault("----")

    val hasValidPreview = principalCents > 0 && effectivePeriods > 0
    val livePreviewText = "每期约 ${LedgerFormat.formatMoney(approxMonthly)} · 共 $effectivePeriods 期 · 末期 $endYm"

    val canSubmit = title.isNotBlank() && principalCents > 0 && effectivePeriods in 1..120

    LedgerSheet(
        onDismissRequest = { if (!isSubmitting) onDismissRequest() },
        title = "新建分期",
        actionText = if (isSubmitting) "创建中…" else "创建",
        isActionEnabled = canSubmit && !isSubmitting,
        onAction = {
            if (canSubmit && !isSubmitting) {
                val targetDay = (selectedChannel?.repaymentDay ?: 10).coerceAtMost(firstDueYearMonth.lengthOfMonth())
                val firstDueDateStr = String.format(Locale.US, "%04d-%02d-%02d", firstDueYearMonth.year, firstDueYearMonth.monthValue, targetDay)
                val req = CreateInstallmentPlanRequest(
                    channelId = selectedChannel?.id,
                    channelCode = selectedChannel?.code,
                    title = title.trim(),
                    kind = selectedKind,
                    principal = principalCents,
                    periods = effectivePeriods,
                    feeMode = "none",
                    feeValue = 0L,
                    firstDueDate = firstDueDateStr,
                    repayDay = selectedChannel?.repaymentDay ?: 10,
                    assetId = if (selectedKind == "asset" && selectedGearId.isNotBlank()) selectedGearId else null,
                    createNewGear = selectedKind == "asset" && selectedGearId.isBlank(),
                    gearName = if (selectedKind == "asset" && selectedGearId.isBlank()) title.trim() else null,
                    gearCategory = if (selectedKind == "asset" && selectedGearId.isBlank()) initialGearCategory else null,
                    categoryName = if (selectedKind == "expense") selectedCategory else null,
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
            // 1. 计划名称
            LedgerField(
                value = title,
                onValueChange = { title = it },
                label = "计划名称",
                placeholder = "如 iPhone 16 Pro 分期",
                singleLine = true
            )

            // 2. 分期总额 与 分期期数 并排双列（调用手机原生数字软键盘）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                LedgerField(
                    value = principalYuan,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            val parts = sanitized.split('.')
                            if (parts.size == 1 || parts[1].length <= 2) {
                                principalYuan = sanitized
                            }
                        }
                    },
                    label = "分期总额",
                    placeholder = "0.00",
                    trailingText = "元",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )

                LedgerField(
                    value = periodsText,
                    onValueChange = { input ->
                        periodsText = input.filter { it.isDigit() }.take(3)
                    },
                    label = "分期期数",
                    placeholder = "12",
                    trailingText = "期",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }

            // 3. 常见期数快捷胶囊（统一全圆胶囊）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "常用",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary,
                    maxLines = 1
                )
                listOf("3", "6", "12", "24", "36").forEach { p ->
                    ArchiveSelectableCapsule(
                        text = "${p}期",
                        selected = periodsText == p,
                        onClick = { periodsText = p }
                    )
                }
            }

            // 4. 还款渠道 与 首期月份 并排双列（紧凑合并，不换行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 还款渠道列（只展示用户自建，带 + 新建）
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "还款渠道",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "+ 新建",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .clickable { showCreateChannelDialog = true }
                                .padding(start = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.small)
                            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small)
                            .clickable {
                                if (userChannels.isEmpty()) {
                                    showCreateChannelDialog = true
                                } else {
                                    showChannelSelectDialog = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedChannel?.name ?: if (userChannels.isEmpty()) "点击新建渠道" else "选择渠道",
                                fontFamily = LedgerTheme.typography.serif,
                                fontSize = ArchiveTypeScale.body,
                                color = if (selectedChannel != null) colors.textPrimary else colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = selectedChannel?.let { "每月${it.repaymentDay}日" } ?: "▾",
                                fontFamily = LedgerTheme.typography.serif,
                                fontSize = ArchiveTypeScale.caption,
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // 首期还款月份列（仅选年月）
                Column(modifier = Modifier.weight(0.9f)) {
                    Text(
                        text = "首期还款月份",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.small)
                            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small)
                            .clickable { showMonthPicker = true }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format(Locale.US, "%04d-%02d", firstDueYearMonth.year, firstDueYearMonth.monthValue),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.body,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "▾",
                                color = colors.textSecondary,
                                fontSize = ArchiveTypeScale.caption,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 5. 分期类型与可选关联（全部统一使用全圆可选胶囊）
            if (!isFromGear) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "分期类型",
                            style = LedgerTheme.typography.caption,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        ArchiveSelectableCapsule(
                            text = "购入装备",
                            selected = selectedKind == "asset",
                            onClick = { selectedKind = "asset" }
                        )
                        ArchiveSelectableCapsule(
                            text = "一笔消费",
                            selected = selectedKind == "expense",
                            onClick = { selectedKind = "expense" }
                        )
                    }

                    if (selectedKind == "asset" && existingGears.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "关联装备",
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
                                ArchiveSelectableCapsule(
                                    text = "新装备",
                                    selected = selectedGearId.isBlank(),
                                    onClick = { selectedGearId = "" }
                                )
                                existingGears.forEach { g ->
                                    val isSelected = selectedGearId == g.id
                                    ArchiveSelectableCapsule(
                                        text = g.name,
                                        selected = isSelected,
                                        onClick = { selectedGearId = if (isSelected) "" else g.id }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedKind == "expense") {
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
                    }
                }
            }

            // 6. 备注（选填）
            LedgerField(
                value = note,
                onValueChange = { note = it },
                label = "备注",
                placeholder = "选填",
                singleLine = true
            )

            // 7. 实时预览卡片（常驻单行占位，不随金额输入跳动布局）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.small)
                    .background(colors.surfaceElevated)
                    .border(
                        ArchiveLines.hairline,
                        if (hasValidPreview) colors.accent.copy(alpha = 0.45f) else colors.borderSubtle,
                        ArchiveShapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (hasValidPreview) livePreviewText else "输入金额与期数后将实时试算各期还款额",
                    fontFamily = if (hasValidPreview) ArchiveFontFamilies.MonoScale else LedgerTheme.typography.serif,
                    fontSize = ArchiveTypeScale.caption,
                    color = if (hasValidPreview) colors.accent else colors.textSecondary,
                    fontWeight = if (hasValidPreview) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    // 月份选择底抽
    if (showMonthPicker) {
        LedgerMonthPickerSheet(
            selectedYearMonth = firstDueYearMonth,
            onYearMonthSelected = { ym -> firstDueYearMonth = ym },
            onDismissRequest = { showMonthPicker = false }
        )
    }

    // 快捷新建渠道对话框（复用统一 ArchiveDialog 表单）
    if (showCreateChannelDialog) {
        CreateCreditChannelDialog(
            onDismiss = { showCreateChannelDialog = false },
            confirmText = "创建并选择",
            onSubmit = { name, day ->
                val created = onCreateChannel?.invoke(name, day)
                if (created != null) {
                    selectedChannel = created
                    true
                } else {
                    false
                }
            }
        )
    }

    // 渠道选择对话框
    if (showChannelSelectDialog) {
        SelectCreditChannelDialog(
            channels = userChannels,
            selectedChannelId = selectedChannel?.id,
            onSelect = { ch -> selectedChannel = ch },
            onCreateNewClick = { showCreateChannelDialog = true },
            onDismiss = { showChannelSelectDialog = false }
        )
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

    LedgerSheet(
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
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // 标题与渠道
            Text(
                text = "${plan.channelName ?: plan.channelCode} · ${if (plan.kind == "asset") "购入装备" else "一笔消费"}",
                style = LedgerTheme.typography.caption,
                fontFamily = LedgerTheme.typography.serif,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 大号等宽剩余本金
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "剩余待还本金",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = LedgerFormat.formatMoney(detail.remainingPrincipal),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.h1,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.remainingPrincipal > 0) colors.expense else colors.income,
                        maxLines = 1
                    )
                }

                if (plan.status == "settled") {
                    Text(
                        text = "已结清",
                        fontFamily = LedgerTheme.typography.serif,
                        fontSize = ArchiveTypeScale.body,
                        color = colors.income,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // 次要行：剩余待还期次
            val subText = if (detail.paidFee > 0) {
                "剩余应还总额 ${LedgerFormat.formatMoney(detail.remainingTotal)} · 已付手续费 ${LedgerFormat.formatMoney(detail.paidFee)}"
            } else {
                "剩余待还 ${detail.plan.periods - detail.paidPeriods} 期 · 已还 ${detail.paidPeriods}/${detail.plan.periods} 期"
            }
            Text(
                text = subText,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = ArchiveTypeScale.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            LedgerDivider()

            // 还款计划期次列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (itm in items) {
                    val isPaid = itm.status == "paid"
                    val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val isOverdue = !isPaid && itm.dueDate < todayStr
                    val isDueThisMonth = !isPaid && !isOverdue && itm.dueDate.startsWith(todayStr.take(7))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.small)
                            .clickable {
                                if (isPaid) {
                                    onUnpayItemClick(itm)
                                } else {
                                    onPayItemClick(itm)
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左列：第 N 期 · 到期日
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isDueThisMonth) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "第 ${itm.seq}/${plan.periods} 期 · ${itm.dueDate}",
                                    style = LedgerTheme.typography.body,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    color = colors.textPrimary,
                                    fontSize = ArchiveTypeScale.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (itm.feePart > 0) {
                                Text(
                                    text = "本金 ${LedgerFormat.formatMoney(itm.principalPart)} + 手续费 ${LedgerFormat.formatMoney(itm.feePart)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = ArchiveTypeScale.micro,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 右列：金额与状态文字
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = LedgerFormat.formatMoney(itm.principalPart + itm.FeePartSafe()),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.body,
                                fontWeight = FontWeight.Medium,
                                color = if (isPaid) colors.textSecondary else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statusText = when {
                                isPaid -> "已还"
                                isOverdue -> "逾期"
                                isDueThisMonth -> "本月待还"
                                else -> "未到期"
                            }
                            val statusColor = when {
                                isPaid -> colors.textMuted
                                isOverdue -> colors.alertRed
                                isDueThisMonth -> colors.accent
                                else -> colors.textSecondary
                            }
                            Text(
                                text = statusText,
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = statusColor,
                                fontWeight = if (isOverdue || isDueThisMonth) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            LedgerDivider()

            // 底部操作按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 取消计划 (砖红，仅无已还记录时可用)
                LedgerTextButton(
                    text = "取消计划",
                    isDestructive = true,
                    onClick = onCancelPlanClick,
                    enabled = !hasPaid && plan.status != "settled"
                )

                LedgerTextButton(
                    text = "提前结清",
                    onClick = onSettleClick,
                    enabled = plan.status != "settled" && detail.remainingPrincipal > 0
                )
            }
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
    var paidDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var showPaidDatePicker by remember { mutableStateOf(false) }

    val actualCents = FormatUtils.yuanStringToCents(paidAmountYuan)
    val isValid = actualCents >= item.principalPart

    LedgerSheet(
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
                text = "$planTitle · 应还本金 ${LedgerFormat.formatMoney(item.principalPart)}${if (item.feePart > 0) " + 手续费 " + LedgerFormat.formatMoney(item.feePart) else ""}",
                style = LedgerTheme.typography.caption,
                color = colors.textSecondary
            )

            // 实付金额
            LedgerField(
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
                    text = "实付金额不得低于本期本金 ${LedgerFormat.formatMoney(item.principalPart)}",
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
            LedgerField(
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
        val initialDate = runCatching { LocalDate.parse(paidDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
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
    var paidDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var showPaidDatePicker by remember { mutableStateOf(false) }

    val actualCents = FormatUtils.yuanStringToCents(paidAmountYuan)
    val isValid = actualCents >= remainingPrincipal
    val feeDiff = actualCents - remainingPrincipal

    LedgerSheet(
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
                text = "剩余待还本金总额：${LedgerFormat.formatMoney(remainingPrincipal)}",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = ArchiveTypeScale.body,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            LedgerField(
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
                    text = "实付金额不得低于剩余本金 ${LedgerFormat.formatMoney(remainingPrincipal)}",
                    style = LedgerTheme.typography.caption,
                    color = colors.alertRed
                )
            } else {
                Text(
                    text = "超出剩余本金的差额 ${LedgerFormat.formatMoney(feeDiff)} 将记为手续费支出",
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

            LedgerField(
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
        val initialDate = runCatching { LocalDate.parse(paidDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
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

    LedgerSheet(
        onDismissRequest = onDismissRequest,
        title = "本月待还与逾期分期",
        cancelText = "关闭"
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "本月暂无待还分期",
                    style = LedgerTheme.typography.body,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.itemId }) { itm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.medium)
                            .background(colors.surfaceElevated)
                            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.medium)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (itm.isOverdue) {
                                    Text(
                                        text = "已逾期 ${itm.overdueDays} 天",
                                        fontFamily = LedgerTheme.typography.serif,
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.alertRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "到期日 ${itm.dueDate}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Text(
                                text = "${itm.planTitle} · 第 ${itm.seq}/${itm.totalPeriods} 期",
                                style = LedgerTheme.typography.body,
                                fontFamily = LedgerTheme.typography.serif,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "${itm.channelName} · 应还 ${LedgerFormat.formatMoney(itm.totalPart)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 一键已还
                        LedgerTextButton(
                            text = "已还",
                            onClick = { onQuickPayItem(itm) }
                        )
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

    LedgerSheet(
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

            LedgerField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "计划名称",
                singleLine = true
            )

            LedgerField(
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
                                Text(
                                    text = "›",
                                    color = colors.textSecondary,
                                    fontSize = ArchiveTypeScale.body
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
        val initialDate = runCatching { LocalDate.parse(currentDue, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
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
