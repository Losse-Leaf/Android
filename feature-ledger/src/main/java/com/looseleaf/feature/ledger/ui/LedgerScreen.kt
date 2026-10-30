package com.looseleaf.feature.ledger.ui

import com.looseleaf.app.ui.components.ArchiveDatePickerSheet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.data.MonthlyTotalsRow
import com.looseleaf.feature.ledger.data.monthRangeOf
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerFormat
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 账本独立模块主界面 (LedgerScreen)
 *
 * 遵循散页统一视觉设计系统：
 * - 纯纸色底，无投影、无卡片
 * - 衬线大标题 + 等宽数字
 * - 赤陶色作为主要交互强调色
 * - 顶部排布：顶栏 -> 月份 / 截至今天 -> 纯文字视图切换 -> 错误条 -> 列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    apiClient: APIClient = APIClient.getInstance(LocalContext.current),
    credentialStore: CredentialStore = CredentialStore.getInstance(LocalContext.current),
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showKeepAliveScreen by rememberSaveable { mutableStateOf(false) }

    if (showKeepAliveScreen) {
        LedgerKeepAliveScreen(
            onNavigateBack = { showKeepAliveScreen = false },
            modifier = modifier
        )
        return
    }

    val ledgerSnapshot by LedgerRepository.snapshot.collectAsStateWithLifecycle()
    val expenseCategories = ledgerSnapshot.expenseCategories
    val incomeCategories = ledgerSnapshot.incomeCategories
    val paymentMethods = ledgerSnapshot.paymentMethods
    val creditChannels = ledgerSnapshot.creditChannels
    val assetsOverview = ledgerSnapshot.assetsOverview
    val isLoadingAssets = ledgerSnapshot.isLoadingAssets

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showQuickRecordSheet by rememberSaveable { mutableStateOf(false) }
    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMonthPickerSheet by remember { mutableStateOf(false) }
    var showPendingConfirmSheet by remember { mutableStateOf(false) }

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showCalibrateBalanceDialog by remember { mutableStateOf(false) }
    var showAddGearDialog by remember { mutableStateOf(false) }
    var viewingGearItem by remember { mutableStateOf<GearItem?>(null) }
    var sellingGearItem by remember { mutableStateOf<GearItem?>(null) }
    var showCreditChannelsDialog by remember { mutableStateOf(false) }
    var showCategoriesManagerDialog by remember { mutableStateOf(false) }

    // 真实分期相关状态 (RC-140)
    var showNewInstallmentSheet by remember { mutableStateOf(false) }
    var viewingInstallmentPlanDetail by remember { mutableStateOf<InstallmentPlanDetailResponse?>(null) }
    var editingInstallmentPlanDetail by remember { mutableStateOf<InstallmentPlanDetailResponse?>(null) }
    var payingInstallmentItem by remember { mutableStateOf<Pair<InstallmentItem, String>?>(null) }
    var settlingInstallmentPlan by remember { mutableStateOf<Pair<InstallmentPlan, Long>?>(null) }
    var showUpcomingInstallmentsSheet by remember { mutableStateOf(false) }
    var showCancelInstallmentConfirm by remember { mutableStateOf<InstallmentPlan?>(null) }
    var showUnpayInstallmentConfirm by remember { mutableStateOf<InstallmentItem?>(null) }
    var showDeleteTxRepayConfirm by remember { mutableStateOf<Transaction?>(null) }

    var snackbarData by remember { mutableStateOf<LedgerSnackbarData?>(null) }

    // 5 秒自动消失
    LaunchedEffect(snackbarData) {
        if (snackbarData != null) {
            delay(5000)
            snackbarData = null
        }
    }

    var selectedYearMonth by remember {
        mutableStateOf(YearMonth.now(FormatUtils.BeijingZone))
    }

    val monthRange = remember(selectedYearMonth) { monthRangeOf(selectedYearMonth) }
    val transactions by remember(monthRange) {
        LedgerRepository.transactionsForMonth(monthRange)
    }.collectAsStateWithLifecycle(emptyList())

    val monthlyTotals by remember(monthRange) {
        LedgerRepository.monthlyTotalsFlow(monthRange)
    }.collectAsStateWithLifecycle(MonthlyTotalsRow())

    val monthlyCategoryTotals by remember(monthRange) {
        LedgerRepository.monthlyCategoryTotalsFlow(monthRange)
    }.collectAsStateWithLifecycle(emptyList())

    var ledgerLoadError by remember { mutableStateOf<String?>(null) }
    var isRefreshingLedger by remember { mutableStateOf(false) }

    val transactionsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val assetsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // 资产大盘不显示 FAB，账单明细滚动时收起
    val isFabVisible by remember {
        derivedStateOf {
            selectedTab == 0 && (transactionsListState.firstVisibleItemIndex == 0 || !transactionsListState.isScrollInProgress)
        }
    }

    fun refreshAllData() {
        if (isRefreshingLedger) return
        scope.launch {
            isRefreshingLedger = true
            val res = LedgerRepository.pullLedgerData()
            isRefreshingLedger = false
            if (res.isSuccess) {
                ledgerLoadError = null
            } else {
                val err = res.exceptionOrNull()?.message ?: "同步未完成"
                ledgerLoadError = err
                ArchiveToastManager.show(err, isError = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAllData()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                LedgerTopAppBar(
                    title = "账本",
                    actions = {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "更多操作",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(if (colors.isDark) Color(0xFF1F1D1B) else Color(0xFFFAF6EE))
                                    .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp)),
                                shape = RoundedCornerShape(4.dp),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isPrivacyMasked) "显示金额" else "隐藏金额",
                                            fontFamily = LedgerTheme.typography.serif,
                                            color = colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (isPrivacyMasked && credentialStore.isBiometricsEnabled) {
                                            val act = context as? FragmentActivity
                                            if (act != null && BiometricHelper.canAuthenticate(act)) {
                                                BiometricHelper.authenticate(
                                                    activity = act,
                                                    title = "验证身份",
                                                    subtitle = "使用生物识别解锁敏感收支数字",
                                                    onSuccess = { isPrivacyMasked = false },
                                                    onError = { msg -> ArchiveToastManager.show(msg, isError = true) }
                                                )
                                            } else {
                                                isPrivacyMasked = false
                                            }
                                        } else {
                                            isPrivacyMasked = !isPrivacyMasked
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "分类管理",
                                            fontFamily = LedgerTheme.typography.serif,
                                            color = colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showCategoriesManagerDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "自动记账与保活",
                                            fontFamily = LedgerTheme.typography.serif,
                                            color = colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showKeepAliveScreen = true
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                val fabOffset by animateDpAsState(
                    targetValue = if (snackbarData != null) (-56).dp else 0.dp,
                    label = "fab_offset"
                )
                AnimatedVisibility(
                    visible = isFabVisible,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = { showQuickRecordSheet = true },
                        containerColor = colors.accent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier
                            .offset(y = fabOffset)
                            .size(56.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = "记一笔")
                    }
                }
            },
            containerColor = colors.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 2. 月份 / 截至今天
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    if (selectedTab == 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showMonthPickerSheet = true }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${selectedYearMonth.year} 年 ${selectedYearMonth.monthValue} 月 ▾",
                                style = LedgerTheme.typography.subtitle,
                                fontFamily = LedgerTheme.typography.serif,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "截至今天",
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // 3. 视图切换（账单明细 / 资产大盘）
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LedgerSegmentedTabs(
                        tabs = listOf("账单明细", "资产大盘"),
                        selectedIndex = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // 错误提示条
                ledgerLoadError?.let { msg ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accent.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 原生下拉刷新包装器
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshingLedger,
                    onRefresh = { refreshAllData() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (selectedTab) {
                        0 -> {
                            TransactionsLedgerTab(
                                transactions = transactions,
                                monthlyTotals = monthlyTotals,
                                monthlyCategoryTotals = monthlyCategoryTotals,
                                isPrivacyMasked = isPrivacyMasked,
                                installmentSummary = ledgerSnapshot.installmentSummary,
                                onOpenUpcomingInstallments = { showUpcomingInstallmentsSheet = true },
                                selectedYearMonth = selectedYearMonth,
                                onMonthChange = { selectedYearMonth = it },
                                onOpenMonthPicker = { showMonthPickerSheet = true },
                                onEdit = { editingTransaction = it },
                                onDelete = { tx ->
                                    if (tx.source == "installment_repay") {
                                        showDeleteTxRepayConfirm = tx
                                    } else {
                                        scope.launch {
                                            val ticket = LedgerRepository.deleteTransaction(tx.id)
                                            if (ticket != null) {
                                                snackbarData = LedgerSnackbarData(
                                                    message = "已删除账目",
                                                    actionLabel = "撤销",
                                                    onActionClick = {
                                                        scope.launch {
                                                            LedgerRepository.undoDeleteTransaction(ticket)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                onNavigateToKeypad = { showQuickRecordSheet = true },
                                onNavigateToPending = { showPendingConfirmSheet = true },
                                listState = transactionsListState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        1 -> {
                            AssetsAndGearTab(
                                overview = assetsOverview,
                                isLoading = isLoadingAssets,
                                isPrivacyMasked = isPrivacyMasked,
                                installmentPlans = ledgerSnapshot.installmentPlans,
                                onCalibrateBalanceClick = { showCalibrateBalanceDialog = true },
                                onAddGearClick = { showAddGearDialog = true },
                                onSellGearClick = { viewingGearItem = it },
                                onManageChannelsClick = { showCreditChannelsDialog = true },
                                onNewInstallmentClick = { showNewInstallmentSheet = true },
                                onPlanClick = { planSummary ->
                                    scope.launch {
                                        runCatching {
                                            apiClient.fetchInstallmentPlanDetail(planSummary.id)
                                        }.onSuccess {
                                            viewingInstallmentPlanDetail = it
                                        }
                                    }
                                },
                                listState = assetsListState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 浮动在底部的 LedgerSnackbar（在底栏上方，停留 5 秒）
        AnimatedVisibility(
            visible = snackbarData != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 68.dp, start = 16.dp, end = 16.dp)
        ) {
            snackbarData?.let { data ->
                LedgerSnackbar(
                    message = data.message,
                    actionLabel = data.actionLabel,
                    onActionClick = {
                        val act = data.onActionClick
                        snackbarData = null
                        act?.invoke()
                    }
                )
            }
        }
    }

    // 月份选择弹层
    if (showMonthPickerSheet) {
        LedgerMonthPickerSheet(
            selectedYearMonth = selectedYearMonth,
            onYearMonthSelected = { selectedYearMonth = it },
            onDismissRequest = { showMonthPickerSheet = false }
        )
    }

    // 预留待确认列表占位弹层
    if (showPendingConfirmSheet) {
        val pendingItems = remember(transactions) { transactions.filter { it.syncStatus == "PENDING_CREATE" } }
        LedgerSheet(
            onDismissRequest = { showPendingConfirmSheet = false },
            title = "待确认记账",
            cancelText = "关闭"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (pendingItems.isEmpty()) {
                    Text(
                        text = "当前没有需要确认的记账项",
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Text(
                        text = "共 ${pendingItems.size} 条本地待同步/待放行记录：",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    pendingItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = LedgerTheme.typography.body,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textPrimary
                            )
                            Text(
                                text = LedgerFormat.formatMoney(item.amount),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.expense
                            )
                        }
                        LedgerDivider()
                    }
                }
            }
        }
    }

    // 极速记账工作抽屉
    if (showQuickRecordSheet) {
        QuickRecordSheet(
            onDismiss = { showQuickRecordSheet = false },
            apiClient = apiClient,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            paymentMethods = paymentMethods,
            creditChannels = creditChannels,
            onTransactionCreated = {
                showQuickRecordSheet = false
                refreshAllData()
            }
        )
    }

    // 编辑流水抽屉
    editingTransaction?.let { tx ->
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
        var txOccurredAtMillis by remember(tx.id) {
            mutableStateOf(initialOccurredAt)
        }
        val txOccurredAt = remember(txOccurredAtMillis) {
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(txOccurredAtMillis),
                FormatUtils.BeijingZone
            )
        }

        var isCategoriesExpanded by remember { mutableStateOf(false) }
        var isPaymentMethodsExpanded by remember { mutableStateOf(false) }

        val dateStr = remember(txOccurredAt) {
            String.format(Locale.US, "%04d-%02d-%02d", txOccurredAt.year, txOccurredAt.monthValue, txOccurredAt.dayOfMonth)
        }
        val timeStr = remember(txOccurredAt) {
            String.format(Locale.US, "%02d:%02d", txOccurredAt.hour, txOccurredAt.minute)
        }

        LedgerSheet(
            onDismissRequest = { editingTransaction = null },
            title = "编辑账目",
            actionText = "保存修改",
            onAction = {
                val cents = if (isInstallmentRepay) tx.amount else FormatUtils.yuanStringToCents(editAmount)
                if (cents > 0) {
                    scope.launch {
                        val req = CreateTransactionRequest(
                            amount = cents,
                            type = editType,
                            category = editCategory,
                            title = editTitle.trim().ifBlank { editCategory },
                            note = editNote.trim().ifBlank { null },
                            paymentMethod = editPaymentMethod,
                            occurredAt = FormatUtils.toBeijingIsoString(txOccurredAt),
                            cityCode = tx.cityCode,
                            isInstallment = tx.isInstallment,
                            installmentChannelCode = tx.installmentChannelCode,
                            installmentTotalAmount = tx.installmentTotalAmount,
                            installmentMonths = tx.installmentMonths,
                            installmentMonthlyPay = tx.installmentMonthlyPay,
                            installmentRemainMonths = tx.installmentRemainMonths,
                            repaymentDay = tx.repaymentDay
                        )
                        val success = LedgerRepository.updateTransactionOptimistic(tx.id, req)
                        if (success) {
                            refreshAllData()
                        } else {
                            ArchiveToastManager.show("保存失败，记录不存在", isError = true)
                        }
                        editingTransaction = null
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 支出 / 收入 切换
                if (isInstallmentRepay) {
                    Text(
                        text = "类型：支出（分期还款）",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TypeToggleButton(
                            text = "支出",
                            isSelected = editType == "expense",
                            isExpense = true,
                            onClick = {
                                editType = "expense"
                                if (editCategory !in expenseCategories) {
                                    editCategory = expenseCategories.firstOrNull() ?: "餐饮"
                                }
                            }
                        )
                        TypeToggleButton(
                            text = "收入",
                            isSelected = editType == "income",
                            isExpense = false,
                            onClick = {
                                editType = "income"
                                if (editCategory !in incomeCategories) {
                                    editCategory = incomeCategories.firstOrNull() ?: "薪资"
                                }
                            }
                        )
                    }
                }

                // 金额底线输入框
                if (isInstallmentRepay) {
                    LedgerField(
                        value = editAmount,
                        onValueChange = {},
                        label = "金额 (元)",
                        isNumeric = true,
                        readOnly = true,
                        singleLine = true
                    )
                    Text(
                        text = "分期还款金额由还款期次决定，不可直接修改",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary
                    )
                } else {
                    LedgerField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = "金额 (元)",
                        isNumeric = true,
                        singleLine = true
                    )
                }

                // 标题 / 商户
                LedgerField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = "标题 / 商户",
                    singleLine = true
                )

                // 备注
                LedgerField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = "备注",
                    singleLine = true
                )

                // 分类：自动换行网格，始终可见；超过 7 项显示 7 项 + "更多 ▾"
                Text(
                    text = "分类",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary
                )
                val visibleCategories = if (editCategories.size > 7 && !isCategoriesExpanded) {
                    editCategories.take(7)
                } else {
                    editCategories
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (cat in visibleCategories) {
                        val dotColor = LedgerCategoryColors.colorForCategory(cat, colors.isDark)
                        LedgerChoiceItem(
                            text = cat,
                            isSelected = cat == editCategory,
                            colorDot = dotColor,
                            onClick = { editCategory = cat }
                        )
                    }
                    if (editCategories.size > 7) {
                        LedgerTextButton(
                            text = if (isCategoriesExpanded) "收起 ▴" else "更多 ▾",
                            onClick = { isCategoriesExpanded = !isCategoriesExpanded }
                        )
                    }
                }

                // 账户 / 渠道：自动换行网格，始终可见；超过 7 项显示 7 项 + "更多 ▾"
                Text(
                    text = "账户 / 渠道",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textSecondary
                )
                val visiblePaymentMethods = if (paymentMethods.size > 7 && !isPaymentMethodsExpanded) {
                    paymentMethods.take(7)
                } else {
                    paymentMethods
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (pm in visiblePaymentMethods) {
                        LedgerChoiceItem(
                            text = pm,
                            isSelected = pm == editPaymentMethod,
                            onClick = { editPaymentMethod = pm }
                        )
                    }
                    if (paymentMethods.size > 7) {
                        LedgerTextButton(
                            text = if (isPaymentMethodsExpanded) "收起 ▴" else "更多 ▾",
                            onClick = { isPaymentMethodsExpanded = !isPaymentMethodsExpanded }
                        )
                    }
                }

                // 日期与时间：底线式 LedgerDateTimeSplitRow
                LedgerDateTimeSplitRow(
                    selectedDateTime = txOccurredAt,
                    onDateTimeSelected = { newDt ->
                        txOccurredAtMillis = newDt.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli()
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                LedgerDivider()

                // 底部"删除此账目"文字按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    LedgerTextButton(
                        text = "删除此账目",
                        isDestructive = true,
                        onClick = {
                            val target = tx
                            editingTransaction = null
                            if (target.source == "installment_repay") {
                                showDeleteTxRepayConfirm = target
                            } else {
                                scope.launch {
                                    val ticket = LedgerRepository.deleteTransaction(target.id)
                                    if (ticket != null) {
                                        snackbarData = LedgerSnackbarData(
                                            message = "已删除账目",
                                            actionLabel = "撤销",
                                            onActionClick = {
                                                scope.launch {
                                                    LedgerRepository.undoDeleteTransaction(ticket)
                                                }
                                            }
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

    // 校准余额抽屉 (阶段 2 & 第四轮规范)
    if (showCalibrateBalanceDialog) {
        val currentLiquidCents = assetsOverview?.liquidBalance ?: 0L
        var inputLiquidYuan by remember { mutableStateOf("") }

        fun parseYuanToCents(str: String): Long {
            return if (str.contains("+")) {
                val sumYuan = str.split("+").mapNotNull { it.trim().toDoubleOrNull() }.sum()
                (sumYuan * 100).toLong()
            } else {
                FormatUtils.yuanStringToCents(str)
            }
        }

        val isCalibrateEnabled = inputLiquidYuan.isNotBlank()
        val doCalibrate = {
            if (isCalibrateEnabled) {
                val cents = parseYuanToCents(inputLiquidYuan)
                scope.launch {
                    runCatching {
                        apiClient.updateLiquidBalance(cents)
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                    }.onFailure { err ->
                        ArchiveToastManager.show("校准失败: ${err.message}", isError = true)
                    }
                    showCalibrateBalanceDialog = false
                }
            }
        }

        LedgerSheet(
            onDismissRequest = { showCalibrateBalanceDialog = false },
            title = "校准余额",
            actionText = "确认校准",
            isActionEnabled = isCalibrateEnabled,
            onAction = doCalibrate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 首行显示系统推算金额
                Text(
                    text = "系统当前推算 ${LedgerFormat.formatMoney(currentLiquidCents)}",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )

                // 大号等宽金额输入显示
                Text(
                    text = if (inputLiquidYuan.isEmpty()) "¥ —" else "¥ $inputLiquidYuan",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = if (inputLiquidYuan.isEmpty()) colors.textMuted else colors.textPrimary
                )

                // 实时调整差额
                if (inputLiquidYuan.isNotEmpty()) {
                    val inputCents = parseYuanToCents(inputLiquidYuan)
                    val diffCents = inputCents - currentLiquidCents
                    val diffSign = if (diffCents >= 0) "+" else "−"
                    Text(
                        text = "校准后将调整 $diffSign${LedgerFormat.formatMoney(kotlin.math.abs(diffCents))}",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }

                // 缩短说明文案（去中枢）
                Text(
                    text = "输入流动资金真实总额，系统将以此为基数推算净资产大盘。",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 常驻自定义共享四列等宽数字键盘（去框）
                TactileKeypad(
                    onDigitClick = { digit ->
                        val lastSegment = inputLiquidYuan.substringAfterLast("+")
                        val parts = lastSegment.split(".")
                        if (parts.size == 2 && parts[1].length >= 2) {
                            // 最多两位小数
                        } else if (lastSegment.length < 10) {
                            inputLiquidYuan += digit
                        }
                    },
                    onDotClick = {
                        val lastSegment = inputLiquidYuan.substringAfterLast("+")
                        if (!lastSegment.contains(".")) {
                            inputLiquidYuan = if (lastSegment.isEmpty()) "${inputLiquidYuan}0." else "$inputLiquidYuan."
                        }
                    },
                    onBackspaceClick = {
                        if (inputLiquidYuan.isNotEmpty()) {
                            inputLiquidYuan = inputLiquidYuan.dropLast(1)
                        }
                    },
                    onPlusClick = {
                        if (inputLiquidYuan.isNotEmpty() && !inputLiquidYuan.endsWith("+")) {
                            inputLiquidYuan += "+"
                        }
                    },
                    onClearAll = {
                        inputLiquidYuan = ""
                    },
                    confirmText = "确认校准",
                    isConfirmEnabled = isCalibrateEnabled,
                    onConfirm = doCalibrate,
                    showPlus = true
                )
            }
        }
    }

    // 登记在役新装备
    if (showAddGearDialog) {
        var gearName by remember { mutableStateOf("") }
        var gearSpec by remember { mutableStateOf("") }
        var gearPrice by remember { mutableStateOf("") }
        var gearCategory by remember { mutableStateOf("数码器材") }
        var isAutoTx by remember { mutableStateOf(true) }
        var payMethodChoice by remember { mutableStateOf("full") } // "full" | "installment"

        // 分期展开参数 (RC-140)
        var selectedPeriodPreset by remember { mutableStateOf("12") }
        var customPeriodText by remember { mutableStateOf("") }
        val effectivePeriods = when (selectedPeriodPreset) {
            "3" -> 3
            "6" -> 6
            "12" -> 12
            "24" -> 24
            else -> customPeriodText.toIntOrNull() ?: 12
        }
        var selectedFeeMode by remember { mutableStateOf("none") }
        var feeValueInput by remember { mutableStateOf("") }
        val defaultChannel = creditChannels.firstOrNull { it.code == "huabei" } ?: creditChannels.firstOrNull()
        var selectedChannel by remember { mutableStateOf(defaultChannel) }
        var repayDay by remember { mutableStateOf(defaultChannel?.repaymentDay ?: 10) }

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
        var showGearFirstDueDatePicker by remember { mutableStateOf(false) }

        val canSubmit = gearName.isNotBlank() && gearPrice.isNotBlank() && (FormatUtils.yuanStringToCents(gearPrice) > 0)
        val doSubmitGear: () -> Unit = {
            if (canSubmit) {
                val cents = FormatUtils.yuanStringToCents(gearPrice)
                scope.launch {
                    runCatching {
                        if (payMethodChoice == "installment") {
                            val feeValCents = if (selectedFeeMode == "rate_per_period") {
                                val ratePercent = feeValueInput.toDoubleOrNull() ?: 0.0
                                (ratePercent * 100).toLong()
                            } else {
                                FormatUtils.yuanStringToCents(feeValueInput)
                            }
                            apiClient.createInstallmentPlan(
                                CreateInstallmentPlanRequest(
                                    channelId = selectedChannel?.id,
                                    channelCode = selectedChannel?.code,
                                    title = gearName.trim(),
                                    kind = "asset",
                                    principal = cents,
                                    periods = effectivePeriods,
                                    feeMode = selectedFeeMode,
                                    feeValue = feeValCents,
                                    firstDueDate = firstDueDate,
                                    repayDay = repayDay,
                                    createNewGear = true,
                                    gearName = gearName.trim(),
                                    gearSpec = gearSpec.trim(),
                                    gearCategory = gearCategory
                                )
                            )
                        } else {
                            apiClient.addGearAsset(
                                AddGearAssetRequest(
                                    name = gearName.trim(),
                                    spec = gearSpec.trim(),
                                    priceCents = cents,
                                    category = gearCategory,
                                    createTransaction = isAutoTx && cents > 0
                                )
                            )
                        }
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                    }.onFailure { err ->
                        ArchiveToastManager.show("录入装备失败: ${err.message}", isError = true)
                    }
                    showAddGearDialog = false
                }
            }
        }

        LedgerSheet(
            onDismissRequest = { showAddGearDialog = false },
            title = "录入新装备",
            actionText = "确认录入",
            isActionEnabled = canSubmit,
            onAction = doSubmitGear
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LedgerField(
                    value = gearName,
                    onValueChange = { gearName = it },
                    label = "装备名称",
                    placeholder = "如 MacBook Pro",
                    singleLine = true
                )

                LedgerField(
                    value = gearSpec,
                    onValueChange = { gearSpec = it },
                    label = "规格参数",
                    placeholder = "如 M3 Max 64G",
                    singleLine = true
                )

                LedgerField(
                    value = gearPrice,
                    onValueChange = {},
                    label = "买入价格",
                    placeholder = "0.00",
                    isNumeric = true,
                    readOnly = true,
                    trailingText = "元",
                    singleLine = true
                )

                // 付款方式：一次付清 / 分期
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "付款方式",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary
                    )
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
                }

                if (payMethodChoice == "full") {
                    // 开关与说明（一次付清）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isAutoTx = !isAutoTx }
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
                                text = "同步生成一笔支出流水",
                                style = LedgerTheme.typography.body,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val priceDisplay = if (gearPrice.isNotBlank()) gearPrice else "0.00"
                            Text(
                                text = if (isAutoTx) {
                                    "开启后将从流动资金扣除 ¥$priceDisplay，并计入当月支出"
                                } else {
                                    "关闭则仅增加固定资产，不扣减流动资金与月支出"
                                },
                                style = LedgerTheme.typography.caption,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = isAutoTx,
                            onCheckedChange = { isAutoTx = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.surfaceElevated
                            )
                        )
                    }
                } else {
                    // 分期付款展开字段
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

                    // 手续费模式
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

                    // 还款渠道
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "信贷渠道",
                            style = LedgerTheme.typography.caption,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            creditChannels.forEach { ch ->
                                LedgerChoiceItem(
                                    text = ch.name,
                                    isSelected = selectedChannel?.id == ch.id,
                                    onClick = {
                                        selectedChannel = ch
                                        repayDay = ch.repaymentDay
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 首期到期日
                    LedgerField(
                        value = firstDueDate,
                        onValueChange = {},
                        label = "首期到期日",
                        placeholder = "YYYY-MM-DD",
                        readOnly = true,
                        singleLine = true,
                        trailingText = "每月${repayDay}日",
                        onClick = {
                            showGearFirstDueDatePicker = true
                        }
                    )

                    // 实时试算预览
                    val pCents = FormatUtils.yuanStringToCents(gearPrice)
                    val fValCents = if (selectedFeeMode == "rate_per_period") {
                        val ratePercent = feeValueInput.toDoubleOrNull() ?: 0.0
                        (ratePercent * 100).toLong()
                    } else {
                        FormatUtils.yuanStringToCents(feeValueInput)
                    }
                    val totalFCents = when (selectedFeeMode) {
                        "none" -> 0L
                        "fixed_per_period" -> fValCents * effectivePeriods
                        "rate_per_period" -> ((pCents * fValCents + 5000) / 10000) * effectivePeriods
                        "total_fee" -> fValCents
                        else -> 0L
                    }
                    val avgFCents = if (effectivePeriods > 0) totalFCents / effectivePeriods else 0L
                    val approxMo = if (effectivePeriods > 0) pCents / effectivePeriods + avgFCents else 0L
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
                            text = "每期约 ${LedgerFormat.formatMoney(approxMo)}（含手续费 ${LedgerFormat.formatMoney(avgFCents)}），共 $effectivePeriods 期，总手续费 ${LedgerFormat.formatMoney(totalFCents)}，末期 $endYm",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 买入价格共享等宽去框四列数字键盘
                TactileKeypad(
                    onDigitClick = { digit ->
                        val parts = gearPrice.split(".")
                        if (parts.size == 2 && parts[1].length >= 2) {
                            // 最多两位小数
                        } else if (gearPrice.length < 10) {
                            gearPrice += digit
                        }
                    },
                    onDotClick = {
                        if (!gearPrice.contains(".")) {
                            gearPrice = if (gearPrice.isEmpty()) "0." else "$gearPrice."
                        }
                    },
                    onBackspaceClick = {
                        if (gearPrice.isNotEmpty()) {
                            gearPrice = gearPrice.dropLast(1)
                        }
                    },
                    onClearAll = {
                        gearPrice = ""
                    },
                    confirmText = "确认录入",
                    isConfirmEnabled = canSubmit,
                    onConfirm = doSubmitGear,
                    showPlus = false,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (showGearFirstDueDatePicker) {
            val initialDate = runCatching { LocalDate.parse(firstDueDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(LocalDate.now())
            ArchiveDatePickerSheet(
                selectedDate = initialDate,
                onDateSelected = { newDate ->
                    firstDueDate = String.format(Locale.US, "%04d-%02d-%02d", newDate.year, newDate.monthValue, newDate.dayOfMonth)
                    repayDay = newDate.dayOfMonth
                },
                onDismissRequest = { showGearFirstDueDatePicker = false }
            )
        }
    }

    // 固资装备详情抽屉
    viewingGearItem?.let { gear ->
        LedgerSheet(
            onDismissRequest = { viewingGearItem = null },
            title = "装备详情",
            cancelText = "关闭"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = gear.name,
                        style = LedgerTheme.typography.title,
                        fontFamily = LedgerTheme.typography.serif,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "分类: ${gear.category}" + (gear.spec?.takeIf { it.isNotBlank() }?.let { " · 规格: $it" } ?: ""),
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                    if (!gear.purchaseDate.isNullOrBlank()) {
                        Text(
                            text = "购入日期: ${gear.purchaseDate}",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "买入价格: ${gear.priceCents?.let { LedgerFormat.formatMoney(it) } ?: "—"}",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                LedgerDivider()

                LedgerTextButton(
                    text = "退役 / 出二手",
                    isDestructive = false,
                    onClick = {
                        val target = gear
                        viewingGearItem = null
                        sellingGearItem = target
                    }
                )
            }
        }
    }

    // 变卖装备抽屉
    sellingGearItem?.let { gear ->
        var soldPriceYuan by remember(gear.id) {
            mutableStateOf(FormatUtils.centsToYuanString(gear.priceCents ?: 0L, includeSymbol = false))
        }
        var sellNote by remember(gear.id) { mutableStateOf("二手回血") }

        LedgerSheet(
            onDismissRequest = { sellingGearItem = null },
            title = "退役变卖 - ${gear.name}",
            actionText = "确认变卖回血",
            onAction = {
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
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                    }.onFailure { err ->
                        ArchiveToastManager.show("变卖失败: ${err.message}", isError = true)
                    }
                    sellingGearItem = null
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LedgerField(
                    value = soldPriceYuan,
                    onValueChange = { soldPriceYuan = it },
                    label = "二手成交价格 (元)",
                    isNumeric = true,
                    singleLine = true
                )
                LedgerField(
                    value = sellNote,
                    onValueChange = { sellNote = it },
                    label = "回血备注",
                    singleLine = true
                )
            }
        }
    }

    // 信贷渠道管理抽屉
    if (showCreditChannelsDialog) {
        CreditChannelsManagerDialog(
            creditChannels = creditChannels,
            apiClient = apiClient,
            onDismiss = { showCreditChannelsDialog = false },
            onChannelsChanged = { refreshAllData() },
            onShowSnackbar = { msg, act, cb ->
                snackbarData = LedgerSnackbarData(msg, act, cb)
            }
        )
    }

    // 分类管理抽屉
    if (showCategoriesManagerDialog) {
        CategoriesManagerDialog(
            apiClient = apiClient,
            onDismiss = { showCategoriesManagerDialog = false },
            onCategoriesChanged = { refreshAllData() },
            onShowSnackbar = { msg, act, cb ->
                snackbarData = LedgerSnackbarData(msg, act, cb)
            }
        )
    }

    // ───────────────────────────────────────────────
    // 真实分期体系弹层与确认对话框 (RC-140)
    // ───────────────────────────────────────────────

    // 1. 新建分期计划抽屉
    if (showNewInstallmentSheet) {
        CreateInstallmentSheet(
            creditChannels = creditChannels,
            existingGears = assetsOverview?.gearSummary?.items ?: emptyList(),
            expenseCategories = expenseCategories,
            onDismissRequest = { showNewInstallmentSheet = false },
            onSubmit = { req ->
                scope.launch {
                    runCatching {
                        apiClient.createInstallmentPlan(req)
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                        ArchiveToastManager.show("分期计划创建成功")
                    }.onFailure { err ->
                        ArchiveToastManager.show("创建分期失败: ${err.message}", isError = true)
                    }
                    showNewInstallmentSheet = false
                }
            }
        )
    }

    // 2. 分期计划详情抽屉 (全高)
    viewingInstallmentPlanDetail?.let { detail ->
        InstallmentPlanDetailSheet(
            detail = detail,
            onDismissRequest = { viewingInstallmentPlanDetail = null },
            onPayItemClick = { item ->
                payingInstallmentItem = item to detail.plan.title
            },
            onUnpayItemClick = { item ->
                showUnpayInstallmentConfirm = item
            },
            onSettleClick = {
                settlingInstallmentPlan = detail.plan to detail.remainingPrincipal
            },
            onCancelPlanClick = {
                showCancelInstallmentConfirm = detail.plan
            },
            onEditClick = {
                editingInstallmentPlanDetail = detail
            }
        )
    }

    // 2.5 编辑分期计划抽屉
    editingInstallmentPlanDetail?.let { detail ->
        EditInstallmentPlanSheet(
            detail = detail,
            onDismissRequest = { editingInstallmentPlanDetail = null },
            onSaveSuccess = { title, note, modifiedDates ->
                scope.launch {
                    runCatching {
                        if (title != detail.plan.title || note != (detail.plan.note ?: "")) {
                            apiClient.updateInstallmentPlan(detail.plan.id, mapOf("title" to title, "note" to note))
                        }
                        for ((itemId, newDueDate) in modifiedDates) {
                            apiClient.updateInstallmentItem(itemId, newDueDate)
                        }
                        val updated = apiClient.fetchInstallmentPlanDetail(detail.plan.id)
                        viewingInstallmentPlanDetail = updated
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                        ArchiveToastManager.show("分期计划已更新")
                    }.onFailure { err ->
                        ArchiveToastManager.show("更新失败: ${err.message}", isError = true)
                    }
                    editingInstallmentPlanDetail = null
                }
            }
        )
    }

    // 3. 标记单期已还抽屉
    payingInstallmentItem?.let { (item, planTitle) ->
        PayInstallmentSheet(
            item = item,
            planTitle = planTitle,
            paymentMethods = paymentMethods,
            onDismissRequest = { payingInstallmentItem = null },
            onConfirmPay = { amount, account, date ->
                scope.launch {
                    runCatching {
                        apiClient.payInstallmentItem(
                            id = item.id,
                            req = PayInstallmentItemRequest(paidAmount = amount, payAccount = account, paidAt = date)
                        )
                        val updated = apiClient.fetchInstallmentPlanDetail(item.planId)
                        viewingInstallmentPlanDetail = updated
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                        ArchiveToastManager.show("已标记第 ${item.seq} 期已还")
                    }.onFailure { err ->
                        ArchiveToastManager.show("标记还款失败: ${err.message}", isError = true)
                    }
                    payingInstallmentItem = null
                }
            }
        )
    }

    // 4. 提前结清抽屉
    settlingInstallmentPlan?.let { (plan, remainingPrincipal) ->
        SettleInstallmentSheet(
            plan = plan,
            remainingPrincipal = remainingPrincipal,
            paymentMethods = paymentMethods,
            onDismissRequest = { settlingInstallmentPlan = null },
            onConfirmSettle = { amount, account, date ->
                scope.launch {
                    runCatching {
                        apiClient.settleInstallmentPlan(
                            id = plan.id,
                            req = SettleInstallmentPlanRequest(paidAmount = amount, payAccount = account, paidAt = date)
                        )
                        val updated = apiClient.fetchInstallmentPlanDetail(plan.id)
                        viewingInstallmentPlanDetail = updated
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                        ArchiveToastManager.show("已提前结清")
                    }.onFailure { err ->
                        ArchiveToastManager.show("结清失败: ${err.message}", isError = true)
                    }
                    settlingInstallmentPlan = null
                }
            }
        )
    }

    // 5. 本月待还列表抽屉
    if (showUpcomingInstallmentsSheet) {
        UpcomingInstallmentsSheet(
            items = ledgerSnapshot.upcomingInstallmentItems,
            onDismissRequest = { showUpcomingInstallmentsSheet = false },
            onQuickPayItem = { item ->
                scope.launch {
                    runCatching {
                        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        apiClient.payInstallmentItem(
                            id = item.itemId,
                            req = PayInstallmentItemRequest(
                                paidAmount = item.totalPart,
                                payAccount = "银行卡",
                                paidAt = todayStr
                            )
                        )
                        LedgerRepository.refreshAssets(apiClient)
                        refreshAllData()
                        snackbarData = LedgerSnackbarData(
                            message = "已标记 ${item.planTitle} 第 ${item.seq} 期已还",
                            actionLabel = "撤销",
                            onActionClick = {
                                scope.launch {
                                    runCatching {
                                        apiClient.unpayInstallmentItem(item.itemId)
                                        LedgerRepository.refreshAssets(apiClient)
                                        refreshAllData()
                                    }
                                }
                            }
                        )
                    }.onFailure { err ->
                        ArchiveToastManager.show("还款失败: ${err.message}", isError = true)
                    }
                    showUpcomingInstallmentsSheet = false
                }
            }
        )
    }

    // 6. 二次确认弹窗：撤销已还记录
    showUnpayInstallmentConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { showUnpayInstallmentConfirm = null },
            title = {
                Text(
                    text = "撤销该期还款？",
                    style = LedgerTheme.typography.title,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "撤销后将恢复该期待还本金，冲销还款支出流水，并回滚负债大盘。",
                    style = LedgerTheme.typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                LedgerTextButton(
                    text = "确认撤销",
                    onClick = {
                        scope.launch {
                            runCatching {
                                apiClient.unpayInstallmentItem(item.id)
                                val updated = apiClient.fetchInstallmentPlanDetail(item.planId)
                                viewingInstallmentPlanDetail = updated
                                LedgerRepository.refreshAssets(apiClient)
                                refreshAllData()
                                ArchiveToastManager.show("已撤销还款")
                            }.onFailure { err ->
                                ArchiveToastManager.show("撤销失败: ${err.message}", isError = true)
                            }
                            showUnpayInstallmentConfirm = null
                        }
                    }
                )
            },
            dismissButton = {
                LedgerTextButton(
                    text = "取消",
                    onClick = { showUnpayInstallmentConfirm = null }
                )
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(4.dp)
        )
    }

    // 7. 二次确认弹窗：取消分期计划
    showCancelInstallmentConfirm?.let { plan ->
        AlertDialog(
            onDismissRequest = { showCancelInstallmentConfirm = null },
            title = {
                Text(
                    text = "取消分期计划？",
                    style = LedgerTheme.typography.title,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "取消后将彻底删除此分期计划及所有期次记录。",
                    style = LedgerTheme.typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                LedgerTextButton(
                    text = "确认取消",
                    onClick = {
                        scope.launch {
                            runCatching {
                                apiClient.deleteInstallmentPlan(plan.id)
                                viewingInstallmentPlanDetail = null
                                LedgerRepository.refreshAssets(apiClient)
                                refreshAllData()
                                ArchiveToastManager.show("已取消分期计划")
                            }.onFailure { err ->
                                ArchiveToastManager.show("取消失败: ${err.message}", isError = true)
                            }
                            showCancelInstallmentConfirm = null
                        }
                    }
                )
            },
            dismissButton = {
                LedgerTextButton(
                    text = "暂不取消",
                    onClick = { showCancelInstallmentConfirm = null }
                )
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(4.dp)
        )
    }

    // 8. 二次确认弹窗：删除分期还款账目联动撤销
    showDeleteTxRepayConfirm?.let { tx ->
        AlertDialog(
            onDismissRequest = { showDeleteTxRepayConfirm = null },
            title = {
                Text(
                    text = "删除此还款账目？",
                    style = LedgerTheme.typography.title,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "删除此分期还款账目将同时撤销对应的分期还款期次，并恢复待还负债本金。",
                    style = LedgerTheme.typography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                LedgerTextButton(
                    text = "确认删除",
                    onClick = {
                        scope.launch {
                            runCatching {
                                LedgerRepository.deleteTransaction(tx.id)
                                LedgerRepository.refreshAssets(apiClient)
                                refreshAllData()
                                ArchiveToastManager.show("已删除账目并撤销还款")
                            }.onFailure { err ->
                                ArchiveToastManager.show("删除失败: ${err.message}", isError = true)
                            }
                            showDeleteTxRepayConfirm = null
                        }
                    }
                )
            },
            dismissButton = {
                LedgerTextButton(
                    text = "取消",
                    onClick = { showDeleteTxRepayConfirm = null }
                )
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(4.dp)
        )
    }
}
