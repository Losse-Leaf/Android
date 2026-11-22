package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        actionText = if (isSubmittingCreate) "保存中…" else "保存",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                    ArchiveSelectableCapsule(
                        text = label,
                        selected = wishKind == k,
                        onClick = { wishKind = k }
                    )
                }
            }
            ArchiveField(
                value = inputTitle,
                onValueChange = { inputTitle = it },
                label = "心愿标题",
                placeholder = "输入心愿标题…",
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
                placeholder = "写下向往的缘由…",
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
        title = "达成归册 · ${wish.title}",
        actionText = if (isResolvingWish) "归册中…" else "归册",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("exact" to "如愿拿下", "altered" to "替代方案", "relinquished" to "理性拔草").forEach { (k, label) ->
                    ArchiveSelectableCapsule(
                        text = label,
                        selected = resType == k,
                        onClick = { resType = k }
                    )
                }
            }

            ArchiveDateTimeSplitRow(
                selectedDateTime = fulfilledAtDt,
                onDateTimeSelected = { fulfilledAtDt = it },
                label = stringResource(R.string.projects_wishes_screen_label_03)
            )

            if (resType != "relinquished") {
                ArchiveField(
                    value = actualTitleInput,
                    onValueChange = { actualTitleInput = it },
                    label = stringResource(R.string.projects_wishes_screen_label_04),
                    placeholder = wish.title,
                    singleLine = true
                )
                ArchiveField(
                    value = costInput,
                    onValueChange = { costInput = FormatUtils.sanitizeDecimalYuanInput(it) },
                    label = stringResource(R.string.projects_wishes_screen_label_05),
                    placeholder = "0.00",
                    trailingText = if (costInvalid) "请输入有效数字" else "元",
                    isNumeric = true,
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchiveSelectableCapsule(
                        text = if (createTx) "同步记账" else "不记账",
                        selected = createTx,
                        onClick = { createTx = !createTx }
                    )
                    ArchiveSelectableCapsule(
                        text = if (createEntry) "伴生手记" else "不写手记",
                        selected = createEntry,
                        onClick = { createEntry = !createEntry }
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

            ArchiveField(
                value = entryNote,
                onValueChange = { entryNote = it },
                label = stringResource(R.string.projects_wishes_screen_label_06),
                placeholder = "记录心愿达成时的感悟与细节…",
                singleLine = false,
                minLines = 3
            )
        }
    }
}
