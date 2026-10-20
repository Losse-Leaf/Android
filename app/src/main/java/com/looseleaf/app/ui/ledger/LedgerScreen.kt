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

    val transactions by syncManager.transactionsFlow.collectAsStateWithLifecycle()

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
    var txOccurredAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone)) }

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
    var isInstallment by remember { mutableStateOf(false) }
    var installmentChannelCode by remember { mutableStateOf("huabei") }
    var installmentMonths by remember { mutableStateOf(12) }
    var repaymentDay by remember { mutableStateOf(10) }

    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }

    // 对话框状态
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showCalibrateBalanceDialog by remember { mutableStateOf(false) }
    var showAddGearDialog by remember { mutableStateOf(false) }
    var sellingGearItem by remember { mutableStateOf<GearItem?>(null) }
    var showCreditChannelsDialog by remember { mutableStateOf(false) }
    var showCategoriesManagerDialog by remember { mutableStateOf(false) }
    // 记账附加设置抽屉（日期时刻 / 属地 / 分期）：低频项从首屏收进抽屉（AOPT-01）
    var showExtrasSheet by remember { mutableStateOf(false) }

    // 账单流水筛选月份 (默认当前年月)
    var selectedYearMonth by remember {
        mutableStateOf(YearMonth.now(FormatUtils.BeijingZone))
    }

    suspend fun reloadCategoriesAndAssets(
        forceLoadingSpinner: Boolean = false,
        force: Boolean = false
    ) {
        LedgerRepository.refreshAll(apiClient, force = force, showSpinner = forceLoadingSpinner)
        val latest = LedgerRepository.snapshot.value
        if (latest.paymentMethods.isNotEmpty() && selectedPaymentMethod !in latest.paymentMethods) {
            selectedPaymentMethod = latest.paymentMethods.first()
        }
        if (latest.creditChannels.isNotEmpty() && latest.creditChannels.none { it.code == installmentChannelCode }) {
            installmentChannelCode = latest.creditChannels.first().code
        }
    }

    LaunchedEffect(Unit) {
        reloadCategoriesAndAssets(forceLoadingSpinner = false)
    }

    // 账单明细下拉刷新状态（AOPT-06）：必须在 reloadCategoriesAndAssets 之后定义
    var isRefreshingLedger by remember { mutableStateOf(false) }
    val refreshLedger: () -> Unit = {
        if (!isRefreshingLedger) {
            scope.launch {
                isRefreshingLedger = true
                val res = syncManager.pullRemoteData()
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
        txOccurredAt = LocalDateTime.now(FormatUtils.BeijingZone)
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
                                // 用户主动对齐必须区分成败：失败只弹「已对齐」是撒谎
                                val result = syncManager.pullRemoteData()
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
                                            text = "支出",
                                            isSelected = selectedType == "expense",
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
                                            Text("清空", fontSize = 11.sp, color = colors.inkMuted)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 备注与自然语言拆解
                                PaperTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    placeholder = "备注…（可写「42.5 午餐」自动拆解）",
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
                                                Text("拆解", fontSize = 11.sp, color = colors.accentWarm)
                                            }
                                        }
                                    }
                                )

                                // 自然语言实时预览（AOPT-07a）：备注可被解析且金额尚未输入时给出提示，
                                // 盖章时自动采纳，避免用户以为「写了 42.5 就能直接提交」
                                val naturalPreview = remember(noteText, activeCategories) {
                                    if (noteText.isBlank() || inputAmountString != "0") null
                                    else FormatUtils.parseNaturalTransactionInput(noteText, activeCategories)
                                        .takeIf { it.amountCents > 0 }
                                }
                                naturalPreview?.let { preview ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "识别到 ${FormatUtils.centsToYuanString(preview.amountCents)} · ${preview.category} · 点「盖章」自动采纳",
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
                                        text = "🕘 " + FormatUtils.formatLocalDateTimeDisplay(txOccurredAt),
                                        onClick = { showExtrasSheet = true }
                                    )
                                    LedgerExtraPill(
                                        text = "📍 " + (selectedCityName ?: "未标记属地"),
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
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.accentWarm)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("管理分类", fontSize = 11.5.sp, color = colors.accentWarm)
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
                        TactileKeypad(
                            onDigitClick = { appendDigit(it) },
                            onDotClick = { appendDot() },
                            onBackspaceClick = { backspace() },
                            onClearAll = { clearAll() },
                            keyHeight = keypadKeyHeight
                        )

                        // 记入账本提交按钮
                        // 自然语言兜底（AOPT-07a）：金额未敲但备注写得出「42.5 午餐」时，
                        // 盖章直接采纳解析结果，而不是让按钮一直灰着
                        val lastChanceParse = remember(noteText, activeCategories, inputAmountString) {
                            if (inputAmountString != "0") null
                            else FormatUtils.parseNaturalTransactionInput(noteText, activeCategories)
                                .takeIf { it.amountCents > 0 }
                        }
                        val effectiveCents = FormatUtils.yuanStringToCents(inputAmountString)
                            .takeIf { it > 0 } ?: lastChanceParse?.amountCents ?: 0L
                        TerracottaStampButton(
                            text = "记入账本",
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
                            title = "记账附加设置",
                            actionButtonText = "完成",
                            onActionClick = { showExtrasSheet = false }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "发生时刻",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                                PaperDateTimeSplitRow(
                                    selectedDateTime = txOccurredAt,
                                    onDateTimeSelected = { txOccurredAt = it }
                                )

                                PaperDivider()

                                Text(
                                    text = "属地城市",
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
                                    placeholder = "选择城市…"
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
                                            imageVector = Icons.Outlined.MyLocation,
                                            contentDescription = null,
                                            tint = colors.accentWarm,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "使用当前定位",
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
                                            text = "分期还款入账",
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
                                                            fontSize = 10.sp,
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
                                                            text = "${m}期",
                                                            fontSize = 10.sp,
                                                            color = if (installmentMonths == m) colors.archivePaper else colors.inkSecondary
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "每月${repaymentDay}日还款",
                                                fontSize = 10.sp,
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
                        isPrivacyMasked = isPrivacyMasked,
                        selectedYearMonth = selectedYearMonth,
                        onMonthChange = { selectedYearMonth = it },
                        onEdit = { editingTransaction = it },
                        onDelete = { tx ->
                            val performDelete: () -> Unit = {
                                scope.launch {
                                    syncManager.deleteTransaction(tx.id)
                                    ArchiveToastManager.show("已删除记账明细")
                                    LedgerRepository.refreshAssets(apiClient)
                                }
                            }
                            val activity = context as? FragmentActivity
                            if (credentialStore.isBiometricsEnabled && activity != null &&
                                BiometricHelper.canAuthenticate(activity)
                            ) {
                                BiometricHelper.authenticate(
                                    activity = activity,
                                    title = "验证以删除账目",
                                    subtitle = "删除收支记录需要二次身份确认",
                                    onSuccess = { performDelete() },
                                    onError = { msg -> ArchiveToastManager.show(msg, isError = true) }
                                )
                            } else {
                                performDelete()
                            }
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
            var editCityCode by remember(tx.id) { mutableStateOf(tx.cityCode ?: "") }
            var editCityName by remember(tx.id) {
                mutableStateOf(tx.cityCode?.let { CityDictionary.cityNameForCode(it) } ?: "")
            }
            var editOccurredAt by remember(tx.id) {
                mutableStateOf(FormatUtils.parseToLocalDateTime(tx.occurredAt))
            }
            var editIsInstallment by remember(tx.id) { mutableStateOf(tx.isInstallment ?: false) }
            var editInstallmentChannelCode by remember(tx.id) {
                mutableStateOf(tx.installmentChannelCode ?: (creditChannels.firstOrNull()?.code ?: "huabei"))
            }
            var editInstallmentMonths by remember(tx.id) { mutableStateOf(tx.installmentMonths?.takeIf { it > 0 } ?: 12) }
            var editRepaymentDay by remember(tx.id) {
                mutableStateOf(tx.repaymentDay?.takeIf { it in 1..31 } ?: (creditChannels.firstOrNull { it.code == editInstallmentChannelCode }?.repaymentDay ?: 10))
            }

            ArchiveBottomSheet(
                onDismissRequest = { editingTransaction = null },
                title = "修订记账明细",
                actionButtonText = "保存修订",
                onActionClick = {
                    val cents = FormatUtils.yuanStringToCents(editAmountYuan)
                    if (cents > 0) {
                        scope.launch {
                            val monthlyPay = if (editIsInstallment && editInstallmentMonths > 0) cents / editInstallmentMonths else 0L
                            val req = CreateTransactionRequest(
                                amount = cents,
                                type = editType,
                                category = editCategory.trim(),
                                title = editTitle.trim().ifBlank { editCategory.trim() },
                                note = editNote.trim().takeIf { it.isNotBlank() },
                                paymentMethod = editPaymentMethod.trim(),
                                cityCode = editCityCode.trim().takeIf { it.isNotBlank() },
                                occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
                                wishId = tx.wishId,
                                footprintId = tx.footprintId,
                                isInstallment = editIsInstallment && editType == "expense",
                                installmentChannelCode = editInstallmentChannelCode.takeIf { editIsInstallment && editType == "expense" },
                                installmentTotalAmount = cents.takeIf { editIsInstallment && editType == "expense" },
                                installmentMonths = editInstallmentMonths.takeIf { editIsInstallment && editType == "expense" },
                                installmentMonthlyPay = monthlyPay.takeIf { editIsInstallment && editType == "expense" },
                                installmentRemainMonths = tx.installmentRemainMonths?.takeIf { editIsInstallment && editType == "expense" } ?: editInstallmentMonths.takeIf { editIsInstallment && editType == "expense" },
                                repaymentDay = editRepaymentDay.takeIf { editIsInstallment && editType == "expense" }
                            )
                            syncManager.updateTransactionOptimistic(tx.id, req)
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
                        placeholder = "选择城市…"
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
                                text = "分期还款入账",
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
                                                    fontSize = 10.sp,
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
                                                    text = "${m}期",
                                                    fontSize = 10.sp,
                                                    color = if (editInstallmentMonths == m) colors.archivePaper else colors.inkSecondary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "每月${editRepaymentDay}日还款",
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    }

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
                            LedgerRepository.refreshAssets(apiClient)
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
                            LedgerRepository.refreshAssets(apiClient)
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
                        label = "装备名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = gearSpec,
                        onValueChange = { gearSpec = it },
                        label = "规格参数",
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
                            LedgerRepository.refreshAssets(apiClient)
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

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    apiClient.deleteGearAsset(gear.id)
                                    LedgerRepository.refreshAssets(apiClient)
                                    ArchiveToastManager.show("已彻底删除该设备记录")
                                }.onFailure {
                                    ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                                }
                                sellingGearItem = null
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("彻底删除此设备", fontSize = 11.sp, color = colors.accentWarm)
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
