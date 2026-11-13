package com.looseleaf.app.ui.stream

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShadows
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheetDialog(
    entry: LifeEntry,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaveEdit: (CreateEntryRequest, List<Attachment>) -> Unit,
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
    var editLocation by remember(entry.id) { mutableStateOf(entry.location ?: "") }
    var editOccurredAt by remember(entry.id) { mutableStateOf(FormatUtils.parseToLocalDateTime(entry.occurredAt)) }
    var editAttachments by remember(entry.id) { mutableStateOf(entry.attachments) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = // MOB-P2-68：统一走系统照片选择器（与全局策略一致，无需任何存储权限）
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
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
                } catch (e: CancellationException) {
                    throw e
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

    val saveAction: () -> Unit = {
        val code = editCityCode.trim()
        val cityName = editCityName.trim().takeIf { it.isNotBlank() }
        val hadCity = !entry.cityCode.isNullOrBlank()
        val finalAttachmentIds = editAttachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
        val req = CreateEntryRequest(
            type = editType,
            title = editTitle.trim(),
            content = editContent.trim(),
            occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
            location = editLocation.trim(),
            cityCode = code.takeIf { it.isNotBlank() },
            cityName = cityName,
            clearCity = hadCity && code.isBlank(),
            projectId = if (editType == "project") entry.projectId else null,
            wishId = if (editType == "wish") entry.wishId else null,
            footprintId = entry.footprintId,
            attachmentIds = finalAttachmentIds,
            clearAttachments = finalAttachmentIds.isEmpty()
        )
        onSaveEdit(req, editAttachments)
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = if (isEditing) "修订散页档案" else "散页档案阅览",
        cancelText = if (isEditing) "取消编辑" else "关闭",
        onCancel = {
            if (isEditing) isEditing = false else onDismiss()
        },
        actionText = if (isEditing) stringResource(R.string.stream_dialogs_text_03) else "编辑",
        isActionEnabled = !isEditing || editTitle.isNotBlank() || editContent.isNotBlank(),
        onAction = {
            if (isEditing) saveAction() else isEditing = true
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (isEditing) {
                // 类型切换
                val typeOptions = listOf(
                    "life" to "日常随笔",
                    "travel" to "游历",
                    "project" to "造物日志",
                    "wish" to "心愿印记"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((k, label) in typeOptions) {
                        ArchiveSelectableCapsule(
                            text = label,
                            selected = editType == k,
                            onClick = { editType = k }
                        )
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

                Spacer(modifier = Modifier.height(8.dp))

                PaperTextField(
                    value = editLocation,
                    onValueChange = { editLocation = it },
                    placeholder = stringResource(R.string.quick_note_sheet_text_04),
                    isUnderline = true,
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                                    color = colors.accentSmallText
                                )
                            }
                        } else {
                            TextButton(
                                onClick = {
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
                                    color = colors.accentSmallText
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
                                            tint = colors.archivePaper,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                ArchiveDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ArchiveTextButton(
                        text = "删除此手记",
                        isDestructive = true,
                        onClick = onDelete
                    )
                }
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
                    ArchiveTextButton(
                        text = "删除",
                        isDestructive = true,
                        onClick = onDelete
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    /**
     * 足迹 / 心愿 / 造物结果跳转（RG-31）。
     *
     * 传 null 时退回旧行为（弹只读文本），保留给尚未接入路由的调用方。
     */
    onOpenTarget: ((String, Long) -> Unit)? = null
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

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.stream_dialogs_text_06),
        cancelText = "关闭",
        actionText = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 便签纸条搜索栏 (Sticky Memo Note Search Bar)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colors.archivePaper,
                border = BorderStroke(1.dp, colors.borderSubtle),
                shadowElevation = ArchiveShadows.stickerElevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 便签上沿和纸胶带装饰痕迹
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(46.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(colors.accentWarm.copy(alpha = 0.35f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
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
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                ),
                                cursorBrush = SolidColor(colors.accentWarm),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.stream_dialogs_placeholder_03),
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontSize = 13.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.accentWarm
                                    )
                                } else if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            searchResult = null
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "清除",
                                            tint = colors.inkMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val res = searchResult
                if (res == null || (res.entries.isEmpty() && res.footprints.isEmpty() && res.wishes.isEmpty() && res.projects.isEmpty())) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 380.dp)
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp),
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
                                        SearchHighlightText(
                                            text = entry.title.ifBlank { stringResource(R.string.stream_dialogs_untitled) },
                                            query = searchQuery,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.inkPrimary
                                        )
                                        if (entry.content.isNotBlank()) {
                                            SearchHighlightText(
                                                text = entry.content,
                                                query = searchQuery,
                                                fontSize = 11.sp,
                                                color = colors.inkSecondary,
                                                maxLines = 1
                                            )
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
                                            if (onOpenTarget != null) {
                                                onOpenTarget("footprint", foot.id)
                                            } else {
                                                viewingDetail = "足迹 · ${foot.cityName}" to listOfNotNull(
                                                    foot.title?.takeIf { it.isNotBlank() },
                                                    "到访时刻: ${FormatUtils.formatDisplayDate(foot.visitedAt)}",
                                                    foot.note?.takeIf { it.isNotBlank() }
                                                ).joinToString("\n\n")
                                            }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SearchHighlightText(
                                        text = "${foot.cityName} · ${foot.title ?: stringResource(R.string.stream_dialogs_footprint_default)}",
                                        query = searchQuery,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
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
                                            if (onOpenTarget != null) {
                                                onOpenTarget("wish", wish.id)
                                            } else {
                                                viewingDetail = "心愿清单 · ${wish.title}" to listOfNotNull(
                                                    "心愿类别: ${if (wish.kind == "travel") "旅程" else if (wish.kind == "milestone") "里程碑" else "好物"}",
                                                    if (wish.budget > 0) "意向预算: ${FormatUtils.centsToYuanString(wish.budget)}" else null,
                                                    wish.cityName?.takeIf { it.isNotBlank() }?.let { "目的地: $it" },
                                                    wish.note?.takeIf { it.isNotBlank() }?.let { "手记备忘: $it" }
                                                ).joinToString("\n\n")
                                            }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SearchHighlightText(
                                        text = wish.title,
                                        query = searchQuery,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
                                    if (wish.budget > 0) {
                                        Text(FormatUtils.centsToYuanString(wish.budget), fontSize = 11.sp, color = colors.accentSmallText)
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
                                            if (onOpenTarget != null) {
                                                onOpenTarget("project", proj.id)
                                            } else {
                                                viewingDetail = "造物项目 · ${proj.title}" to listOfNotNull(
                                                    proj.subtitle?.takeIf { it.isNotBlank() }?.let { "副标题: $it" },
                                                    proj.status?.takeIf { it.isNotBlank() }?.let { "状态: $it" },
                                                    proj.description?.takeIf { it.isNotBlank() }?.let { "描述: $it" },
                                                    proj.story?.takeIf { it.isNotBlank() }?.let { "造物故事: $it" },
                                                    proj.githubUrl?.takeIf { it.isNotBlank() }?.let { "源码仓库: $it" }
                                                ).joinToString("\n\n")
                                            }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SearchHighlightText(
                                        text = proj.title,
                                        query = searchQuery,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(proj.status ?: "", fontSize = 11.sp, color = colors.inkMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * 搜索关键词高亮文本：赤陶色下划线笔痕，绝无刺眼色块底色
 */
@Composable
private fun SearchHighlightText(
    text: String,
    query: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val trimmed = query.trim()
    val annotated = remember(text, trimmed, colors.accentWarm) {
        buildAnnotatedString {
            if (trimmed.isEmpty()) {
                append(text)
            } else {
                var start = 0
                while (start < text.length) {
                    val index = text.indexOf(trimmed, start, ignoreCase = true)
                    if (index == -1) {
                        append(text.substring(start))
                        break
                    }
                    if (index > start) {
                        append(text.substring(start, index))
                    }
                    val matchEnd = index + trimmed.length
                    pushStyle(
                        SpanStyle(
                            color = colors.accentWarm,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    append(text.substring(index, matchEnd))
                    pop()
                    start = matchEnd
                }
            }
        }
    }

    Text(
        text = annotated,
        fontSize = fontSize,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
