package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CreateFootprintRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.ProvinceMemoryDrawerResponse
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

@Composable
internal fun EditWishSheetDialog(
    wish: Wish,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    // MOB-P1-59：心愿编辑草稿 —— 误关抽屉不再丢失已填内容
    val draftContext = LocalContext.current
    val draftStore = remember { DraftStore.get(draftContext) }
    val draftPrefix = remember(wish.id) { "wish_${wish.id}_" }

    var title by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}title") ?: wish.title) }
    var kind by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}kind") ?: (wish.kind ?: "item")) }
    var budgetYuan by remember(wish.id) {
        mutableStateOf(
            draftStore.getString("${draftPrefix}budget")
                ?: FormatUtils.centsToYuanString(wish.budget, includeSymbol = false)
        )
    }
    var spec by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}spec") ?: (wish.spec ?: "")) }
    var note by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}note") ?: (wish.note ?: "")) }
    var cityCode by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}city_code") ?: (wish.cityCode ?: "")) }
    var cityName by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}city_name") ?: (wish.cityName ?: "")) }

    // 字段变化即落草稿
    LaunchedEffect(title, kind, budgetYuan, spec, note, cityCode, cityName) {
        draftStore.putString("${draftPrefix}title", title)
        draftStore.putString("${draftPrefix}kind", kind)
        draftStore.putString("${draftPrefix}budget", budgetYuan)
        draftStore.putString("${draftPrefix}spec", spec)
        draftStore.putString("${draftPrefix}note", note)
        draftStore.putString("${draftPrefix}city_code", cityCode)
        draftStore.putString("${draftPrefix}city_name", cityName)
    }
    var isSubmitting by remember { mutableStateOf(false) }
    // MOB 表单审计 #20：金额输入非法（非数字）时禁用保存并提示
    val budgetInvalid = budgetYuan.isNotBlank() && budgetYuan.trim().toDoubleOrNull() == null

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_01, wish.title),
        actionText = "保存修订",
        isActionEnabled = title.isNotBlank() && !budgetInvalid && !isSubmitting,
        onAction = {
            if (budgetInvalid) {
                ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                return@ArchiveSheet
            }
            isSubmitting = true
            scope.launch {
                runCatching {
                    val cents = FormatUtils.yuanStringToCents(budgetYuan)
                    apiClient.updateWish(
                        id = wish.id,
                        req = CreateWishRequest(
                            title = title.trim(),
                            kind = kind,
                            budget = cents,
                            spec = if (kind == "item") spec.trim() else "",
                            cityCode = if (kind == "travel") cityCode else "",
                            cityName = if (kind == "travel") cityName else "",
                            note = note.trim(),
                            targetDate = wish.targetDate,
                            attachmentIds = wish.attachments.map { it.id }.takeIf { it.isNotEmpty() }
                        )
                    )
                    ArchiveToastManager.show("心愿已修订")
                    draftStore.clearPrefix(draftPrefix)
                    onSaved()
                    onDismiss()
                }.onFailure {
                    ArchiveToastManager.show("修订失败: ${it.message}", isError = true)
                }
                isSubmitting = false
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
                    ArchiveChoiceItem(
                        text = label,
                        isSelected = kind == k,
                        onClick = { kind = k }
                    )
                }
            }

            ArchiveField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.projects_wishes_sheets_label_01),
                placeholder = "输入心愿标题…",
                singleLine = true,
                errorMessage = if (title.isBlank()) "请输入心愿标题" else null
            )

            if (kind == "travel") {
                ArchiveCityPickerField(
                    selectedCityCode = cityCode,
                    selectedCityName = cityName,
                    onCitySelected = { c, n -> cityCode = c; cityName = n },
                    label = "目的地城市",
                    placeholder = "选择城市"
                )
            }

            if (kind == "item") {
                ArchiveField(
                    value = spec,
                    onValueChange = { spec = it },
                    label = stringResource(R.string.projects_wishes_sheets_label_02),
                    placeholder = "规格/型号（选填）",
                    singleLine = true
                )
            }

            ArchiveField(
                value = budgetYuan,
                onValueChange = { budgetYuan = it },
                label = stringResource(R.string.projects_wishes_sheets_label_03),
                placeholder = "0.00",
                isNumeric = true,
                singleLine = true,
                errorMessage = if (budgetInvalid) "金额格式不正确，请输入有效数字" else null
            )

            ArchiveField(
                value = note,
                onValueChange = { note = it },
                label = "手记与备忘",
                placeholder = stringResource(R.string.projects_wishes_sheets_placeholder_02),
                singleLine = false,
                minLines = 3
            )
        }
    }
}

@Composable
internal fun EditFootprintSheetDialog(
    footprint: Footprint,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    // MOB-P1-59：足迹编辑草稿
    val draftContext = LocalContext.current
    val draftStore = remember { DraftStore.get(draftContext) }
    val draftPrefix = remember(footprint.id) { "foot_${footprint.id}_" }

    var cityCode by remember(footprint.id) { mutableStateOf(draftStore.getString("${draftPrefix}city_code") ?: footprint.cityCode) }
    var cityName by remember(footprint.id) { mutableStateOf(draftStore.getString("${draftPrefix}city_name") ?: footprint.cityName) }
    var title by remember(footprint.id) { mutableStateOf(draftStore.getString("${draftPrefix}title") ?: (footprint.title ?: "")) }
    var note by remember(footprint.id) { mutableStateOf(draftStore.getString("${draftPrefix}note") ?: (footprint.note ?: "")) }

    LaunchedEffect(cityCode, cityName, title, note) {
        draftStore.putString("${draftPrefix}city_code", cityCode)
        draftStore.putString("${draftPrefix}city_name", cityName)
        draftStore.putString("${draftPrefix}title", title)
        draftStore.putString("${draftPrefix}note", note)
    }
    var visitedAtDt by remember(footprint.id) {
        mutableStateOf(
            FormatUtils.parseToLocalDateTime(footprint.visitedAt)
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_02, footprint.cityName),
        actionText = "保存票根",
        isActionEnabled = cityName.isNotBlank() && !isSubmitting,
        onAction = {
            footprint.departedAt?.let { departed ->
                val departedDt = FormatUtils.parseToLocalDateTime(departed)
                if (departedDt.isBefore(visitedAtDt)) {
                    ArchiveToastManager.show("返程时刻早于到访时刻，请修正到访时间", isError = true)
                    return@ArchiveSheet
                }
            }
            isSubmitting = true
            scope.launch {
                runCatching {
                    apiClient.updateFootprint(
                        id = footprint.id,
                        req = CreateFootprintRequest(
                            cityCode = cityCode.ifBlank { footprint.cityCode },
                            cityName = cityName.ifBlank { footprint.cityName },
                            title = title.trim(),
                            visitedAt = FormatUtils.toBeijingIsoString(visitedAtDt),
                            departedAt = footprint.departedAt,
                            note = note.trim()
                        )
                    )
                    ArchiveToastManager.show("票根已更新")
                    draftStore.clearPrefix(draftPrefix)
                    onSaved()
                    onDismiss()
                }.onFailure {
                    ArchiveToastManager.show("更新失败: ${it.message}", isError = true)
                }
                isSubmitting = false
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
            ArchiveCityPickerField(
                selectedCityCode = cityCode,
                selectedCityName = cityName,
                onCitySelected = { c, n -> cityCode = c; cityName = n },
                label = "到访城市",
                placeholder = "选择城市",
                errorMessage = if (cityName.isBlank()) "请选择城市" else null
            )
            ArchiveDateTimeSplitRow(
                selectedDateTime = visitedAtDt,
                onDateTimeSelected = { visitedAtDt = it },
                label = "到访时刻"
            )
            ArchiveField(
                value = title,
                onValueChange = { title = it },
                label = "标题（选填）",
                placeholder = "如 秋日漫步",
                singleLine = true
            )
            ArchiveField(
                value = note,
                onValueChange = { note = it },
                label = "行旅手记",
                placeholder = "记录旅途所见所感…",
                singleLine = false,
                minLines = 5
            )
        }
    }
}

@Composable
internal fun ProvinceMemoryDrawerBottomSheet(
    adcode: String,
    provinceName: String,
    apiClient: APIClient,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var drawerData by remember(adcode) { mutableStateOf<ProvinceMemoryDrawerResponse?>(null) }
    var isLoading by remember(adcode) { mutableStateOf(true) }
    var drawerError by remember(adcode) { mutableStateOf<String?>(null) }

    suspend fun loadDrawer() {
        isLoading = true
        drawerError = null
        runCatching {
            drawerData = apiClient.fetchProvinceMemoryDrawer(adcode)
        }.onFailure { drawerError = it.message ?: "网络异常" }
        isLoading = false
    }

    LaunchedEffect(adcode) {
        loadDrawer()
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_03, provinceName),
        cancelText = "合上"
    ) {
        val d = drawerData
        if (drawerError != null && d == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_wishes_sheets_text_tpl_01, drawerError.orEmpty()),
                    fontSize = 12.sp,
                    color = colors.alertRed,
                    modifier = Modifier.clickable(role = Role.Button) { scope.launch { loadDrawer() } }
                )
            }
        } else if (isLoading || d == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_wishes_fetching_province),
                    fontSize = 13.sp,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    color = colors.inkMuted
                )
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
                        text = stringResource(R.string.projects_wishes_sheets_text_tpl_02, d.visitedCities.size, d.footprints.size),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                    if (d.ledgerSummary.totalSpent > 0) {
                        Text(
                            text = stringResource(R.string.projects_wishes_sheets_text_tpl_03, FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 13.sp,
                            color = colors.accentMoss
                        )
                    }
                }

                if (d.visitedCities.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.projects_wishes_city_dist),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        d.visitedCities.forEach { city ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.projects_wishes_sheets_text_tpl_04, city.cityName, city.visitCount),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }

                if (d.entries.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.projects_wishes_local_notes),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        d.entries.take(5).forEachIndexed { idx, entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = entry.title.ifBlank { stringResource(R.string.projects_wishes_note_title) },
                                    fontSize = 13.sp,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatDisplayDate(entry.occurredAt),
                                    fontSize = 11.sp,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    color = colors.inkMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
