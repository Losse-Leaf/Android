package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 极速触觉记账、账单流水与资产大盘 (LedgerScreen)
 * 三栏架构：0 极速记账 | 1 账单明细 | 2 资产与信贷
 */
@Composable
fun LedgerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val haptics = remember { HapticsEngine.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val apiClient = remember { APIClient.getInstance(context) }

    val transactions by syncManager.transactionsFlow.collectAsState()

    // 0: 极速记账, 1: 账单流水, 2: 资产与信贷
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    var expenseCategories by remember { mutableStateOf(DefaultExpenseCategories) }
    var incomeCategories by remember { mutableStateOf(DefaultIncomeCategories) }
    var paymentMethods by remember { mutableStateOf(listOf("微信支付", "支付宝", "银行卡", "现金")) }
    var creditChannels by remember { mutableStateOf<List<CreditChannel>>(emptyList()) }

    // 记账输入状态
    var inputAmountString by rememberSaveable { mutableStateOf("0") }
    var selectedType by rememberSaveable { mutableStateOf("expense") } // "expense" | "income"
    var selectedCategory by rememberSaveable { mutableStateOf(inferRecommendedCategory()) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf("微信支付") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var hasInitializedLedger by rememberSaveable { mutableStateOf(false) }

    // 分期参数
    var isInstallment by remember { mutableStateOf(false) }
    var installmentChannelCode by remember { mutableStateOf("huabei") }
    var installmentMonths by remember { mutableStateOf(12) }
    var repaymentDay by remember { mutableStateOf(10) }

    // 资产大盘数据
    var assetsOverview by remember { mutableStateOf<AssetsOverview?>(null) }
    var isLoadingAssets by remember { mutableStateOf(false) }
    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }

    // 对话框状态
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showCalibrateBalanceDialog by remember { mutableStateOf(false) }
    var showAddGearDialog by remember { mutableStateOf(false) }
    var sellingGearItem by remember { mutableStateOf<GearItem?>(null) }
    var showCreditChannelsDialog by remember { mutableStateOf(false) }
    var showCategoriesManagerDialog by remember { mutableStateOf(false) }

    // 账单流水筛选月份 (默认当前年月)
    var selectedYearMonth by remember {
        mutableStateOf(YearMonth.now(FormatUtils.BeijingZone))
    }

    suspend fun reloadCategoriesAndAssets(forceLoadingSpinner: Boolean = false) {
        runCatching {
            val allCats = apiClient.fetchCategories()
            val exp = allCats.filter { it.scope == "expense" }.map { it.name }
            if (exp.isNotEmpty()) expenseCategories = exp
            val inc = allCats.filter { it.scope == "income" }.map { it.name }
            if (inc.isNotEmpty()) incomeCategories = inc
            val pay = allCats.filter { it.scope == "payment" }.map { it.name }
            if (pay.isNotEmpty()) {
                paymentMethods = pay
                if (selectedPaymentMethod !in pay) selectedPaymentMethod = pay.first()
            }
        }
        runCatching {
            creditChannels = apiClient.fetchCreditChannels()
            if (creditChannels.isNotEmpty() && creditChannels.none { it.code == installmentChannelCode }) {
                installmentChannelCode = creditChannels.first().code
            }
        }
        runCatching {
            if (forceLoadingSpinner || assetsOverview == null) {
                isLoadingAssets = true
            }
            assetsOverview = apiClient.fetchAssetsOverview()
            isLoadingAssets = false
        }
    }

    LaunchedEffect(hasInitializedLedger) {
        if (!hasInitializedLedger) {
            reloadCategoriesAndAssets(forceLoadingSpinner = false)
            hasInitializedLedger = true
        }
    }

    val activeCategories = if (selectedType == "income") incomeCategories else expenseCategories

    fun appendDigit(digit: String) {
        if (inputAmountString == "0") {
            inputAmountString = digit
        } else {
            if (inputAmountString.contains(".")) {
                val decimals = inputAmountString.substringAfter(".")
                if (decimals.length < 2) {
                    inputAmountString += digit
                }
            } else if (inputAmountString.length < 8) {
                inputAmountString += digit
            }
        }
    }

    fun appendDot() {
        if (!inputAmountString.contains(".")) {
            inputAmountString += "."
        }
    }

    fun backspace() {
        if (inputAmountString.length > 1) {
            inputAmountString = inputAmountString.dropLast(1)
            if (inputAmountString.endsWith(".")) {
                inputAmountString = inputAmountString.dropLast(1)
            }
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
        isInstallment = false
    }

    NoiseBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
            // 顶栏：标题与隐私锁
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "散页 · 口袋账本",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "生活收支流水与资产中枢",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptics.tapKey()
                            if (isPrivacyMasked && credentialStore.isBiometricsEnabled) {
                                val act = context as? FragmentActivity
                                if (act != null && BiometricHelper.canAuthenticate(act)) {
                                    BiometricHelper.authenticate(
                                        activity = act,
                                        title = "验证以查看账目金额",
                                        subtitle = "使用生物识别解锁敏感收支数字",
                                        onSuccess = { isPrivacyMasked = false },
                                        onError = { msg ->
                                            ArchiveToastManager.show(msg, isError = true)
                                        }
                                    )
                                } else {
                                    isPrivacyMasked = false
                                }
                            } else {
                                isPrivacyMasked = !isPrivacyMasked
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPrivacyMasked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "隐蔽金额",
                            tint = if (isPrivacyMasked) colors.accentWarm else colors.inkSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                haptics.tapKey()
                                syncManager.pullRemoteData()
                                reloadCategoriesAndAssets()
                                ArchiveToastManager.show("账簿与资产已对齐")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // 三项分段 Tab 切换器 (极速记账 / 账单流水 / 资产与信贷)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.archiveSubtle)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(16.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabTitles = listOf("极速记账", "账单明细 (${transactions.size})", "资产与信贷")
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.archivePaper else Color.Transparent)
                            .clickable {
                                haptics.tapKey()
                                selectedTab = index
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (selectedTab) {
                0 -> {
                    // ==========================================
                    // Tab 0: 极速触觉记账面板 (全机型自适应)
                    // ==========================================
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val isCompactHeight = maxHeight < 700.dp
                        val keypadKeyHeight = if (isCompactHeight) 44.dp else 52.dp

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        // 金额与属性装裱卡片
                        PaperCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column {
                                // 收支类型切换 + 支付方式滑动选择
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.archivePaper)
                                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(14.dp))
                                            .padding(2.dp)
                                    ) {
                                        TypeToggleButton(
                                            text = "支出",
                                            isSelected = selectedType == "expense",
                                            onClick = {
                                                haptics.tapKey()
                                                selectedType = "expense"
                                                if (selectedCategory !in expenseCategories) {
                                                    selectedCategory = expenseCategories.firstOrNull() ?: "餐饮"
                                                }
                                            }
                                        )
                                        TypeToggleButton(
                                            text = "收入",
                                            isSelected = selectedType == "income",
                                            onClick = {
                                                haptics.tapKey()
                                                selectedType = "income"
                                                isInstallment = false
                                                if (selectedCategory !in incomeCategories) {
                                                    selectedCategory = incomeCategories.firstOrNull() ?: "薪资"
                                                }
                                            }
                                        )
                                    }

                                    // 支付方式横向单选
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (pm in paymentMethods) {
                                            val isPmSelected = pm == selectedPaymentMethod
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isPmSelected) colors.inkPrimary else Color.Transparent)
                                                    .border(0.5.dp, if (isPmSelected) colors.inkPrimary else colors.borderDivider, RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        haptics.tapKey()
                                                        selectedPaymentMethod = pm
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = pm,
                                                    fontSize = 11.sp,
                                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                    color = if (isPmSelected) colors.archivePaper else colors.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 大金额展示
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (selectedType == "income") "+ ¥" else "¥",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedType == "income") colors.accentMoss else colors.accentWarm,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPrivacyMasked) "****" else inputAmountString,
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 38.sp,
                                        letterSpacing = 1.sp,
                                        color = colors.inkPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (inputAmountString != "0") {
                                        TextButton(onClick = { clearAll() }) {
                                            Text("清空", fontSize = 11.sp, color = colors.inkMuted)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 备注与自然语言拆解
                                PaperTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    placeholder = "备注，或输入“42.5 手冲咖啡”点拆解",
                                    isUnderline = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        if (noteText.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    val parsed = FormatUtils.parseNaturalTransactionInput(noteText)
                                                    if (parsed.amountCents > 0) {
                                                        haptics.tapKey()
                                                        inputAmountString = FormatUtils.centsToYuanString(parsed.amountCents, includeSymbol = false)
                                                        selectedType = parsed.type
                                                        selectedCategory = parsed.category
                                                        noteText = parsed.title
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text("拆解", fontSize = 11.sp, color = colors.accentWarm)
                                            }
                                        }
                                    }
                                )

                                // 支出模式下的分期支持开关
                                if (selectedType == "expense") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isInstallment,
                                                onCheckedChange = { isInstallment = it },
                                                colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                                            )
                                            Text(
                                                text = "分期还款入账",
                                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                fontSize = 12.sp,
                                                color = colors.inkPrimary
                                            )
                                        }

                                        if (isInstallment) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(3, 6, 12, 24).forEach { m ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (installmentMonths == m) colors.accentWarm else colors.archiveSubtle)
                                                            .clickable { installmentMonths = m }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${m}期",
                                                            fontSize = 10.sp,
                                                            color = if (installmentMonths == m) colors.archivePaper else colors.inkSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 分类滑动胶囊
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedType == "income") "收入分类" else "支出分类",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                            TextButton(
                                onClick = { showCategoriesManagerDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.accentWarm)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("管理分类", fontSize = 11.5.sp, color = colors.accentWarm)
                            }
                        }

                        CategoryPills(
                            selectedCategory = selectedCategory,
                            categories = activeCategories,
                            onCategorySelect = { cat ->
                                haptics.tapKey()
                                selectedCategory = cat
                            }
                        )

                        // 4x3 触觉小键盘 (紧凑自适应高度，单屏无滚动直达)
                        TactileKeypad(
                            onDigitClick = { appendDigit(it) },
                            onDotClick = { appendDot() },
                            onBackspaceClick = { backspace() },
                            onClearAll = { clearAll() },
                            haptics = haptics,
                            keyHeight = keypadKeyHeight
                        )

                        // 记入账本提交按钮
                        TerracottaStampButton(
                            text = "记入账本",
                            enabled = inputAmountString != "0" && inputAmountString.toDoubleOrNull() != 0.0,
                            onClick = {
                                val amountDouble = inputAmountString.toDoubleOrNull() ?: 0.0
                                val amountCents = (amountDouble * 100.0).roundToLong()
                                if (amountCents <= 0) return@TerracottaStampButton

                                haptics.stampSave()
                                AudioEngine.playShutter()
                                val title = if (noteText.isNotBlank()) noteText.trim() else selectedCategory

                                scope.launch {
                                    val monthlyPay = if (isInstallment && installmentMonths > 0) amountCents / installmentMonths else 0L
                                    syncManager.createTransactionOptimistic(
                                        amountCents = amountCents,
                                        type = selectedType,
                                        category = selectedCategory,
                                        title = title,
                                        note = noteText.takeIf { it.isNotBlank() },
                                        paymentMethod = selectedPaymentMethod,
                                        isInstallment = isInstallment,
                                        installmentChannelCode = installmentChannelCode.takeIf { isInstallment },
                                        installmentTotalAmount = amountCents.takeIf { isInstallment },
                                        installmentMonths = installmentMonths.takeIf { isInstallment },
                                        installmentMonthlyPay = monthlyPay.takeIf { isInstallment },
                                        installmentRemainMonths = installmentMonths.takeIf { isInstallment },
                                        repaymentDay = repaymentDay.takeIf { isInstallment }
                                    )
                                    ArchiveToastManager.show("已盖印记下: ¥ $inputAmountString")
                                    clearAll()
                                }
                            },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
                1 -> {
                    // ==========================================
                    // Tab 1: 账单明细流水与月度汇总光谱
                    // ==========================================
                    TransactionsLedgerTab(
                        transactions = transactions,
                        isPrivacyMasked = isPrivacyMasked,
                        selectedYearMonth = selectedYearMonth,
                        onMonthChange = { selectedYearMonth = it },
                        onEdit = { editingTransaction = it },
                        onDelete = { tx ->
                            scope.launch {
                                haptics.tearDelete()
                                syncManager.deleteTransaction(tx.id)
                                ArchiveToastManager.show("已删除记账明细")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                2 -> {
                    // ==========================================
                    // Tab 2: 资产大盘、信贷负债与在役固资装备库
                    // ==========================================
                    AssetsAndGearTab(
                        overview = assetsOverview,
                        isLoading = isLoadingAssets,
                        isPrivacyMasked = isPrivacyMasked,
                        onCalibrateBalanceClick = { showCalibrateBalanceDialog = true },
                        onAddGearClick = { showAddGearDialog = true },
                        onSellGearClick = { sellingGearItem = it },
                        onManageChannelsClick = { showCreditChannelsDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 1. 编辑记账流水底部抽屉
        editingTransaction?.let { tx ->
            var editAmountYuan by remember(tx.id) {
                mutableStateOf(FormatUtils.centsToYuanString(tx.amount, includeSymbol = false))
            }
            var editType by remember(tx.id) { mutableStateOf(tx.type) }
            var editCategory by remember(tx.id) { mutableStateOf(tx.category) }
            var editTitle by remember(tx.id) { mutableStateOf(tx.title) }
            var editNote by remember(tx.id) { mutableStateOf(tx.note ?: "") }
            var editPaymentMethod by remember(tx.id) { mutableStateOf(tx.paymentMethod ?: "微信支付") }

            ArchiveBottomSheet(
                onDismissRequest = { editingTransaction = null },
                title = "修订记账明细",
                actionButtonText = "保存修订",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(editAmountYuan)
                    if (cents > 0) {
                        scope.launch {
                            val req = CreateTransactionRequest(
                                amount = cents,
                                type = editType,
                                category = editCategory.trim(),
                                title = editTitle.trim().ifBlank { editCategory.trim() },
                                note = editNote.trim().takeIf { it.isNotBlank() },
                                paymentMethod = editPaymentMethod.trim(),
                                occurredAt = tx.occurredAt
                            )
                            syncManager.updateTransactionOptimistic(tx.id, req)
                            editingTransaction = null
                            ArchiveToastManager.show("已保存账目修订")
                        }
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("expense" to "支出", "income" to "收入").forEach { (k, label) ->
                            PaperFilterPill(
                                selected = editType == k,
                                onClick = { editType = k },
                                label = label
                            )
                        }
                    }

                    PaperTextField(
                        value = editAmountYuan,
                        onValueChange = { editAmountYuan = it },
                        label = "金额 (元)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = "标题/简要",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = "分类",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editPaymentMethod,
                        onValueChange = { editPaymentMethod = it },
                        label = "支付方式",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextEditor(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = "随附手记",
                        minHeight = 70.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. 校准初始流动资金底数抽屉
        if (showCalibrateBalanceDialog) {
            var inputLiquidYuan by remember {
                mutableStateOf(
                    FormatUtils.centsToYuanString(assetsOverview?.initialLiquidBalance ?: 0L, includeSymbol = false)
                )
            }
            ArchiveBottomSheet(
                onDismissRequest = { showCalibrateBalanceDialog = false },
                title = "校准初始流动资金底数",
                actionButtonText = "确认校准",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(inputLiquidYuan)
                    scope.launch {
                        runCatching {
                            apiClient.updateLiquidBalance(cents)
                            assetsOverview = apiClient.fetchAssetsOverview()
                            ArchiveToastManager.show("流动资金底数已校准")
                        }
                        showCalibrateBalanceDialog = false
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "设定初始底数后，系统将依据全量收支流水动态实时推算当前流动资产与净资产：",
                        fontSize = 13.sp,
                        color = colors.inkSecondary,
                        lineHeight = 18.sp
                    )
                    PaperTextField(
                        value = inputLiquidYuan,
                        onValueChange = { inputLiquidYuan = it },
                        label = "初始资金底数 (元)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. 登记在役新装备抽屉
        if (showAddGearDialog) {
            var gearName by remember { mutableStateOf("") }
            var gearSpec by remember { mutableStateOf("") }
            var gearPrice by remember { mutableStateOf("") }
            var gearCategory by remember { mutableStateOf("数码器材") }
            var isAutoTx by remember { mutableStateOf(true) }

            ArchiveBottomSheet(
                onDismissRequest = { showAddGearDialog = false },
                title = "登记在役装备",
                actionButtonText = "录入装备",
                isActionEnabled = gearName.isNotBlank(),
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(gearPrice)
                    scope.launch {
                        runCatching {
                            apiClient.addGearAsset(
                                AddGearAssetRequest(
                                    name = gearName.trim(),
                                    spec = gearSpec.trim(),
                                    priceCents = cents,
                                    category = gearCategory,
                                    createTransaction = isAutoTx && cents > 0
                                )
                            )
                            assetsOverview = apiClient.fetchAssetsOverview()
                            syncManager.pullRemoteData()
                            ArchiveToastManager.show("装备已入库")
                        }
                        showAddGearDialog = false
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperTextField(
                        value = gearName,
                        onValueChange = { gearName = it },
                        label = "装备名称 (如: 索尼 A7M4)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = gearSpec,
                        onValueChange = { gearSpec = it },
                        label = "规格参数 (可选)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = gearPrice,
                        onValueChange = { gearPrice = it },
                        label = "购入总价 (元)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAutoTx,
                            onCheckedChange = { isAutoTx = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                        )
                        Text("同步生成一笔购置支出账目", fontSize = 12.sp, color = colors.inkPrimary)
                    }
                }
            }
        }

        // 4. 二手变卖与回血抽屉
        sellingGearItem?.let { gear ->
            var soldPriceYuan by remember(gear.id) {
                mutableStateOf(FormatUtils.centsToYuanString(gear.priceCents ?: 0L, includeSymbol = false))
            }
            var sellNote by remember(gear.id) { mutableStateOf("出清回血") }

            ArchiveBottomSheet(
                onDismissRequest = { sellingGearItem = null },
                title = "变卖出清 · ${gear.name}",
                actionButtonText = "确认回血",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(soldPriceYuan)
                    scope.launch {
                        runCatching {
                            apiClient.sellGearAsset(
                                id = gear.id,
                                req = SellGearRequest(
                                    soldPriceCents = cents,
                                    soldDate = FormatUtils.beijingNowIsoString().take(10),
                                    note = sellNote.trim()
                                )
                            )
                            assetsOverview = apiClient.fetchAssetsOverview()
                            syncManager.pullRemoteData()
                            ArchiveToastManager.show("已变卖归档并入账")
                        }
                        sellingGearItem = null
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "变卖后设备将标记为已退役，回血金额将自动生成一笔二手收入流水：",
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                    PaperTextField(
                        value = soldPriceYuan,
                        onValueChange = { soldPriceYuan = it },
                        label = "成交出清金额 (元)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = sellNote,
                        onValueChange = { sellNote = it },
                        label = "退役备忘",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 5. 信贷渠道管理对话框
        if (showCreditChannelsDialog) {
            CreditChannelsManagerDialog(
                creditChannels = creditChannels,
                apiClient = apiClient,
                onDismiss = { showCreditChannelsDialog = false },
                onChannelsChanged = {
                    scope.launch { reloadCategoriesAndAssets() }
                }
            )
        }

        // 6. 分类字典管理对话框
        if (showCategoriesManagerDialog) {
            CategoriesManagerDialog(
                apiClient = apiClient,
                onDismiss = { showCategoriesManagerDialog = false },
                onCategoriesChanged = {
                    scope.launch { reloadCategoriesAndAssets() }
                }
            )
        }
    }
}
}

/**
 * 账单明细选项卡：月份切换、月度指标看板、分类光谱比例条、流水明细卡片
 */
@Composable
private fun TransactionsLedgerTab(
    transactions: List<Transaction>,
    isPrivacyMasked: Boolean,
    selectedYearMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    // 过滤属于选中年月的明细
    val monthTransactions = remember(transactions, selectedYearMonth) {
        val targetPrefix = String.format("%04d-%02d", selectedYearMonth.year, selectedYearMonth.monthValue)
        val targetDotPrefix = String.format("%04d.%02d", selectedYearMonth.year, selectedYearMonth.monthValue)
        transactions.filter { tx ->
            tx.occurredAt.startsWith(targetPrefix) || tx.occurredAt.startsWith(targetDotPrefix)
        }
    }

    val totalExpense = remember(monthTransactions) {
        monthTransactions.filter { it.type == "expense" }.sumOf { it.amount }
    }
    val totalIncome = remember(monthTransactions) {
        monthTransactions.filter { it.type == "income" }.sumOf { it.amount }
    }
    val monthBalance = totalIncome - totalExpense

    // 分类支出占比
    val categoryBreakdown = remember(monthTransactions) {
        monthTransactions.filter { it.type == "expense" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
    ) {
        // 月份选择控制行
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onMonthChange(selectedYearMonth.minusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", tint = colors.inkPrimary)
                }

                Text(
                    text = "${selectedYearMonth.year} 年 ${selectedYearMonth.monthValue} 月",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = colors.inkPrimary
                )

                IconButton(onClick = { onMonthChange(selectedYearMonth.plusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月", tint = colors.inkPrimary)
                }
            }
        }

        // 月度汇总统计看板卡片
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("本月总支出", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(totalExpense),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = colors.accentWarm
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("本月总收入", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(totalIncome),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = colors.accentMoss
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("月收支结余", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(monthBalance),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = if (monthBalance >= 0) colors.inkPrimary else colors.accentWarm
                            )
                        }
                    }

                    // 分类支出光谱条 (Spectrum Bar)
                    if (totalExpense > 0 && categoryBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // 多彩比例横条
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            categoryBreakdown.forEach { (cat, amt) ->
                                val weight = (amt.toFloat() / totalExpense.toFloat()).coerceAtLeast(0.01f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(SpectrumColors.colorForCategory(cat))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 前 4 大类目图例
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryBreakdown.take(5).forEach { (cat, amt) ->
                                val pct = (amt * 100.0 / totalExpense).roundToLong()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(SpectrumColors.colorForCategory(cat))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$cat $pct%",
                                        fontSize = 10.sp,
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        color = colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 记账明细列表
        if (monthTransactions.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Text(
                        text = "本月暂无记账明细",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        color = colors.inkMuted
                    )
                }
            }
        } else {
            items(monthTransactions, key = { it.id }) { tx ->
                val isIncome = tx.type == "income"
                val sign = if (isIncome) "+" else "-"
                val displayAmount = if (isPrivacyMasked) "****" else "$sign${FormatUtils.centsToYuanString(tx.amount)}"
                val spectrum = SpectrumColors.colorForCategory(tx.category)

                PaperCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(tx) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(spectrum)
                                    )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tx.title,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = colors.inkPrimary
                                )
                                if (tx.syncStatus != "SYNCED") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentWarm)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = listOfNotNull(
                                    FormatUtils.formatDisplayDateTime(tx.occurredAt),
                                    tx.category,
                                    tx.paymentMethod
                                ).joinToString(" · "),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayAmount,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (isIncome) colors.accentMoss else colors.accentWarm
                            )

                            IconButton(
                                onClick = { onDelete(tx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(16.dp)
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
 * 资产大盘选项卡：净资产卡片、资金底数校准、信贷负债大盘、在役装备库存
 */
@Composable
private fun AssetsAndGearTab(
    overview: AssetsOverview?,
    isLoading: Boolean,
    isPrivacyMasked: Boolean,
    onCalibrateBalanceClick: () -> Unit,
    onAddGearClick: () -> Unit,
    onSellGearClick: (GearItem) -> Unit,
    onManageChannelsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (isLoading && overview == null) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
        }
        return
    }

    val ov = overview ?: AssetsOverview()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
    ) {
        // 1. 核心净资产透视卡片
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "个人核心资产大盘",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkMuted
                        )
                        TextButton(onClick = onCalibrateBalanceClick) {
                            Text("校准资金底数", fontSize = 11.sp, color = colors.accentWarm)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.netWorth),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("流动可用资金", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.liquidBalance),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.accentMoss
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("在役固定资产", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.fixedAssets),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("待还信贷负债", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.totalLiabilities),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.accentWarm
                            )
                        }
                    }
                }
            }
        }

        // 2. 信贷渠道与分期还款负债
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "信贷渠道与分期负债",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = onManageChannelsClick,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("管理渠道", fontSize = 11.5.sp, color = colors.accentWarm)
                            }
                        }
                        if (ov.monthlyLiabilities > 0) {
                            Text(
                                text = "月供 ${FormatUtils.centsToYuanString(ov.monthlyLiabilities)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (ov.channelLiabilities.isEmpty() || ov.channelLiabilities.all { it.totalDebt <= 0 }) {
                        Text(
                            text = "暂无未清还信贷分期，资金健康。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        ov.channelLiabilities.filter { it.totalDebt > 0 }.forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = ch.channelName,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "每月 ${ch.repaymentDay} 号还款 (距还款日 ${ch.daysUntilRepayment} 天)",
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FormatUtils.centsToYuanString(ch.totalDebt),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 13.sp,
                                        color = colors.accentWarm
                                    )
                                    Text(
                                        text = "月供 ${FormatUtils.centsToYuanString(ch.monthlyPay)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 在役固资与随身装备库
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "在役固资装备 (${ov.gearSummary.items.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        TextButton(onClick = onAddGearClick) {
                            Text("+ 登记新装备", fontSize = 11.sp, color = colors.accentWarm)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (ov.gearSummary.items.isEmpty()) {
                        Text(
                            text = "暂无录入固资装备，可点击右上角登记数码相机、工作台等资产。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        ov.gearSummary.items.forEach { gear ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onSellGearClick(gear) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = gear.name,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            gear.category,
                                            gear.spec.takeIf { it.isNotBlank() },
                                            gear.purchaseDate
                                        ).joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = gear.priceCents?.let { FormatUtils.centsToYuanString(it) } ?: "—",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "出清",
                                        fontSize = 10.sp,
                                        color = colors.accentWarm,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.accentWarm.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(12.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) colors.accentWarm else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            color = if (isSelected) colors.archivePaper else colors.inkSecondary
        )
    }
}

@Composable
private fun CreditChannelsManagerDialog(
    creditChannels: List<CreditChannel>,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onChannelsChanged: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var newChannelName by remember { mutableStateOf("") }
    var newChannelDay by remember { mutableStateOf("10") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.archivePaper)
                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "信贷分期渠道管理",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 新增渠道行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaperTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        placeholder = "渠道名称 (如: 招行信用卡)",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    PaperTextField(
                        value = newChannelDay,
                        onValueChange = { newChannelDay = it.filter { ch -> ch.isDigit() } },
                        placeholder = "还款日",
                        singleLine = true,
                        modifier = Modifier.width(60.dp)
                    )
                    Button(
                        onClick = {
                            if (newChannelName.isNotBlank() && !isSubmitting) {
                                isSubmitting = true
                                scope.launch {
                                    runCatching {
                                        val day = newChannelDay.toIntOrNull()?.coerceIn(1, 31) ?: 10
                                        apiClient.createCreditChannel(
                                            CreateCreditChannelRequest(
                                                name = newChannelName.trim(),
                                                repaymentDay = day
                                            )
                                        )
                                        newChannelName = ""
                                        onChannelsChanged()
                                    }
                                    isSubmitting = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                    ) {
                        Text("添加", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(creditChannels, key = { it.id }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = ch.name,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "每月 ${ch.repaymentDay} 日还款${if (ch.isBuiltin) " · 系统内置" else ""}",
                                    fontSize = 10.5.sp,
                                    color = colors.inkMuted
                                )
                            }
                            if (!ch.isBuiltin) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                apiClient.deleteCreditChannel(ch.id)
                                                onChannelsChanged()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除渠道", tint = colors.accentWarm, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesManagerDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onCategoriesChanged: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var selectedScope by remember { mutableStateOf("expense") }
    var categories by remember { mutableStateOf<List<CategoryOption>>(emptyList()) }
    var newCategoryName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadCats() {
        isLoading = true
        runCatching {
            categories = apiClient.fetchCategories(selectedScope)
        }
        isLoading = false
    }

    LaunchedEffect(selectedScope) {
        loadCats()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.archivePaper)
                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分类字典管理",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scope 切换
                val scopes = listOf(
                    "expense" to "支出",
                    "income" to "收入",
                    "payment" to "支付方式",
                    "gear" to "固定资产",
                    "project" to "造物"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    scopes.forEach { (sc, label) ->
                        PaperFilterPill(
                            selected = selectedScope == sc,
                            onClick = { selectedScope = sc },
                            label = label
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 新增输入行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaperTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = "新分类名称…",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                scope.launch {
                                    runCatching {
                                        apiClient.addCategoryOption(selectedScope, newCategoryName.trim())
                                        newCategoryName = ""
                                        loadCats()
                                        onCategoriesChanged()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                    ) {
                        Text("新增", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider)
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Text("加载中…", fontSize = 12.sp, color = colors.inkMuted)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name + if (cat.isBuiltin) " (系统)" else "",
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                                if (!cat.isBuiltin) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    apiClient.deleteCategoryOption(cat.id)
                                                    loadCats()
                                                    onCategoriesChanged()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "删除分类", tint = colors.accentWarm, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
