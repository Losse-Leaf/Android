package com.looseleaf.feature.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.core.R
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.data.monthRangeOf
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    modifier: Modifier = Modifier,
    focusGearId: String? = null,
    onFocusConsumed: (() -> Unit)? = null
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

    // 从展台点某件装备进来：落到资产大盘分段，由 AssetsAndGearTab 负责滚动与高亮。
    // 不去动「无跳转时」的 selectedTab，否则用户上次停留的分段会被重置。
    LaunchedEffect(focusGearId) {
        if (focusGearId != null) selectedTab = 1
    }
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

    var snackbarData by remember { mutableStateOf<ArchiveSnackbarData?>(null) }

    // 8 秒自动消失（撤销类提示留足操作时间；key 仍为 snackbarData，保持「新消息替换旧消息」语义）
    LaunchedEffect(snackbarData) {
        if (snackbarData != null) {
            delay(8000)
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
                ArchivePageHeader(
                    title = "账本",
                    subtitle = if (selectedTab == 0) {
                        "${selectedYearMonth.year} 年 ${selectedYearMonth.monthValue} 月 ▾"
                    } else {
                        "截至今天"
                    },
                    onSubtitleClick = if (selectedTab == 0) {
                        { showMonthPickerSheet = true }
                    } else {
                        null
                    },
                    actions = {
                        // 自动记账从「更多」菜单提上来（RG-28）：它是 Android 端独有的能力，
                        // 埋在三级菜单里等于没做。点进去是权限状态 + 保活检查 + 模拟测试页。
                        IconButton(onClick = { showKeepAliveScreen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_auto_record),
                                contentDescription = "自动记账与保活",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                ArchiveFab(
                    visible = isFabVisible,
                    onClick = { showQuickRecordSheet = true },
                    modifier = Modifier.offset(y = fabOffset),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "记一笔",
                            tint = colors.background,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            },
            containerColor = colors.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 月份 / 「截至今天」并入顶栏副标题，省掉原先独占 34dp 的一行，
                // 首屏多出约一条账目的高度。
                // 3. 视图切换（账单明细 / 资产大盘）
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ArchiveSegmentedTabs(
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
                                                snackbarData = ArchiveSnackbarData(
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
                                focusGearId = focusGearId,
                                onFocusConsumed = onFocusConsumed,
                                listState = assetsListState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 浮动在底部的 ArchiveSnackbar（在底栏上方，停留 8 秒）
        AnimatedVisibility(
            visible = snackbarData != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        ) {
            snackbarData?.let { data ->
                ArchiveSnackbar(
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
        ArchiveMonthPickerSheet(
            selectedYearMonth = selectedYearMonth,
            onYearMonthSelected = { selectedYearMonth = it },
            onDismissRequest = { showMonthPickerSheet = false }
        )
    }

    // 预留待确认列表占位弹层
    if (showPendingConfirmSheet) {
        val pendingItems = remember(transactions) { transactions.filter { it.syncStatus == "PENDING_CREATE" } }
        ArchiveSheet(
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
                                text = FormatUtils.formatMoney(item.amount),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.expense
                            )
                        }
                        ArchiveDivider()
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
        EditTransactionSheet(
            tx = tx,
            apiClient = apiClient,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            paymentMethods = paymentMethods,
            creditChannels = creditChannels,
            onDismiss = { editingTransaction = null },
            onSaved = { refreshAllData() },
            onRequestDeleteRepayConfirm = { showDeleteTxRepayConfirm = it },
            onTransactionDeletedWithUndo = { snackbarData = it },
            onOpenInstallmentPlanDetail = { viewingInstallmentPlanDetail = it }
        )
    }

    // 校准余额抽屉
    if (showCalibrateBalanceDialog) {
        CalibrateBalanceSheet(
            currentLiquidCents = assetsOverview?.liquidBalance ?: 0L,
            apiClient = apiClient,
            onDismiss = { showCalibrateBalanceDialog = false },
            onCalibrated = { refreshAllData() }
        )
    }

    // 登记在役新装备
    if (showAddGearDialog) {
        AddGearSheet(
            creditChannels = creditChannels,
            apiClient = apiClient,
            onDismiss = { showAddGearDialog = false },
            onSubmitted = { refreshAllData() }
        )
    }

    // 固资装备详情抽屉
    viewingGearItem?.let { gear ->
        GearDetailSheet(
            gear = gear,
            allGearItems = assetsOverview?.gearSummary?.items ?: emptyList(),
            isPrivacyMasked = isPrivacyMasked,
            apiClient = apiClient,
            onDismiss = { viewingGearItem = null },
            onEditClick = { editingGearItem = it },
            onSellClick = { sellingGearItem = it },
            onDeletedWithUndo = { snackbarData = it },
            onRefreshAll = { refreshAllData() }
        )
    }

    // 编辑装备抽屉
    editingGearItem?.let { gear ->
        EditGearSheet(
            gear = gear,
            allGearItems = assetsOverview?.gearSummary?.items ?: emptyList(),
            apiClient = apiClient,
            onDismiss = { editingGearItem = null },
            onSaved = { refreshAllData() }
        )
    }

    // 变卖装备抽屉
    sellingGearItem?.let { gear ->
        SellGearSheet(
            gear = gear,
            apiClient = apiClient,
            onDismiss = { sellingGearItem = null },
            onSold = { refreshAllData() }
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
                        val todayStr = LocalDate.now(FormatUtils.BeijingZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
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
                        snackbarData = ArchiveSnackbarData(
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
