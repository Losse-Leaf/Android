package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import com.looseleaf.app.darkroom.ExtractedExif
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource

internal data class AttachedPhotoMeta(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exif: ExtractedExif
)

internal data class AttachedPhotoItem(
    val previewUri: String,
    val processed: AttachedPhotoMeta,
    val savedFile: File
)

@Serializable
internal data class AttachedPhotoSaveModel(
    val previewUri: String,
    val filePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exif: com.looseleaf.app.darkroom.ExtractedExif
)

internal fun AttachedPhotoItem.toSaveModel() = AttachedPhotoSaveModel(
    previewUri = previewUri,
    filePath = savedFile.absolutePath,
    mimeType = processed.mimeType,
    width = processed.width,
    height = processed.height,
    exif = processed.exif
)

internal fun AttachedPhotoSaveModel.toItem() = AttachedPhotoItem(
    previewUri = previewUri,
    processed = AttachedPhotoMeta(
        mimeType = mimeType,
        width = width,
        height = height,
        exif = exif
    ),
    savedFile = File(filePath)
)

internal val attachedPhotosSaver = androidx.compose.runtime.saveable.Saver<MutableState<List<AttachedPhotoItem>>, String>(
    save = { state ->
        val models = state.value.map { it.toSaveModel() }
        Json.encodeToString(models)
    },
    restore = { jsonStr ->
        val models = runCatching { Json.decodeFromString<List<AttachedPhotoSaveModel>>(jsonStr) }.getOrDefault(emptyList())
        mutableStateOf(models.filter { File(it.filePath).exists() }.map { it.toItem() })
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickNoteSheet(
    onDismiss: () -> Unit,
    syncManager: SyncManager,
    apiClient: APIClient,
    onSuccess: () -> Unit
) {
    QuickNoteSheetBody(onDismiss, syncManager, apiClient, onSuccess)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteSheetBody(
    onDismiss: () -> Unit,
    syncManager: SyncManager,
    @Suppress("UNUSED_PARAMETER") apiClient: APIClient,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors

    val draftStore = remember { DraftStore.get(context) }
    val draftPrefix = "quick_note_"

    var noteTitle by remember { mutableStateOf(draftStore.getString("${draftPrefix}title").orEmpty()) }
    var noteContent by remember { mutableStateOf(draftStore.getString("${draftPrefix}content").orEmpty()) }
    var noteLocation by remember { mutableStateOf(draftStore.getString("${draftPrefix}location").orEmpty()) }
    var isPrivate by remember { mutableStateOf(false) }
    // 方式 A 原生分类：空串 = 日常；草稿里保留上次选择
    var noteCategory by remember {
        mutableStateOf(
            com.looseleaf.app.model.EntryCategory.fromCode(draftStore.getString("${draftPrefix}category")).code
        )
    }
    var isLocating by remember { mutableStateOf(false) }
    var userEditedTime by remember { mutableStateOf(false) }
    var userEditedLocation by remember { mutableStateOf(noteLocation.isNotBlank()) }
    var noteOccurredAt by remember {
        mutableStateOf(
            draftStore.getString("${draftPrefix}occurred_at_millis")?.toLongOrNull()
                ?.let { millis ->
                    java.time.Instant.ofEpochMilli(millis)
                        .atZone(FormatUtils.BeijingZone)
                        .toLocalDateTime()
                }
                ?: LocalDateTime.now(FormatUtils.BeijingZone)
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(noteTitle, noteContent, noteLocation, noteOccurredAt, noteCategory) {
        delay(300)
        draftStore.putString("${draftPrefix}category", noteCategory)
        draftStore.putString("${draftPrefix}title", noteTitle)
        draftStore.putString("${draftPrefix}content", noteContent)
        draftStore.putString("${draftPrefix}location", noteLocation)
        draftStore.putString(
            "${draftPrefix}occurred_at_millis",
            noteOccurredAt.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli().toString()
        )
    }

    var attachedPhotos by rememberSaveable(saver = attachedPhotosSaver) { mutableStateOf<List<AttachedPhotoItem>>(emptyList()) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    var noteSubmitted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            val isChangingConfig = (context as? android.app.Activity)?.isChangingConfigurations == true
            if (!noteSubmitted && !isChangingConfig) {
                attachedPhotos.forEach { photo ->
                    runCatching { photo.savedFile.delete() }
                }
            }
        }
    }

    val fetchCurrentLocation: () -> Unit = {
        scope.launch {
            isLocating = true
            try {
                val res = LocationHelper.getCurrentLocation(context)
                if (res != null && res.locationName.isNotBlank()) {
                    noteLocation = res.locationName
                    // 当前定位属于自动填充，后续装入带 GPS 元信息的照片时仍优先采用照片拍摄地点
                    userEditedLocation = false
                }
            } finally {
                isLocating = false
            }
        }
    }

    var locationPermanentlyDenied by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            locationPermanentlyDenied = false
            fetchCurrentLocation()
        } else {
            val activity = context as? android.app.Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION
                )
            if (permanentlyDenied) {
                locationPermanentlyDenied = true
                ArchiveToastManager.show("定位权限已被永久拒绝，请到系统设置开启", isError = true)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (attachedPhotos.size >= 9) {
                ArchiveToastManager.show("相纸已满 9 张，请先移除后再装入", isError = true)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isProcessingPhoto = true
                try {
                    val remainingSlots = 9 - attachedPhotos.size
                    val targetUris = uris.take(remainingSlots)
                    val newItems = withContext(Dispatchers.IO) {
                        targetUris.map { uri ->
                            async {
                                val res = ImagePipeline.processImage(context, uri)
                                val saved = syncManager.saveCompressedImageToOutboxDir(res.compressedBytes)
                                AttachedPhotoItem(
                                    previewUri = "file://${saved.absolutePath}",
                                    processed = AttachedPhotoMeta(
                                        mimeType = res.mimeType,
                                        width = res.width,
                                        height = res.height,
                                        exif = res.exif
                                    ),
                                    savedFile = saved
                                )
                            }
                        }.awaitAll()
                    }
                    if (newItems.isNotEmpty()) {
                        attachedPhotos = (attachedPhotos + newItems).take(9)
                        // 发照片优先读取元信息（EXIF）中的拍摄时间与 GPS 地点
                        var appliedExifTime = false
                        var appliedExifLocation = false

                        val exifDateTime = newItems.firstNotNullOfOrNull { item ->
                            FormatUtils.parseExifDateTimeOrNull(
                                item.processed.exif.dateTimeOriginal,
                                item.processed.exif.offsetTimeOriginal
                            )
                        }
                        if (exifDateTime != null && !userEditedTime) {
                            noteOccurredAt = exifDateTime
                            appliedExifTime = true
                        }

                        val exifCoords = newItems.firstNotNullOfOrNull { item ->
                            val lat = item.processed.exif.latitude
                            val lng = item.processed.exif.longitude
                            if (lat != null && lng != null) lat to lng else null
                        }
                        if (exifCoords != null && !userEditedLocation) {
                            isLocating = true
                            try {
                                val resolved = LocationHelper.resolveCoordinates(
                                    context = context,
                                    latitude = exifCoords.first,
                                    longitude = exifCoords.second
                                )
                                if (resolved != null && resolved.locationName.isNotBlank() && !userEditedLocation) {
                                    noteLocation = resolved.locationName
                                    appliedExifLocation = true
                                }
                            } finally {
                                isLocating = false
                            }
                        }

                        val toastMsg = when {
                            appliedExifTime && appliedExifLocation ->
                                "已装裱 ${newItems.size} 张相纸，已读取拍摄时间与地点"
                            appliedExifTime ->
                                "已装裱 ${newItems.size} 张相纸，已读取拍摄时间"
                            appliedExifLocation ->
                                "已装裱 ${newItems.size} 张相纸，已读取拍摄地点"
                            else ->
                                "已装裱 ${newItems.size} 张相纸"
                        }
                        ArchiveToastManager.show(toastMsg)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ArchiveToastManager.show("相纸装裱失败: ${e.message}", isError = true)
                } finally {
                    isProcessingPhoto = false
                }
            }
        }
    }

    val canSave = !isProcessingPhoto && !isSubmitting &&
        (noteTitle.isNotBlank() || noteContent.isNotBlank() || attachedPhotos.isNotEmpty())

    val submitText = if (isSubmitting) "提交中…" else "归册"

    val onSubmitAction: () -> Unit = onSubmitAction@{
        if (isSubmitting) return@onSubmitAction
        isSubmitting = true
        scope.launch {
            try {
                // 未填标题时如实提交空串：卡片与详情页会直接展示正文，不再出现占位标题
                val effectiveTitle = noteTitle.trim()
                val occurredAtIso = FormatUtils.toBeijingIsoString(noteOccurredAt)
                val trimmedContent = noteContent.trim()
                val trimmedLocation = noteLocation.trim().takeIf { it.isNotBlank() }

                if (attachedPhotos.isNotEmpty()) {
                    val primary = attachedPhotos.first()
                    val extraFiles = attachedPhotos.drop(1).map {
                        OfflineMediaFileItem(
                            filePath = it.savedFile.absolutePath,
                            mimeType = it.processed.mimeType,
                            width = it.processed.width,
                            height = it.processed.height
                        )
                    }
                    val req = CreateEntryRequest(
                        type = "life",
                        title = effectiveTitle,
                        content = trimmedContent,
                        occurredAt = occurredAtIso,
                        location = trimmedLocation,
                        isPrivate = isPrivate,
                        category = noteCategory
                    )
                    syncManager.createMediaEntryOffline(
                        localImageFilePath = primary.savedFile.absolutePath,
                        mimeType = primary.processed.mimeType,
                        width = primary.processed.width,
                        height = primary.processed.height,
                        req = req,
                        additionalMediaFiles = extraFiles
                    )
                    ArchiveToastManager.show("图文散页已装裱归册")
                } else {
                    syncManager.createEntryOptimistic(
                        type = "life",
                        title = effectiveTitle,
                        content = trimmedContent,
                        occurredAt = occurredAtIso,
                        location = trimmedLocation,
                        isPrivate = isPrivate,
                        category = noteCategory
                    )
                    ArchiveToastManager.show("随笔已归册")
                }

                noteSubmitted = true
                draftStore.clearPrefix(draftPrefix)
                onSuccess()
            } catch (e: CancellationException) {
                isSubmitting = false
                throw e
            } catch (e: Exception) {
                isSubmitting = false
                ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
            } finally {
                if (!noteSubmitted) {
                    isSubmitting = false
                }
            }
        }
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        dismissEnabled = { !isSubmitting },
        title = "新散页",
        cancelText = stringResource(R.string.quick_note_cancel),
        actionText = submitText,
        onAction = onSubmitAction,
        isActionEnabled = canSave
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchiveSelectableCapsule(
                    text = if (isPrivate) "仅自己可见" else "公开 · 含图同步至 Space",
                    selected = isPrivate,
                    onClick = { isPrivate = !isPrivate },
                    leadingDotColor = if (isPrivate) colors.accentWarm else colors.inkMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 原生分类（与 Vesper Space 画廊共享）：日常为默认，不强加画廊分类
            EntryCategoryPicker(
                selectedCode = noteCategory,
                onSelect = { noteCategory = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.foundation.text.BasicTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = com.looseleaf.app.theme.ArchiveTextStyles.pageTitle.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (noteTitle.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_note_sheet_text_01),
                            style = com.looseleaf.app.theme.ArchiveTextStyles.pageTitle.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
                            color = colors.inkMuted.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                    .background(colors.accentWarm.copy(alpha = 0.7f))
            )
            Spacer(modifier = Modifier.height(14.dp))

            androidx.compose.foundation.text.BasicTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    // 随内容自然向下生长，由外层 verticalScroll 负责滚动，不再在固定框内裁切
                    .heightIn(min = 168.dp),
                textStyle = com.looseleaf.app.theme.ArchiveTextStyles.essay.copy(
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (noteContent.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_note_sheet_text_02),
                            style = com.looseleaf.app.theme.ArchiveTextStyles.essay.copy(fontSize = 16.sp, lineHeight = 28.sp),
                            color = colors.inkMuted.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (attachedPhotos.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quick_note_sheet_text_tpl_01, attachedPhotos.size),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                    if (attachedPhotos.size < 9) {
                        TextButton(
                            onClick = {
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo_library),
                                contentDescription = "装裱相纸",
                                tint = colors.accentSmallText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(stringResource(R.string.quick_note_mount_paper), fontSize = 12.sp, color = colors.accentSmallText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachedPhotos.forEachIndexed { idx, photo ->
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(com.looseleaf.app.theme.ArchiveShapes.sm)
                                .background(colors.archiveSubtle)
                                .border(1.dp, colors.borderCard, com.looseleaf.app.theme.ArchiveShapes.sm)
                        ) {
                            AsyncImage(
                                model = photo.previewUri,
                                contentDescription = stringResource(R.string.quick_note_sheet_contentDescription_01),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colors.inverseSurface.copy(alpha = 0.75f))
                                    .clickable {
                                        runCatching { photo.savedFile.delete() }
                                        attachedPhotos = attachedPhotos.filterIndexed { i, _ -> i != idx }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = com.looseleaf.app.ui.components.FolioGlyphs.Close,
                                    contentDescription = stringResource(R.string.quick_note_sheet_contentDescription_02),
                                    tint = colors.inverseInk,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(3.dp)
                                    .background(colors.inkPrimary.copy(alpha = 0.6f), com.looseleaf.app.theme.ArchiveShapes.xs)
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.quick_note_sheet_text_tpl_02, idx + 1),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.onAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.looseleaf.app.theme.ArchiveShapes.lg)
                        .background(colors.archiveSubtle)
                        .clickable(enabled = !isProcessingPhoto) {
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                            .background(colors.archiveCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = colors.accentWarm,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo_library),
                                contentDescription = "相册装裱",
                                tint = colors.accentSmallText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isProcessingPhoto) "相纸显影处理中" else stringResource(R.string.quick_note_album_mount),
                            style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "可选 9 张，优先读取照片拍摄时间与地点",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            color = colors.inkMuted
                        )
                    }
                    Icon(
                        imageVector = com.looseleaf.app.ui.components.FolioGlyphs.Plus,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.quick_note_sheet_text_03),
                style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            ArchiveDateTimeSplitRow(
                selectedDateTime = noteOccurredAt,
                onDateTimeSelected = {
                    noteOccurredAt = it
                    userEditedTime = true
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quick_note_sheet_text_04),
                    style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                    color = colors.inkMuted
                )
                ArchiveTextButton(
                    text = when {
                        isLocating -> "正在定位…"
                        locationPermanentlyDenied -> "去设置"
                        else -> "使用当前定位"
                    },
                    enabled = !isLocating,
                    onClick = {
                        when {
                            locationPermanentlyDenied -> context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                            LocationHelper.hasLocationPermission(context) -> fetchCurrentLocation()
                            else -> locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            ArchiveField(
                value = noteLocation,
                onValueChange = {
                    noteLocation = it
                    userEditedLocation = it.isNotBlank()
                },
                label = "具体地点",
                placeholder = "街巷或地标注记（选填）",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
