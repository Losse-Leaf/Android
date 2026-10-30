package com.looseleaf.feature.ledger.ui

import com.looseleaf.app.ui.components.ArchiveDatePickerSheet
import java.util.Locale
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerFormat
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 极速记账底部弹层 (QuickRecordSheet)
 *
 * 遵循阶段 3 规范：
 * 1. 弹层纸色，顶部圆角 4dp，无投影；
 * 2. 顶部：支出 / 收入 文字切换（选中加粗 + 2dp 赤陶下划线）；
 * 3. 金额：大号等宽数字，自定义数字键盘（0-9、小数点、退格、"+"用于累加），键盘常驻；
 * 4. 分类：常用 6 个（色点 + 文字），末尾"更多"展开全部；
 * 5. 默认值：时间 = 现在；账户 = 上次使用；分类 = 常用分类预选；
 * 6. "备注 / 商户 / 时间 / 账户"折叠在"更多"里，默认不展开；
 * 7. 底部主按钮"记下"，赤陶实心；金额为 0 禁用；不丢焦点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickRecordSheet(
    onDismiss: () -> Unit,
    apiClient: APIClient,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    paymentMethods: List<String>,
    creditChannels: List<CreditChannel>,
    onTransactionCreated: () -> Unit,
    ledgerRepository: LedgerRepository = LedgerRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LedgerTheme.colors

    // 记住上次使用的支付方式
    val sp = remember { context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE) }
    val lastPaymentMethod = remember {
        sp.getString("last_payment_method", null) ?: paymentMethods.firstOrNull() ?: "微信支付"
    }

    var selectedType by rememberSaveable { mutableStateOf("expense") }
    val activeCategories = if (selectedType == "income") incomeCategories else expenseCategories
    var selectedCategory by rememberSaveable(selectedType) {
        mutableStateOf(activeCategories.firstOrNull() ?: if (selectedType == "income") "薪资" else "餐饮")
    }

    var selectedPaymentMethod by rememberSaveable {
        mutableStateOf(lastPaymentMethod)
    }

    var payMethodChoice by rememberSaveable { mutableStateOf("full") } // "full" | "installment"

    // 分期参数
    var selectedPeriodPreset by rememberSaveable { mutableStateOf("12") }
    var customPeriodText by rememberSaveable { mutableStateOf("") }
    val effectivePeriods = when (selectedPeriodPreset) {
        "3" -> 3
        "6" -> 6
        "12" -> 12
        "24" -> 24
        else -> customPeriodText.toIntOrNull() ?: 12
    }

    var selectedFeeMode by rememberSaveable { mutableStateOf("none") }
    var feeValueInput by rememberSaveable { mutableStateOf("") }

    val defaultChannel = creditChannels.firstOrNull { it.code == "huabei" } ?: creditChannels.firstOrNull()
    var selectedChannel by remember(creditChannels) { mutableStateOf(defaultChannel) }
    var repayDay by remember(selectedChannel) { mutableStateOf(selectedChannel?.repaymentDay ?: 10) }

    var firstDueDate by rememberSaveable {
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
    var showFirstDueDatePicker by remember { mutableStateOf(false) }

    var linkGearAsset by rememberSaveable { mutableStateOf(false) }

    var inputAmountString by rememberSaveable { mutableStateOf("0") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }
    var showMoreDetails by rememberSaveable { mutableStateOf(false) }

    var txOccurredAtMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val txOccurredAt = remember(txOccurredAtMillis) {
        LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(txOccurredAtMillis),
            FormatUtils.BeijingZone
        )
    }

    // 简单累加计算
    fun calculateEffectiveCents(): Long {
        return if (inputAmountString.contains("+")) {
            val sumYuan = inputAmountString.split("+")
                .mapNotNull { it.trim().toDoubleOrNull() }
                .sum()
            (sumYuan * 100).toLong()
        } else {
            FormatUtils.yuanStringToCents(inputAmountString)
        }
    }

    val effectiveCents = remember(inputAmountString) { calculateEffectiveCents() }

    val isInstallment = (selectedType == "expense" && payMethodChoice == "installment")
    val feeValCents = if (selectedFeeMode == "rate_per_period") {
        val ratePercent = feeValueInput.toDoubleOrNull() ?: 0.0
        (ratePercent * 100).toLong()
    } else {
        FormatUtils.yuanStringToCents(feeValueInput)
    }
    val totalFCents = when (selectedFeeMode) {
        "none" -> 0L
        "fixed_per_period" -> feeValCents * effectivePeriods
        "rate_per_period" -> ((effectiveCents * feeValCents + 5000) / 10000) * effectivePeriods
        "total_fee" -> feeValCents
        else -> 0L
    }
    val avgFCents = if (effectivePeriods > 0) totalFCents / effectivePeriods else 0L
    val approxMonthly = if (effectivePeriods > 0) effectiveCents / effectivePeriods + avgFCents else 0L

    fun appendDigit(digit: String) {
        if (inputAmountString == "0") {
            inputAmountString = digit
        } else {
            val lastSegment = inputAmountString.substringAfterLast("+")
            if (lastSegment.contains(".")) {
                val decimals = lastSegment.substringAfter(".")
                if (decimals.length < 2) {
                    inputAmountString += digit
                }
            } else if (lastSegment.length < 8) {
                inputAmountString += digit
            }
        }
    }

    fun appendDot() {
        val lastSegment = inputAmountString.substringAfterLast("+")
        if (!lastSegment.contains(".")) {
            inputAmountString += "."
        }
    }

    fun appendPlus() {
        if (!inputAmountString.endsWith("+") && inputAmountString != "0") {
            inputAmountString += "+"
        }
    }

    fun backspace() {
        if (inputAmountString.length > 1) {
            inputAmountString = inputAmountString.dropLast(1)
            if (inputAmountString.isEmpty()) {
                inputAmountString = "0"
            }
        } else {
            inputAmountString = "0"
        }
    }

    fun clearAll() {
        inputAmountString = "0"
        noteText = ""
        txOccurredAtMillis = System.currentTimeMillis()
    }

    val handleSubmit: () -> Unit = {
        val cents = calculateEffectiveCents()
        if (cents > 0) {
            // 持久化上次使用的支付方式
            sp.edit().putString("last_payment_method", selectedPaymentMethod).apply()

            val trimmedNote = noteText.trim()
            val title = if (trimmedNote.isNotBlank()) {
                trimmedNote.lineSequence().firstOrNull()?.take(20)?.ifBlank { selectedCategory } ?: selectedCategory
            } else selectedCategory
            val note = trimmedNote.takeIf { it.isNotBlank() && it != title }

            scope.launch {
                val req = CreateTransactionRequest(
                    amount = cents,
                    type = selectedType,
                    category = selectedCategory,
                    title = title,
                    note = note,
                    paymentMethod = if (isInstallment) (selectedChannel?.name ?: "分期付款") else selectedPaymentMethod,
                    occurredAt = FormatUtils.toBeijingIsoString(txOccurredAt),
                    isInstallment = if (isInstallment) true else null,
                    installmentChannelCode = if (isInstallment) selectedChannel?.code else null,
                    installmentTotalAmount = if (isInstallment) cents else null,
                    installmentMonths = if (isInstallment) effectivePeriods else null,
                    installmentMonthlyPay = if (isInstallment) approxMonthly else null,
                    installmentRemainMonths = if (isInstallment) effectivePeriods else null,
                    repaymentDay = if (isInstallment) repayDay else null,
                    installmentFeeMode = if (isInstallment) selectedFeeMode else null,
                    installmentFeeValue = if (isInstallment) feeValCents else null,
                    firstDueDate = if (isInstallment) firstDueDate else null,
                    linkGearAsset = isInstallment && linkGearAsset,
                    gearCategory = if (isInstallment && linkGearAsset) selectedCategory else null
                )
                ledgerRepository.createTransactionOptimistic(req)
                onTransactionCreated()
                onDismiss()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = (screenHeight - statusBarTop - 16.dp).coerceAtLeast(200.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶栏：支出 / 收入 文字切换，右侧"取消"与"重置"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TypeToggleButton(
                            text = "支出",
                            isSelected = selectedType == "expense",
                            isExpense = true,
                            onClick = {
                                selectedType = "expense"
                                if (selectedCategory !in expenseCategories) {
                                    selectedCategory = expenseCategories.firstOrNull() ?: "餐饮"
                                }
                            }
                        )
                        TypeToggleButton(
                            text = "收入",
                            isSelected = selectedType == "income",
                            isExpense = false,
                            onClick = {
                                selectedType = "income"
                                payMethodChoice = "full"
                                if (selectedCategory !in incomeCategories) {
                                    selectedCategory = incomeCategories.firstOrNull() ?: "薪资"
                                }
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (inputAmountString != "0") {
                            TextButton(
                                onClick = { clearAll() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("重置", color = colors.textSecondary, fontFamily = LedgerTheme.typography.serif, fontSize = 13.sp)
                            }
                        }
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("取消", color = colors.textSecondary, fontFamily = LedgerTheme.typography.serif, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 大金额展示行（等宽数字）
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (selectedType == "income") "+ ¥" else "¥",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedType == "income") colors.income else colors.expense,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = inputAmountString,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                // 付款方式选择：一次付清 / 分期付款 (仅支出时展示)
                if (selectedType == "expense") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LedgerChoiceItem(
                            text = "一次付清",
                            isSelected = payMethodChoice == "full",
                            onClick = { payMethodChoice = "full" },
                            modifier = Modifier.weight(1f)
                        )
                        LedgerChoiceItem(
                            text = "分期付款",
                            isSelected = payMethodChoice == "installment",
                            onClick = { payMethodChoice = "installment" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (payMethodChoice == "installment") {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 分期期数
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
                                    LedgerChoiceItem(
                                        text = if (p == "自定义") p else "${p}期",
                                        isSelected = selectedPeriodPreset == p,
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

                        // 信贷渠道
                        if (creditChannels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "信贷渠道",
                                    style = LedgerTheme.typography.caption,
                                    color = colors.textSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    creditChannels.forEach { ch ->
                                        LedgerChoiceItem(
                                            text = ch.name,
                                            isSelected = selectedChannel?.id == ch.id,
                                            onClick = {
                                                selectedChannel = ch
                                                repayDay = ch.repaymentDay
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 手续费模式
                        Spacer(modifier = Modifier.height(6.dp))
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
                                listOf(
                                    "none" to "无费用",
                                    "fixed_per_period" to "每期固定",
                                    "rate_per_period" to "每期费率",
                                    "total_fee" to "总手续费"
                                ).forEach { (mode, label) ->
                                    LedgerChoiceItem(
                                        text = label,
                                        isSelected = selectedFeeMode == mode,
                                        onClick = {
                                            selectedFeeMode = mode
                                            if (mode == "none") feeValueInput = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (selectedFeeMode != "none") {
                                val (fLabel, fUnit, fHolder) = when (selectedFeeMode) {
                                    "fixed_per_period" -> Triple("每期固定手续费", "元", "如 6.00")
                                    "rate_per_period" -> Triple("每期费率", "%", "如 0.5")
                                    "total_fee" -> Triple("总手续费", "元", "如 72.00")
                                    else -> Triple("手续费", "", "")
                                }
                                LedgerField(
                                    value = feeValueInput,
                                    onValueChange = { feeValueInput = it },
                                    label = fLabel,
                                    placeholder = fHolder,
                                    isNumeric = true,
                                    trailingText = fUnit,
                                    singleLine = true
                                )
                            }
                        }

                        // 首期到期日
                        Spacer(modifier = Modifier.height(6.dp))
                        LedgerField(
                            value = firstDueDate,
                            onValueChange = {},
                            label = "首期到期日",
                            placeholder = "YYYY-MM-DD",
                            readOnly = true,
                            singleLine = true,
                            trailingText = "每月${repayDay}日",
                            onClick = {
                                showFirstDueDatePicker = true
                            }
                        )

                        // 实时试算卡片
                        Spacer(modifier = Modifier.height(6.dp))
                        val endYm = runCatching {
                            val dt = LocalDate.parse(firstDueDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            dt.plusMonths((effectivePeriods - 1).toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                        }.getOrDefault("----")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceElevated)
                                .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (effectiveCents <= 0) {
                                    "输入金额后将实时试算各期还款额与手续费"
                                } else {
                                    "每期约 ${LedgerFormat.formatMoney(approxMonthly)}（含手续费 ${LedgerFormat.formatMoney(avgFCents)}），共 $effectivePeriods 期，总手续费 ${LedgerFormat.formatMoney(totalFCents)}，末期 $endYm"
                                },
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }

                        // 收录在役装备开关
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { linkGearAsset = !linkGearAsset }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "同步收录至在役装备库",
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (linkGearAsset) "开启后将同时在「资产大盘」创建在役硬件单品" else "关闭则仅建立分期账单与负债，不建立装备单品",
                                    style = LedgerTheme.typography.caption,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = linkGearAsset,
                                onCheckedChange = { linkGearAsset = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textMuted,
                                    uncheckedTrackColor = colors.surfaceElevated
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

            // 分类网格：显示常用 6 个（色点 + 文字），末尾"更多"展开全部
            val categoriesToShow = if (showAllCategories) activeCategories else activeCategories.take(6)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categoriesToShow.chunked(3).forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (cat in rowCats) {
                            val isSelected = cat == selectedCategory
                            val dotColor = LedgerCategoryColors.colorForCategory(cat, colors.isDark)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                                    .border(
                                        ArchiveLines.hairline,
                                        if (isSelected) colors.accent else colors.borderSubtle,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable(role = Role.RadioButton) { selectedCategory = cat }
                                    .padding(horizontal = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = cat,
                                        fontFamily = LedgerTheme.typography.serif,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary
                                    )
                                }
                            }
                        }
                        // 填补对齐
                        for (i in 0 until (3 - rowCats.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 展开全部 / 收起更多分类按钮
                if (activeCategories.size > 6) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllCategories = !showAllCategories }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showAllCategories) "收起分类 ▴" else "更多分类 ▾",
                            fontFamily = LedgerTheme.typography.serif,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // "备注 / 商户 / 时间 / 账户" 折叠在"更多"里，默认不展开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMoreDetails = !showMoreDetails }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showMoreDetails) "收起详细信息 ▴" else "添加备注、账户与时间 ▾",
                    fontFamily = LedgerTheme.typography.serif,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                if (!showMoreDetails && (noteText.isNotBlank() || selectedPaymentMethod != lastPaymentMethod)) {
                    Text(
                        text = listOfNotNull(
                            noteText.takeIf { it.isNotBlank() },
                            selectedPaymentMethod.takeIf { it != lastPaymentMethod }
                        ).joinToString(" · "),
                        fontFamily = LedgerTheme.typography.serif,
                        fontSize = 11.5.sp,
                        color = colors.accent
                    )
                }
            }

            if (showMoreDetails) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    LedgerTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = "商户或备注（可选）",
                        singleLine = true
                    )

                    // 支付账户选择行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (pm in paymentMethods) {
                            val isPmSelected = pm == selectedPaymentMethod
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isPmSelected) colors.surfaceElevated else Color.Transparent)
                                    .border(
                                        ArchiveLines.hairline,
                                        if (isPmSelected) colors.accent else colors.borderSubtle,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { selectedPaymentMethod = pm }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = pm,
                                    fontFamily = LedgerTheme.typography.serif,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPmSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isPmSelected) colors.accent else colors.textSecondary
                                )
                            }
                        }
                    }

                    // 发生时刻选择
                    LedgerDateTimeSplitRow(
                        selectedDateTime = txOccurredAt,
                        onDateTimeSelected = {
                            txOccurredAtMillis = it.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli()
                        }
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 常驻自定义共享四列数字键盘（0-9、小数点、退格、"+"累加、右列"记下/分期记下"确认按钮），不弹系统键盘
            TactileKeypad(
                onDigitClick = { appendDigit(it) },
                onDotClick = { appendDot() },
                onBackspaceClick = { backspace() },
                onPlusClick = { appendPlus() },
                onClearAll = { clearAll() },
                confirmText = if (isInstallment) "分期记下" else "记下",
                isConfirmEnabled = effectiveCents > 0,
                onConfirm = handleSubmit,
                showPlus = true,
                keyHeight = 46.dp
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showFirstDueDatePicker) {
        val initialDate = runCatching { LocalDate.parse(firstDueDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
        ArchiveDatePickerSheet(
            selectedDate = initialDate,
            onDateSelected = { newDate ->
                firstDueDate = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
                repayDay = newDate.dayOfMonth
            },
            onDismissRequest = { showFirstDueDatePicker = false }
        )
    }
}
