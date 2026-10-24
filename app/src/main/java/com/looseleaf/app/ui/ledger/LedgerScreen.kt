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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.data.local.LedgerRepository
import com.looseleaf.app.data.local.MonthlyTotalsRow
import com.looseleaf.app.data.local.monthRangeOf
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.app.ui.components.*
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.looseleaf.app.utils.LocationHelper
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.roundToLong
import androidx.compose.ui.platform.LocalDensity
import com.looseleaf.app.ui.components.DraftStore
import androidx.compose.animation.Crossfade
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
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
    val syncManager = remember { SyncManager.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val apiClient = remember { APIClient.getInstance(context) }

    // 分类 / 支付方式 / 信贷渠道 / 资产大盘的生命周期与进程一致，切 Tab 不再丢状态
    val ledgerSnapshot by LedgerRepository.snapshot.collectAsStateWithLifecycle()
    val expenseCategories = ledgerSnapshot.expenseCategories
    val incomeCategories = ledgerSnapshot.incomeCategories
    val paymentMethods = ledgerSnapshot.paymentMethods
    val creditChannels = ledgerSnapshot.creditChannels
    val assetsOverview = ledgerSnapshot.assetsOverview
    val isLoadingAssets = ledgerSnapshot.isLoadingAssets

    // 0: 极速记账, 1: 账单流水, 2: 资产与信贷
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 记账输入状态
    var inputAmountString by rememberSaveable { mutableStateOf("0") }
    var selectedType by rememberSaveable { mutableStateOf("expense") } // "expense" | "income"
    var selectedCategory by rememberSaveable { mutableStateOf(inferRecommendedCategory()) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf("微信支付") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var selectedCityCode by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCityName by rememberSaveable { mutableStateOf<String?>(null) }
    // MOB-P1-56：日期/分期等关键状态改用 rememberSaveable ——
    // 原先用 remember，旋转屏幕（或系统回收后恢复）会把用户手动设好的
    // 日期、分期开关与期数**全部重置**，用户以为记下的内容与实际不符。
    // LocalDateTime 不可 Saveable，这里存毫秒时间戳再派生。
    var txOccurredAtMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val txOccurredAt: LocalDateTime = remember(txOccurredAtMillis) {
        java.time.Instant.ofEpochMilli(txOccurredAtMillis)
            .atZone(FormatUtils.BeijingZone)
            .toLocalDateTime()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc != null && !loc.cityCode.isNullOrBlank()) {
                    selectedCityCode = loc.cityCode
                    selectedCityName = loc.cityName
                }
            }
        }
    }

    fun applyLocation(loc: LocationHelper.ResolvedLocation?) {
        if (loc != null && !loc.cityCode.isNullOrBlank()) {
            selectedCityCode = loc.cityCode
            selectedCityName = loc.cityName
        }
    }

    fun requestCurrentLocation() {
        if (LocationHelper.hasLocationPermission(context)) {
            scope.launch { applyLocation(LocationHelper.getCurrentLocation(context)) }
        } else {
            // 只有用户主动点「定位」时才申请权限
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        // 已授权则静默补全；未授权不再一进页面就弹系统权限框
        if (selectedCityCode.isNullOrBlank() && LocationHelper.hasLocationPermission(context)) {
            applyLocation(LocationHelper.getCurrentLocation(context))
        }
    }

    // 分期参数
    var isInstallment by rememberSaveable { mutableStateOf(false) }
    var installmentChannelCode by rememberSaveable { mutableStateOf("huabei") }
    var installmentMonths by rememberSaveable { mutableStateOf(12) }
    var repaymentDay by rememberSaveable { mutableStateOf(10) }

    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }

    // 对话框状态
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    // MOB-P0-14：待确认删除的账目
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showCalibrateBalanceDialog by remember { mutableStateOf(false) }
    var showAddGearDialog by remember { mutableStateOf(false) }
    var sellingGearItem by remember { mutableStateOf<GearItem?>(null) }
    // MOB-P0-14：待确认彻底删除的装备
    var gearToDelete by remember { mutableStateOf<GearItem?>(null) }
    var showCreditChannelsDialog by remember { mutableStateOf(false) }
    var showCategoriesManagerDialog by remember { mutableStateOf(false) }
    // 记账附加设置抽屉（日期时刻 / 属地 / 分期）：低频项从首屏收进抽屉（AOPT-01）
    var showExtrasSheet by remember { mutableStateOf(false) }

    // 账单流水筛选月份 (默认当前年月)
    var selectedYearMonth by remember {
        mutableStateOf(YearMonth.now(FormatUtils.BeijingZone))
    }

    // AND-4：账本页只订阅**当月**流水；汇总与分类占比由 SQL 聚合后单独下发，
    // 不再把全量流水物化到 Compose 里做月份 filter + 三遍遍历。
    val monthRange = remember(selectedYearMonth) { monthRangeOf(selectedYearMonth) }
    val transactions by remember(monthRange) {
        syncManager.transactionsForMonth(monthRange)
    }.collectAsStateWithLifecycle(emptyList())
    val monthlyTotals by remember(monthRange) {
        syncManager.monthlyTotalsFlow(monthRange)
    }.collectAsStateWithLifecycle(MonthlyTotalsRow())
    val monthlyCategoryTotals by remember(monthRange) {
        syncManager.monthlyCategoryTotalsFlow(monthRange)
    }.collectAsStateWithLifecycle(emptyList())

    // MOB-P1-87：账本各 Tab 此前**没有任何错误态** ——
    // 拉取失败时列表只是「空着」，用户完全无法区分「真的没有账目」与「没拉到」。
    var ledgerLoadError by remember { mutableStateOf<String?>(null) }
    // 生物识别弹窗标题：必须在 composable 作用域取值（onClick lambda 不是 composable）
    val privacyUnlockTitle = stringResource(R.string.ledger_screen_title_01)
    val deleteTxBiometricTitle = stringResource(R.string.ledger_screen_title_06)

    suspend fun reloadCategoriesAndAssets(
        forceLoadingSpinner: Boolean = false,
        force: Boolean = false
    ) {
        ledgerLoadError = runCatching {
            LedgerRepository.refreshAll(apiClient, force = force, showSpinner = forceLoadingSpinner)
        }.exceptionOrNull()?.message
        val latest = LedgerRepository.snapshot.value
        if (latest.paymentMethods.isNotEmpty() && selectedPaymentMethod !in latest.paymentMethods) {
            selectedPaymentMethod = latest.paymentMethods.first()
        }
        if (latest.creditChannels.isNotEmpty() && latest.creditChannels.none { it.code == installmentChannelCode }) {
            installmentChannelCode = latest.creditChannels.first().code
        }
    }

    LaunchedEffect(Unit) {
        // 会话门禁改为**进程级**标志：原先用 rememberSaveable，系统回收进程后
        // 从 savedInstanceState 恢复时标志仍为 true，这个新会话里账本页
        // 永远不做首次拉取，用户对着陈旧数据无提示。
        if (!SyncManager.isSessionPullDone("ledger")) {
            // MOB-P1-37 遗留：账目流水来自本地库（Room），而拆分后各页只拉自己渲染的那一类数据 ——
            // 账本页若不在进入时拉一次，「账单明细」永远是 0（只有分类与资产是直连 API 取的）。
            runCatching { syncManager.pullLedgerData() }
            reloadCategoriesAndAssets(forceLoadingSpinner = false)
            SyncManager.markSessionPullDone("ledger")
        }
    }

    // 账单明细下拉刷新状态（AOPT-06）：必须在 reloadCategoriesAndAssets 之后定义
    var isRefreshingLedger by remember { mutableStateOf(false) }
    val refreshLedger: () -> Unit = {
        if (!isRefreshingLedger) {
            scope.launch {
                isRefreshingLedger = true
                val res = syncManager.pullLedgerData()
                reloadCategoriesAndAssets(force = true)
                isRefreshingLedger = false
                if (res.isSuccess) {
                    ArchiveToastManager.show("账目已同步")
                } else {
                    ArchiveToastManager.show(res.exceptionOrNull()?.message ?: "同步未完成", isError = true)
                }
            }
        }
    }

    val activeCategories = if (selectedType == "income") incomeCategories else expenseCategories

    // MOB-P1-30：自然语言解析原本在「预览」与「提交兜底」两处各做一次
    // （每次按键解析两遍，且每次都分配 9 个关键词列表）。
    // 这里合并为**唯一一次**解析结果，由两处共享。
    val naturalParse = remember(noteText, activeCategories, inputAmountString) {
        if (noteText.isBlank() || inputAmountString != "0") null
        else FormatUtils.parseNaturalTransactionInput(noteText, activeCategories)
            .takeIf { it.amountCents > 0 }
    }

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
        selectedCityCode = null
        selectedCityName = null
        txOccurredAtMillis = System.currentTimeMillis()
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
                        text = stringResource(R.string.ledger_screen_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.ledger_screen_text_02),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isPrivacyMasked && credentialStore.isBiometricsEnabled) {
                                val act = context as? FragmentActivity
                                if (act != null && BiometricHelper.canAuthenticate(act)) {
                                    BiometricHelper.authenticate(
                                        activity = act,
                                        title = privacyUnlockTitle,
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
                            painter = if (isPrivacyMasked) painterResource(R.drawable.ic_lock) else painterResource(R.drawable.ic_lock_open),
                            contentDescription = stringResource(R.string.ledger_screen_contentDescription_01),
                            tint = if (isPrivacyMasked) colors.accentWarm else colors.inkSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                // 用户主动对齐必须区分成败：失败只弹「已对齐」是撒谎
                                val result = syncManager.pullLedgerData()
                                if (result.isSuccess) {
                                    reloadCategoriesAndAssets(force = true)
                                    ArchiveToastManager.show("账簿与资产已对齐")
                                } else {
                                    ArchiveToastManager.show(
                                        "对齐失败: ${result.exceptionOrNull()?.message ?: "网络异常"}",
                                        isError = true
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.ledger_screen_contentDescription_02),
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // MOB-P1-87：可点击的错误横幅 + 重试（对齐 ProjectsWishesScreen 的既有范式）
            ledgerLoadError?.let { message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accentWarm.copy(alpha = 0.10f))
                        .border(0.5.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            scope.launch {
                                ledgerLoadError = null
                                reloadCategoriesAndAssets(force = true)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error_outline),
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ledger_screen_text_03),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 12.sp,
                        color = colors.accentWarm
                    )
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

            // MOB-P2-64：Tab 切换此前是硬切，加一段短淡入让状态变化可感知
            Crossfade(targetState = selectedTab, label = stringResource(R.string.ledger_screen_label_01)) { tab ->
            when (tab) {
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
                        // MOB-P1-76：触控热区下限 48dp（原先紧凑屏降到 44dp，低于系统基线）
                        val keypadKeyHeight = if (isCompactHeight) 48.dp else 52.dp
                        // MOB-P1-60：大字体下再压缩一档，保证 4 行键盘 + 盖章按钮始终在屏内
                        val fontScale = LocalDensity.current.fontScale
                        val keypadKeyHeightScaled = if (fontScale > 1.2f) {
                            (keypadKeyHeight.value * (1.2f / fontScale).coerceAtLeast(0.82f)).dp
                        } else {
                            keypadKeyHeight
                        }
                        // MOB-P0-13：系统键盘可见时收起自定义大键盘。
                        // 原先两套键盘同屏，且软键盘弹出后 weight(1f) 滚动区被压到 0，
                        // 固定高度的键盘与「记入账本」按钮被挤到屏幕外 / 键盘之下 → 提交按钮点不到。
                        // API 30+ 用 isImeVisible 精确判定；API 26~29 走 adjustResize，
                        // 窗口高度被压缩，此时用可用高度阈值兜底。
                        val imeVisible = WindowInsets.isImeVisible
                        val showKeypad = !imeVisible && maxHeight > 420.dp

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 上半区（金额 / 备注 / 低频元数据）吸收剩余高度，极小屏可滚动；
                            // 下半区（分类 + 键盘 + 盖章）固定锚定拇指热区——
                            // 记账不再需要先下滑页面才能点到「记入账本」（AOPT-01）
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
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
                                            text = stringResource(R.string.ledger_screen_text_04),
                                            isSelected = selectedType == "expense",
                                            onClick = {
                                                selectedType = "expense"
                                                if (selectedCategory !in expenseCategories) {
                                                    selectedCategory = expenseCategories.firstOrNull() ?: "餐饮"
                                                }
                                            }
                                        )
                                        TypeToggleButton(
                                            text = stringResource(R.string.ledger_screen_text_05),
                                            isSelected = selectedType == "income",
                                            onClick = {
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
                                            Text(stringResource(R.string.ledger_screen_clear), fontSize = 11.sp, color = colors.inkMuted)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 备注与自然语言拆解
                                PaperTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    placeholder = stringResource(R.string.ledger_screen_placeholder_01),
                                    isUnderline = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        if (noteText.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    val parsed = FormatUtils.parseNaturalTransactionInput(noteText, activeCategories)
                                                    if (parsed.amountCents > 0) {
                                                        inputAmountString = FormatUtils.centsToYuanString(parsed.amountCents, includeSymbol = false)
                                                        selectedType = parsed.type
                                                        selectedCategory = parsed.category
                                                        noteText = parsed.title
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text(stringResource(R.string.ledger_screen_disassemble), fontSize = 11.sp, color = colors.accentWarm)
                                            }
                                        }
                                    }
                                )

                                // 自然语言实时预览（AOPT-07a）：备注可被解析且金额尚未输入时给出提示，
                                // 盖章时自动采纳，避免用户以为「写了 42.5 就能直接提交」
                                naturalParse?.let { preview ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.ledger_screen_text_tpl_01, FormatUtils.centsToYuanString(preview.amountCents), preview.category),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 10.5.sp,
                                        color = colors.accentMoss
                                    )
                                }

                                // 低频元数据收拢成一行纸签胶囊：日期时刻 / 属地 / 分期。
                                // 默认只占一行高度，点任一胶囊展开设置抽屉（AOPT-01）
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LedgerExtraPill(
                                        text = stringResource(R.string.ledger_screen_text_06) + FormatUtils.formatLocalDateTimeDisplay(txOccurredAt),
                                        onClick = { showExtrasSheet = true }
                                    )
                                    LedgerExtraPill(
                                        text = stringResource(R.string.ledger_screen_text_07) + (selectedCityName ?: "未标记属地"),
                                        onClick = { showExtrasSheet = true }
                                    )
                                    if (selectedType == "expense") {
                                        LedgerExtraPill(
                                            text = if (isInstallment) "分期 ${installmentMonths} 期" else "一次性",
                                            highlighted = isInstallment,
                                            onClick = { showExtrasSheet = true }
                                        )
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
                                Icon(painter = painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.accentWarm)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(stringResource(R.string.ledger_screen_manage_categories), fontSize = 11.5.sp, color = colors.accentWarm)
                            }
                        }

                        CategoryPills(
                            selectedCategory = selectedCategory,
                            categories = activeCategories,
                            onCategorySelect = { cat ->
                                selectedCategory = cat
                            }
                        )

                        // 4x3 触觉小键盘 (紧凑自适应高度，单屏无滚动直达)
                        // MOB-P0-13：软键盘可见时隐藏，避免双键盘并存并把提交按钮挤出可触区
                        if (showKeypad) {
                            TactileKeypad(
                                onDigitClick = { appendDigit(it) },
                                onDotClick = { appendDot() },
                                onBackspaceClick = { backspace() },
                                onClearAll = { clearAll() },
                                keyHeight = keypadKeyHeightScaled
                            )
                        }

                        // 记入账本提交按钮
                        // 自然语言兜底（AOPT-07a）：金额未敲但备注写得出「42.5 午餐」时，
                        // 盖章直接采纳解析结果，而不是让按钮一直灰着
                        val lastChanceParse = naturalParse?.takeIf { it.amountCents > 0 }
                        val effectiveCents = FormatUtils.yuanStringToCents(inputAmountString)
                            .takeIf { it > 0 } ?: lastChanceParse?.amountCents ?: 0L
                        TerracottaStampButton(
                            text = stringResource(R.string.ledger_screen_text_08),
                            enabled = effectiveCents > 0,
                            onClick = {
                                val typedCents = FormatUtils.yuanStringToCents(inputAmountString)
                                val fallback = if (typedCents <= 0) lastChanceParse else null
                                val amountCents = if (typedCents > 0) typedCents else (fallback?.amountCents ?: 0L)
                                if (amountCents <= 0) return@TerracottaStampButton

                                val finalCategory = fallback?.category ?: selectedCategory
                                val finalType = fallback?.type ?: selectedType
                                val finalNote = if (fallback != null) fallback.title else noteText
                                val title = if (finalNote.isNotBlank()) finalNote.trim() else finalCategory

                                scope.launch {
                                    val monthlyPay = if (isInstallment && installmentMonths > 0) amountCents / installmentMonths else 0L
                                    syncManager.createTransactionOptimistic(
                                        amountCents = amountCents,
                                        type = finalType,
                                        category = finalCategory,
                                        title = title,
                                        note = finalNote.takeIf { it.isNotBlank() },
                                        paymentMethod = selectedPaymentMethod,
                                        occurredAt = FormatUtils.toBeijingIsoString(txOccurredAt),
                                        cityCode = selectedCityCode,
                                        isInstallment = isInstallment,
                                        installmentChannelCode = installmentChannelCode.takeIf { isInstallment },
                                        installmentTotalAmount = amountCents.takeIf { isInstallment },
                                        installmentMonths = installmentMonths.takeIf { isInstallment },
                                        installmentMonthlyPay = monthlyPay.takeIf { isInstallment },
                                        installmentRemainMonths = installmentMonths.takeIf { isInstallment },
                                        repaymentDay = repaymentDay.takeIf { isInstallment }
                                    )
                                    ArchiveToastManager.show("已盖印记下: ¥ ${FormatUtils.centsToYuanString(amountCents)}")
                                    clearAll()
                                }
                            },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                    // 记账附加设置抽屉：日期时刻 / 属地 / 分期（AOPT-01 低频项收拢）
                    if (showExtrasSheet) {
                        ArchiveBottomSheet(
                            onDismissRequest = { showExtrasSheet = false },
                            title = stringResource(R.string.ledger_screen_title_02),
                            actionButtonText = "完成",
                            onActionClick = { showExtrasSheet = false }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = stringResource(R.string.ledger_screen_text_09),
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                                PaperDateTimeSplitRow(
                                    selectedDateTime = txOccurredAt,
                                    onDateTimeSelected = {
                                        txOccurredAtMillis = it
                                            .atZone(FormatUtils.BeijingZone)
                                            .toInstant()
                                            .toEpochMilli()
                                    }
                                )

                                PaperDivider()

                                Text(
                                    text = stringResource(R.string.ledger_screen_text_10),
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                                PaperCityPickerField(
                                    selectedCityCode = selectedCityCode,
                                    selectedCityName = selectedCityName,
                                    onCitySelected = { code, name ->
                                        selectedCityCode = code
                                        selectedCityName = name
                                    },
                                    placeholder = stringResource(R.string.ledger_screen_placeholder_02)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { requestCurrentLocation() },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_my_location),
                                            contentDescription = null,
                                            tint = colors.accentWarm,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.ledger_screen_text_11),
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            fontSize = 11.sp,
                                            color = colors.accentWarm
                                        )
                                    }
                                }

                                if (selectedType == "expense") {
                                    PaperDivider()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isInstallment,
                                            onCheckedChange = { isInstallment = it },
                                            colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                                        )
                                        Text(
                                            text = stringResource(R.string.ledger_screen_text_12),
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontSize = 12.sp,
                                            color = colors.inkPrimary
                                        )
                                    }
                                    if (isInstallment) {
                                        if (creditChannels.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                creditChannels.forEach { ch ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (installmentChannelCode == ch.code) colors.accentWarm else colors.archiveSubtle)
                                                            .clickable {
                                                                installmentChannelCode = ch.code
                                                                repaymentDay = ch.repaymentDay
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = ch.name,
                                                            fontSize = 11.sp,
                                                            color = if (installmentChannelCode == ch.code) colors.archivePaper else colors.inkSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(3, 6, 12, 24).forEach { m ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (installmentMonths == m) colors.accentWarm else colors.archiveSubtle)
                                                            .clickable { installmentMonths = m }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.ledger_screen_text_tpl_02, m),
                                                            fontSize = 11.sp,
                                                            color = if (installmentMonths == m) colors.archivePaper else colors.inkSecondary
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = stringResource(R.string.ledger_screen_text_tpl_03, repaymentDay),
                                                fontSize = 11.sp,
                                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                color = colors.inkMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ==========================================
                    // Tab 1: 账单明细流水与月度汇总光谱
                    // ==========================================
                    // 下拉刷新（AOPT-06）：账单明细滚到顶继续下拉即同步
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = isRefreshingLedger,
                        onRefresh = refreshLedger,
                        modifier = Modifier.weight(1f)
                    ) {
                    TransactionsLedgerTab(
                        transactions = transactions,
                        monthlyTotals = monthlyTotals,
                        monthlyCategoryTotals = monthlyCategoryTotals,
                        isPrivacyMasked = isPrivacyMasked,
                        isRefreshing = isRefreshingLedger,
                        selectedYearMonth = selectedYearMonth,
                        onMonthChange = { selectedYearMonth = it },
                        onEdit = { editingTransaction = it },
                        onDelete = { tx ->
                            // MOB-P0-14：账目删除先弹确认框，再叠加生物识别二次签名。
                            // 原先生物识别关闭时是「零确认直接删」，与其他破坏性路径不一致。
                            transactionToDelete = tx
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    }
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
        }

        // 1. 编辑记账流水底部抽屉
        editingTransaction?.let { tx ->
            // MOB-P1-59：账目修订草稿 —— 误关抽屉 / 系统回收后重新打开自动恢复已填内容。
            val draftStore = remember { DraftStore.get(context) }
            val draftPrefix = remember(tx.id) { "tx_${tx.id}_" }

            var editAmountYuan by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}amount")
                        ?: FormatUtils.centsToYuanString(tx.amount, includeSymbol = false)
                )
            }
            var editType by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}type") ?: tx.type)
            }
            var editCategory by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}category") ?: tx.category)
            }
            var editTitle by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}title") ?: tx.title)
            }
            var editNote by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}note") ?: (tx.note ?: ""))
            }
            var editPaymentMethod by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}payment") ?: (tx.paymentMethod ?: "微信支付")
                )
            }
            var editCityCode by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}city_code") ?: (tx.cityCode ?: ""))
            }
            var editCityName by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}city_name")
                        ?: (tx.cityCode?.let { CityDictionary.cityNameForCode(it) } ?: "")
                )
            }
            var editOccurredAt by remember(tx.id) {
                // 草稿同样覆盖「发生时间」（epoch millis）：原先不落草稿，
                // 误关抽屉重开后金额/分类/分期全部恢复、唯独日期被静默重置回原值。
                mutableStateOf(
                    draftStore.getString("${draftPrefix}occurred_at_millis")?.toLongOrNull()
                        ?.let { millis ->
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(FormatUtils.BeijingZone)
                                .toLocalDateTime()
                        }
                        ?: FormatUtils.parseToLocalDateTime(tx.occurredAt)
                )
            }
            var editIsInstallment by remember(tx.id) {
                mutableStateOf(draftStore.getString("${draftPrefix}installment")?.toBoolean() ?: (tx.isInstallment ?: false))
            }
            var editInstallmentChannelCode by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}channel")
                        ?: (tx.installmentChannelCode ?: (creditChannels.firstOrNull()?.code ?: "huabei"))
                )
            }
            var editInstallmentMonths by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}months")?.toIntOrNull()
                        ?: (tx.installmentMonths?.takeIf { it > 0 } ?: 12)
                )
            }
            var editRepaymentDay by remember(tx.id) {
                mutableStateOf(
                    draftStore.getString("${draftPrefix}repay_day")?.toIntOrNull()?.takeIf { it in 1..31 }
                        ?: (tx.repaymentDay?.takeIf { it in 1..31 }
                            ?: (creditChannels.firstOrNull { it.code == editInstallmentChannelCode }?.repaymentDay ?: 10))
                )
            }

            // 字段变化即落草稿
            LaunchedEffect(
                editAmountYuan, editType, editCategory, editTitle, editNote,
                editPaymentMethod, editCityCode, editCityName, editOccurredAt,
                editIsInstallment, editInstallmentChannelCode, editInstallmentMonths, editRepaymentDay
            ) {
                draftStore.putString("${draftPrefix}amount", editAmountYuan)
                draftStore.putString("${draftPrefix}type", editType)
                draftStore.putString("${draftPrefix}category", editCategory)
                draftStore.putString("${draftPrefix}title", editTitle)
                draftStore.putString("${draftPrefix}note", editNote)
                draftStore.putString("${draftPrefix}payment", editPaymentMethod)
                draftStore.putString("${draftPrefix}city_code", editCityCode)
                draftStore.putString("${draftPrefix}city_name", editCityName)
                draftStore.putString(
                    "${draftPrefix}occurred_at_millis",
                    editOccurredAt.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli().toString()
                )
                draftStore.putString("${draftPrefix}installment", editIsInstallment.toString())
                draftStore.putString("${draftPrefix}channel", editInstallmentChannelCode)
                draftStore.putString("${draftPrefix}months", editInstallmentMonths.toString())
                draftStore.putString("${draftPrefix}repay_day", editRepaymentDay.toString())
            }

            ArchiveBottomSheet(
                onDismissRequest = { editingTransaction = null },
                title = stringResource(R.string.ledger_screen_title_03),
                actionButtonText = "保存修订",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(editAmountYuan)
                    if (cents > 0) {
                        scope.launch {
                            val monthlyPay = if (editIsInstallment && editInstallmentMonths > 0) cents / editInstallmentMonths else 0L
                            // ── 分期三态（后端契约）──────────────────────────
                            // 关键：原先这里发的是 `isInstallment = editIsInstallment && editType == "expense"`，
                            // 也就是「不是分期」时发 **false**。
                            // 但客户端用 encodeDefaults=false，false 恰好是默认值 →
                            // 整个 key 被**省略** → 服务端收到 nil → 走「保持原值」分支 →
                            // **分期根本没被取消**，界面却弹「已保存账目修订」。
                            // 更糟的是本地缓存被乐观写成 isInstallment=false，
                            // 于是本地显示「一次性」、服务端仍是「分期」，两端当场分叉。
                            //
                            // 现在按三态分别处理：
                            //   仍是分期   → is_installment: true
                            //   取消分期   → is_installment: false + clear_installment: true
                            //   与分期无关 → is_installment 不携带（服务端保持原值）
                            val wantInstallment = editIsInstallment && editType == "expense"
                            val wasInstallment = (tx.isInstallment ?: false)
                            val cancelInstallment = !wantInstallment && wasInstallment
                            val req = CreateTransactionRequest(
                                amount = cents,
                                type = editType,
                                category = editCategory.trim(),
                                // 空串是显式清空；列表展示层已有「空标题回退类目」。
                                title = editTitle.trim(),
                                note = editNote.trim(),
                                paymentMethod = editPaymentMethod.trim(),
                                // 契约 A：清空属地不再被「省略 = 保持原值」吞掉——
                                // 城市选择器清空后发 clear_city: true，本地乐观写同步清空，
                                // 否则本地显示「无属地」、服务端仍是旧城市，下次拉取又「复活」。
                                cityCode = editCityCode.trim().takeIf { it.isNotBlank() },
                                clearCity = editCityCode.isBlank(),
                                occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
                                wishId = tx.wishId,
                                footprintId = tx.footprintId,
                                isInstallment = when {
                                    wantInstallment -> true
                                    cancelInstallment -> false
                                    else -> null // 不携带：本次与分期无关，服务端保持原值
                                },
                                clearInstallment = cancelInstallment,
                                installmentChannelCode = editInstallmentChannelCode.takeIf { wantInstallment },
                                installmentTotalAmount = cents.takeIf { wantInstallment },
                                installmentMonths = editInstallmentMonths.takeIf { wantInstallment },
                                installmentMonthlyPay = monthlyPay.takeIf { wantInstallment },
                                installmentRemainMonths = tx.installmentRemainMonths?.takeIf { wantInstallment } ?: editInstallmentMonths.takeIf { wantInstallment },
                                repaymentDay = editRepaymentDay.takeIf { wantInstallment }
                            )
                            syncManager.updateTransactionOptimistic(tx.id, req)
                            // MOB-P1-59：提交成功后清空草稿
                            draftStore.clearPrefix(draftPrefix)
                            editingTransaction = null
                            ArchiveToastManager.show("已保存账目修订")
                        }
                    } else {
                        // 非法输入（如「一百二十」）换算成 0 时必须提示，否则用户以为按钮坏了
                        ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("expense" to "支出", "income" to "收入").forEach { (k, label) ->
                            PaperFilterPill(
                                selected = editType == k,
                                onClick = {
                                    editType = k
                                    if (k == "income") editIsInstallment = false
                                },
                                label = label
                            )
                        }
                    }

                    PaperTextField(
                        value = editAmountYuan,
                        onValueChange = { editAmountYuan = it },
                        label = stringResource(R.string.ledger_screen_label_02),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = stringResource(R.string.ledger_screen_label_03),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = stringResource(R.string.ledger_screen_label_04),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = editPaymentMethod,
                        onValueChange = { editPaymentMethod = it },
                        label = stringResource(R.string.ledger_screen_label_05),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperDateTimeSplitRow(
                        selectedDateTime = editOccurredAt,
                        onDateTimeSelected = { editOccurredAt = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PaperCityPickerField(
                        selectedCityCode = editCityCode,
                        selectedCityName = editCityName,
                        onCitySelected = { code, name ->
                            editCityCode = code
                            editCityName = name
                        },
                        placeholder = stringResource(R.string.ledger_screen_placeholder_02)
                    )

                    if (editType == "expense") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = editIsInstallment,
                                onCheckedChange = { editIsInstallment = it },
                                colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                            )
                            Text(
                                text = stringResource(R.string.ledger_screen_text_12),
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkPrimary
                            )
                        }

                        if (editIsInstallment) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (creditChannels.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        creditChannels.forEach { ch ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (editInstallmentChannelCode == ch.code) colors.accentWarm else colors.archiveSubtle)
                                                    .clickable {
                                                        editInstallmentChannelCode = ch.code
                                                        editRepaymentDay = ch.repaymentDay
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = ch.name,
                                                    fontSize = 11.sp,
                                                    color = if (editInstallmentChannelCode == ch.code) colors.archivePaper else colors.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(3, 6, 12, 24).forEach { m ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (editInstallmentMonths == m) colors.accentWarm else colors.archiveSubtle)
                                                    .clickable { editInstallmentMonths = m }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.ledger_screen_text_tpl_02, m),
                                                    fontSize = 11.sp,
                                                    color = if (editInstallmentMonths == m) colors.archivePaper else colors.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.ledger_screen_text_tpl_04, editRepaymentDay),
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    }

                    PaperTextEditor(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = stringResource(R.string.ledger_screen_label_06),
                        minHeight = 70.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. 校准初始流动资金底数抽屉
        // MOB-P0-14：彻底删除装备记录确认
    gearToDelete?.let { gear ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.ledger_screen_title_04),
            content = "确认彻底删除「${gear.name}」的设备记录？服役时长与折旧数据将一并清空，此操作不可逆。",
            confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    runCatching {
                        apiClient.deleteGearAsset(gear.id)
                        LedgerRepository.refreshAssets(apiClient)
                        ArchiveToastManager.show("已彻底删除该设备记录")
                    }.onFailure {
                        ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                    }
                    gearToDelete = null
                    sellingGearItem = null
                }
            },
            onDismiss = { gearToDelete = null }
        )
    }

    // MOB-P0-14：账目删除二次确认（确认后再叠加生物识别）
    transactionToDelete?.let { tx ->
        val performDelete: () -> Unit = {
            scope.launch {
                // MOB-P2-47：删除后给一个「撤销」机会
                val ticket = syncManager.deleteTransaction(tx.id)
                LedgerRepository.refreshAssets(apiClient)
                if (ticket != null) {
                    ArchiveToastManager.showWithAction("已删除记账明细", "撤销") {
                        scope.launch {
                            val restored = syncManager.undoDeleteTransaction(ticket)
                            ArchiveToastManager.show(
                                if (restored) "已恢复该笔账目" else "已同步到服务端，无法撤销",
                                isError = !restored
                            )
                        }
                    }
                } else {
                    ArchiveToastManager.show("已删除记账明细")
                }
            }
        }
        ArchiveConfirmDialog(
            title = stringResource(R.string.ledger_screen_title_05),
            content = "确认删除「${tx.title.ifBlank { tx.category }}」的收支记录？金额 ¥${FormatUtils.centsToYuanString(tx.amount)}，此操作不可逆。",
            confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
            isDestructive = true,
            onConfirm = {
                transactionToDelete = null
                val activity = context as? FragmentActivity
                if (credentialStore.isBiometricsEnabled && activity != null &&
                    BiometricHelper.canAuthenticate(activity)
                ) {
                    BiometricHelper.authenticate(
                        activity = activity,
                        title = deleteTxBiometricTitle,
                        subtitle = "删除收支记录需要二次身份确认",
                        onSuccess = { performDelete() },
                        onError = { msg -> ArchiveToastManager.show(msg, isError = true) }
                    )
                } else {
                    performDelete()
                }
            },
            onDismiss = { transactionToDelete = null }
        )
    }

    if (showCalibrateBalanceDialog) {
            var inputLiquidYuan by remember {
                mutableStateOf(
                    FormatUtils.centsToYuanString(assetsOverview?.initialLiquidBalance ?: 0L, includeSymbol = false)
                )
            }
            ArchiveBottomSheet(
                onDismissRequest = { showCalibrateBalanceDialog = false },
                title = stringResource(R.string.ledger_screen_title_07),
                actionButtonText = "确认校准",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(inputLiquidYuan)
                    scope.launch {
                        runCatching {
                            apiClient.updateLiquidBalance(cents)
                            LedgerRepository.refreshAssets(apiClient)
                            ArchiveToastManager.show("流动资金底数已校准")
                        }
                        showCalibrateBalanceDialog = false
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.ledger_screen_text_13),
                        fontSize = 13.sp,
                        color = colors.inkSecondary,
                        lineHeight = 18.sp
                    )
                    PaperTextField(
                        value = inputLiquidYuan,
                        onValueChange = { inputLiquidYuan = it },
                        label = stringResource(R.string.ledger_screen_label_07),
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
                title = stringResource(R.string.ledger_screen_title_08),
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
                            LedgerRepository.refreshAssets(apiClient)
                            syncManager.pullLedgerData()
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
                        label = stringResource(R.string.ledger_screen_label_08),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = gearSpec,
                        onValueChange = { gearSpec = it },
                        label = stringResource(R.string.ledger_screen_label_09),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = gearPrice,
                        onValueChange = { gearPrice = it },
                        label = stringResource(R.string.ledger_screen_label_10),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAutoTx,
                            onCheckedChange = { isAutoTx = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                        )
                        Text(stringResource(R.string.ledger_screen_sync_purchase), fontSize = 12.sp, color = colors.inkPrimary)
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
                title = stringResource(R.string.ledger_screen_title_tpl_01, gear.name),
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
                            LedgerRepository.refreshAssets(apiClient)
                            syncManager.pullLedgerData()
                            ArchiveToastManager.show("已变卖归档并入账")
                        }
                        sellingGearItem = null
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ledger_screen_text_14),
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                    PaperTextField(
                        value = soldPriceYuan,
                        onValueChange = { soldPriceYuan = it },
                        label = stringResource(R.string.ledger_screen_label_11),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = sellNote,
                        onValueChange = { sellNote = it },
                        label = stringResource(R.string.ledger_screen_label_12),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            // MOB-P0-14：破坏性删除先确认
                            gearToDelete = gear
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.ledger_screen_delete_equipment), fontSize = 11.sp, color = colors.accentWarm)
                    }
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
                    scope.launch { reloadCategoriesAndAssets(force = true) }
                }
            )
        }

        // 6. 分类字典管理对话框
        if (showCategoriesManagerDialog) {
            CategoriesManagerDialog(
                apiClient = apiClient,
                onDismiss = { showCategoriesManagerDialog = false },
                onCategoriesChanged = {
                    scope.launch { reloadCategoriesAndAssets(force = true) }
                }
            )
        }
    }
}
}
