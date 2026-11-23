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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateCreditChannelRequest
import com.looseleaf.app.model.CreateTransactionRequest
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
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
 * 7. 底部为触感键盘，确认键「记下」朱砂实心；金额为 0 禁用；不丢焦点。
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 备注等文本输入获得焦点时，系统 IME 与自定义数字键盘不得同屏（KB-03）。
    // 此时小键盘让位给等高占位条，输入结束后再切回。
    var isNoteEditing by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val dismissSheet: () -> Unit = {
        // 提交进行中不允许关闭（含下滑手势）：否则提交协程随组合销毁被取消
        if (!isSubmitting) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismiss()
        }
    }

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

    val userChannels = remember(creditChannels) { creditChannels.userCreditChannels() }
    var selectedChannel by remember(userChannels) { mutableStateOf(userChannels.firstOrNull()) }

    var firstDueYearMonth by remember(selectedChannel) {
        mutableStateOf(defaultFirstDueYearMonth(selectedChannel?.repaymentDay ?: 10))
    }

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

    // 简单累加计算统一走 FormatUtils.AmountInput.toCents（整数分运算，杜绝浮点尾差）
    fun calculateEffectiveCents(): Long = FormatUtils.AmountInput.toCents(inputAmountString)

    val effectiveCents = remember(inputAmountString) { calculateEffectiveCents() }

    /**
     * 自然语义记账（RG-27）：备注里写「42.5 午餐手冲咖啡」时实时解析出金额 + 分类 + 标题。
     *
     * 只在用户**还没敲金额**时采纳 —— 一旦敲了数字键盘，说明他已经明确表达金额，
     * 此时解析结果让位，避免「我明明输了 30，却被备注里的 42.5 覆盖」。
     */
    val parsedNote = remember(noteText, activeCategories) {
        if (noteText.isBlank()) {
            null
        } else {
            FormatUtils.parseNaturalTransactionInput(noteText, activeCategories)
                .takeIf { it.amountCents > 0 }
        }
    }
    val useParsedNote = parsedNote != null && effectiveCents == 0L

    /**
     * 本次提交实际采用的金额（分）—— 速记弹层唯一的「金额事实源」。
     *
     * 此前按钮 enabled / 分期试算 / 提交金额各算各的：enabled 只看 effectiveCents，
     * handleSubmit 却会采纳 parsedNote.amountCents。于是备注里写「42.5 午餐」时，
     * 界面提示「点『记下』自动采纳」，按钮却是灰的、根本点不动（RG-27 接线遗漏）。
     * 现在所有判定都读这一个值，不会再出现「UI 说能提交、按钮不让提交」。
     */
    val submitCents = FormatUtils.resolveQuickRecordCents(effectiveCents, parsedNote?.amountCents)

    // 解析出收入（如「+5000 工资」）时把类型切过去，保证界面与提交一致；
    // 依赖 useParsedNote 而不是 selectedType，避免用户手动改回支出后被反复纠正。
    LaunchedEffect(useParsedNote, parsedNote?.type) {
        if (useParsedNote && parsedNote?.type == "income" && selectedType != "income") {
            selectedType = "income"
        }
    }

    val isInstallment = (selectedType == "expense" && payMethodChoice == "installment")
    val approxMonthly = if (effectivePeriods > 0) submitCents / effectivePeriods else 0L

    // 金额写入只走 FormatUtils.AmountInput：金额行是纯展示，系统键盘/粘贴无法绕过校验
    fun appendDigit(digit: String) {
        inputAmountString = FormatUtils.AmountInput.digit(inputAmountString, digit)
    }

    fun appendDot() {
        inputAmountString = FormatUtils.AmountInput.dot(inputAmountString)
    }

    fun appendPlus() {
        inputAmountString = FormatUtils.AmountInput.plus(inputAmountString)
    }

    fun backspace() {
        inputAmountString = FormatUtils.AmountInput.backspace(inputAmountString)
    }

    fun clearAll() {
        inputAmountString = FormatUtils.AmountInput.clear()
        noteText = ""
        txOccurredAtMillis = System.currentTimeMillis()
    }

    val handleSubmit: () -> Unit = handleSubmit@{
        val parsed = if (useParsedNote) parsedNote else null
        val cents = submitCents
        if (cents > 0 && !isSubmitting) {
            val submitType = parsed?.type ?: selectedType
            val submitCategory = parsed?.category ?: selectedCategory
            // 分期只对支出生效：语义解析可能把类型切成收入，这里再兜一次底
            val submitInstallment = isInstallment && submitType == "expense"
            if (submitInstallment) {
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
            val title = parsed?.title ?: if (trimmedNote.isNotBlank()) {
                trimmedNote.lineSequence().firstOrNull()?.take(20)?.ifBlank { submitCategory } ?: submitCategory
            } else submitCategory
            // 解析路径下备注已被拆成金额与标题，再存一份原文只会重复
            val note = if (parsed != null) null else trimmedNote.takeIf { it.isNotBlank() && it != title }

            val firstDueDateStr = formatFirstDueDate(firstDueYearMonth, selectedChannel?.repaymentDay ?: 10)

            scope.launch {
                try {
                    val req = CreateTransactionRequest(
                        amount = cents,
                        type = submitType,
                        category = submitCategory,
                        title = title,
                        note = note,
                        paymentMethod = if (submitInstallment) (selectedChannel?.name ?: "分期付款") else selectedPaymentMethod,
                        occurredAt = FormatUtils.toBeijingIsoString(txOccurredAt),
                        isInstallment = if (submitInstallment) true else null,
                        installmentChannelCode = if (submitInstallment) selectedChannel?.code else null,
                        installmentTotalAmount = if (submitInstallment) cents else null,
                        installmentMonths = if (submitInstallment) effectivePeriods else null,
                        installmentMonthlyPay = if (submitInstallment) approxMonthly else null,
                        installmentRemainMonths = if (submitInstallment) effectivePeriods else null,
                        repaymentDay = if (submitInstallment) (selectedChannel?.repaymentDay ?: 10) else null,
                        installmentFeeMode = if (submitInstallment) "none" else null,
                        installmentFeeValue = if (submitInstallment) 0L else null,
                        firstDueDate = if (submitInstallment) firstDueDateStr else null
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
    val maxSheetHeight = (screenHeight - statusBarTop - 12.dp).coerceAtLeast(200.dp)
    val isIncome = selectedType == "income"
    val amountTint = if (isIncome) colors.income else colors.expense
    val isAmountEmpty = inputAmountString == FormatUtils.AmountInput.EMPTY || inputAmountString == "0"

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        scrimColor = colors.archive.scrim,
        shape = com.looseleaf.app.theme.ArchiveShapes.sheetTop,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
        ) {
            ArchiveSheetGrip()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // 顶栏：支出 / 收入 分段 + 右侧「重置」与关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                            .background(colors.surfaceElevated)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ArchiveTypeToggleButton(
                            text = "支出",
                            isSelected = selectedType == "expense",
                            onClick = {
                                selectedType = "expense"
                                if (selectedCategory !in expenseCategories) {
                                    selectedCategory = expenseCategories.firstOrNull() ?: "餐饮"
                                }
                            }
                        )
                        ArchiveTypeToggleButton(
                            text = "收入",
                            isSelected = isIncome,
                            onClick = {
                                selectedType = "income"
                                payMethodChoice = "full"
                                if (selectedCategory !in incomeCategories) {
                                    selectedCategory = incomeCategories.firstOrNull() ?: "薪资"
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val canReset = inputAmountString != "0"
                    if (canReset) {
                        ArchiveTextButton(
                            text = "重置",
                            onClick = { clearAll() },
                            textColor = colors.textSecondary
                        )
                    }
                    ArchiveHeaderIconButton(
                        onClick = dismissSheet,
                        contentDescription = "取消",
                        imageVector = FolioGlyphs.Close,
                        tint = colors.textSecondary,
                        iconSize = 20.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 金额：纯展示（唯一写入口是下方自定义数字键盘，避免与系统 IME 叠屏）
                Text(
                    text = if (isIncome) "收入金额" else "支出金额",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isIncome) "+¥" else "¥",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.amountHero.copy(fontSize = 26.sp),
                        color = if (isAmountEmpty) colors.textMuted else amountTint,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (inputAmountString == FormatUtils.AmountInput.EMPTY) "0.00" else inputAmountString,
                        style = com.looseleaf.app.theme.ArchiveTextStyles.amountHero.copy(fontSize = 48.sp),
                        color = if (isAmountEmpty) colors.textMuted.copy(alpha = 0.6f) else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                        .background(if (isAmountEmpty) colors.borderSubtle else amountTint.copy(alpha = 0.55f))
                )

                // 语义识别提示（RG-27）：金额还没敲时，明确告诉用户备注里那串数字会被采纳
                val activeParsed = if (useParsedNote) parsedNote else null
                if (activeParsed != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                            .background(colors.accentSoft)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "识别到 ${FormatUtils.formatMoney(activeParsed.amountCents)} · ${activeParsed.category}",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                            color = colors.archive.accentSmallText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "记下时自动采纳",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            color = colors.textSecondary
                        )
                    }
                }

                // 付款方式：一次付清 / 分期付款（仅支出）
                if (selectedType == "expense") {
                    Spacer(modifier = Modifier.height(14.dp))
                    InstallmentConfigSection(
                        periodsText = periodsText,
                        onPeriodsTextChange = { periodsText = it },
                        channels = userChannels,
                        selectedChannel = selectedChannel,
                        onChannelSelected = { selectedChannel = it },
                        payMethodChoice = payMethodChoice,
                        onPayMethodChoiceChange = { payMethodChoice = it },
                        firstDueYearMonth = firstDueYearMonth,
                        onFirstDueYearMonthChange = { firstDueYearMonth = it },
                        principalCents = submitCents,
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

                Spacer(modifier = Modifier.height(18.dp))

                // 分类：常用 6 个，「更多」展开全部；每格为分类首字方印 + 名称
                Text(
                    text = "分类",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                                val catColor = LedgerCategoryColors.colorForCategory(cat, colors.isDark)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                                        .background(
                                            if (isSelected) catColor.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
                                            else colors.surface
                                        )
                                        .border(
                                            if (isSelected) 1.5.dp else ArchiveLines.hairline,
                                            if (isSelected) catColor else colors.archive.borderCard,
                                            com.looseleaf.app.theme.ArchiveShapes.md
                                        )
                                        .clickable(role = Role.RadioButton) { selectedCategory = cat }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(com.looseleaf.app.theme.ArchiveShapes.sm)
                                            .background(catColor.copy(alpha = if (isSelected) 1f else 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat.take(1),
                                            fontFamily = com.looseleaf.app.theme.ArchiveFontFamilies.Display,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) colors.onAccent else catColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cat,
                                        style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            for (i in 0 until (3 - rowCats.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (activeCategories.size > 6) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                                .clickable(role = Role.Button) { showAllCategories = !showAllCategories }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showAllCategories) "收起分类" else "全部 ${activeCategories.size} 个分类",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showAllCategories) FolioGlyphs.ChevronUp else FolioGlyphs.ChevronDown,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 「备注 / 账户 / 时间」折叠条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                        .background(colors.surfaceElevated)
                        .clickable(role = Role.Button) {
                            val expanded = !showMoreDetails
                            showMoreDetails = expanded
                            // 收起时若备注框仍有焦点，必须一并收键盘，否则 isNoteEditing 会残留
                            if (!expanded) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isNoteEditing = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showMoreDetails) "收起详细信息" else "备注、账户与时间",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                            color = colors.textPrimary
                        )
                        val summary = listOfNotNull(
                            noteText.takeIf { it.isNotBlank() },
                            if (selectedType == "expense" && payMethodChoice == "installment") null else selectedPaymentMethod
                        ).joinToString(" · ")
                        if (!showMoreDetails && summary.isNotBlank()) {
                            Text(
                                text = summary,
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                color = if (noteText.isNotBlank()) colors.archive.accentSmallText else colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showMoreDetails) FolioGlyphs.ChevronUp else FolioGlyphs.ChevronDown,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showMoreDetails) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        ArchiveTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = "商户或备注，可写「42.5 午餐」",
                            singleLine = true,
                            modifier = Modifier.onFocusChanged { isNoteEditing = it.isFocused }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (pm in paymentMethods) {
                                ArchiveSelectableCapsule(
                                    text = pm,
                                    selected = pm == selectedPaymentMethod,
                                    onClick = { selectedPaymentMethod = pm }
                                )
                            }
                        }

                        ArchiveDateTimeSplitRow(
                            selectedDateTime = txOccurredAt,
                            onDateTimeSelected = {
                                txOccurredAtMillis = it.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 底部操作区：分期为全宽主按钮；普通记账为触感小键盘；文本输入期间让位给等高占位条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 12.dp)
            ) {
                ArchiveDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(modifier = Modifier.height(12.dp))
                if (isInstallment) {
                    ArchivePrimaryButton(
                        text = if (isSubmitting) "记账中" else "分期记下",
                        enabled = submitCents > 0 && !isSubmitting && selectedChannel != null && effectivePeriods in 1..60,
                        onClick = handleSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 12.dp)
                    )
                } else if (isNoteEditing) {
                    // 系统 IME 是唯一键盘：小键盘让位给等高占位（4 行 × 46dp + 3 × 8dp = 208dp）
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(208.dp)
                    ) {
                        ArchivePrimaryButton(
                            text = "完成输入，返回数字键盘",
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isNoteEditing = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    TactileKeypad(
                        onDigitClick = { appendDigit(it) },
                        onDotClick = { appendDot() },
                        onBackspaceClick = { backspace() },
                        onPlusClick = { appendPlus() },
                        onClearAll = { clearAll() },
                        confirmText = if (isSubmitting) "记账中" else "记下",
                        isConfirmEnabled = submitCents > 0 && !isSubmitting,
                        onConfirm = handleSubmit,
                        showPlus = true,
                        keyHeight = 46.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 主按钮禁用时把原因写在按钮外，保持读屏「已禁用」语义
                if (submitCents <= 0 && !isSubmitting && !isNoteEditing) {
                    Text(
                        text = "填好金额后可记下",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }
            }
        }
    }
}
