package com.looseleaf.feature.ledger.ui

import com.looseleaf.app.ui.components.ArchiveDatePickerSheet
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.theme.LedgerFormat
import com.looseleaf.feature.ledger.theme.LedgerTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// 赤陶小点色（本月待还与临期提示）
private val TerracottaDot = Color(0xFFC46243)
// 砖红文字（逾期警示）
private val BrickRedOverdue = Color(0xFFB33A2B)

/**
 * 阶段 2：新建分期 LedgerSheet
 */
@Composable
fun CreateInstallmentSheet(
    creditChannels: List<CreditChannel>,
    existingGears: List<GearItem>,
    expenseCategories: List<String>,
    onDismissRequest: () -> Unit,
    onSubmit: (CreateInstallmentPlanRequest) -> Unit,
    initialTitle: String = "",
    initialPriceYuan: String = "",
    initialGearCategory: String = "数码器材",
    isFromGear: Boolean = false
) {
    val colors = LedgerTheme.colors
    val context = LocalContext.current

    var title by remember { mutableStateOf(initialTitle) }
    var principalYuan by remember { mutableStateOf(initialPriceYuan) }
    var activeInputTarget by remember { mutableStateOf("principal") } // "principal" | "fee"

    // 期数选项：3, 6, 12, 24, 自定义
    var selectedPeriodPreset by remember { mutableStateOf("12") }
    var customPeriodText by remember { mutableStateOf("") }
    val effectivePeriods = when (selectedPeriodPreset) {
        "3" -> 3
        "6" -> 6
        "12" -> 12
        "24" -> 24
        else -> customPeriodText.toIntOrNull() ?: 12
    }

    // 手续费模式：none, fixed_per_period, rate_per_period, total_fee
    var selectedFeeMode by remember { mutableStateOf("none") }
    var feeValueInput by remember { mutableStateOf("") }

    // 渠道选择
    val defaultChannel = creditChannels.firstOrNull { it.code == "huabei" } ?: creditChannels.firstOrNull()
    var selectedChannel by remember { mutableStateOf(defaultChannel) }
    var repayDay by remember { mutableStateOf(defaultChannel?.repaymentDay ?: 10) }

    // 首期还款日推算
    var firstDueDate by remember {
        val now = LocalDate.now()
        val targetDay = defaultChannel?.repaymentDay ?: 10
        val dueDate = if (now.dayOfMonth <= targetDay) {
            val length = YearMonth.from(now).lengthOfMonth()
            now.withDayOfMonth(targetDay.coerceAtMost(length))
        } else {
            val next = now.plusMonths(1)
            val length = YearMonth.from(next).lengthOfMonth()
            next.withDayOfMonth(targetDay.coerceAtMost(length))
        }
        mutableStateOf(dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    }
    var showDueDatePicker by remember { mutableStateOf(false) }

    // 分期类型：asset | expense
    var selectedKind by remember { mutableStateOf(if (isFromGear) "asset" else "asset") }
    var selectedGearId by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull { it == "购物" || it == "数码器材" } ?: expenseCategories.firstOrNull() ?: "购物") }
    var note by remember { mutableStateOf("") }

    // 实时试算
    val principalCents = FormatUtils.yuanStringToCents(principalYuan)
    val feeValueCents = if (selectedFeeMode == "rate_per_period") {
        // 百分比，例如 0.5% -> bps 50
        val ratePercent = feeValueInput.toDoubleOrNull() ?: 0.0
        (ratePercent * 100).toLong()
    } else {
        FormatUtils.yuanStringToCents(feeValueInput)
    }

    val livePreviewText = remember(principalCents, effectivePeriods, selectedFeeMode, feeValueCents, firstDueDate) {
        if (principalCents <= 0 || effectivePeriods <= 0) {
            "输入本金与期数后将实时预览还款明细"
        } else {
            val baseP = principalCents / effectivePeriods
            val totalFeeCents = when (selectedFeeMode) {
                "none" -> 0L
                "fixed_per_period" -> feeValueCents * effectivePeriods
                "rate_per_period" -> ((principalCents * feeValueCents + 5000) / 10000) * effectivePeriods
                "total_fee" -> feeValueCents
                else -> 0L
            }
            val avgFeePerPeriod = if (effectivePeriods > 0) totalFeeCents / effectivePeriods else 0L
            val approxMonthly = baseP + avgFeePerPeriod

            val endYm = runCatching {
                val dt = LocalDate.parse(firstDueDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                dt.plusMonths((effectivePeriods - 1).toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM"))
            }.getOrDefault("----")

            "每期约 ${LedgerFormat.formatMoney(approxMonthly)}（含手续费 ${LedgerFormat.formatMoney(avgFeePerPeriod)}），共 $effectivePeriods 期，总手续费 ${LedgerFormat.formatMoney(totalFeeCents)}，末期 $endYm"
        }
    }

    val canSubmit = title.isNotBlank() && principalCents > 0 && effectivePeriods in 1..120

    LedgerSheet(
        onDismissRequest = onDismissRequest,
        title = "新建分期",
        actionText = "创建",
        isActionEnabled = canSubmit,
        onAction = {
            if (canSubmit) {
                onSubmit(
                    CreateInstallmentPlanRequest(
                        channelId = selectedChannel?.id,
                        channelCode = selectedChannel?.code,
                        title = title.trim(),
                        kind = selectedKind,
                        principal = principalCents,
                        periods = effectivePeriods,
                        feeMode = selectedFeeMode,
                        feeValue = feeValueCents,
                        firstDueDate = firstDueDate,
                        repayDay = repayDay,
                        assetId = if (selectedKind == "asset" && selectedGearId.isNotBlank()) selectedGearId else null,
                        createNewGear = selectedKind == "asset" && selectedGearId.isBlank(),
                        gearName = if (selectedKind == "asset" && selectedGearId.isBlank()) title.trim() else null,
                        gearCategory = if (selectedKind == "asset" && selectedGearId.isBlank()) initialGearCategory else null,
                        categoryName = if (selectedKind == "expense") selectedCategory else null,
                        note = note.trim().ifEmpty { null }
                    )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 分期名称
            LedgerField(
                value = title,
                onValueChange = { title = it },
                label = "计划名称",
                placeholder = "如 iPhone 15 Pro 分期",
                singleLine = true
            )

            // 2. 本金与买入价格
            LedgerField(
                value = principalYuan,
                onValueChange = {},
                label = if (activeInputTarget == "principal") "分期本金（键盘输入中）" else "分期本金（点击切换键盘）",
                placeholder = "0.00",
                isNumeric = true,
                readOnly = true,
                trailingText = "元",
                singleLine = true,
                onClick = { activeInputTarget = "principal" }
            )

            // 3. 期数选项
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "分期期数",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("3", "6", "12", "24", "自定义").forEach { p ->
                        val isSelected = selectedPeriodPreset == p
                        LedgerChoiceItem(
                            text = if (p == "自定义") p else "${p}期",
                            isSelected = isSelected,
                            onClick = { selectedPeriodPreset = p },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (selectedPeriodPreset == "自定义") {
                    LedgerField(
                        value = customPeriodText,
                        onValueChange = { customPeriodText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = "自定义期数",
                        placeholder = "输入 1~120 期",
                        isNumeric = true,
                        trailingText = "期",
                        singleLine = true
                    )
                }
            }

            // 4. 手续费模式
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "手续费模式",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        "none" to "无费用",
                        "fixed_per_period" to "每期固定",
                        "rate_per_period" to "每期费率",
                        "total_fee" to "总手续费"
                    )
                    modes.forEach { (mode, label) ->
                        val isSelected = selectedFeeMode == mode
                        LedgerChoiceItem(
                            text = label,
                            isSelected = isSelected,
                            onClick = {
                                selectedFeeMode = mode
                                if (mode == "none") feeValueInput = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedFeeMode != "none") {
                    val (feeLabel, feeUnit, feePlaceholder) = when (selectedFeeMode) {
                        "fixed_per_period" -> Triple("每期固定手续费", "元", "如 6.00")
                        "rate_per_period" -> Triple("每期费率（按总本金）", "%", "如 0.5")
                        "total_fee" -> Triple("总手续费（按期均摊）", "元", "如 72.00")
                        else -> Triple("手续费数值", "", "")
                    }
                    LedgerField(
                        value = feeValueInput,
                        onValueChange = {},
                        label = if (activeInputTarget == "fee") "$feeLabel（键盘输入中）" else "$feeLabel（点击切换键盘）",
                        placeholder = feePlaceholder,
                        isNumeric = true,
                        readOnly = true,
                        trailingText = feeUnit,
                        singleLine = true,
                        onClick = { activeInputTarget = "fee" }
                    )
                }
            }

            // 5. 信贷渠道与首期到期日
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "还款渠道",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    creditChannels.forEach { ch ->
                        val isSelected = selectedChannel?.id == ch.id
                        LedgerChoiceItem(
                            text = ch.name,
                            isSelected = isSelected,
                            onClick = {
                                selectedChannel = ch
                                repayDay = ch.repaymentDay
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 首期还款日选择
            LedgerField(
                value = firstDueDate,
                onValueChange = {},
                label = "首期到期日",
                placeholder = "YYYY-MM-DD",
                readOnly = true,
                singleLine = true,
                trailingText = "每月${repayDay}日",
                onClick = {
                    showDueDatePicker = true
                }
            )

            // 6. 分期类型 (购入装备 / 一笔消费)
            if (!isFromGear) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "分期类型",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LedgerChoiceItem(
                            text = "购入装备",
                            isSelected = selectedKind == "asset",
                            onClick = { selectedKind = "asset" },
                            modifier = Modifier.weight(1f)
                        )
                        LedgerChoiceItem(
                            text = "一笔消费",
                            isSelected = selectedKind == "expense",
                            onClick = { selectedKind = "expense" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedKind == "asset" && existingGears.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "可选关联已有设备：",
                            style = LedgerTheme.typography.caption,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingGears.take(4).forEach { g ->
                                val isSelected = selectedGearId == g.id
                                LedgerChoiceItem(
                                    text = g.name,
                                    isSelected = isSelected,
                                    onClick = { selectedGearId = if (isSelected) "" else g.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (selectedKind == "expense") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "消费分类：",
                            style = LedgerTheme.typography.caption,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            expenseCategories.take(4).forEach { cat ->
                                val isSelected = selectedCategory == cat
                                LedgerChoiceItem(
                                    text = cat,
                                    isSelected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 底部实时预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceElevated)
                    .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = livePreviewText,
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )
            }

            // 键盘目标切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LedgerChoiceItem(
                    text = "本金: ¥${if (principalYuan.isBlank()) "0.00" else principalYuan}",
                    isSelected = activeInputTarget == "principal",
                    onClick = { activeInputTarget = "principal" },
                    modifier = Modifier.weight(1f)
                )
                if (selectedFeeMode != "none") {
                    val fDisp = if (selectedFeeMode == "rate_per_period") "${if (feeValueInput.isBlank()) "0" else feeValueInput}%" else "¥${if (feeValueInput.isBlank()) "0.00" else feeValueInput}"
                    LedgerChoiceItem(
                        text = "手续费: $fDisp",
                        isSelected = activeInputTarget == "fee",
                        onClick = { activeInputTarget = "fee" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 共享数字键盘
            TactileKeypad(
                onDigitClick = { digit ->
                    if (activeInputTarget == "principal") {
                        val parts = principalYuan.split(".")
                        if (parts.size == 2 && parts[1].length >= 2) {
                            // 两位小数截断
                        } else if (principalYuan.length < 9) {
                            principalYuan += digit
                        }
                    } else {
                        val parts = feeValueInput.split(".")
                        if (parts.size == 2 && parts[1].length >= 2) {
                            // 两位小数
                        } else if (feeValueInput.length < 8) {
                            feeValueInput += digit
                        }
                    }
                },
                onDotClick = {
                    if (activeInputTarget == "principal") {
                        if (!principalYuan.contains(".")) {
                            principalYuan = if (principalYuan.isEmpty()) "0." else "$principalYuan."
                        }
                    } else {
                        if (!feeValueInput.contains(".")) {
                            feeValueInput = if (feeValueInput.isEmpty()) "0." else "$feeValueInput."
                        }
                    }
                },
                onBackspaceClick = {
                    if (activeInputTarget == "principal") {
                        if (principalYuan.isNotEmpty()) principalYuan = principalYuan.dropLast(1)
                    } else {
                        if (feeValueInput.isNotEmpty()) feeValueInput = feeValueInput.dropLast(1)
                    }
                },
                onClearAll = {
                    if (activeInputTarget == "principal") {
                        principalYuan = ""
                    } else {
                        feeValueInput = ""
                    }
                },
                showPlus = false,
                onConfirm = {
                    // 完成输入
                }
            )
        }
    }

    if (showDueDatePicker) {
        val initialDate = runCatching { LocalDate.parse(firstDueDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
        ArchiveDatePickerSheet(
            selectedDate = initialDate,
            onDateSelected = { newDate ->
                firstDueDate = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
                repayDay = newDate.dayOfMonth
            },
            onDismissRequest = { showDueDatePicker = false }
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
        actionText = "关闭",
        isActionEnabled = true,
        onAction = onDismissRequest
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
                color = colors.textSecondary
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
                        color = colors.textSecondary
                    )
                    Text(
                        text = LedgerFormat.formatMoney(detail.remainingPrincipal),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.remainingPrincipal > 0) colors.expense else colors.income
                    )
                }

                if (plan.status == "settled") {
                    Text(
                        text = "已结清",
                        fontFamily = LedgerTheme.typography.serif,
                        fontSize = 13.sp,
                        color = colors.income,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 次要行：剩余应还总额 · 已付手续费
            Text(
                text = "剩余应还总额 ${LedgerFormat.formatMoney(detail.remainingTotal)} · 已付手续费 ${LedgerFormat.formatMoney(detail.paidFee)}",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 11.5.sp,
                color = colors.textSecondary,
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
                            .clip(RoundedCornerShape(4.dp))
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
                                            .background(TerracottaDot)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "第 ${itm.seq}/${plan.periods} 期 · ${itm.dueDate}",
                                    style = LedgerTheme.typography.body,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                            if (itm.feePart > 0) {
                                Text(
                                    text = "本金 ${LedgerFormat.formatMoney(itm.principalPart)} + 手续费 ${LedgerFormat.formatMoney(itm.feePart)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // 右列：金额与状态文字
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = LedgerFormat.formatMoney(itm.principalPart + itm.FeePartSafe()),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isPaid) colors.textSecondary else colors.textPrimary
                            )
                            val statusText = when {
                                isPaid -> "已还"
                                isOverdue -> "逾期"
                                isDueThisMonth -> "本月待还"
                                else -> "未到期"
                            }
                            val statusColor = when {
                                isPaid -> colors.textMuted
                                isOverdue -> BrickRedOverdue
                                isDueThisMonth -> TerracottaDot
                                else -> colors.textSecondary
                            }
                            Text(
                                text = statusText,
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = statusColor,
                                fontWeight = if (isOverdue || isDueThisMonth) FontWeight.Medium else FontWeight.Normal
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
                    onClick = onCancelPlanClick,
                    enabled = !hasPaid && plan.status != "settled"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerTextButton(
                        text = "编辑",
                        onClick = onEditClick
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
    val context = LocalContext.current

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
        actionText = "确认已还",
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
                singleLine = true
            )
            if (!isValid) {
                Text(
                    text = "实付金额不得低于本期本金 ${LedgerFormat.formatMoney(item.principalPart)}",
                    style = LedgerTheme.typography.caption,
                    color = BrickRedOverdue
                )
            }

            // 付款账户
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "付款账户",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentMethods.take(4).forEach { acc ->
                        val isSelected = selectedAccount == acc
                        LedgerChoiceItem(
                            text = acc,
                            isSelected = isSelected,
                            onClick = { selectedAccount = acc },
                            modifier = Modifier.weight(1f)
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
    val context = LocalContext.current

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
        actionText = "确认结清",
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            LedgerField(
                value = paidAmountYuan,
                onValueChange = { paidAmountYuan = it },
                label = "实付结清总额",
                isNumeric = true,
                trailingText = "元",
                singleLine = true
            )

            if (!isValid) {
                Text(
                    text = "实付金额不得低于剩余本金 ${LedgerFormat.formatMoney(remainingPrincipal)}",
                    style = LedgerTheme.typography.caption,
                    color = BrickRedOverdue
                )
            } else {
                Text(
                    text = "超出剩余本金的差额 ${LedgerFormat.formatMoney(feeDiff)} 将记为手续费支出",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "付款账户",
                    style = LedgerTheme.typography.caption,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentMethods.take(4).forEach { acc ->
                        val isSelected = selectedAccount == acc
                        LedgerChoiceItem(
                            text = acc,
                            isSelected = isSelected,
                            onClick = { selectedAccount = acc },
                            modifier = Modifier.weight(1f)
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
        actionText = "完成",
        isActionEnabled = true,
        onAction = onDismissRequest
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated)
                            .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
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
                                        fontSize = 11.5.sp,
                                        color = BrickRedOverdue,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TerracottaDot)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "到期日 ${itm.dueDate}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 11.5.sp,
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
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "${itm.channelName} · 应还 ${LedgerFormat.formatMoney(itm.totalPart)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.textSecondary
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
    val context = LocalContext.current
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
        actionText = "保存修改",
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
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isPaid) {
                                adjustingItem = itm
                            }
                            .background(colors.surfaceElevated)
                            .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
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
                                fontSize = 13.sp,
                                color = if (isPaid) colors.textMuted else colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            if (!isPaid) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "›",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
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
