package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@Composable
internal fun CalibrateBalanceSheet(
    currentLiquidCents: Long,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onCalibrated: () -> Unit
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()
    var inputLiquidYuan by remember { mutableStateOf("") }

    val isCalibrateEnabled = inputLiquidYuan.isNotBlank()
    val doCalibrate: () -> Unit = {
        if (isCalibrateEnabled) {
            val cents = FormatUtils.AmountInput.toCents(inputLiquidYuan)
            scope.launch {
                runCatching {
                    apiClient.updateLiquidBalance(cents)
                    LedgerRepository.refreshAssets(apiClient)
                    onCalibrated()
                }.onSuccess {
                    onDismiss()
                }.onFailure { err ->
                    ArchiveToastManager.show("校准失败: ${err.message}", isError = true)
                }
            }
        }
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
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
            Text(
                text = "系统当前推算 ${FormatUtils.formatMoney(currentLiquidCents)}",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = ArchiveTypeScale.body,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ArchiveField(
                value = inputLiquidYuan,
                onValueChange = { inputLiquidYuan = FormatUtils.sanitizeDecimalYuanInput(it) },
                label = "流动资金真实总额",
                placeholder = "0.00",
                trailingText = "元",
                isNumeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (inputLiquidYuan.isNotEmpty()) {
                val inputCents = FormatUtils.AmountInput.toCents(inputLiquidYuan)
                val diffCents = inputCents - currentLiquidCents
                val diffSign = if (diffCents >= 0) "+" else "−"
                Text(
                    text = "校准后将调整 $diffSign${FormatUtils.formatMoney(abs(diffCents))}",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = ArchiveTypeScale.body,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

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

@Composable
internal fun AddGearSheet(
    creditChannels: List<CreditChannel>,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()

    var gearName by remember { mutableStateOf("") }
    var gearSpec by remember { mutableStateOf("") }
    var gearPrice by remember { mutableStateOf("") }
    var gearCategory by remember { mutableStateOf("数码器材") }
    var isAutoTx by remember { mutableStateOf(true) }
    var payMethodChoice by remember { mutableStateOf("full") }

    var periodsText by remember { mutableStateOf("12") }
    val effectivePeriods = periodsText.toIntOrNull() ?: 12

    val userChannels = remember(creditChannels) { creditChannels.userCreditChannels() }
    var selectedChannel by remember(userChannels) { mutableStateOf(userChannels.firstOrNull()) }
    var firstDueYearMonth by remember(selectedChannel) {
        mutableStateOf(defaultFirstDueYearMonth(selectedChannel?.repaymentDay ?: 10))
    }

    var isSubmittingGear by remember { mutableStateOf(false) }
    val priceCents = FormatUtils.yuanToCents(gearPrice)
    val canSubmit = gearName.isNotBlank() && gearPrice.isNotBlank() && priceCents > 0

    val doSubmitGear: () -> Unit = {
        if (canSubmit && !isSubmittingGear) {
            isSubmittingGear = true
            scope.launch {
                runCatching {
                    if (payMethodChoice == "installment") {
                        val firstDueDateStr = formatFirstDueDate(firstDueYearMonth, selectedChannel?.repaymentDay ?: 10)
                        apiClient.createInstallmentPlan(
                            CreateInstallmentPlanRequest(
                                channelId = selectedChannel?.id,
                                channelCode = selectedChannel?.code,
                                title = gearName.trim(),
                                kind = "asset",
                                principal = priceCents,
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
                                priceCents = priceCents,
                                category = gearCategory,
                                createTransaction = isAutoTx && priceCents > 0
                            )
                        )
                    }
                    LedgerRepository.refreshAssets(apiClient)
                    onSubmitted()
                }.onSuccess {
                    isSubmittingGear = false
                    onDismiss()
                }.onFailure { err ->
                    isSubmittingGear = false
                    ArchiveToastManager.show("录入装备失败: ${err.message}", isError = true)
                }
            }
        }
    }

    ArchiveSheet(
        onDismissRequest = { if (!isSubmittingGear) onDismiss() },
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
            ArchiveField(
                value = gearName,
                onValueChange = { gearName = it },
                label = "装备名称",
                placeholder = "如 MacBook Pro",
                singleLine = true
            )

            ArchiveField(
                value = gearSpec,
                onValueChange = { gearSpec = it },
                label = "规格参数",
                placeholder = "如 M3 Max 64G",
                singleLine = true
            )

            ArchiveField(
                value = gearPrice,
                onValueChange = { gearPrice = FormatUtils.sanitizeDecimalYuanInput(it) },
                label = "买入价格",
                placeholder = "0.00",
                isNumeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingText = "元",
                singleLine = true
            )

            InstallmentConfigSection(
                periodsText = periodsText,
                onPeriodsTextChange = { periodsText = it },
                channels = userChannels,
                selectedChannel = selectedChannel,
                onChannelSelected = { selectedChannel = it },
                toggleLabel = "付款方式",
                payMethodChoice = payMethodChoice,
                onPayMethodChoiceChange = { payMethodChoice = it },
                firstDueYearMonth = firstDueYearMonth,
                onFirstDueYearMonthChange = { firstDueYearMonth = it },
                principalCents = priceCents,
                emptyPreviewHint = "输入购入价格后将实时试算各期还款额",
                onCreateChannel = { name, day ->
                    runCatching {
                        val created = apiClient.createCreditChannel(
                            CreateCreditChannelRequest(name = name, repaymentDay = day)
                        )
                        LedgerRepository.refreshAssets(apiClient)
                        created
                    }.onFailure { err ->
                        ArchiveToastManager.show("创建渠道失败: ${err.message}", isError = true)
                    }.getOrNull()
                },
                fullPaymentContent = {
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
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun GearDetailSheet(
    gear: GearItem,
    allGearItems: List<GearItem>,
    isPrivacyMasked: Boolean,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onEditClick: (GearItem) -> Unit,
    onSellClick: (GearItem) -> Unit,
    onDeletedWithUndo: (ArchiveSnackbarData) -> Unit,
    onRefreshAll: () -> Unit
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()
    val companionLabel = remember(gear.purchaseDate) { formatGearCompanionDays(gear.purchaseDate) }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "装备详情",
        cancelText = "关闭",
        actionText = "编辑",
        onAction = {
            onDismiss()
            onEditClick(gear)
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
                    ArchiveDivider()
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
                    ArchiveDivider()
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
                ArchiveDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("买入价格", style = LedgerTheme.typography.caption, color = colors.textSecondary)
                    Text(
                        text = if (isPrivacyMasked) "••••" else gear.priceCents?.let { FormatUtils.formatMoney(it) } ?: "—",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
                if (!gear.note.isNullOrBlank()) {
                    ArchiveDivider()
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

            ArchiveDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArchiveTextButton(
                    text = "退役 / 出二手",
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        onSellClick(gear)
                    }
                )

                ArchiveTextButton(
                    text = "删除装备",
                    isDestructive = true,
                    onClick = {
                        val targetIdx = allGearItems.indexOfFirst { it.id == gear.id }
                        onDismiss()
                        scope.launch {
                            runCatching {
                                apiClient.deleteGearAsset(gear.id)
                                LedgerRepository.refreshAssets(apiClient)
                                onRefreshAll()
                                onDeletedWithUndo(
                                    ArchiveSnackbarData(
                                        message = "已删除装备「${gear.name}」",
                                        actionLabel = "撤销",
                                        onActionClick = {
                                            scope.launch {
                                                runCatching {
                                                    apiClient.restoreGearAsset(
                                                        RestoreGearAssetRequest(
                                                            id = gear.id,
                                                            name = gear.name,
                                                            spec = gear.spec,
                                                            category = gear.category,
                                                            status = gear.status,
                                                            priceCents = gear.priceCents ?: 0L,
                                                            purchaseDate = gear.purchaseDate,
                                                            soldPriceCents = gear.soldPriceCents ?: 0L,
                                                            soldDate = gear.soldDate,
                                                            note = gear.note,
                                                            index = targetIdx
                                                        )
                                                    )
                                                    LedgerRepository.refreshAssets(apiClient)
                                                    onRefreshAll()
                                                }
                                            }
                                        }
                                    )
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

@Composable
internal fun EditGearSheet(
    gear: GearItem,
    allGearItems: List<GearItem>,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()

    var editName by remember(gear.id) { mutableStateOf(gear.name) }
    var editSpec by remember(gear.id) { mutableStateOf(gear.spec) }
    var editCategory by remember(gear.id) { mutableStateOf(gear.category.ifBlank { "数码器材" }) }
    var editPriceYuan by remember(gear.id) {
        mutableStateOf(FormatUtils.centsToYuanString(gear.priceCents ?: 0L, includeSymbol = false))
    }
    var editPurchaseDate by remember(gear.id) {
        mutableStateOf(gear.purchaseDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }
    var editNote by remember(gear.id) { mutableStateOf(gear.note.orEmpty()) }
    var isSavingGear by remember(gear.id) { mutableStateOf(false) }

    val gearCatPresets = remember { listOf("数码器材", "摄影影音", "随身文具", "户外出行", "家居生活") }

    ArchiveSheet(
        onDismissRequest = { if (!isSavingGear) onDismiss() },
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
                    priceCents = FormatUtils.yuanToCents(editPriceYuan),
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
                        onSaved()
                        ArchiveToastManager.show("装备信息已保存")
                    }.onSuccess {
                        isSavingGear = false
                        onDismiss()
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
            ArchiveField(
                value = editName,
                onValueChange = { editName = it },
                label = "装备名称",
                singleLine = true
            )

            ArchiveField(
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

            ArchiveField(
                value = editPriceYuan,
                onValueChange = { editPriceYuan = FormatUtils.sanitizeDecimalYuanInput(it) },
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

            ArchiveField(
                value = editNote,
                onValueChange = { editNote = it },
                label = "备注",
                placeholder = "选填",
                singleLine = true
            )
        }
    }
}

@Composable
internal fun SellGearSheet(
    gear: GearItem,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSold: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var soldPriceYuan by remember(gear.id) {
        mutableStateOf(FormatUtils.centsToYuanString(gear.priceCents ?: 0L, includeSymbol = false))
    }
    var sellNote by remember(gear.id) { mutableStateOf("二手回血") }
    var isSelling by remember(gear.id) { mutableStateOf(false) }

    ArchiveSheet(
        onDismissRequest = { if (!isSelling) onDismiss() },
        title = "退役变卖 · ${gear.name}",
        actionText = if (isSelling) "提交中…" else "变卖",
        isActionEnabled = FormatUtils.yuanToCents(soldPriceYuan) > 0 && !isSelling,
        onAction = {
            val cents = FormatUtils.yuanToCents(soldPriceYuan)
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
                        onSold()
                        ArchiveToastManager.show("已登记二手回血")
                    }.onSuccess {
                        isSelling = false
                        onDismiss()
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
            ArchiveField(
                value = soldPriceYuan,
                onValueChange = { soldPriceYuan = FormatUtils.sanitizeDecimalYuanInput(it) },
                label = "二手成交价格 (元)",
                isNumeric = true,
                singleLine = true
            )
            ArchiveField(
                value = sellNote,
                onValueChange = { sellNote = it },
                label = "回血备注",
                singleLine = true
            )
        }
    }
}
