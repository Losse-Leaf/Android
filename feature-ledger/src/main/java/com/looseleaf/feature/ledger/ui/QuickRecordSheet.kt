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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateCreditChannelRequest
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.ArchivePrimaryButton
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

    // 分期参数（仅保留自建渠道，无手续费模式，首期仅选月份）
    var periodsText by rememberSaveable { mutableStateOf("12") }
    val effectivePeriods = periodsText.toIntOrNull() ?: 12

    val userChannels = remember(creditChannels) {
        creditChannels.filterNot { it.isBuiltin || it.code in setOf("HUABEI", "BAITIAO", "CUSTOM_INSTALLMENT", "huabei", "baitiao", "custom_installment") }
    }
    var selectedChannel by remember(userChannels) { mutableStateOf(userChannels.firstOrNull()) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showChannelSelectDialog by remember { mutableStateOf(false) }

    var firstDueYearMonth by remember(selectedChannel) {
        val now = LocalDate.now()
        val targetDay = selectedChannel?.repaymentDay ?: 10
        val ym = if (now.dayOfMonth <= targetDay) YearMonth.from(now) else YearMonth.from(now).plusMonths(1)
        mutableStateOf(ym)
    }
    var showMonthPicker by remember { mutableStateOf(false) }

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

    // 简单累加计算（使用 FormatUtils.yuanStringToCents 整数分运算，杜绝浮点精度丢失）
    fun calculateEffectiveCents(): Long {
        return if (inputAmountString.contains("+")) {
            inputAmountString.split("+")
                .map { FormatUtils.yuanStringToCents(it.trim()) }
                .sum()
        } else {
            FormatUtils.yuanStringToCents(inputAmountString)
        }
    }

    val effectiveCents = remember(inputAmountString) { calculateEffectiveCents() }

    val isInstallment = (selectedType == "expense" && payMethodChoice == "installment")
    val approxMonthly = if (effectivePeriods > 0) effectiveCents / effectivePeriods else 0L

    var isSubmitting by remember { mutableStateOf(false) }

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

    val handleSubmit: () -> Unit = handleSubmit@{
        val cents = calculateEffectiveCents()
        if (cents > 0 && !isSubmitting) {
            if (isInstallment) {
                if (selectedChannel == null) {
                    ArchiveToastManager.show("请选择分期渠道", isError = true)
                    return@handleSubmit
                }
                if (effectivePeriods !in 1..60) {
                    ArchiveToastManager.show("分期期数需在 1 至 60 期之间", isError = true)
                    return@handleSubmit
                }
            }
            isSubmitting = true
            // 持久化上次使用的支付方式
            sp.edit().putString("last_payment_method", selectedPaymentMethod).apply()

            val trimmedNote = noteText.trim()
            val title = if (trimmedNote.isNotBlank()) {
                trimmedNote.lineSequence().firstOrNull()?.take(20)?.ifBlank { selectedCategory } ?: selectedCategory
            } else selectedCategory
            val note = trimmedNote.takeIf { it.isNotBlank() && it != title }

            val targetDay = (selectedChannel?.repaymentDay ?: 10).coerceAtMost(firstDueYearMonth.lengthOfMonth())
            val firstDueDateStr = String.format(Locale.US, "%04d-%02d-%02d", firstDueYearMonth.year, firstDueYearMonth.monthValue, targetDay)

            scope.launch {
                try {
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
                        repaymentDay = if (isInstallment) (selectedChannel?.repaymentDay ?: 10) else null,
                        installmentFeeMode = if (isInstallment) "none" else null,
                        installmentFeeValue = if (isInstallment) 0L else null,
                        firstDueDate = if (isInstallment) firstDueDateStr else null,
                        linkGearAsset = isInstallment && linkGearAsset,
                        gearCategory = if (isInstallment && linkGearAsset) selectedCategory else null
                    )
                    ledgerRepository.createTransactionOptimistic(req)
                    onTransactionCreated()
                } catch (e: Exception) {
                    ArchiveToastManager.show("记账失败: ${e.message}", isError = true)
                    isSubmitting = false
                }
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
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
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

                // 大金额展示行（等宽数字，支持点击直接调起手机软键盘输入）
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
                    BasicTextField(
                        value = if (inputAmountString == "0") "" else inputAmountString,
                        onValueChange = { newVal ->
                            val sanitized = newVal.filter { it.isDigit() || it == '.' || it == '+' }
                            inputAmountString = if (sanitized.isEmpty()) "0" else sanitized
                        },
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            if (inputAmountString == "0") {
                                Text(
                                    text = "0.00",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textMuted
                                )
                            }
                            innerTextField()
                        }
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

                        // 1. 分期期数输入（留一个空填）+ 常用期数快捷胶囊并排
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                modifier = Modifier.width(110.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "常用期数",
                                    style = LedgerTheme.typography.caption,
                                    fontFamily = LedgerTheme.typography.serif,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("3", "6", "12", "24", "36").forEach { p ->
                                        val isSelected = periodsText == p
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                                                .border(
                                                    ArchiveLines.hairline,
                                                    if (isSelected) colors.accent else colors.borderSubtle,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { periodsText = p }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "${p}期",
                                                fontFamily = LedgerTheme.typography.serif,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) colors.accent else colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. 还款渠道 与 首期还款月份 并排单行双列（合并精简，单行截断）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 还款渠道列（仅展示自建渠道，带 + 新建）
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
                                        .clickable {
                                            if (userChannels.isEmpty()) {
                                                showCreateChannelDialog = true
                                            } else {
                                                showChannelSelectDialog = true
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = selectedChannel?.name ?: if (userChannels.isEmpty()) "点击新建渠道" else "选择渠道",
                                            fontFamily = LedgerTheme.typography.serif,
                                            fontSize = 14.sp,
                                            color = if (selectedChannel != null) colors.textPrimary else colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = selectedChannel?.let { "每月${it.repaymentDay}日" } ?: "▾",
                                            fontFamily = LedgerTheme.typography.serif,
                                            fontSize = 12.sp,
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
                                        .clickable { showMonthPicker = true }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%04d-%02d", firstDueYearMonth.year, firstDueYearMonth.monthValue),
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "▾",
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 实时试算单行卡片
                        val endYm = runCatching {
                            firstDueYearMonth.plusMonths((effectivePeriods - 1).toLong().coerceAtLeast(0L)).toString()
                        }.getOrDefault("----")

                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceElevated)
                                .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val previewText = if (effectiveCents <= 0) {
                                "输入金额后将实时试算各期还款额"
                            } else {
                                "每期约 ${LedgerFormat.formatMoney(approxMonthly)} · 共 $effectivePeriods 期 · 末期 $endYm"
                            }
                            Text(
                                text = previewText,
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 4. 收录在役装备开关
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
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (linkGearAsset) "开启后将同时在「资产大盘」创建在役硬件单品" else "关闭则仅建立分期账单与负债，不建立装备单品",
                                    style = LedgerTheme.typography.caption,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = linkGearAsset,
                                onCheckedChange = { linkGearAsset = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.background,
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
                        fontSize = 12.sp,
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

            // 分期付款使用全宽按钮，普通记账保留快速计算小键盘
            if (isInstallment) {
                ArchivePrimaryButton(
                    text = if (isSubmitting) "记账中…" else "分期记下",
                    enabled = effectiveCents > 0 && !isSubmitting && selectedChannel != null && effectivePeriods in 1..60,
                    onClick = handleSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            } else {
                TactileKeypad(
                    onDigitClick = { appendDigit(it) },
                    onDotClick = { appendDot() },
                    onBackspaceClick = { backspace() },
                    onPlusClick = { appendPlus() },
                    onClearAll = { clearAll() },
                    confirmText = if (isSubmitting) "记账中…" else "记下",
                    isConfirmEnabled = effectiveCents > 0 && !isSubmitting,
                    onConfirm = handleSubmit,
                    showPlus = true,
                    keyHeight = 46.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
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

    // 快捷新建渠道对话框
    if (showCreateChannelDialog) {
        var newName by remember { mutableStateOf("") }
        var newDay by remember { mutableStateOf("10") }
        var isSubmittingChannel by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingChannel) showCreateChannelDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "新建还款渠道",
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LedgerField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = "渠道名称",
                        placeholder = "如 招商银行信用卡",
                        singleLine = true
                    )
                    LedgerField(
                        value = newDay,
                        onValueChange = { newDay = it.filter { c -> c.isDigit() }.take(2) },
                        label = "每月固定还款日",
                        placeholder = "10",
                        trailingText = "日",
                        isNumeric = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                LedgerTextButton(
                    text = "创建并选择",
                    enabled = newName.isNotBlank() && !isSubmittingChannel,
                    onClick = {
                        val day = newDay.toIntOrNull()?.coerceIn(1, 31) ?: 10
                        isSubmittingChannel = true
                        scope.launch {
                            runCatching {
                                val created = apiClient.createCreditChannel(
                                    CreateCreditChannelRequest(name = newName.trim(), repaymentDay = day)
                                )
                                selectedChannel = created
                                LedgerRepository.refreshAssets(apiClient)
                            }.onFailure { err ->
                                ArchiveToastManager.show("创建渠道失败: ${err.message}", isError = true)
                            }
                            isSubmittingChannel = false
                            showCreateChannelDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                LedgerTextButton(
                    text = "取消",
                    enabled = !isSubmittingChannel,
                    onClick = { showCreateChannelDialog = false }
                )
            }
        )
    }

    // 渠道选择对话框
    if (showChannelSelectDialog) {
        AlertDialog(
            onDismissRequest = { showChannelSelectDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "选择还款渠道",
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    userChannels.forEach { ch ->
                        val isSelected = selectedChannel?.id == ch.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                                .clickable {
                                    selectedChannel = ch
                                    showChannelSelectDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ch.name,
                                fontFamily = LedgerTheme.typography.serif,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                color = if (isSelected) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "每月${ch.repaymentDay}日",
                                fontFamily = LedgerTheme.typography.serif,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                LedgerTextButton(
                    text = "+ 新建渠道",
                    onClick = {
                        showChannelSelectDialog = false
                        showCreateChannelDialog = true
                    }
                )
            },
            dismissButton = {
                LedgerTextButton(
                    text = "关闭",
                    onClick = { showChannelSelectDialog = false }
                )
            }
        )
    }
}
