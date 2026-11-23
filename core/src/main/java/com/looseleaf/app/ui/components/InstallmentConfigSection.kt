package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

val BUILTIN_CREDIT_CHANNEL_CODES: Set<String> = setOf(
    "HUABEI", "BAITIAO", "CUSTOM_INSTALLMENT",
    "huabei", "baitiao", "custom_installment"
)

fun List<CreditChannel>.userCreditChannels(): List<CreditChannel> =
    filterNot { it.isBuiltin || it.code in BUILTIN_CREDIT_CHANNEL_CODES }

fun defaultFirstDueYearMonth(repaymentDay: Int = 10): YearMonth {
    val now = LocalDate.now(FormatUtils.BeijingZone)
    return if (now.dayOfMonth <= repaymentDay) YearMonth.from(now) else YearMonth.from(now).plusMonths(1)
}

fun formatFirstDueDate(yearMonth: YearMonth, repaymentDay: Int = 10): String {
    val targetDay = repaymentDay.coerceIn(1, yearMonth.lengthOfMonth())
    return String.format(Locale.US, "%04d-%02d-%02d", yearMonth.year, yearMonth.monthValue, targetDay)
}

@Composable
fun CreateCreditChannelDialog(
    onDismiss: () -> Unit,
    confirmText: String = "保存",
    onSubmit: suspend (name: String, repaymentDay: Int) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var newDay by remember { mutableStateOf("10") }
    var isSubmitting by remember { mutableStateOf(false) }

    ArchiveDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = "新增还款渠道",
        confirmButton = {
            ArchiveTextButton(
                text = if (isSubmitting) "保存中…" else confirmText,
                enabled = newName.isNotBlank() && !isSubmitting,
                onClick = {
                    val day = newDay.toIntOrNull()?.coerceIn(1, 31) ?: 10
                    isSubmitting = true
                    scope.launch {
                        val ok = onSubmit(newName.trim(), day)
                        isSubmitting = false
                        if (ok) {
                            onDismiss()
                        }
                    }
                }
            )
        },
        dismissButton = {
            ArchiveTextButton(
                text = "取消",
                enabled = !isSubmitting,
                onClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArchiveField(
                value = newName,
                onValueChange = { newName = it },
                label = "渠道名称",
                placeholder = "如 招商银行信用卡",
                singleLine = true
            )
            ArchiveField(
                value = newDay,
                onValueChange = { newDay = it.filter { c -> c.isDigit() }.take(2) },
                label = "每月固定还款日",
                placeholder = "10",
                trailingText = "日",
                isNumeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

@Composable
fun SelectCreditChannelDialog(
    channels: List<CreditChannel>,
    selectedChannelId: Long?,
    onSelect: (CreditChannel) -> Unit,
    onCreateNewClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    ArchiveDialog(
        onDismissRequest = onDismiss,
        title = "选择还款渠道",
        confirmButton = {
            if (onCreateNewClick != null) {
                ArchiveTextButton(
                    text = "+ 新建渠道",
                    onClick = {
                        onDismiss()
                        onCreateNewClick()
                    }
                )
            }
        },
        dismissButton = {
            ArchiveTextButton(
                text = "取消",
                onClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            channels.forEach { ch ->
                val isSelected = selectedChannelId == ch.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.small)
                        .background(if (isSelected) colors.archiveSubtle else Color.Transparent)
                        .clickable {
                            onSelect(ch)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ch.name,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = ArchiveTypeScale.body,
                        color = if (isSelected) colors.accentWarm else colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "每月${ch.repaymentDay}日",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = ArchiveTypeScale.caption,
                        color = colors.inkSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Shared installment configuration section used across:
 * - QuickRecordSheet
 * - EditTransactionSheet
 * - AddGearSheet
 * - CreateInstallmentSheet
 * - ResolveWishSheet
 */
@Composable
fun InstallmentConfigSection(
    periodsText: String,
    onPeriodsTextChange: (String) -> Unit,
    channels: List<CreditChannel>,
    selectedChannel: CreditChannel?,
    onChannelSelected: (CreditChannel) -> Unit,
    modifier: Modifier = Modifier,
    showPayMethodToggle: Boolean = true,
    toggleLabel: String? = null,
    payMethodChoice: String = "installment",
    onPayMethodChoiceChange: ((String) -> Unit)? = null,
    periodPresets: List<String> = listOf("3", "6", "12", "24", "36"),
    maxPeriodDigits: Int = 3,
    leadingAmountField: (@Composable RowScope.() -> Unit)? = null,
    firstDueYearMonth: YearMonth? = null,
    onFirstDueYearMonthChange: ((YearMonth) -> Unit)? = null,
    principalCents: Long = 0L,
    showPreviewCard: Boolean = true,
    emptyPreviewHint: String = "输入金额后将实时试算各期还款额",
    onCreateChannel: (suspend (name: String, repaymentDay: Int) -> CreditChannel?)? = null,
    fullPaymentContent: (@Composable ColumnScope.() -> Unit)? = null,
    installmentExtraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showChannelSelectDialog by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val isInstallmentActive = !showPayMethodToggle || payMethodChoice == "installment"
    val effectivePeriods = periodsText.toIntOrNull() ?: 12

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showPayMethodToggle) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!toggleLabel.isNullOrBlank()) {
                    Text(
                        text = toggleLabel,
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ArchiveSegmentedTabs(
                    tabs = listOf("一次付清", "分期付款"),
                    selectedIndex = if (payMethodChoice == "installment") 1 else 0,
                    onTabSelected = { idx ->
                        onPayMethodChoiceChange?.invoke(if (idx == 1) "installment" else "full")
                    }
                )
            }
        }

        if (!isInstallmentActive) {
            fullPaymentContent?.invoke(this)
        } else {
            if (leadingAmountField != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    leadingAmountField()
                    ArchiveField(
                        value = periodsText,
                        onValueChange = { input ->
                            onPeriodsTextChange(input.filter { it.isDigit() }.take(maxPeriodDigits))
                        },
                        label = "分期期数",
                        placeholder = "12",
                        trailingText = "期",
                        isNumeric = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "常用",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                        color = colors.inkMuted,
                        maxLines = 1
                    )
                    periodPresets.forEach { p ->
                        ArchiveSelectableCapsule(
                            text = "${p}期",
                            selected = periodsText == p,
                            onClick = { onPeriodsTextChange(p) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchiveField(
                        value = periodsText,
                        onValueChange = { input ->
                            onPeriodsTextChange(input.filter { it.isDigit() }.take(maxPeriodDigits))
                        },
                        label = "分期期数",
                        placeholder = "12",
                        trailingText = "期",
                        isNumeric = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(106.dp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        periodPresets.forEach { p ->
                            ArchiveSelectableCapsule(
                                text = "${p}期",
                                selected = periodsText == p,
                                onClick = { onPeriodsTextChange(p) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "还款渠道",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (onCreateChannel != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(ArchiveShapes.full)
                                    .clickable { showCreateChannelDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = FolioGlyphs.Plus,
                                    contentDescription = null,
                                    tint = colors.accentSmallText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "新建",
                                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accentSmallText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(ArchiveShapes.field)
                            .background(colors.archiveSubtle)
                            .clickable(enabled = channels.isNotEmpty() || onCreateChannel != null) {
                                if (channels.isEmpty() && onCreateChannel != null) {
                                    showCreateChannelDialog = true
                                } else if (channels.isNotEmpty()) {
                                    showChannelSelectDialog = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedChannel?.name ?: when {
                                    channels.isNotEmpty() -> "选择渠道"
                                    onCreateChannel != null -> "点击新建渠道"
                                    else -> "暂无自定义还款渠道"
                                },
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = ArchiveTypeScale.body,
                                color = if (selectedChannel != null) colors.inkPrimary else colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (selectedChannel != null) {
                                Text(
                                    text = "每月${selectedChannel.repaymentDay}日",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = ArchiveTypeScale.caption,
                                    color = colors.inkSecondary,
                                    maxLines = 1
                                )
                            } else {
                                Icon(
                                    imageVector = FolioGlyphs.ChevronDown,
                                    contentDescription = null,
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (firstDueYearMonth != null && onFirstDueYearMonthChange != null) {
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = "首期还款月份",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(ArchiveShapes.field)
                                .background(colors.archiveSubtle)
                                .clickable { showMonthPicker = true }
                                .padding(horizontal = 14.dp, vertical = 13.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%04d-%02d",
                                        firstDueYearMonth.year,
                                        firstDueYearMonth.monthValue
                                    ),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = ArchiveTypeScale.body,
                                    color = colors.inkPrimary,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = FolioGlyphs.ChevronDown,
                                    contentDescription = null,
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            installmentExtraContent?.invoke(this)

            if (showPreviewCard) {
                val approxMonthly = if (effectivePeriods > 0) principalCents / effectivePeriods else 0L
                val baseYm = firstDueYearMonth ?: defaultFirstDueYearMonth(selectedChannel?.repaymentDay ?: 10)
                val endYm = runCatching {
                    baseYm.plusMonths((effectivePeriods - 1).toLong().coerceAtLeast(0L)).toString()
                }.getOrDefault("----")
                val hasValidPreview = principalCents > 0 && effectivePeriods > 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.md)
                        .background(if (hasValidPreview) colors.accentTint else colors.archiveSubtle)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasValidPreview) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每期约",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                color = colors.inkMuted
                            )
                            Text(
                                text = FormatUtils.formatMoney(approxMonthly),
                                style = com.looseleaf.app.theme.ArchiveTextStyles.amount,
                                color = colors.accentSmallText,
                                maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "共 $effectivePeriods 期",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = "末期 $endYm",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                color = colors.inkMuted
                            )
                        }
                    } else {
                        Text(
                            text = emptyPreviewHint,
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showMonthPicker && firstDueYearMonth != null && onFirstDueYearMonthChange != null) {
        ArchiveMonthPickerSheet(
            selectedYearMonth = firstDueYearMonth,
            onYearMonthSelected = onFirstDueYearMonthChange,
            onDismissRequest = { showMonthPicker = false }
        )
    }

    if (showCreateChannelDialog && onCreateChannel != null) {
        CreateCreditChannelDialog(
            onDismiss = { showCreateChannelDialog = false },
            confirmText = "创建并选择",
            onSubmit = { name, day ->
                val created = onCreateChannel(name, day)
                if (created != null) {
                    onChannelSelected(created)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showChannelSelectDialog) {
        SelectCreditChannelDialog(
            channels = channels,
            selectedChannelId = selectedChannel?.id,
            onSelect = onChannelSelected,
            onCreateNewClick = if (onCreateChannel != null) { { showCreateChannelDialog = true } } else null,
            onDismiss = { showChannelSelectDialog = false }
        )
    }
}
