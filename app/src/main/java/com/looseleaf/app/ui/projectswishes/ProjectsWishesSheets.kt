package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import com.looseleaf.app.ui.components.DraftStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
@OptIn(ExperimentalMaterial3Api::class)
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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_01, wish.title),
        actionButtonText = "保存修订",
        isActionEnabled = title.isNotBlank() && !isSubmitting,
        onActionClick = {
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
                            spec = spec.trim().takeIf { it.isNotBlank() },
                            cityCode = cityCode.takeIf { it.isNotBlank() },
                            cityName = cityName.takeIf { it.isNotBlank() },
                            note = note.trim().takeIf { it.isNotBlank() },
                            targetDate = wish.targetDate,
                            attachmentIds = wish.attachments.map { it.id }.takeIf { it.isNotEmpty() }
                        )
                    )
                    ArchiveToastManager.show("心愿已修订")
                    // MOB-P1-59：提交成功后清空草稿
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                    PaperFilterPill(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = label
                    )
                }
            }
            PaperTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.projects_wishes_sheets_label_01), singleLine = true)
            if (kind == "travel") {
                PaperCityPickerField(selectedCityCode = cityCode, selectedCityName = cityName, onCitySelected = { c, n -> cityCode = c; cityName = n }, placeholder = stringResource(R.string.projects_wishes_sheets_placeholder_01))
            }
            PaperTextField(value = spec, onValueChange = { spec = it }, label = stringResource(R.string.projects_wishes_sheets_label_02), singleLine = true)
            PaperTextField(value = budgetYuan, onValueChange = { budgetYuan = it }, label = stringResource(R.string.projects_wishes_sheets_label_03), singleLine = true)
            PaperTextEditor(value = note, onValueChange = { note = it }, placeholder = stringResource(R.string.projects_wishes_sheets_placeholder_02), minHeight = 80.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_02, footprint.cityName),
        actionButtonText = "保存票根",
        isActionEnabled = cityName.isNotBlank() && !isSubmitting,
        onActionClick = {
            isSubmitting = true
            scope.launch {
                runCatching {
                    apiClient.updateFootprint(
                        id = footprint.id,
                        req = CreateFootprintRequest(
                            cityCode = cityCode.ifBlank { footprint.cityCode },
                            cityName = cityName.ifBlank { footprint.cityName },
                            title = title.trim().takeIf { it.isNotBlank() },
                            visitedAt = FormatUtils.toBeijingIsoString(visitedAtDt),
                            departedAt = footprint.departedAt,
                            note = note.trim().takeIf { it.isNotBlank() }
                        )
                    )
                    ArchiveToastManager.show("票根已更新")
                    // MOB-P1-59：提交成功后清空草稿
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperCityPickerField(
                selectedCityCode = cityCode,
                selectedCityName = cityName,
                onCitySelected = { c, n -> cityCode = c; cityName = n },
                placeholder = stringResource(R.string.projects_wishes_sheets_placeholder_03)
            )
            PaperDateTimePickerField(
                selectedDateTime = visitedAtDt,
                onDateTimeSelected = { visitedAtDt = it },
                label = stringResource(R.string.projects_wishes_sheets_label_04)
            )
            PaperTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.projects_wishes_sheets_label_05), singleLine = true)
            PaperTextEditor(value = note, onValueChange = { note = it }, placeholder = stringResource(R.string.projects_wishes_sheets_placeholder_04), minHeight = 90.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.projects_wishes_sheets_title_tpl_03, provinceName),
        actionButtonText = "合上",
        onActionClick = onDismiss
    ) {
        val d = drawerData
        if (drawerError != null && d == null) {
            // 失败态可重试，不再永远停在「正在聚合」
            Text(
                text = stringResource(R.string.projects_wishes_sheets_text_tpl_01, drawerError.orEmpty()),
                fontSize = 12.sp,
                color = colors.accentWarm,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .clickable(role = Role.Button) { scope.launch { loadDrawer() } }
            )
        } else if (isLoading || d == null) {
            Text(stringResource(R.string.projects_wishes_fetching_province), fontSize = 12.sp, color = colors.inkMuted, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.projects_wishes_sheets_text_tpl_02, d.visitedCities.size, d.footprints.size),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.accentWarm
                    )
                    if (d.ledgerSummary.totalSpent > 0) {
                        Text(
                            text = stringResource(R.string.projects_wishes_sheets_text_tpl_03, FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.accentMoss
                        )
                    }
                }

                if (d.visitedCities.isNotEmpty()) {
                    Text(stringResource(R.string.projects_wishes_city_dist), fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 12.5.sp, color = colors.inkSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        d.visitedCities.forEach { city ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.projects_wishes_sheets_text_tpl_04, city.cityName, city.visitCount),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }

                if (d.entries.isNotEmpty()) {
                    Text(stringResource(R.string.projects_wishes_local_notes), fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 12.5.sp, color = colors.inkSecondary)
                    d.entries.take(5).forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.archiveSubtle)
                                .padding(8.dp)
                        ) {
                            Text(text = entry.title.ifBlank { stringResource(R.string.projects_wishes_note_title) }, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.inkPrimary)
                            Text(text = FormatUtils.formatDisplayDate(entry.occurredAt), fontSize = 11.sp, fontFamily = ArchiveFontFamilies.MonoScale, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}
