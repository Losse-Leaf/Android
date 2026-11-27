package com.looseleaf.feature.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import com.looseleaf.app.theme.ArchiveMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContent
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
 * - 顶部排布：顶栏 -> 纯文字视图切换（流水 | 心愿单） -> 错误条 -> 列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    apiClient: APIClient = APIClient.getInstance(LocalContext.current),
    credentialStore: CredentialStore = CredentialStore.getInstance(LocalContext.current),
    modifier: Modifier = Modifier,
    requestedTab: Int? = null,
    onRequestedTabConsumed: (() -> Unit)? = null,
    wishlistTabContent: (@Composable (Modifier) -> Unit)? = null,
    wishlistCount: Int? = null,
    onRefreshWishlist: (suspend () -> Unit)? = null,
    onAddWishClick: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab.coerceIn(0, 1)
            onRequestedTabConsumed?.invoke()
        }
    }

    var showQuickRecordSheet by rememberSaveable { mutableStateOf(false) }
    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMonthPickerSheet by remember { mutableStateOf(false) }
    var showPendingConfirmSheet by remember { mutableStateOf(false) }

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // 真实分期相关状态 (RC-140)
    var showInstallmentPlansSheet by remember { mutableStateOf(false) }
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
    val transactions by remember(context, monthRange) {
        LedgerRepository.attachContextOnly(context)
        LedgerRepository.transactionsForMonth(monthRange)
    }.collectAsStateWithLifecycle(emptyList())

    var ledgerLoadError by remember { mutableStateOf<String?>(null) }
    var isRefreshingLedger by remember { mutableStateOf(false) }
    var isRefreshingWishlist by remember { mutableStateOf(false) }

    val transactionsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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

    val isFabVisible by remember(onAddWishClick, wishlistCount) {
        derivedStateOf {
            when (selectedTab) {
                0 -> transactions.isNotEmpty() &&
                    (isScrollingUp || transactionsListState.firstVisibleItemIndex == 0)
                1 -> onAddWishClick != null && (wishlistCount == null || wishlistCount > 0)
                else -> false
            }
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

    fun refreshCurrentTab() {
        if (selectedTab == 0) {
            refreshAllData(force = true)
        } else if (onRefreshWishlist != null && !isRefreshingWishlist) {
            scope.launch {
                isRefreshingWishlist = true
                runCatching { onRefreshWishlist() }
                isRefreshingWishlist = false
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
                        "${selectedYearMonth.year} 年 ${selectedYearMonth.monthValue} 月"
                    } else {
                        "心愿与向往"
                    },
                    onSubtitleClick = if (selectedTab == 0) {
                        { showMonthPickerSheet = true }
                    } else {
                        null
                    },
                    actions = {
                        ArchiveHeaderIconButton(
                            onClick = { showKeepAliveScreen = true },
                            contentDescription = "自动记账与保活",
                            painter = painterResource(R.drawable.ic_auto_record)
                        )
                        Box {
                            ArchiveHeaderIconButton(
                                onClick = { showMoreMenu = true },
                                contentDescription = "更多操作",
                                imageVector = FolioGlyphs.More,
                                highlighted = showMoreMenu
                            )

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(colors.surfaceRaised)
                                    .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.md),
                                containerColor = colors.surfaceRaised,
                                shape = ArchiveShapes.md,
                                tonalElevation = 0.dp,
                                shadowElevation = 12.dp
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
                                            text = "分期计划",
                                            fontFamily = LedgerTheme.typography.serif,
                                            color = colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showInstallmentPlansSheet = true
                                    }
                                )
                                if (onOpenSettings != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "设置",
                                                fontFamily = LedgerTheme.typography.serif,
                                                color = colors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                val fabOffset by animateDpAsState(
                    targetValue = if (snackbarData != null) (-76).dp else 0.dp,
                    label = "fab_offset"
                )
                ArchiveFab(
                    visible = isFabVisible,
                    onClick = {
                        if (selectedTab == 0) {
                            showQuickRecordSheet = true
                        } else {
                            onAddWishClick?.invoke()
                        }
                    },
                    modifier = Modifier.offset(y = fabOffset),
                    icon = {
                        Icon(
                            imageVector = FolioGlyphs.Plus,
                            contentDescription = if (selectedTab == 0) "记一笔" else "许下心愿",
                            tint = colors.onAccent,
                            modifier = Modifier.size(26.dp)
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
                // 视图切换（流水 / 心愿单）
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                    ArchiveSegmentedTabs(
                        tabs = listOf("流水", "心愿单"),
                        selectedIndex = selectedTab,
                        onTabSelected = { selectedTab = it },
                        counts = wishlistCount?.let { listOf(transactions.size, it) }
                    )
                }

                // 错误提示条（仅流水 Tab 展示账本同步错误）
                if (selectedTab == 0) {
                    ledgerLoadError?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(ArchiveShapes.md)
                                .background(colors.archive.negativeTint)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = FolioGlyphs.Alert,
                                contentDescription = null,
                                tint = colors.alertRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                style = LedgerTheme.typography.caption,
                                color = colors.alertRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 原生下拉刷新包装器
                val isCurrentlyRefreshing = if (selectedTab == 0) isRefreshingLedger else isRefreshingWishlist
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isCurrentlyRefreshing,
                    onRefresh = { refreshCurrentTab() },
                    state = pullToRefreshState,
                    indicator = {
                        PaperclipPullIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isCurrentlyRefreshing
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 流水 / 心愿单切换：按方向轻推 + 淡入，旧页先行淡出
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { ledgerTabTransition() },
                        label = "ledgerTab",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
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
                                if (wishlistTabContent != null) {
                                    wishlistTabContent(Modifier.fillMaxSize())
                                } else {
                                    ArchiveEmptyState(
                                        title = "心愿单暂未接入",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = snackbarData != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
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

    if (showMonthPickerSheet) {
        ArchiveMonthPickerSheet(
            selectedYearMonth = selectedYearMonth,
            onYearMonthSelected = { selectedYearMonth = it },
            onDismissRequest = { showMonthPickerSheet = false }
        )
    }

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
                            val itemIsIncome = isIncomeTransaction(item)
                            Text(
                                text = (if (itemIsIncome) "+" else "-") + FormatUtils.formatMoney(item.amount),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = if (itemIsIncome) colors.income else colors.expense
                            )
                        }
                        ArchiveDivider()
                    }
                }
            }
        }
    }

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

    // ───────────────────────────────────────────────
    // 真实分期体系弹层与确认对话框 (RC-140)
    // ───────────────────────────────────────────────

    if (showInstallmentPlansSheet) {
        InstallmentPlansSheet(
            plans = ledgerSnapshot.installmentPlans,
            isPrivacyMasked = isPrivacyMasked,
            onDismissRequest = { showInstallmentPlansSheet = false },
            onNewInstallmentClick = { showNewInstallmentSheet = true },
            onPlanClick = { planSummary ->
                showInstallmentPlansSheet = false
                scope.launch {
                    runCatching {
                        apiClient.fetchInstallmentPlanDetail(planSummary.id)
                    }.onSuccess {
                        viewingInstallmentPlanDetail = it
                    }.onFailure { err ->
                        ArchiveToastManager.show("加载分期计划失败: ${err.message}", isError = true)
                    }
                }
            }
        )
    }

    if (showNewInstallmentSheet) {
        CreateInstallmentSheet(
            creditChannels = creditChannels,
            expenseCategories = expenseCategories,
            onDismissRequest = { showNewInstallmentSheet = false },
            onCreateChannel = { name, repayDay ->
                runCatching {
                    val created = apiClient.createCreditChannel(
                        CreateCreditChannelRequest(name = name, repaymentDay = repayDay)
                    )
                    LedgerRepository.refreshInstallments(apiClient)
                    refreshAllData()
                    created
                }.onFailure { err ->
                    ArchiveToastManager.show("新建渠道失败: ${err.message}", isError = true)
                }.getOrNull()
            },
            onSubmit = { req ->
                runCatching {
                    apiClient.createInstallmentPlan(req)
                    LedgerRepository.refreshInstallments(apiClient)
                    refreshAllData()
                    ArchiveToastManager.show("分期计划创建成功")
                }.onFailure { err ->
                    ArchiveToastManager.show("创建分期失败: ${err.message}", isError = true)
                }.isSuccess
            }
        )
    }

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
                        LedgerRepository.refreshInstallments(apiClient)
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
                        LedgerRepository.refreshInstallments(apiClient)
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
                        LedgerRepository.refreshInstallments(apiClient)
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
                        LedgerRepository.refreshInstallments(apiClient)
                        refreshAllData()
                        snackbarData = ArchiveSnackbarData(
                            message = "已标记 ${item.planTitle} 第 ${item.seq} 期已还",
                            actionLabel = "撤销",
                            onActionClick = {
                                scope.launch {
                                    runCatching {
                                        apiClient.unpayInstallmentItem(item.itemId)
                                        LedgerRepository.refreshInstallments(apiClient)
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

    showUnpayInstallmentConfirm?.let { item ->
        ArchiveConfirmDialog(
            title = "撤销该期还款？",
            content = "撤销后将恢复该期待还本金并冲销还款支出流水。",
            confirmText = "确认撤销",
            isDestructive = true,
            onDismiss = { showUnpayInstallmentConfirm = null },
            onConfirm = {
                scope.launch {
                    runCatching {
                        apiClient.unpayInstallmentItem(item.id)
                        val updated = apiClient.fetchInstallmentPlanDetail(item.planId)
                        viewingInstallmentPlanDetail = updated
                        LedgerRepository.refreshInstallments(apiClient)
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
                        LedgerRepository.refreshInstallments(apiClient)
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

    showDeleteTxRepayConfirm?.let { tx ->
        ArchiveConfirmDialog(
            title = "删除此还款账目？",
            content = "删除此分期还款账目将同时撤销对应的分期还款期次，并恢复待还本金。",
            confirmText = "确认删除",
            isDestructive = true,
            onDismiss = { showDeleteTxRepayConfirm = null },
            onConfirm = {
                scope.launch {
                    runCatching {
                        LedgerRepository.deleteTransaction(tx.id)
                        LedgerRepository.refreshInstallments(apiClient)
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

/**
 * 账本分页切换：前进（流水 → 心愿单）从右侧轻推进入，后退反之；位移只有 1/8 屏宽，避免晃眼。
 */
private fun AnimatedContentTransitionScope<Int>.ledgerTabTransition(): ContentTransform {
    val forward = targetState > initialState
    val enter = slideInHorizontally(
        animationSpec = tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized)
    ) { width -> if (forward) width / 8 else -width / 8 } + fadeIn(tween(ArchiveMotion.DurationBase))
    val exit = slideOutHorizontally(
        animationSpec = tween(ArchiveMotion.DurationFast, easing = ArchiveMotion.EaseIn)
    ) { width -> if (forward) -width / 8 else width / 8 } + fadeOut(tween(ArchiveMotion.DurationFast))
    return enter togetherWith exit
}
