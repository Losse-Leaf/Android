package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Code
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
    var title by remember(wish.id) { mutableStateOf(wish.title) }
    var kind by remember(wish.id) { mutableStateOf(wish.kind ?: "item") }
    var budgetYuan by remember(wish.id) { mutableStateOf(FormatUtils.centsToYuanString(wish.budget, includeSymbol = false)) }
    var spec by remember(wish.id) { mutableStateOf(wish.spec ?: "") }
    var note by remember(wish.id) { mutableStateOf(wish.note ?: "") }
    var cityCode by remember(wish.id) { mutableStateOf(wish.cityCode ?: "") }
    var cityName by remember(wish.id) { mutableStateOf(wish.cityName ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "修订心愿 · ${wish.title}",
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
            PaperTextField(value = title, onValueChange = { title = it }, label = "心愿名称", singleLine = true)
            if (kind == "travel") {
                PaperCityPickerField(selectedCityCode = cityCode, selectedCityName = cityName, onCitySelected = { c, n -> cityCode = c; cityName = n }, placeholder = "选择目的地城市…")
            }
            PaperTextField(value = spec, onValueChange = { spec = it }, label = "规格参数或备忘", singleLine = true)
            PaperTextField(value = budgetYuan, onValueChange = { budgetYuan = it }, label = "意向预算 (元)", singleLine = true)
            PaperTextEditor(value = note, onValueChange = { note = it }, placeholder = "初衷手记…", minHeight = 80.dp)
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
    var cityCode by remember(footprint.id) { mutableStateOf(footprint.cityCode) }
    var cityName by remember(footprint.id) { mutableStateOf(footprint.cityName) }
    var title by remember(footprint.id) { mutableStateOf(footprint.title ?: "") }
    var note by remember(footprint.id) { mutableStateOf(footprint.note ?: "") }
    var visitedAtDt by remember(footprint.id) {
        mutableStateOf(
            FormatUtils.parseToLocalDateTime(footprint.visitedAt)
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "修订行迹票根 · ${footprint.cityName}",
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
                placeholder = "点选属地城市…"
            )
            PaperDateTimePickerField(
                selectedDateTime = visitedAtDt,
                onDateTimeSelected = { visitedAtDt = it },
                label = "到访时刻"
            )
            PaperTextField(value = title, onValueChange = { title = it }, label = "旅程标题", singleLine = true)
            PaperTextEditor(value = note, onValueChange = { note = it }, placeholder = "随笔手记…", minHeight = 90.dp)
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
        title = "$provinceName · 省级回忆抽屉",
        actionButtonText = "合上",
        onActionClick = onDismiss
    ) {
        val d = drawerData
        if (drawerError != null && d == null) {
            // 失败态可重试，不再永远停在「正在聚合」
            Text(
                text = "全省行迹检索失败：$drawerError · 点此重试",
                fontSize = 12.sp,
                color = colors.accentWarm,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .clickable(role = Role.Button) { scope.launch { loadDrawer() } }
            )
        } else if (isLoading || d == null) {
            Text("正在聚合全省行迹票根与属地手记…", fontSize = 12.sp, color = colors.inkMuted, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "点亮 ${d.visitedCities.size} 座城邑 · 履迹 ${d.footprints.size} 次",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.accentWarm
                    )
                    if (d.ledgerSummary.totalSpent > 0) {
                        Text(
                            text = "全省花销: ${FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)}",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.accentMoss
                        )
                    }
                }

                if (d.visitedCities.isNotEmpty()) {
                    Text("到访城市分布", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 12.5.sp, color = colors.inkSecondary)
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
                                    text = "${city.cityName} (${city.visitCount})",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }

                if (d.entries.isNotEmpty()) {
                    Text("属地散页手记", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 12.5.sp, color = colors.inkSecondary)
                    d.entries.take(5).forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.archiveSubtle)
                                .padding(8.dp)
                        ) {
                            Text(text = entry.title.ifBlank { "散页手记" }, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.inkPrimary)
                            Text(text = FormatUtils.formatDisplayDate(entry.occurredAt), fontSize = 11.sp, fontFamily = ArchiveFontFamilies.MonoScale, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}
