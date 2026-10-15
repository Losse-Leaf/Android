package com.looseleaf.app.ui.ledger

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.AssetsOverview
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperBadge
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.ui.components.PaperDivider
import com.looseleaf.app.ui.components.TerracottaStampButton
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * 极速触觉记账与口袋账本 (LedgerScreen) - docs/mobile.md §3.1
 * 单拇指可达、无脑盲打、严格分位整型存储、细腻触感反馈
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

    val transactions by syncManager.transactionsFlow.collectAsState(initial = emptyList())

    var expenseCategories by remember { mutableStateOf(DefaultExpenseCategories) }
    var incomeCategories by remember { mutableStateOf(DefaultIncomeCategories) }
    var paymentMethods by remember { mutableStateOf(listOf("微信支付", "支付宝", "银行卡", "现金")) }

    var inputAmountString by remember { mutableStateOf("0") }
    var selectedType by remember { mutableStateOf("expense") } // "expense" | "income"
    var selectedCategory by remember { mutableStateOf(inferRecommendedCategory()) }
    var selectedPaymentMethod by remember { mutableStateOf("微信支付") }
    var noteText by remember { mutableStateOf("") }
    var showRecentList by remember { mutableStateOf(false) }
    var showAssetsDialog by remember { mutableStateOf(false) }
    var assetsOverview by remember { mutableStateOf<AssetsOverview?>(null) }
    var isPrivacyMasked by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }

    LaunchedEffect(Unit) {
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
            assetsOverview = apiClient.fetchAssetsOverview()
        }
    }

    val activeCategories = if (selectedType == "income") incomeCategories else expenseCategories

    fun appendDigit(digit: String) {
        if (inputAmountString == "0") {
            inputAmountString = digit
        } else {
            // 最多支持两位小数
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
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏：标题与明细列表切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "触觉账本",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "口袋单手盲打 · 严格分位整型存储",
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
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    isPrivacyMasked = false
                                }
                            } else {
                                isPrivacyMasked = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPrivacyMasked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "隐蔽敏感金额",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            haptics.tapKey()
                            showAssetsDialog = true
                        }
                    ) {
                        Text(
                            text = "资产大盘",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }

                    TextButton(
                        onClick = {
                            haptics.tapKey()
                            showRecentList = !showRecentList
                        }
                    ) {
                        Text(
                            text = if (showRecentList) "返回小键盘" else "近期待办 (${transactions.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.accentWarm
                        )
                    }
                }
            }

            if (showRecentList) {
                // 近期账目流水列表
                RecentTransactionsList(
                    transactions = transactions,
                    isPrivacyMasked = isPrivacyMasked,
                    onDelete = { tx ->
                        scope.launch {
                            haptics.tearDelete()
                            syncManager.deleteTransaction(tx.id)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 记账面板
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 金额展示区
                    PaperCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column {
                            // 收支切换胶囊
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.archivePaper)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(16.dp))
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
                                        if (selectedCategory !in incomeCategories) {
                                            selectedCategory = incomeCategories.firstOrNull() ?: "薪资"
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 大金额数字
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (selectedType == "income") "+ ¥" else "¥",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedType == "income") colors.accentMoss else colors.accentWarm,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPrivacyMasked) "****" else inputAmountString,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 42.sp,
                                    letterSpacing = 1.sp,
                                    color = colors.inkPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 备注快速输入 + 自然语言语义拆解按钮
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = {
                                    Text(
                                        text = "备注或输入“42.5 手冲咖啡”点右侧拆解",
                                        fontSize = 12.sp,
                                        color = colors.inkMuted
                                    )
                                },
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
                                            }
                                        ) {
                                            Text("语义拆解", fontSize = 11.sp, color = colors.accentWarm)
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentWarm,
                                    unfocusedBorderColor = colors.borderSubtle,
                                    focusedTextColor = colors.inkPrimary,
                                    unfocusedTextColor = colors.inkPrimary
                                )
                            )
                        }
                    }

                    // 分类胶囊滑动栏
                    CategoryPills(
                        selectedCategory = selectedCategory,
                        categories = activeCategories,
                        onCategorySelect = { cat ->
                            haptics.tapKey()
                            selectedCategory = cat
                        }
                    )

                    // 4x3 拟物微隆起大键位键盘
                    TactileKeypad(
                        onDigitClick = { appendDigit(it) },
                        onDotClick = { appendDot() },
                        onBackspaceClick = { backspace() },
                        onClearAll = { clearAll() },
                        haptics = haptics
                    )

                    // 陶土赭石色高对比【盖章记下】提交条
                    TerracottaStampButton(
                        text = "【 盖章记下 (SAVE) 】",
                        enabled = inputAmountString != "0" && inputAmountString.toDoubleOrNull() != 0.0,
                        onClick = {
                            val amountDouble = inputAmountString.toDoubleOrNull() ?: 0.0
                            val amountCents = (amountDouble * 100.0).roundToLong()
                            if (amountCents <= 0) return@TerracottaStampButton

                            haptics.stampSave()
                            AudioEngine.playShutter()
                            val title = if (noteText.isNotBlank()) noteText.trim() else selectedCategory

                            scope.launch {
                                syncManager.createTransactionOptimistic(
                                    amountCents = amountCents,
                                    type = selectedType,
                                    category = selectedCategory,
                                    title = title,
                                    note = noteText.takeIf { it.isNotBlank() },
                                    paymentMethod = selectedPaymentMethod
                                )
                                Toast.makeText(context, "已盖印入账: ¥ $inputAmountString", Toast.LENGTH_SHORT).show()
                                clearAll()
                            }
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }

        if (showAssetsDialog) {
            AlertDialog(
                onDismissRequest = { showAssetsDialog = false },
                title = {
                    Text(
                        text = "个人资产管理中枢",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium
                    )
                },
                text = {
                    val ov = assetsOverview
                    if (ov == null) {
                        Text("正在同步资产大盘数据…", fontSize = 13.sp, color = colors.inkSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "净资产: ${if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.netWorth)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = "流动可用资金: ${if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.liquidBalance)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 13.sp,
                                color = colors.accentMoss
                            )
                            Text(
                                text = "在役固资沉淀 (${ov.gearSummary.activeCount} 件): ${if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.fixedAssets)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 13.sp,
                                color = colors.inkSecondary
                            )
                            Text(
                                text = "待还信贷负债: ${if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.totalLiabilities)} (月供 ${FormatUtils.centsToYuanString(ov.monthlyLiabilities)})",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 13.sp,
                                color = colors.accentWarm
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAssetsDialog = false }) {
                        Text("关闭", color = colors.accentWarm)
                    }
                }
            )
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
    val shape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) colors.accentWarm else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 12.sp,
            color = if (isSelected) colors.archivePaper else colors.inkSecondary
        )
    }
}

@Composable
private fun RecentTransactionsList(
    transactions: List<Transaction>,
    isPrivacyMasked: Boolean,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (transactions.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text(
                text = "暂无本地记账明细",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = colors.inkMuted
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(transactions, key = { it.id }) { tx ->
            val isIncome = tx.type == "income"
            val sign = if (isIncome) "+" else "-"
            val displayAmount = if (isPrivacyMasked) "****" else "$sign${FormatUtils.centsToYuanString(tx.amount)}"
            val spectrum = SpectrumColors.colorForCategory(tx.category)

            PaperCard(modifier = Modifier.fillMaxWidth()) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            if (tx.syncStatus != "SYNCED") {
                                PaperBadge(text = "待同步", isPendingSync = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${FormatUtils.formatDisplayDateTime(tx.occurredAt)} · ${tx.category}",
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
                        IconButton(onClick = { onDelete(tx) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
