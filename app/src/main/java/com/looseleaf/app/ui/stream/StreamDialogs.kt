package com.looseleaf.app.ui.stream

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.looseleaf.app.R
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import com.looseleaf.app.ui.components.ArchiveDateTimeSplitRow
import com.looseleaf.app.ui.components.ArchiveField
import com.looseleaf.app.ui.components.ArchiveSecondaryButton
import com.looseleaf.app.ui.components.ArchiveSelectableCapsule
import com.looseleaf.app.ui.components.ArchiveSheet
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 散页详情 / 修订抽屉
 *
 * 阅读态：一页排版好的书 —— 日期小标与分类签、衬线大标题、朱砂短线、宽行距衬线正文、
 *        地点、配图，最后是描边的「删除这一页」。
 * 修订态：类型胶囊、标题 / 正文输入槽、时间、地点、私密开关、配图管理（含「添加」方块）。
 */
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
    // 编辑态的 key 用 entry.id 而不是 entry 本身：同步刷新会用同一条记录的新实例替换旧实例，
    // 若以对象作 key，用户正在输入的内容会被重置回服务器旧值。
    var isEditing by remember(entry.id) { mutableStateOf(false) }
    var editType by remember(entry.id) { mutableStateOf(if (entry.type == "wish") "wish" else "life") }
    // 派生 / 占位标题进入修订态时视为未填写，避免保存后又把首句写回标题
    var editTitle by remember(entry.id) { mutableStateOf(if (entry.hasDistinctTitle()) entry.title else "") }
    var editContent by remember(entry.id) { mutableStateOf(entry.content) }
    var editLocation by remember(entry.id) { mutableStateOf(entry.location ?: "") }
    var editOccurredAt by remember(entry.id) { mutableStateOf(FormatUtils.parseToLocalDateTime(entry.occurredAt)) }
    var editIsPrivate by remember(entry.id) { mutableStateOf(entry.isPrivate) }
    var editAttachments by remember(entry.id) { mutableStateOf(entry.attachments) }
    var editCategory by remember(entry.id) { mutableStateOf(com.looseleaf.app.model.EntryCategory.fromCode(entry.category).code) }
    var userEditedTime by remember(entry.id) { mutableStateOf(false) }
    var userEditedLocation by remember(entry.id) { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        // 统一走系统照片选择器，无需任何存储权限
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isUploadingPhoto = true
                val hadNoPhotosBefore = editAttachments.isEmpty()
                val addedExifs = mutableListOf<com.looseleaf.app.darkroom.ExtractedExif>()
                try {
                    for (uri in uris) {
                        val processed = ImagePipeline.processImage(context, uri)
                        val att = ImagePipeline.uploadImage(apiClient, processed)
                        editAttachments = editAttachments + att
                        addedExifs += processed.exif
                    }
                    if (addedExifs.isNotEmpty()) {
                        if (hadNoPhotosBefore && !userEditedTime) {
                            addedExifs.firstNotNullOfOrNull { exif ->
                                FormatUtils.parseExifDateTimeOrNull(exif.dateTimeOriginal, exif.offsetTimeOriginal)
                            }?.let { editOccurredAt = it }
                        }
                        if ((hadNoPhotosBefore || editLocation.isBlank()) && !userEditedLocation) {
                            val coords = addedExifs.firstNotNullOfOrNull { exif ->
                                val lat = exif.latitude
                                val lng = exif.longitude
                                if (lat != null && lng != null) lat to lng else null
                            }
                            if (coords != null) {
                                val resolved = LocationHelper.resolveCoordinates(context, coords.first, coords.second)
                                if (resolved != null && resolved.locationName.isNotBlank() && !userEditedLocation) {
                                    editLocation = resolved.locationName
                                }
                            }
                        }
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
        val finalAttachmentIds = editAttachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
        val req = CreateEntryRequest(
            type = editType,
            title = editTitle.trim(),
            content = editContent.trim(),
            occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
            location = editLocation.trim(),
            wishId = if (editType == "wish") entry.wishId else null,
            isPrivate = editIsPrivate,
            attachmentIds = finalAttachmentIds,
            clearAttachments = finalAttachmentIds.isEmpty(),
            // 心愿印记不携带画廊分类；日常随笔按所选分类同步到后端与 Vesper Space
            category = if (editType == "wish") "" else editCategory
        )
        onSaveEdit(req, editAttachments)
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = if (isEditing) "修订这一页" else "散页",
        cancelText = if (isEditing) "取消修订" else "关闭",
        onCancel = {
            if (isEditing) isEditing = false else onDismiss()
        },
        actionText = if (isEditing) stringResource(R.string.stream_dialogs_text_03) else "修订",
        isActionEnabled = !isEditing || editTitle.isNotBlank() || editContent.isNotBlank(),
        onAction = {
            if (isEditing) saveAction() else isEditing = true
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            if (isEditing) {
                EditSectionLabel("类型")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchiveSelectableCapsule(
                        text = "日常随笔",
                        selected = editType == "life",
                        leadingDotColor = colors.tagDaily,
                        onClick = { editType = "life" }
                    )
                    ArchiveSelectableCapsule(
                        text = "心愿印记",
                        selected = editType == "wish",
                        leadingDotColor = colors.tagWish,
                        onClick = { editType = "wish" }
                    )
                }

                if (editType == "life") {
                    Spacer(modifier = Modifier.height(18.dp))
                    EditSectionLabel("分类")
                    EntryCategoryPicker(
                        selectedCode = editCategory,
                        onSelect = { editCategory = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                ArchiveField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = "标题",
                    placeholder = stringResource(R.string.stream_dialogs_placeholder_01),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                ArchiveField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = "正文",
                    placeholder = stringResource(R.string.stream_dialogs_placeholder_02),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                EditSectionLabel("时间")
                ArchiveDateTimeSplitRow(
                    selectedDateTime = editOccurredAt,
                    onDateTimeSelected = {
                        editOccurredAt = it
                        userEditedTime = true
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ArchiveField(
                    value = editLocation,
                    onValueChange = {
                        editLocation = it
                        userEditedLocation = it.isNotBlank()
                    },
                    label = "地点",
                    placeholder = stringResource(R.string.quick_note_sheet_text_04),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 私密开关：整行可点，右侧为状态胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.md)
                        .background(colors.archiveSubtle)
                        .clickable(role = Role.Switch) { editIsPrivate = !editIsPrivate }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FolioGlyphs.Lock,
                        contentDescription = null,
                        tint = if (editIsPrivate) colors.accentSmallText else colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "仅自己可见",
                            style = ArchiveTextStyles.label,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = if (editIsPrivate) "仅保存在散页，不对外公开或投射" else "公开记录，带图散页将自动同步至 Vesper Space",
                            style = ArchiveTextStyles.meta,
                            color = colors.inkMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(ArchiveShapes.full)
                            .background(if (editIsPrivate) colors.inverseSurface else colors.archiveCard)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (editIsPrivate) "私密" else "公开",
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = if (editIsPrivate) colors.inverseInk else colors.inkSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 配图管理
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stream_dialogs_text_tpl_01, editAttachments.size),
                        style = ArchiveTextStyles.overline,
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUploadingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp,
                            color = colors.accentWarm,
                            trackColor = colors.borderSubtle
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_01),
                            style = ArchiveTextStyles.meta,
                            color = colors.accentSmallText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
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
                                .size(84.dp)
                                .clip(ArchiveShapes.md)
                                .background(colors.filmMatteBg)
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
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.inverseSurface.copy(alpha = 0.78f))
                                    .clickable(role = Role.Button) {
                                        editAttachments = editAttachments.filter { it.id != att.id }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = FolioGlyphs.Close,
                                    contentDescription = stringResource(R.string.stream_dialogs_contentDescription_01),
                                    tint = colors.inverseInk,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    if (!isUploadingPhoto && editAttachments.size < 9) {
                        Column(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(ArchiveShapes.md)
                                .background(colors.archiveSubtle)
                                .border(ArchiveLines.hairline, colors.borderDivider, ArchiveShapes.md)
                                .clickable(role = Role.Button) {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = FolioGlyphs.Plus,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_02),
                                style = ArchiveTextStyles.meta,
                                color = colors.inkSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                ArchiveSecondaryButton(
                    text = "删除这一页",
                    isDestructive = true,
                    onClick = onDelete
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // ---------- 阅读态 ----------
                val (tagLabel, tagTint) = entryTagOf(entry, colors)
                // 标题若只是正文首句的派生或占位，阅读态不再重复渲染
                val showTitle = entry.hasDistinctTitle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                        style = ArchiveTextStyles.overline,
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.isPrivate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ArchiveShapes.full)
                                .background(colors.archiveSubtle)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = FolioGlyphs.Lock,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "私密", style = ArchiveTextStyles.meta, color = colors.inkSecondary)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(ArchiveShapes.full)
                            .background(tagTint.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(tagTint)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = tagLabel, style = ArchiveTextStyles.meta, fontWeight = FontWeight.Medium, color = tagTint)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (showTitle) {
                    Text(
                        text = entry.title,
                        style = ArchiveTextStyles.pageTitle.copy(fontSize = 26.sp, lineHeight = 34.sp),
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .clip(ArchiveShapes.full)
                        .background(colors.accentWarm)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                    Text(
                        text = entry.content,
                        style = ArchiveTextStyles.essay.copy(fontSize = 16.sp, lineHeight = 29.sp),
                        color = colors.inkPrimary.copy(alpha = 0.86f)
                    )
                }

                val detailPlace = entry.location?.trim().orEmpty()
                if (detailPlace.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = FolioGlyphs.Pin,
                            contentDescription = "地点",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = detailPlace,
                            style = ArchiveTextStyles.meta,
                            color = colors.inkSecondary
                        )
                    }
                }

                if (entry.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    EntryAttachmentsGrid(
                        attachments = entry.attachments,
                        apiClient = apiClient,
                        onPhotoClick = onPhotoClick
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                ArchiveSecondaryButton(
                    text = "删除这一页",
                    isDestructive = true,
                    onClick = onDelete
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EditSectionLabel(text: String) {
    Text(
        text = text,
        style = ArchiveTextStyles.overline,
        color = LooseLeafTheme.colors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * 全局检索抽屉
 *
 * 顶部为凹槽搜索槽（放大镜 + 输入 + 清除 / 加载），结果按「散页」「心愿」分组，
 * 每条左侧为类型方印，命中关键词以朱砂淡底高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    /** 命中心愿时跳转到心愿单；传 null 时展示只读摘要弹窗。 */
    onOpenWish: ((Long) -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<com.looseleaf.app.model.GlobalSearchResponse?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var viewingDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

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
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // 搜索槽
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.full)
                    .background(colors.archiveSubtle)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FolioGlyphs.Search,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
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
                        fontFamily = ArchiveFontFamilies.Text,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.stream_dialogs_placeholder_03),
                                    style = ArchiveTextStyles.body,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentWarm,
                            trackColor = colors.borderSubtle
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.inkMuted.copy(alpha = 0.18f))
                                .clickable(role = Role.Button) {
                                    searchQuery = ""
                                    searchResult = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = FolioGlyphs.Close,
                                contentDescription = "清除",
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val res = searchResult
            if (res == null || (res.entries.isEmpty() && res.wishes.isEmpty())) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 380.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.archiveSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FolioGlyphs.Search,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "检索所有归册的记忆" else "没有找到匹配的生活碎片",
                        style = ArchiveTextStyles.essayTitle.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "输入至少两个字，标题、正文与心愿都会被搜到" else "换个关键词，或少输几个字试试",
                        style = ArchiveTextStyles.meta,
                        color = colors.inkMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (res.entries.isNotEmpty()) {
                        item {
                            SearchGroupLabel(stringResource(R.string.stream_dialogs_entries_count, res.entries.size))
                        }
                        items(res.entries, key = { "e_${it.id}" }) { entry ->
                            SearchResultRow(
                                badge = if (entry.type == "wish") "愿" else "页",
                                badgeTint = if (entry.type == "wish") colors.tagWish else colors.tagDaily,
                                onClick = {
                                    onSelectEntry(entry)
                                    onDismiss()
                                },
                                trailing = FormatUtils.formatDisplayDate(entry.occurredAt)
                            ) {
                                SearchHighlightText(
                                    text = if (entry.hasDistinctTitle()) entry.title
                                        else entry.content.lineSequence().firstOrNull()?.trim().orEmpty()
                                            .ifBlank { stringResource(R.string.stream_dialogs_untitled) },
                                    query = searchQuery,
                                    fontSize = 15.sp,
                                    fontFamily = ArchiveFontFamilies.Reading,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkPrimary,
                                    maxLines = 1
                                )
                                if (entry.content.isNotBlank()) {
                                    SearchHighlightText(
                                        text = entry.content,
                                        query = searchQuery,
                                        fontSize = 12.sp,
                                        color = colors.inkSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (res.wishes.isNotEmpty()) {
                        item {
                            SearchGroupLabel(stringResource(R.string.stream_dialogs_wishes_count, res.wishes.size))
                        }
                        items(res.wishes, key = { "w_${it.id}" }) { wish ->
                            SearchResultRow(
                                badge = "愿",
                                badgeTint = colors.tagWish,
                                onClick = {
                                    if (onOpenWish != null) {
                                        onOpenWish(wish.id)
                                    } else {
                                        viewingDetail = "心愿清单 · ${wish.title}" to listOfNotNull(
                                            "心愿类别: ${if (wish.kind == "travel") "旅程" else if (wish.kind == "milestone") "里程碑" else "好物"}",
                                            if (wish.budget > 0) "意向预算: ${FormatUtils.centsToYuanString(wish.budget)}" else null,
                                            wish.note?.takeIf { it.isNotBlank() }?.let { "手记备忘: $it" }
                                        ).joinToString("\n\n")
                                    }
                                },
                                trailing = if (wish.budget > 0) FormatUtils.centsToYuanString(wish.budget) else null,
                                trailingTint = colors.tagWish
                            ) {
                                SearchHighlightText(
                                    text = wish.title,
                                    query = searchQuery,
                                    fontSize = 15.sp,
                                    fontFamily = ArchiveFontFamilies.Reading,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupLabel(text: String) {
    Text(
        text = text,
        style = ArchiveTextStyles.overline,
        color = LooseLeafTheme.colors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SearchResultRow(
    badge: String,
    badgeTint: Color,
    onClick: () -> Unit,
    trailing: String?,
    trailingTint: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.md)
            .background(colors.archiveCard)
            .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(ArchiveShapes.sm)
                .background(badgeTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                fontFamily = ArchiveFontFamilies.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = badgeTint
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
        if (!trailing.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = trailing,
                style = ArchiveTextStyles.meta,
                color = trailingTint ?: colors.inkMuted
            )
        }
    }
}

/**
 * 搜索关键词高亮：命中片段为朱砂字 + 朱砂淡底
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
    val highlightFg = colors.accentSmallText
    val highlightBg = colors.accentTint
    val annotated = remember(text, trimmed, highlightFg, highlightBg) {
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
                            color = highlightFg,
                            background = highlightBg,
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
        fontFamily = fontFamily ?: ArchiveFontFamilies.Text,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
