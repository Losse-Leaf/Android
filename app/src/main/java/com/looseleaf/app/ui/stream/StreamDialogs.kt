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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
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
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
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
    // MOB-P1-xx：编辑态的 key 用 **entry.id** 而不是 entry 对象本身。
    //
    // 为什么：同步刷新（entriesRevisionFlow → 列表重载）会用**同一条记录的新实例**
    // 替换掉旧实例（数据类按值比较，内容一样但引用不同）。
    // 若用 remember(entry) 作 key，新实例一到，remember 的 key 就变了 ⇒
    // 用户正在输入的标题/正文被**重置回服务器上的旧值** —— 表现为「打字打到一半被清空」。
    // 用 entry.id 作 key：同一条记录的刷新不会打断输入；切换到另一条记录才重置。
    var isEditing by remember(entry.id) { mutableStateOf(false) }
    var editType by remember(entry.id) { mutableStateOf(entry.type) }
    var editTitle by remember(entry.id) { mutableStateOf(entry.title) }
    var editContent by remember(entry.id) { mutableStateOf(entry.content) }
    var editCityCode by remember(entry.id) { mutableStateOf(entry.cityCode ?: "") }
    var editCityName by remember(entry.id) { mutableStateOf(entry.cityName ?: entry.location ?: "") }
    var editOccurredAt by remember(entry.id) { mutableStateOf(FormatUtils.parseToLocalDateTime(entry.occurredAt)) }
    var editAttachments by remember(entry.id) { mutableStateOf(entry.attachments) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = // MOB-P2-68：统一走系统照片选择器（与全局策略一致，无需任何存储权限）
        ActivityResultContracts.PickMultipleVisualMedia()
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
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.projects_wishes_lists_contentDescription_01),
                                tint = if (isEditing) colors.accentWarm else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.ledger_transactions_tab_contentDescription_03),
                                tint = colors.accentWarm,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.paper_components_contentDescription_02),
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
                        placeholder = stringResource(R.string.stream_dialogs_placeholder_01),
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
                        placeholder = stringResource(R.string.stream_dialogs_placeholder_02),
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
                        placeholder = stringResource(R.string.ledger_screen_placeholder_02)
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
                                text = stringResource(R.string.stream_dialogs_text_tpl_01, editAttachments.size),
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
                                        text = stringResource(R.string.stream_dialogs_text_01),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 11.sp,
                                        color = colors.accentWarm
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                            // MOB-P2-68：PickMultipleVisualMedia 需要传 Request
                                            photoPickerLauncher.launch(
                                                androidx.activity.result.PickVisualMediaRequest(
                                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.stream_dialogs_text_02),
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
                                                contentDescription = stringResource(R.string.stream_dialogs_contentDescription_01),
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

                    Spacer(modifier = Modifier.height(16.dp))

                    TerracottaStampButton(
                        text = stringResource(R.string.stream_dialogs_text_03),
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
    // MOB-P1-32：原先传入全量档案只为算「最早年份」—— 分页化后不再有全量列表，
    // 改为直接传入最早年份（由 SQL DISTINCT 查询得到）。
    earliestEntryYear: Int?,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val currentYear = remember { LocalDate.now(FormatUtils.BeijingZone).year }
    val earliestYear = remember(earliestEntryYear, currentYear) {
        if (earliestEntryYear != null) minOf(earliestEntryYear, currentYear) else currentYear
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
                            text = stringResource(R.string.stream_dialogs_text_tpl_02, selectedYear),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_05),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paper_components_contentDescription_02), tint = colors.inkMuted)
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
                                text = stringResource(R.string.stream_dialogs_text_tpl_03, yr),
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
                        text = stringResource(R.string.stream_dialogs_text_tpl_04, errorMsg.orEmpty()),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                } else if (summary != null) {
                    val data = summary ?: AlmanacSummaryResponse()

                    // 第一幕：岁月卷首
                    AlmanacChapterSection(
                        chapterNumber = "第一幕",
                        title = stringResource(R.string.stream_dialogs_title_01)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_tpl_05, data.prologue.totalEntries, data.prologue.totalWords, data.prologue.activeDays),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        if (data.prologue.firstDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_06, data.prologue.firstDate, data.prologue.lastDate),
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
                        title = stringResource(R.string.stream_dialogs_title_tpl_01, data.atlas.totalCities)
                    ) {
                        if (data.atlas.cities.isEmpty()) {
                            Text(stringResource(R.string.stream_dialogs_empty_footprints), fontSize = 12.sp, color = colors.inkMuted)
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
                        title = stringResource(R.string.stream_dialogs_title_02)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_tpl_07, data.wishes.fulfilledCount, data.wishes.wantingCount),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (data.wishes.totalActual > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_08, FormatUtils.centsToYuanString(data.wishes.totalActual)),
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
                        title = stringResource(R.string.stream_dialogs_title_tpl_02, data.highlights.photos.size)
                    ) {
                        val seasonItems = listOfNotNull(
                            data.highlights.spring?.let { "春 · ${it.title}" },
                            data.highlights.summer?.let { "夏 · ${it.title}" },
                            data.highlights.autumn?.let { "秋 · ${it.title}" },
                            data.highlights.winter?.let { "冬 · ${it.title}" }
                        )
                        if (seasonItems.isEmpty()) {
                            Text(stringResource(R.string.stream_dialogs_empty_footprints_desc), fontSize = 12.sp, color = colors.inkMuted)
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
                        title = stringResource(R.string.stream_dialogs_title_03)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_09, FormatUtils.centsToYuanString(data.finances.totalExpense)),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_10, FormatUtils.centsToYuanString(data.finances.totalIncome)),
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

    // MOB-P2-43：原先用的是 Material3 原生 AlertDialog ——
    // 圆角、字体、按钮排布与全站纸墨弹窗家族完全不同，深色模式下还会带上 M3 默认紫调。
    // 这里统一迁移到自研 ArchiveConfirmDialog（只保留一个「合上」动作）。
    viewingDetail?.let { (title, content) ->
        ArchiveConfirmDialog(
            title = title,
            content = content,
            confirmText = stringResource(R.string.stream_dialogs_confirmText_01),
            dismissText = null,
            onConfirm = { viewingDetail = null },
            onDismiss = { viewingDetail = null }
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
                        text = stringResource(R.string.stream_dialogs_text_06),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paper_components_contentDescription_02), tint = colors.inkMuted)
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
                    placeholder = stringResource(R.string.stream_dialogs_placeholder_03),
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
                                Text(stringResource(R.string.stream_dialogs_entries_count, res.entries.size), fontSize = 11.sp, color = colors.inkMuted)
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
                                        Text(entry.title.ifBlank { stringResource(R.string.stream_dialogs_untitled) }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.inkPrimary)
                                        if (entry.content.isNotBlank()) {
                                            Text(entry.content, fontSize = 11.sp, color = colors.inkSecondary, maxLines = 1)
                                        }
                                    }
                                    Text(FormatUtils.formatDisplayDate(entry.occurredAt), fontSize = 11.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.footprints.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(stringResource(R.string.stream_dialogs_footprints_count, res.footprints.size), fontSize = 11.sp, color = colors.inkMuted)
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
                                    Text("${foot.cityName} · ${foot.title ?: stringResource(R.string.stream_dialogs_footprint_default)}", fontSize = 12.sp, color = colors.inkPrimary)
                                    Text(foot.visitedAt?.take(10) ?: "", fontSize = 11.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.wishes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(stringResource(R.string.stream_dialogs_wishes_count, res.wishes.size), fontSize = 11.sp, color = colors.inkMuted)
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
                                Text(stringResource(R.string.stream_dialogs_projects_count, res.projects.size), fontSize = 11.sp, color = colors.inkMuted)
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
                                    Text(proj.status ?: "", fontSize = 11.sp, color = colors.inkMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
