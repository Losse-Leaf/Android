package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
@Composable
internal fun EntryDetailSheetDialog(
    entry: LifeEntry,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaveEdit: (CreateEntryRequest) -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String, String?) -> Unit
) {
    val colors = LooseLeafTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editType by remember(entry) { mutableStateOf(entry.type) }
    var editTitle by remember(entry) { mutableStateOf(entry.title) }
    var editContent by remember(entry) { mutableStateOf(entry.content) }
    var editCityCode by remember(entry) { mutableStateOf(entry.cityCode ?: "") }
    var editCityName by remember(entry) { mutableStateOf(entry.cityName ?: entry.location ?: "") }
    var editOccurredAt by remember(entry) { mutableStateOf(FormatUtils.parseToLocalDateTime(entry.occurredAt)) }
    var editFeatured by remember(entry) { mutableStateOf(entry.featured ?: false) }
    var editAttachments by remember(entry) { mutableStateOf(entry.attachments) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isUploadingPhoto = true
                try {
                    for (uri in uris) {
                        val processed = ImagePipeline.processImage(context, uri)
                        val att = ImagePipeline.uploadImage(apiClient, processed)
                        editAttachments = editAttachments + att
                    }
                } catch (e: Exception) {
                    ArchiveToastManager.show(
                        "相纸追加需要联网（当前离线或服务不可达）：${e.message ?: "网络异常"}",
                        isError = true
                    )
                } finally {
                    isUploadingPhoto = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 弹窗顶部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEditing) "修订散页档案" else "散页档案阅览",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val targetFeatured = !(entry.featured ?: false)
                            val code = entry.cityCode
                            val cityName = entry.cityName ?: entry.location
                            val req = CreateEntryRequest(
                                type = entry.type,
                                title = entry.title,
                                content = entry.content,
                                occurredAt = entry.occurredAt,
                                // 精选只改 featured，不得把「西湖区龙井村」这类精确地点覆盖成城市名
                                location = entry.location,
                                cityCode = code,
                                cityName = cityName,
                                projectId = entry.projectId,
                                wishId = entry.wishId,
                                footprintId = entry.footprintId,
                                featured = targetFeatured,
                                attachmentIds = entry.attachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
                            )
                            onSaveEdit(req)
                        }) {
                            Icon(
                                imageVector = if (entry.featured == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (entry.featured == true) "取消精选" else "设为精选",
                                tint = if (entry.featured == true) colors.featuredGold else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "编辑",
                                tint = if (isEditing) colors.accentWarm else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = colors.accentWarm,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                PaperDivider()
                Spacer(modifier = Modifier.height(12.dp))

                if (isEditing) {
                    // 类型切换
                    val typeOptions = listOf(
                        "life" to "日常随笔",
                        "travel" to "山川游历",
                        "project" to "造物日志",
                        "wish" to "心愿印记"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((k, label) in typeOptions) {
                            val selected = editType == k
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.accentWarm else colors.archivePaper)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
                                    .clickable { editType = k }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 11.sp,
                                    color = if (selected) colors.archivePaper else colors.inkSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PaperTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        placeholder = "标题",
                        isUnderline = true,
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PaperTextEditor(
                        value = editContent,
                        onValueChange = { editContent = it },
                        placeholder = "正文手记...",
                        minHeight = 110.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PaperDateTimeSplitRow(
                        selectedDateTime = editOccurredAt,
                        onDateTimeSelected = { editOccurredAt = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PaperCityPickerField(
                        selectedCityCode = editCityCode,
                        selectedCityName = editCityName,
                        onCitySelected = { code, name ->
                            editCityCode = code
                            editCityName = name
                        },
                        placeholder = "选择城市…"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 照片附件管理与增删
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "手账影像相纸 (${editAttachments.size})",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            )
                            if (isUploadingPhoto) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = colors.accentWarm
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "上传中…",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 11.sp,
                                        color = colors.accentWarm
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "+ 补选相纸照片",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 12.sp,
                                        color = colors.accentWarm
                                    )
                                }
                            }
                        }

                        if (editAttachments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (att in editAttachments) {
                                    val fullUrl = apiClient.resolveMediaUrl(att.url)
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                                    ) {
                                        AsyncImage(
                                            model = fullUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(colors.inkPrimary.copy(alpha = 0.7f))
                                                .clickable {
                                                    editAttachments = editAttachments.filter { it.id != att.id }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "撕除此照片",
                                                // 深底角标上的撕除图标：取反色语义而非裸白
                                                tint = colors.archivePaper,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 精选印记标记
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editFeatured = !editFeatured }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editFeatured,
                            onCheckedChange = { editFeatured = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentWarm,
                                uncheckedColor = colors.inkMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "标记为精选印记（收录于精选特刊）",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TerracottaStampButton(
                        text = "保存修订",
                        enabled = editTitle.isNotBlank() || editContent.isNotBlank(),
                        onClick = {
                            val code = editCityCode.ifBlank { entry.cityCode }
                            val cityName = editCityName.ifBlank { entry.cityName }
                            val req = CreateEntryRequest(
                                type = editType,
                                title = editTitle.ifBlank { "无题随笔" },
                                content = editContent.ifBlank { "无文字记录" },
                                occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
                                location = cityName,
                                cityCode = code,
                                cityName = cityName,
                                projectId = entry.projectId,
                                wishId = entry.wishId,
                                footprintId = entry.footprintId,
                                featured = editFeatured,
                                attachmentIds = editAttachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
                            )
                            onSaveEdit(req)
                        }
                    )
                } else {
                    // 阅览模式
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                        if (entry.featured == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.featuredGoldBg)
                                    .border(0.5.dp, colors.featuredGoldBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "★ 精选印记",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 10.sp,
                                    color = colors.featuredGold,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    val detailPlace = listOfNotNull(entry.cityName, entry.location).distinct().filter { it.isNotBlank() }.joinToString(" · ")
                    if (detailPlace.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = detailPlace,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = entry.title,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = entry.content,
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkSecondary
                    )

                    if (entry.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        EntryAttachmentsGrid(
                            attachments = entry.attachments,
                            apiClient = apiClient,
                            onPhotoClick = onPhotoClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlmanacDialog(
    apiClient: APIClient,
    entries: List<LifeEntry>,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val currentYear = remember { LocalDate.now(FormatUtils.BeijingZone).year }
    val earliestYear = remember(entries) {
        val entryYears = entries.mapNotNull { it.occurredAt.take(4).toIntOrNull() }
        if (entryYears.isNotEmpty()) minOf(entryYears.min(), currentYear) else currentYear
    }
    val availableYears = remember(earliestYear, currentYear) {
        (earliestYear..currentYear).toList().reversed()
    }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var summary by remember { mutableStateOf<AlmanacSummaryResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedYear) {
        isLoading = true
        errorMsg = null
        try {
            summary = apiClient.fetchAlmanacSummary(selectedYear)
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$selectedYear 年度生活年鉴",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "五幕生命叙事 · 岁月卷首至柴米油盐",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 年份切换胶囊 (从刚进入的年份至当前年份，支持横滑)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (yr in availableYears) {
                        val isSelected = yr == selectedYear
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.accentWarm else colors.archivePaper)
                                .border(0.5.dp, if (isSelected) colors.accentWarm else colors.borderDivider, RoundedCornerShape(12.dp))
                                .clickable { selectedYear = yr }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$yr 年",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = if (isSelected) colors.archivePaper else colors.inkSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PaperDivider()
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = "获取年鉴失败: $errorMsg",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                } else if (summary != null) {
                    val data = summary ?: AlmanacSummaryResponse()

                    // 第一幕：岁月卷首
                    AlmanacChapterSection(
                        chapterNumber = "第一幕",
                        title = "岁月卷首 · 书写刻度"
                    ) {
                        Text(
                            text = "全年共落笔 ${data.prologue.totalEntries} 篇散页，累计 ${data.prologue.totalWords} 字，点亮 ${data.prologue.activeDays} 个真实日子。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        if (data.prologue.firstDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "起止刻度: ${data.prologue.firstDate} — ${data.prologue.lastDate}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第二幕：山川行迹
                    AlmanacChapterSection(
                        chapterNumber = "第二幕",
                        title = "山川行迹 · 步履所至 (${data.atlas.totalCities} 城)"
                    ) {
                        if (data.atlas.cities.isEmpty()) {
                            Text("本年度暂无远行足迹记录", fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            Text(
                                text = data.atlas.cities.joinToString(" · ") { it.cityName },
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第三幕：心愿圆满
                    AlmanacChapterSection(
                        chapterNumber = "第三幕",
                        title = "心愿圆满 · 念念回响"
                    ) {
                        Text(
                            text = "圆满达成 ${data.wishes.fulfilledCount} 项心愿 · 仍有 ${data.wishes.wantingCount} 项向往中",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (data.wishes.totalActual > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "心愿兑现投入: ${FormatUtils.centsToYuanString(data.wishes.totalActual)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第四幕：浮光掠影
                    AlmanacChapterSection(
                        chapterNumber = "第四幕",
                        title = "浮光掠影 · 四季相纸 (${data.highlights.photos.size} 帧)"
                    ) {
                        val seasonItems = listOfNotNull(
                            data.highlights.spring?.let { "春 · ${it.title}" },
                            data.highlights.summer?.let { "夏 · ${it.title}" },
                            data.highlights.autumn?.let { "秋 · ${it.title}" },
                            data.highlights.winter?.let { "冬 · ${it.title}" }
                        )
                        if (seasonItems.isEmpty()) {
                            Text("四季流转，静待更多影像沉淀", fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            seasonItems.forEach { line ->
                                Text(
                                    text = line,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第五幕：柴米油盐
                    AlmanacChapterSection(
                        chapterNumber = "第五幕",
                        title = "柴米油盐 · 生活光谱"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "年度总支出: ${FormatUtils.centsToYuanString(data.finances.totalExpense)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                            Text(
                                text = "年度总收入: ${FormatUtils.centsToYuanString(data.finances.totalIncome)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentMoss
                            )
                        }
                        if (data.finances.categories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            data.finances.categories.take(4).forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, fontSize = 12.sp, color = colors.inkSecondary)
                                    Text(
                                        FormatUtils.centsToYuanString(cat.amount),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlmanacChapterSection(
    chapterNumber: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archivePaper)
            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chapterNumber,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                color = colors.accentWarm
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.inkPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
internal fun GlobalSearchDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<com.looseleaf.app.model.GlobalSearchResponse?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var viewingDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    viewingDetail?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { viewingDetail = null },
            containerColor = colors.archivePaper,
            title = {
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Text(
                    text = content,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewingDetail = null }) {
                    Text("合上", color = colors.accentWarm)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "全库检索生活记忆",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                PaperTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        searchJob?.cancel()
                        if (q.trim().length >= 2) {
                            searchJob = scope.launch {
                                kotlinx.coroutines.delay(280)
                                isSearching = true
                                runCatching {
                                    searchResult = apiClient.searchGlobalArchive(q)
                                }
                                isSearching = false
                            }
                        } else {
                            searchResult = null
                            isSearching = false
                        }
                    },
                    placeholder = "搜索……",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.accentWarm)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                val res = searchResult
                if (res == null || (res.entries.isEmpty() && res.footprints.isEmpty() && res.wishes.isEmpty() && res.projects.isEmpty())) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "输入关键词检索所有归册记忆" else "未找到匹配的生活碎片",
                            fontSize = 12.sp,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            color = colors.inkMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (res.entries.isNotEmpty()) {
                            item {
                                Text("生活印记 (${res.entries.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.entries, key = { "e_${it.id}" }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            onSelectEntry(entry)
                                            onDismiss()
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title.ifBlank { "无题随笔" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.inkPrimary)
                                        if (entry.content.isNotBlank()) {
                                            Text(entry.content, fontSize = 11.sp, color = colors.inkSecondary, maxLines = 1)
                                        }
                                    }
                                    Text(FormatUtils.formatDisplayDate(entry.occurredAt), fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.footprints.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("山川足迹 (${res.footprints.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.footprints, key = { "f_${it.id}" }) { foot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "山川足迹 · ${foot.cityName}" to listOfNotNull(
                                                foot.title?.takeIf { it.isNotBlank() },
                                                "到访时刻: ${FormatUtils.formatDisplayDate(foot.visitedAt)}",
                                                foot.note?.takeIf { it.isNotBlank() }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${foot.cityName} · ${foot.title ?: "足迹"}", fontSize = 12.sp, color = colors.inkPrimary)
                                    Text(foot.visitedAt?.take(10) ?: "", fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.wishes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("心愿清单 (${res.wishes.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.wishes, key = { "w_${it.id}" }) { wish ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "心愿清单 · ${wish.title}" to listOfNotNull(
                                                "心愿类别: ${if (wish.kind == "travel") "山川旅程" else if (wish.kind == "milestone") "里程碑" else "好物"}",
                                                if (wish.budget > 0) "意向预算: ${FormatUtils.centsToYuanString(wish.budget)}" else null,
                                                wish.cityName?.takeIf { it.isNotBlank() }?.let { "目的地: $it" },
                                                wish.note?.takeIf { it.isNotBlank() }?.let { "手记备忘: $it" }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(wish.title, fontSize = 12.sp, color = colors.inkPrimary)
                                    if (wish.budget > 0) {
                                        Text(FormatUtils.centsToYuanString(wish.budget), fontSize = 11.sp, color = colors.accentWarm)
                                    }
                                }
                            }
                        }

                        if (res.projects.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("造物项目 (${res.projects.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.projects, key = { "p_${it.id}" }) { proj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "造物项目 · ${proj.title}" to listOfNotNull(
                                                proj.subtitle?.takeIf { it.isNotBlank() }?.let { "副标题: $it" },
                                                proj.status?.takeIf { it.isNotBlank() }?.let { "状态: $it" },
                                                proj.description?.takeIf { it.isNotBlank() }?.let { "描述: $it" },
                                                proj.story?.takeIf { it.isNotBlank() }?.let { "造物故事: $it" },
                                                proj.githubUrl?.takeIf { it.isNotBlank() }?.let { "源码仓库: $it" }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(proj.title, fontSize = 12.sp, color = colors.inkPrimary)
                                    Text(proj.status ?: "", fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
