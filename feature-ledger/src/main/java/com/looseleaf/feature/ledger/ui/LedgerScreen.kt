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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.*
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import com.looseleaf.app.ui.components.ArchiveDatePickerField
import com.looseleaf.app.ui.components.ArchiveDatePickerSheet
import com.looseleaf.app.ui.components.ArchiveSelectableCapsule
import com.looseleaf.app.ui.components.PaperclipPullIndicator
import com.looseleaf.app.ui.components.formatGearCompanionDays
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
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
    var editingGearItem by remember { mutableStateOf<GearItem?>(null) }
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

    var isScrollingUp by remember { mutableStateOf(true) }
    var prevScrollIndex by remember { mutableIntStateOf(0) }
    var prevScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(transactionsListState) {
        snapshotFlow { transactionsListState.firstVisibleItemIndex to transactionsListState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                if (idx > prevScrollIndex || (idx == prevScrollIndex && off > prevScrollOffset + 12)) {
                    isScrollingUp = false
                } else if (idx < prevScrollIndex || (idx == prevScrollIndex && off < prevScrollOffset - 12)) {
                    isScrollingUp = true
                }
                prevScrollIndex = idx
                prevScrollOffset = off
            }
    }

    // 资产大盘与空账单状态均隐藏 FAB（空状态已自带实心主按钮），向下滚动时平滑收起
    val isFabVisible by remember {
        derivedStateOf {
            selectedTab == 0 &&
                transactions.isNotEmpty() &&
                (isScrollingUp || transactionsListState.firstVisibleItemIndex == 0)
        }
    }

    fun refreshAllData(force: Boolean = false) {
        if (isRefreshingLedger) return
        scope.launch {
            isRefreshingLedger = true
            val res = LedgerRepository.pullLedgerData(force = force)
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
        refreshAllData(force = false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                    .background(colors.surfaceElevated)
                                    .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small),
                                shape = ArchiveShapes.small,
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
                        contentColor = colors.background,
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
                // 2. 副标题区：固定高度 34dp，切换「账单明细 / 资产大盘」时绝不跳动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (selectedTab == 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ArchiveShapes.small)
                                .clickable(role = Role.Button) { showMonthPickerSheet = true }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${selectedYearMonth.year} 年 ${selectedYearMonth.monthValue} 月 ▾",
                                fontFamily = LedgerTheme.typography.serif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.bodyLarge,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = "截至今天",
                            fontFamily = LedgerTheme.typography.serif,
                            fontWeight = FontWeight.Normal,
                            fontSize = ArchiveTypeScale.body,
                            color = colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
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

                // 原生下拉刷新包装器（黄铜回形针指示器，未下拉时不显示任何异常底圈）
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshingLedger,
                    onRefresh = { refreshAllData(force = true) },
                    state = pullToRefreshState,
                    indicator = {
                        PaperclipPullIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshingLedger
                        )
                    },
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
                str.split("+")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .sumOf { FormatUtils.yuanStringToCents(it) }
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
                    }.onSuccess {
                        showCalibrateBalanceDialog = false
                    }.onFailure { err ->
                        ArchiveToastManager.show("校准失败: ${err.message}", isError = true)
                    }
                }
            }
        }

        LedgerSheet(
            onDismissRequest = { showCalibrateBalanceDialog = false },
            title = "校准余额",
            actionText = "保存",
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
                    fontSize = ArchiveTypeScale.body,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                LedgerField(
                    value = inputLiquidYuan,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            val parts = sanitized.split('.')
                            if (parts.size == 1 || parts[1].length <= 2) {
                                inputLiquidYuan = sanitized
                            }
                        }
                    },
                    label = "流动资金真实总额",
                    placeholder = "0.00",
                    trailingText = "元",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // 实时调整差额
                if (inputLiquidYuan.isNotEmpty()) {
                    val inputCents = parseYuanToCents(inputLiquidYuan)
                    val diffCents = inputCents - currentLiquidCents
                    val diffSign = if (diffCents >= 0) "+" else "−"
                    Text(
                        text = "校准后将调整 $diffSign${LedgerFormat.formatMoney(kotlin.math.abs(diffCents))}",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.body,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 说明文案
                Text(
                    text = "输入流动资金真实总额，系统将以此为基数推算净资产大盘。",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(16.dp))
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

        // 分期展开参数（无手续费，仅自建渠道，首期仅选月份）
        var periodsText by remember { mutableStateOf("12") }
        val effectivePeriods = periodsText.toIntOrNull() ?: 12

        val userChannels = remember(creditChannels) {
            creditChannels.filterNot { it.isBuiltin || it.code in setOf("HUABEI", "BAITIAO", "CUSTOM_INSTALLMENT", "huabei", "baitiao", "custom_installment") }
        }
        var selectedChannel by remember(userChannels) { mutableStateOf(userChannels.firstOrNull()) }
        var repayDay by remember(selectedChannel) { mutableStateOf(selectedChannel?.repaymentDay ?: 10) }
        var showGearCreateChannelDialog by remember { mutableStateOf(false) }
        var showGearChannelSelectDialog by remember { mutableStateOf(false) }

        var firstDueYearMonth by remember(selectedChannel) {
            val now = LocalDate.now()
            val targetDay = selectedChannel?.repaymentDay ?: 10
            val ym = if (now.dayOfMonth <= targetDay) YearMonth.from(now) else YearMonth.from(now).plusMonths(1)
            mutableStateOf(ym)
        }
        var showGearMonthPicker by remember { mutableStateOf(false) }

        var isSubmittingGear by remember { mutableStateOf(false) }
        val canSubmit = gearName.isNotBlank() && gearPrice.isNotBlank() && (FormatUtils.yuanStringToCents(gearPrice) > 0)
        val doSubmitGear: () -> Unit = {
            if (canSubmit && !isSubmittingGear) {
                val cents = FormatUtils.yuanStringToCents(gearPrice)
                isSubmittingGear = true
                scope.launch {
                    runCatching {
                        if (payMethodChoice == "installment") {
                            val targetDay = (selectedChannel?.repaymentDay ?: 10).coerceAtMost(firstDueYearMonth.lengthOfMonth())
                            val firstDueDateStr = String.format(Locale.US, "%04d-%02d-%02d", firstDueYearMonth.year, firstDueYearMonth.monthValue, targetDay)
                            apiClient.createInstallmentPlan(
                                CreateInstallmentPlanRequest(
                                    channelId = selectedChannel?.id,
                                    channelCode = selectedChannel?.code,
                                    title = gearName.trim(),
                                    kind = "asset",
                                    principal = cents,
                                    periods = effectivePeriods,
                                    feeMode = "none",
                                    feeValue = 0L,
                                    firstDueDate = firstDueDateStr,
                                    repayDay = selectedChannel?.repaymentDay ?: 10,
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
                    }.onSuccess {
                        isSubmittingGear = false
                        showAddGearDialog = false
                    }.onFailure { err ->
                        isSubmittingGear = false
                        ArchiveToastManager.show("录入装备失败: ${err.message}", isError = true)
                    }
                }
            }
        }

        LedgerSheet(
            onDismissRequest = { if (!isSubmittingGear) showAddGearDialog = false },
            title = "录入新装备",
            actionText = if (isSubmittingGear) "录入中…" else "录入",
            isActionEnabled = canSubmit && !isSubmittingGear,
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
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            val parts = sanitized.split('.')
                            if (parts.size == 1 || parts[1].length <= 2) {
                                gearPrice = sanitized
                            }
                        }
                    },
                    label = "买入价格",
                    placeholder = "0.00",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingText = "元",
                    singleLine = true
                )

                // 付款方式：一次付清 / 分期
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "付款方式",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isAutoTx,
                            onCheckedChange = { isAutoTx = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.surfaceElevated
                            )
                        )
                    }
                } else {
                    // 分期付款展开字段（精简合并，调用原生键盘）
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
                            modifier = Modifier.width(104.dp)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("3", "6", "12", "24", "36").forEach { p ->
                                ArchiveSelectableCapsule(
                                    text = "${p}期",
                                    selected = periodsText == p,
                                    onClick = { periodsText = p }
                                )
                            }
                        }
                    }

                    // 还款渠道 与 首期还款月份 并排单行双列
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 还款渠道列（仅自建，带 + 新建）
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
                                        .clickable { showGearCreateChannelDialog = true }
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
                                            showGearCreateChannelDialog = true
                                        } else {
                                            showGearChannelSelectDialog = true
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
                                    .clickable { showGearMonthPicker = true }
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

                    // 实时试算单行预览（常驻占位，不因金额输入跳动）
                    val pCents = FormatUtils.yuanStringToCents(gearPrice)
                    val approxMo = if (effectivePeriods > 0) pCents / effectivePeriods else 0L
                    val endYm = runCatching {
                        firstDueYearMonth.plusMonths((effectivePeriods - 1).toLong().coerceAtLeast(0L)).toString()
                    }.getOrDefault("----")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.small)
                            .background(colors.surfaceElevated)
                            .border(
                                ArchiveLines.hairline,
                                if (pCents > 0) colors.accent.copy(alpha = 0.45f) else colors.borderSubtle,
                                ArchiveShapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (pCents > 0) {
                                "每期约 ${LedgerFormat.formatMoney(approxMo)} · 共 $effectivePeriods 期 · 末期 $endYm"
                            } else {
                                "输入购入价格后将实时试算各期还款额"
                            },
                            fontFamily = if (pCents > 0) ArchiveFontFamilies.MonoScale else LedgerTheme.typography.serif,
                            fontSize = ArchiveTypeScale.caption,
                            fontWeight = if (pCents > 0) FontWeight.Medium else FontWeight.Normal,
                            color = if (pCents > 0) colors.accent else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (showGearMonthPicker) {
            LedgerMonthPickerSheet(
                selectedYearMonth = firstDueYearMonth,
                onYearMonthSelected = { ym -> firstDueYearMonth = ym },
                onDismissRequest = { showGearMonthPicker = false }
            )
        }

        if (showGearCreateChannelDialog) {
            CreateCreditChannelDialog(
                onDismiss = { showGearCreateChannelDialog = false },
                confirmText = "创建并选择",
                onSubmit = { name, day ->
                    runCatching {
                        val created = apiClient.createCreditChannel(
                            CreateCreditChannelRequest(name = name, repaymentDay = day)
                        )
                        selectedChannel = created
                        repayDay = created.repaymentDay
                        LedgerRepository.refreshAssets(apiClient)
                    }.onFailure { err ->
                        ArchiveToastManager.show("创建渠道失败: ${err.message}", isError = true)
                    }.isSuccess
                }
            )
        }

        if (showGearChannelSelectDialog) {
            SelectCreditChannelDialog(
                channels = userChannels,
                selectedChannelId = selectedChannel?.id,
                onSelect = { ch ->
                    selectedChannel = ch
                    repayDay = ch.repaymentDay
                },
                onCreateNewClick = { showGearCreateChannelDialog = true },
                onDismiss = { showGearChannelSelectDialog = false }
            )
        }
    }

    // 固资装备详情抽屉（主操作「编辑」，底部次级危险操作「退役 / 出二手」「删除装备」）
    viewingGearItem?.let { gear ->
        val companionLabel = remember(gear.purchaseDate) { formatGearCompanionDays(gear.purchaseDate) }
        val allGearItems = assetsOverview?.gearSummary?.items ?: emptyList()

        LedgerSheet(
            onDismissRequest = { viewingGearItem = null },
            title = "装备详情",
            cancelText = "关闭",
            actionText = "编辑",
            onAction = {
                val target = gear
                viewingGearItem = null
                editingGearItem = target
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = gear.name,
                        style = LedgerTheme.typography.title,
                        fontFamily = LedgerTheme.typography.serif,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    if (!companionLabel.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(ArchiveShapes.full)
                                .background(colors.surfaceElevated)
                                .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.full)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = companionLabel,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.caption,
                                color = colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.medium)
                        .background(colors.surfaceElevated)
                        .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.medium)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("分类", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                        Text(gear.category.ifBlank { "数码器材" }, style = LedgerTheme.typography.body, color = colors.textPrimary)
                    }
                    if (gear.spec.isNotBlank()) {
                        LedgerDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("规格参数", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                            Text(
                                text = gear.spec,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.body,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                    if (!gear.purchaseDate.isNullOrBlank()) {
                        LedgerDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("购入日期", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                            Text(
                                text = gear.purchaseDate.orEmpty().take(10),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = ArchiveTypeScale.body,
                                color = colors.textPrimary
                            )
                        }
                    }
                    LedgerDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("买入价格", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                        Text(
                            text = if (isPrivacyMasked) "••••" else gear.priceCents?.let { LedgerFormat.formatMoney(it) } ?: "—",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = ArchiveTypeScale.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                    if (!gear.note.isNullOrBlank()) {
                        LedgerDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("备注", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                            Text(
                                text = gear.note.orEmpty(),
                                style = LedgerTheme.typography.body,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                LedgerDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerTextButton(
                        text = "退役 / 出二手",
                        isDestructive = true,
                        onClick = {
                            val target = gear
                            viewingGearItem = null
                            sellingGearItem = target
                        }
                    )

                    LedgerTextButton(
                        text = "删除装备",
                        isDestructive = true,
                        onClick = {
                            val target = gear
                            val targetIdx = allGearItems.indexOfFirst { it.id == target.id }
                            viewingGearItem = null
                            scope.launch {
                                runCatching {
                                    apiClient.deleteGearAsset(target.id)
                                    LedgerRepository.refreshAssets(apiClient)
                                    refreshAllData()
                                    snackbarData = LedgerSnackbarData(
                                        message = "已删除装备「${target.name}」",
                                        actionLabel = "撤销",
                                        onActionClick = {
                                            scope.launch {
                                                runCatching {
                                                    apiClient.restoreGearAsset(
                                                        RestoreGearAssetRequest(
                                                            id = target.id,
                                                            name = target.name,
                                                            spec = target.spec,
                                                            category = target.category,
                                                            status = target.status,
                                                            priceCents = target.priceCents ?: 0L,
                                                            purchaseDate = target.purchaseDate,
                                                            soldPriceCents = target.soldPriceCents ?: 0L,
                                                            soldDate = target.soldDate,
                                                            note = target.note,
                                                            index = targetIdx
                                                        )
                                                    )
                                                    LedgerRepository.refreshAssets(apiClient)
                                                    refreshAllData()
                                                }
                                            }
                                        }
                                    )
                                }.onFailure { err ->
                                    ArchiveToastManager.show("删除失败: ${err.message}", isError = true)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 编辑装备抽屉
    editingGearItem?.let { gear ->
        val allGearItems = assetsOverview?.gearSummary?.items ?: emptyList()
        var editName by remember(gear.id) { mutableStateOf(gear.name) }
        var editSpec by remember(gear.id) { mutableStateOf(gear.spec) }
        var editCategory by remember(gear.id) { mutableStateOf(gear.category.ifBlank { "数码器材" }) }
        var editPriceYuan by remember(gear.id) {
            mutableStateOf(FormatUtils.centsToYuanString(gear.priceCents ?: 0L, includeSymbol = false))
        }
        var editPurchaseDate by remember(gear.id) {
            mutableStateOf(
                gear.purchaseDate?.take(10)?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
            )
        }
        var editNote by remember(gear.id) { mutableStateOf(gear.note.orEmpty()) }
        var isSavingGear by remember(gear.id) { mutableStateOf(false) }

        val gearCatPresets = remember { listOf("数码器材", "摄影影音", "随身文具", "户外出行", "家居生活") }

        LedgerSheet(
            onDismissRequest = { if (!isSavingGear) editingGearItem = null },
            title = "编辑装备",
            actionText = if (isSavingGear) "保存中…" else "保存",
            isActionEnabled = editName.isNotBlank() && !isSavingGear,
            onAction = {
                if (editName.isNotBlank() && !isSavingGear) {
                    isSavingGear = true
                    val targetIdx = allGearItems.indexOfFirst { it.id == gear.id }
                    val updatedItem = gear.copy(
                        name = editName.trim(),
                        spec = editSpec.trim(),
                        category = editCategory,
                        priceCents = FormatUtils.yuanStringToCents(editPriceYuan),
                        purchaseDate = editPurchaseDate?.toString(),
                        note = editNote.trim().ifEmpty { null }
                    )
                    scope.launch {
                        runCatching {
                            apiClient.updateGearAsset(
                                original = gear,
                                updated = updatedItem,
                                index = targetIdx
                            )
                            LedgerRepository.refreshAssets(apiClient)
                            refreshAllData()
                            ArchiveToastManager.show("装备信息已保存")
                        }.onSuccess {
                            isSavingGear = false
                            editingGearItem = null
                        }.onFailure { err ->
                            isSavingGear = false
                            ArchiveToastManager.show("保存失败: ${err.message}", isError = true)
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
                LedgerField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "装备名称",
                    singleLine = true
                )

                LedgerField(
                    value = editSpec,
                    onValueChange = { editSpec = it },
                    label = "规格参数",
                    placeholder = "选填",
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "装备分类",
                        style = LedgerTheme.typography.caption,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (gearCatPresets + listOfNotNull(editCategory.takeIf { it !in gearCatPresets })).forEach { cat ->
                            ArchiveSelectableCapsule(
                                text = cat,
                                selected = editCategory == cat,
                                onClick = { editCategory = cat }
                            )
                        }
                    }
                }

                LedgerField(
                    value = editPriceYuan,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            val parts = sanitized.split('.')
                            if (parts.size == 1 || parts[1].length <= 2) {
                                editPriceYuan = sanitized
                            }
                        }
                    },
                    label = "买入价格",
                    placeholder = "0.00",
                    trailingText = "元",
                    isNumeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                ArchiveDatePickerField(
                    selectedDate = editPurchaseDate,
                    onDateSelected = { editPurchaseDate = it },
                    label = "购入日期",
                    placeholder = "未设置",
                    onClear = { editPurchaseDate = null }
                )

                LedgerField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = "备注",
                    placeholder = "选填",
                    singleLine = true
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
        var isSelling by remember(gear.id) { mutableStateOf(false) }

        LedgerSheet(
            onDismissRequest = { if (!isSelling) sellingGearItem = null },
            title = "退役变卖 · ${gear.name}",
            actionText = if (isSelling) "提交中…" else "变卖",
            isActionEnabled = FormatUtils.yuanStringToCents(soldPriceYuan) > 0 && !isSelling,
            onAction = {
                val cents = FormatUtils.yuanStringToCents(soldPriceYuan)
                if (cents > 0 && !isSelling) {
                    isSelling = true
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
                            ArchiveToastManager.show("已登记二手回血")
                        }.onSuccess {
                            isSelling = false
                            sellingGearItem = null
                        }.onFailure { err ->
                            isSelling = false
                            ArchiveToastManager.show("变卖失败: ${err.message}", isError = true)
                        }
                    }
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
            onCreateChannel = { name, repayDay ->
                runCatching {
                    val created = apiClient.createCreditChannel(
                        CreateCreditChannelRequest(name = name, repaymentDay = repayDay)
                    )
                    LedgerRepository.refreshAssets(apiClient)
                    refreshAllData()
                    created
                }.onFailure { err ->
                    ArchiveToastManager.show("新建渠道失败: ${err.message}", isError = true)
                }.getOrNull()
            },
            onSubmit = { req ->
                runCatching {
                    apiClient.createInstallmentPlan(req)
                    LedgerRepository.refreshAssets(apiClient)
                    refreshAllData()
                    ArchiveToastManager.show("分期计划创建成功")
                }.onFailure { err ->
                    ArchiveToastManager.show("创建分期失败: ${err.message}", isError = true)
                }.isSuccess
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
                    }.onSuccess {
                        editingInstallmentPlanDetail = null
                    }.onFailure { err ->
                        ArchiveToastManager.show("更新失败: ${err.message}", isError = true)
                    }
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
                    }.onSuccess {
                        payingInstallmentItem = null
                    }.onFailure { err ->
                        ArchiveToastManager.show("标记还款失败: ${err.message}", isError = true)
                    }
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
                    }.onSuccess {
                        settlingInstallmentPlan = null
                    }.onFailure { err ->
                        ArchiveToastManager.show("结清失败: ${err.message}", isError = true)
                    }
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
        ArchiveConfirmDialog(
            title = "撤销该期还款？",
            content = "撤销后将恢复该期待还本金，冲销还款支出流水，并回滚负债大盘。",
            confirmText = "确认撤销",
            isDestructive = true,
            onDismiss = { showUnpayInstallmentConfirm = null },
            onConfirm = {
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
    }

    // 7. 二次确认弹窗：取消分期计划
    showCancelInstallmentConfirm?.let { plan ->
        ArchiveConfirmDialog(
            title = "取消分期计划？",
            content = "取消后将彻底删除此分期计划及所有期次记录。",
            confirmText = "确认取消",
            dismissText = "暂不取消",
            isDestructive = true,
            onDismiss = { showCancelInstallmentConfirm = null },
            onConfirm = {
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
    }

    // 8. 二次确认弹窗：删除分期还款账目联动撤销
    showDeleteTxRepayConfirm?.let { tx ->
        ArchiveConfirmDialog(
            title = "删除此还款账目？",
            content = "删除此分期还款账目将同时撤销对应的分期还款期次，并恢复待还负债本金。",
            confirmText = "确认删除",
            isDestructive = true,
            onDismiss = { showDeleteTxRepayConfirm = null },
            onConfirm = {
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
    }
}
