package com.looseleaf.app.ui.wishes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.*
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun CreateWishSheet(
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onCreated: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputTitle by remember { mutableStateOf("") }
    var inputNote by remember { mutableStateOf("") }
    var budgetInput by remember { mutableStateOf("") }
    var wishKind by remember { mutableStateOf("item") }
    var wishSpec by remember { mutableStateOf("") }
    var wishTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var isSubmittingCreate by remember { mutableStateOf(false) }

    val isSaveEnabled = inputTitle.isNotBlank() && !isSubmittingCreate

    ArchiveSheet(
        onDismissRequest = { if (!isSubmittingCreate) onDismiss() },
        title = "许下心愿",
        cancelText = "取消",
        actionText = if (isSubmittingCreate) "保存中" else "许下",
        isActionEnabled = isSaveEnabled,
        onAction = onAction@{
            if (isSubmittingCreate) return@onAction
            isSubmittingCreate = true
            scope.launch {
                try {
                    val queued = syncManager.createWishOrQueue(
                        CreateWishRequest(
                            title = inputTitle.trim(),
                            kind = wishKind,
                            spec = wishSpec.trim().takeIf { it.isNotBlank() },
                            note = inputNote.trim().takeIf { it.isNotBlank() },
                            budget = FormatUtils.yuanToCents(budgetInput),
                            targetDate = wishTargetDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        )
                    )
                    if (queued) ArchiveToastManager.show("已离线排队许下心愿")
                    else ArchiveToastManager.show("心愿已记录")
                    onDismiss()
                    onCreated()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ArchiveToastManager.show("创建失败: ${e.message}", isError = true)
                } finally {
                    isSubmittingCreate = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WishKindSelector(kind = wishKind, onKindChange = { wishKind = it })
            ArchiveField(
                value = inputTitle,
                onValueChange = { inputTitle = it },
                label = "心愿标题",
                placeholder = "想要的东西、想去的地方、想达成的事",
                singleLine = true
            )
            ArchiveField(
                value = wishSpec,
                onValueChange = { wishSpec = it },
                label = "规格 / 补充说明（选填）",
                placeholder = "如 银色 256G",
                singleLine = true
            )
            ArchiveField(
                value = budgetInput,
                onValueChange = { budgetInput = FormatUtils.sanitizeDecimalYuanInput(it) },
                label = "预算金额 (元)",
                placeholder = "0.00",
                isNumeric = true,
                singleLine = true
            )
            ArchiveDatePickerField(
                selectedDate = wishTargetDate,
                onDateSelected = { wishTargetDate = it },
                label = "期许达成日期",
                placeholder = "未设置",
                onClear = { wishTargetDate = null }
            )
            ArchiveField(
                value = inputNote,
                onValueChange = { inputNote = it },
                label = "心愿手记",
                placeholder = "写下向往它的缘由",
                singleLine = false,
                minLines = 4
            )
        }
    }
}

@Composable
internal fun ResolveWishSheet(
    wish: Wish,
    apiClient: APIClient,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onResolved: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()

    var costInput by remember(wish.id) {
        mutableStateOf(FormatUtils.centsToYuanString(wish.budget, includeSymbol = false))
    }
    var resType by remember(wish.id) { mutableStateOf("exact") }
    var actualTitleInput by remember(wish.id) { mutableStateOf("") }
    var entryNote by remember(wish.id) { mutableStateOf("") }
    var fulfilledAtDt by remember(wish.id) {
        mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone))
    }
    var createTx by remember(wish.id) { mutableStateOf(wish.budget > 0) }
    var wishPayChoice by remember(wish.id) { mutableStateOf("full") }
    var wishPeriodsText by remember(wish.id) { mutableStateOf("12") }
    var wishChannels by remember(wish.id) { mutableStateOf<List<CreditChannel>>(emptyList()) }
    var selectedChannel by remember(wish.id) { mutableStateOf<CreditChannel?>(null) }

    LaunchedEffect(wish.id) {
        runCatching {
            val channels = apiClient.fetchCreditChannels().userCreditChannels()
            wishChannels = channels
            if (channels.isNotEmpty() && selectedChannel == null) {
                selectedChannel = channels.first()
            }
        }
    }

    var createEntry by remember(wish.id) { mutableStateOf(true) }
    var isResolvingWish by remember { mutableStateOf(false) }

    val costCents = FormatUtils.yuanToCents(costInput)
    val costInvalid = resType != "relinquished" &&
        costInput.isNotBlank() &&
        costInput.trim() != "0" &&
        costInput.trim() != "0.00" &&
        costCents == 0L
    val installmentReady = resType == "relinquished" || !createTx ||
        wishPayChoice != "installment" || selectedChannel != null

    ArchiveSheet(
        onDismissRequest = { if (!isResolvingWish) onDismiss() },
        title = wish.title,
        actionText = if (isResolvingWish) "归册中" else if (resType == "relinquished") "拔草" else "归册",
        isActionEnabled = !costInvalid && installmentReady && !isResolvingWish,
        onAction = onAction@{
            if (isResolvingWish) return@onAction
            if (costInvalid) {
                ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                return@onAction
            }
            isResolvingWish = true
            scope.launch {
                try {
                    val isRel = resType == "relinquished"
                    val cents = if (isRel) 0L else costCents
                    val isInst = !isRel && createTx && wishPayChoice == "installment"
                    val queued = syncManager.resolveWishOrQueue(
                        id = wish.id,
                        req = ResolveWishRequest(
                            fulfilledAt = FormatUtils.toBeijingIsoString(fulfilledAtDt),
                            actualCost = cents,
                            resolutionType = resType,
                            actualTitle = actualTitleInput.trim().takeIf { it.isNotBlank() },
                            createTransaction = !isRel && createTx && cents > 0,
                            createLifeEntry = createEntry,
                            entryContent = entryNote.takeIf { it.isNotBlank() },
                            isInstallment = if (isInst) true else null,
                            installmentChannelCode = if (isInst) selectedChannel?.code else null,
                            installmentMonths = if (isInst) (wishPeriodsText.toIntOrNull() ?: 12) else null,
                            repaymentDay = if (isInst) (selectedChannel?.repaymentDay ?: 10) else null
                        )
                    )
                    if (queued) ArchiveToastManager.show("已离线排队心愿达成")
                    else ArchiveToastManager.show("心愿已如愿达成")
                    onDismiss()
                    onResolved()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                } finally {
                    isResolvingWish = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WishResolutionSelector(
                resType = resType,
                onResTypeChange = { resType = it }
            )

            if (resType == "relinquished" && wish.budget > 0) {
                RelinquishSavingCard(budgetCents = wish.budget)
            }

            ArchiveDateTimeSplitRow(
                selectedDateTime = fulfilledAtDt,
                onDateTimeSelected = { fulfilledAtDt = it },
                label = stringResource(R.string.wishes_resolve_fulfilled_at)
            )

            if (resType != "relinquished") {
                ArchiveField(
                    value = actualTitleInput,
                    onValueChange = { actualTitleInput = it },
                    label = stringResource(R.string.wishes_resolve_actual_title),
                    placeholder = wish.title,
                    singleLine = true
                )
                ArchiveField(
                    value = costInput,
                    onValueChange = { costInput = FormatUtils.sanitizeDecimalYuanInput(it) },
                    label = stringResource(R.string.wishes_resolve_actual_cost),
                    placeholder = "0.00",
                    trailingText = if (costInvalid) "请输入有效数字" else "元",
                    isNumeric = true,
                    singleLine = true
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.leaf)
                        .background(LooseLeafTheme.colors.archiveCard)
                        .border(ArchiveLines.hairline, LooseLeafTheme.colors.borderCard, ArchiveShapes.leaf)
                ) {
                    WishToggleRow(
                        title = "同步记一笔支出",
                        subtitle = "按实际花费写入账本，可选分期",
                        checked = createTx,
                        onCheckedChange = { createTx = it }
                    )
                    ArchiveDivider(modifier = Modifier.padding(start = 16.dp))
                    WishToggleRow(
                        title = "留下一页手记",
                        subtitle = "在散页时间流里记下这一刻",
                        checked = createEntry,
                        onCheckedChange = { createEntry = it }
                    )
                }

                if (createTx) {
                    InstallmentConfigSection(
                        periodsText = wishPeriodsText,
                        onPeriodsTextChange = { wishPeriodsText = it },
                        channels = wishChannels,
                        selectedChannel = selectedChannel,
                        onChannelSelected = { selectedChannel = it },
                        payMethodChoice = wishPayChoice,
                        onPayMethodChoiceChange = { wishPayChoice = it },
                        principalCents = costCents,
                        onCreateChannel = { name, day ->
                            runCatching {
                                val created = apiClient.createCreditChannel(
                                    CreateCreditChannelRequest(name = name, repaymentDay = day)
                                )
                                wishChannels = wishChannels + created
                                created
                            }.onFailure { err ->
                                ArchiveToastManager.show("创建渠道失败: ${err.message}", isError = true)
                            }.getOrNull()
                        }
                    )
                }
            }

            if (resType == "relinquished") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.leaf)
                        .background(LooseLeafTheme.colors.archiveCard)
                        .border(ArchiveLines.hairline, LooseLeafTheme.colors.borderCard, ArchiveShapes.leaf)
                ) {
                    WishToggleRow(
                        title = "留下一页手记",
                        subtitle = "记下放下它的理由，以后翻到会会心一笑",
                        checked = createEntry,
                        onCheckedChange = { createEntry = it }
                    )
                }
            }

            ArchiveField(
                value = entryNote,
                onValueChange = { entryNote = it },
                label = stringResource(R.string.wishes_resolve_note),
                placeholder = "记录这一刻的感受与细节",
                singleLine = false,
                minLines = 3
            )
        }
    }
}

/**
 * 心愿类别：三枚等宽的方印卡（好物 / 旅程 / 里程碑），选中为反相墨卡。
 */
@Composable
internal fun WishKindSelector(kind: String, onKindChange: (String) -> Unit) {
    val options = listOf(
        Triple("item", "物", "好物"),
        Triple("travel", "行", "旅程"),
        Triple("milestone", "成", "里程碑")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, glyph, label) ->
            ChoiceCard(
                glyph = glyph,
                title = label,
                subtitle = null,
                selected = kind == key,
                accent = LooseLeafTheme.colors.tagWish,
                onClick = { onKindChange(key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 心愿去向：如愿拿下 / 替代方案 / 理性拔草，每张卡带一句说明。
 */
@Composable
private fun WishResolutionSelector(resType: String, onResTypeChange: (String) -> Unit) {
    val colors = LooseLeafTheme.colors
    val options = listOf(
        listOf("exact", "得", "如愿拿下", "就是它"),
        listOf("altered", "替", "替代方案", "换了更合适的"),
        listOf("relinquished", "舍", "理性拔草", "想通了，不要了")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, glyph, title, subtitle) ->
            ChoiceCard(
                glyph = glyph,
                title = title,
                subtitle = subtitle,
                selected = resType == key,
                accent = if (key == "relinquished") colors.inkSecondary else colors.accentMoss,
                onClick = { onResTypeChange(key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    glyph: String,
    title: String,
    subtitle: String?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val bg by animateColorAsState(
        targetValue = if (selected) colors.inverseSurface else colors.archiveCard,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "choiceCardBg"
    )
    Column(
        modifier = modifier
            .semantics { this.selected = selected }
            .clip(ArchiveShapes.lg)
            .background(bg)
            .border(ArchiveLines.hairline, if (selected) colors.inverseSurface else colors.borderCard, ArchiveShapes.lg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(ArchiveShapes.md)
                .background(if (selected) accent else accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = glyph,
                fontFamily = ArchiveFontFamilies.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (selected) colors.onAccent else accent
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = ArchiveTextStyles.label,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.inverseInk else colors.inkPrimary,
            maxLines = 1
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = ArchiveTextStyles.meta,
                color = if (selected) colors.inverseInk.copy(alpha = 0.7f) else colors.inkMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * 拔草时的「省下」小结：把预算换算成一句克制的正反馈。
 */
@Composable
private fun RelinquishSavingCard(budgetCents: Long) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.positiveTint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.accentMoss)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "省下 ${FormatUtils.formatMoney(budgetCents)}",
                style = ArchiveTextStyles.amount,
                color = colors.accentMoss
            )
            Text(
                text = "按原预算计。拔草不记账，只把这份克制留在心愿单里。",
                style = ArchiveTextStyles.meta,
                color = colors.inkSecondary
            )
        }
    }
}

@Composable
private fun WishToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(text = title, style = ArchiveTextStyles.rowTitle, color = colors.inkPrimary)
            Text(text = subtitle, style = ArchiveTextStyles.meta, color = colors.inkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentWarm,
                checkedBorderColor = colors.accentWarm,
                uncheckedThumbColor = colors.inkMuted,
                uncheckedTrackColor = colors.archiveSubtle,
                uncheckedBorderColor = colors.borderDivider
            )
        )
    }
}
