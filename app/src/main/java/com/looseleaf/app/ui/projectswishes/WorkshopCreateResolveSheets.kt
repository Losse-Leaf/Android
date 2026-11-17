package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun WorkshopCreateSheet(
    selectedTab: Int,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onCreated: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputTitle by remember { mutableStateOf("") }
    var inputSubtitle by remember { mutableStateOf("") }
    var inputNote by remember { mutableStateOf("") }
    var inputThird by remember { mutableStateOf("") }
    var projCategory by remember { mutableStateOf("软件") }
    var projStatus by remember { mutableStateOf("进行中") }
    var projStory by remember { mutableStateOf("") }
    var projGithubUrl by remember { mutableStateOf("") }
    var wishKind by remember { mutableStateOf("item") }
    var wishSpec by remember { mutableStateOf("") }
    var wishTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var fpCityCode by remember { mutableStateOf("") }
    var fpCityName by remember { mutableStateOf("") }
    var wishCityCode by remember { mutableStateOf("") }
    var wishCityName by remember { mutableStateOf("") }
    var fpVisitedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone)) }
    var fpIsMultiDay by remember { mutableStateOf(false) }
    var fpDepartedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone).plusDays(2)) }
    var isSubmittingCreate by remember { mutableStateOf(false) }

    val isSaveEnabled = (if (selectedTab == 2) fpCityName.isNotBlank() else inputTitle.isNotBlank()) && !isSubmittingCreate

    ArchiveSheet(
        onDismissRequest = { if (!isSubmittingCreate) onDismiss() },
        title = when (selectedTab) {
            0 -> "新建造物"
            1 -> "许下心愿"
            else -> "点亮足迹"
        },
        cancelText = "取消",
        actionText = if (isSubmittingCreate) "保存中…" else "保存",
        isActionEnabled = isSaveEnabled,
        onAction = onAction@{
            if (isSubmittingCreate) return@onAction
            isSubmittingCreate = true
            scope.launch {
                try {
                    when (selectedTab) {
                        0 -> {
                            val queued = syncManager.createProjectOrQueue(
                                CreateProjectRequest(
                                    title = inputTitle.trim(),
                                    subtitle = inputSubtitle.trim().takeIf { it.isNotBlank() },
                                    description = inputNote.trim(),
                                    story = projStory.trim().takeIf { it.isNotBlank() },
                                    category = projCategory,
                                    status = projStatus,
                                    githubUrl = projGithubUrl.trim().takeIf { it.isNotBlank() }
                                )
                            )
                            if (queued) ArchiveToastManager.show("已离线排队创建造物")
                            else ArchiveToastManager.show("造物已创建")
                        }
                        1 -> {
                            val queued = syncManager.createWishOrQueue(
                                CreateWishRequest(
                                    title = inputTitle.trim(),
                                    kind = wishKind,
                                    spec = if (wishKind == "item") wishSpec.trim().takeIf { it.isNotBlank() } else null,
                                    note = inputNote.trim().takeIf { it.isNotBlank() },
                                    budget = FormatUtils.yuanToCents(inputThird),
                                    cityCode = if (wishKind == "travel") wishCityCode.takeIf { it.isNotBlank() } else null,
                                    cityName = if (wishKind == "travel") wishCityName.takeIf { it.isNotBlank() } else null,
                                    targetDate = wishTargetDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                )
                            )
                            if (queued) ArchiveToastManager.show("已离线排队许下心愿")
                            else ArchiveToastManager.show("心愿已记录")
                        }
                        else -> {
                            val queued = syncManager.createFootprintOrQueue(
                                CreateFootprintRequest(
                                    cityCode = fpCityCode,
                                    cityName = fpCityName,
                                    title = inputThird.trim().takeIf { it.isNotBlank() },
                                    visitedAt = FormatUtils.toBeijingIsoString(fpVisitedAt),
                                    departedAt = if (fpIsMultiDay) FormatUtils.toBeijingIsoString(fpDepartedAt) else null,
                                    note = inputNote.trim().takeIf { it.isNotBlank() }
                                )
                            )
                            if (queued) ArchiveToastManager.show("已离线排队足迹")
                            else ArchiveToastManager.show("足迹已记录")
                        }
                    }
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
            if (selectedTab == 0) {
                ArchiveField(
                    value = inputTitle,
                    onValueChange = { inputTitle = it },
                    label = "造物名称",
                    placeholder = "输入造物名称…",
                    singleLine = true
                )
                ArchiveField(
                    value = inputSubtitle,
                    onValueChange = { inputSubtitle = it },
                    label = "副标题",
                    placeholder = "一句话描述（选填）",
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArchiveSelectField(
                        label = "造物分类",
                        selectedValue = projCategory,
                        options = listOf(
                            "软件" to "软件",
                            "硬件" to "硬件",
                            "设计" to "设计",
                            "手作" to "手作",
                            "写作" to "写作",
                            "生活" to "生活"
                        ),
                        onValueSelected = { projCategory = it },
                        modifier = Modifier.weight(1f)
                    )
                    ArchiveSelectField(
                        label = "当前状态",
                        selectedValue = projStatus,
                        options = listOf(
                            "进行中" to "进行中",
                            "已完成" to "已完成",
                            "维护中" to "维护中",
                            "已搁置" to "已搁置"
                        ),
                        onValueSelected = { projStatus = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                ArchiveField(
                    value = inputNote,
                    onValueChange = { inputNote = it },
                    label = "手记与简介",
                    placeholder = "书写关于造物的构想或记录…",
                    singleLine = false,
                    minLines = 3
                )
                ArchiveField(
                    value = projStory,
                    onValueChange = { projStory = it },
                    label = "缘起故事（选填）",
                    placeholder = "为何着手做这件造物…",
                    singleLine = false,
                    minLines = 2
                )
                ArchiveField(
                    value = projGithubUrl,
                    onValueChange = { projGithubUrl = it },
                    label = "源码仓库 URL（选填）",
                    placeholder = "github.com/...",
                    singleLine = true
                )
            } else if (selectedTab == 1) {
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
                if (wishKind == "item") {
                    ArchiveField(
                        value = wishSpec,
                        onValueChange = { wishSpec = it },
                        label = "规格 / 型号（选填）",
                        placeholder = "如 银色 256G",
                        singleLine = true
                    )
                } else if (wishKind == "travel") {
                    ArchiveCityPickerField(
                        selectedCityCode = wishCityCode,
                        selectedCityName = wishCityName,
                        onCitySelected = { c, n -> wishCityCode = c; wishCityName = n },
                        label = "目的地城市",
                        placeholder = "选择城市"
                    )
                }
                ArchiveField(
                    value = inputThird,
                    onValueChange = { inputThird = FormatUtils.sanitizeDecimalYuanInput(it) },
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
            } else {
                ArchiveCityPickerField(
                    selectedCityCode = fpCityCode,
                    selectedCityName = fpCityName,
                    onCitySelected = { c, n -> fpCityCode = c; fpCityName = n },
                    label = "到访城市",
                    placeholder = "选择城市"
                )
                ArchiveDateTimeSplitRow(
                    selectedDateTime = fpVisitedAt,
                    onDateTimeSelected = { fpVisitedAt = it },
                    label = "到访时刻"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchiveSelectableCapsule(
                        text = "单日",
                        selected = !fpIsMultiDay,
                        onClick = { fpIsMultiDay = false }
                    )
                    ArchiveSelectableCapsule(
                        text = "多日",
                        selected = fpIsMultiDay,
                        onClick = { fpIsMultiDay = true }
                    )
                }
                if (fpIsMultiDay) {
                    ArchiveDateTimeSplitRow(
                        selectedDateTime = fpDepartedAt,
                        onDateTimeSelected = { fpDepartedAt = it },
                        label = "返程时刻"
                    )
                }
                ArchiveField(
                    value = inputThird,
                    onValueChange = { inputThird = it },
                    label = "标题（选填）",
                    placeholder = "如 秋日漫步",
                    singleLine = true
                )
                ArchiveField(
                    value = inputNote,
                    onValueChange = { inputNote = it },
                    label = "行旅手记",
                    placeholder = "记录旅途所见所感…",
                    singleLine = false,
                    minLines = 5
                )
            }
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
    val colors = LooseLeafTheme.colors
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

    var createGear by remember(wish.id) { mutableStateOf(wish.kind == "item") }
    var createEntry by remember(wish.id) { mutableStateOf(true) }
    var createFp by remember(wish.id) { mutableStateOf(wish.kind == "travel") }
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
                            createGearAsset = !isRel && createGear && wish.kind == "item",
                            createLifeEntry = createEntry,
                            createFootprint = !isRel && createFp && wish.kind == "travel" &&
                                !wish.cityCode.isNullOrBlank(),
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
                    if (wish.kind == "item") {
                        ArchiveSelectableCapsule(
                            text = if (createGear) "同步建固资" else "不建固资",
                            selected = createGear,
                            onClick = { createGear = !createGear }
                        )
                    }
                    if (wish.kind == "travel") {
                        if (wish.cityCode.isNullOrBlank()) {
                            Text(
                                text = "未设目的地，无法亮足迹",
                                fontSize = ArchiveTypeScale.caption,
                                color = colors.inkMuted,
                                modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)
                            )
                        } else {
                            ArchiveSelectableCapsule(
                                text = if (createFp) "同步亮足迹" else "不建足迹",
                                selected = createFp,
                                onClick = { createFp = !createFp }
                            )
                        }
                    }
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

@Composable
internal fun CityMemoryDrawerBottomSheet(
    footprint: Footprint,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    onOpenProvinceDrawer: (adcode: String, provinceName: String) -> Unit,
    onEditFootprint: (Footprint) -> Unit,
    onRequestDeleteFootprint: (Footprint, Int) -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var drawerData by remember(footprint.cityCode) { mutableStateOf<CityMemoryDrawerResponse?>(null) }
    var drawerError by remember(footprint.cityCode) { mutableStateOf<String?>(null) }

    LaunchedEffect(footprint.cityCode) {
        drawerError = null
        runCatching {
            drawerData = apiClient.fetchCityMemoryDrawer(footprint.cityCode)
        }.onFailure { drawerError = it.message ?: "网络异常" }
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_screen_title_tpl_02, footprint.cityName),
        cancelText = "关闭"
    ) {
        val d = drawerData
        if (d == null) {
            if (drawerError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.projects_wishes_screen_text_tpl_02, drawerError.orEmpty()),
                        fontSize = 12.sp,
                        color = colors.alertRed,
                        modifier = Modifier.clickable(role = Role.Button) {
                            drawerError = null
                            scope.launch {
                                runCatching {
                                    drawerData = apiClient.fetchCityMemoryDrawer(footprint.cityCode)
                                }.onFailure { drawerError = it.message ?: "网络异常" }
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.projects_wishes_screen_text_07),
                        fontSize = 13.sp,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        color = colors.inkMuted
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.projects_wishes_screen_text_tpl_03, d.visitStats.totalVisits, d.entries.size),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                    if (d.ledgerSummary.totalSpent > 0) {
                        Text(
                            text = stringResource(R.string.projects_wishes_screen_text_tpl_04, FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.accentMoss
                        )
                    }
                }

                if (d.entries.isNotEmpty()) {
                    ArchiveSectionHeader(title = "属地散页手记")
                    Column(modifier = Modifier.fillMaxWidth()) {
                        d.entries.forEachIndexed { index, entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectEntry(entry) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = entry.title.ifBlank { "散页手记" },
                                    fontSize = 14.sp,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatDisplayDate(entry.occurredAt),
                                    fontSize = 11.sp,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    color = colors.inkSecondary
                                )
                            }
                            if (index < d.entries.size - 1) {
                                ArchiveDivider()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                ArchiveDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchiveTextButton(
                        text = stringResource(R.string.projects_wishes_province_drawer),
                        textColor = colors.accentWarm,
                        onClick = {
                            val prefix = if (footprint.cityCode.length >= 2) footprint.cityCode.substring(0, 2) else ""
                            val prov = CityDictionary.provincePrefixMap[prefix] ?: "属地省份"
                            val code = footprint.cityCode
                            onDismiss()
                            onOpenProvinceDrawer(code, prov)
                        }
                    )

                    ArchiveTextButton(
                        text = stringResource(R.string.projects_wishes_edit_ticket),
                        textColor = colors.accentWarm,
                        onClick = {
                            onDismiss()
                            onEditFootprint(footprint)
                        }
                    )

                    ArchiveTextButton(
                        text = stringResource(R.string.projects_wishes_remove),
                        isDestructive = true,
                        onClick = {
                            val count = d.entries.size
                            onDismiss()
                            onRequestDeleteFootprint(footprint, count)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditProjectSheet(
    project: Project,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onSaved: suspend () -> Unit,
    onRequestDelete: (Project) -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var editTitle by remember(project.id) { mutableStateOf(project.title) }
    var editSubtitle by remember(project.id) { mutableStateOf(project.subtitle ?: "") }
    var editDesc by remember(project.id) { mutableStateOf(project.description ?: "") }
    var editStatus by remember(project.id) { mutableStateOf(project.status ?: "进行中") }
    var editGithub by remember(project.id) { mutableStateOf(project.githubUrl ?: "") }
    var isUpdatingProject by remember { mutableStateOf(false) }

    ArchiveSheet(
        onDismissRequest = { if (!isUpdatingProject) onDismiss() },
        title = stringResource(R.string.projects_wishes_screen_title_tpl_03, project.title),
        actionText = if (isUpdatingProject) "保存中…" else "保存",
        isActionEnabled = editTitle.isNotBlank() && !isUpdatingProject,
        onAction = onAction@{
            if (isUpdatingProject) return@onAction
            isUpdatingProject = true
            scope.launch {
                try {
                    val queued = syncManager.updateProjectOrQueue(
                        id = project.id,
                        req = CreateProjectRequest(
                            title = editTitle.trim(),
                            subtitle = editSubtitle.trim(),
                            description = editDesc.trim(),
                            story = project.story,
                            category = project.category,
                            status = editStatus,
                            githubUrl = editGithub.trim(),
                            order = project.order ?: 0
                        )
                    )
                    if (queued) {
                        ArchiveToastManager.show("已离线排队更新造物")
                    } else {
                        ArchiveToastManager.show("造物项目已更新")
                    }
                    onDismiss()
                    onSaved()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ArchiveToastManager.show("更新造物失败: ${e.message}", isError = true)
                } finally {
                    isUpdatingProject = false
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "进行中" to colors.tagTravel,
                    "已完成" to colors.inkSecondary,
                    "维护中" to colors.tagProject,
                    "已搁置" to colors.inkMuted
                ).forEach { (s, dotColor) ->
                    ArchiveSelectableCapsule(
                        text = s,
                        selected = editStatus == s,
                        leadingDotColor = dotColor,
                        onClick = { editStatus = s }
                    )
                }
            }

            ArchiveField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = stringResource(R.string.projects_wishes_screen_label_07),
                placeholder = "输入造物名称…",
                singleLine = true,
                errorMessage = if (editTitle.isBlank()) "请输入造物名称" else null
            )
            ArchiveField(
                value = editSubtitle,
                onValueChange = { editSubtitle = it },
                label = stringResource(R.string.projects_wishes_screen_label_08),
                placeholder = "一句话简介（选填）",
                singleLine = true
            )
            ArchiveField(
                value = editDesc,
                onValueChange = { editDesc = it },
                label = stringResource(R.string.projects_wishes_screen_label_09),
                placeholder = "详细说明与记录…",
                singleLine = false,
                minLines = 3
            )
            ArchiveField(
                value = editGithub,
                onValueChange = { editGithub = it },
                label = stringResource(R.string.projects_wishes_screen_label_10),
                placeholder = "github.com/...",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            ArchiveDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ArchiveTextButton(
                    text = stringResource(R.string.projects_wishes_delete_project),
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        onRequestDelete(project)
                    }
                )
            }
        }
    }
}
