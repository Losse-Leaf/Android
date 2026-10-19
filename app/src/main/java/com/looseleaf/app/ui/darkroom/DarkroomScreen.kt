package com.looseleaf.app.ui.darkroom

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.looseleaf.app.utils.LocationHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.darkroom.ExtractedExif
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

private data class DarkroomPhotoSlot(
    val previewUri: String,
    val processed: ProcessedImageResult,
    val savedOutboxFile: File? = null
)

/** LocalDateTime 走 ISO 字符串持久化，补记的历史时刻不会因旋转被悄悄改成「现在」。 */
private val BeijingDateTimeSaver: Saver<LocalDateTime, String> = Saver(
    save = { it.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
    restore = {
        runCatching { LocalDateTime.parse(it) }
            .getOrDefault(LocalDateTime.now(FormatUtils.BeijingZone))
    }
)

/**
 * 从落盘的相纸文件恢复槽位（旋转 / 进程死亡后的重建）。
 * EXIF 元数据不重建（原始 Uri 已不可得），这点损失远好于
 * 「旋转一下整个槽位连同相纸一起被清空」。
 */
private fun restoreSlotFromPath(path: String): DarkroomPhotoSlot? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        file.inputStream().use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
    }
    return DarkroomPhotoSlot(
        previewUri = "file://$path",
        processed = ProcessedImageResult(
            compressedBytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0)),
            width = bounds.outWidth.coerceAtLeast(1),
            height = bounds.outHeight.coerceAtLeast(1),
            mimeType = "image/jpeg",
            exif = ExtractedExif()
        ),
        savedOutboxFile = file
    )
}

/**
 * 随行暗房与现场拍照记录界面 (DarkroomScreen) - docs/mobile.md §3.2
 * 支持：现场快门全高清直拍、相册多图装入 (最多 9 张)、EXIF 经纬度自动推断城市、0ms 乐观离线秒存与后台静默直传 R2
 */
@Composable
fun DarkroomScreen(
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }

    var photoSlots by remember { mutableStateOf<List<DarkroomPhotoSlot>>(emptyList()) }
    // 已落盘相纸的路径：旋转 / 进程死亡后据此重建 photoSlots（DarkroomPhotoSlot 本身含
    // ByteArray 等不可保存类型，整槽位走 Saver 代价高且易碎）
    var persistedSlotPaths by rememberSaveable { mutableStateOf(listOf<String>()) }
    var activeSlotIndex by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    /** 是否已经成功归册：未归册就离开暗房，需要清理已落盘的相纸，避免孤儿文件 */
    var entrySubmitted by remember { mutableStateOf(false) }
    var lightboxPhotoUrl by remember { mutableStateOf<String?>(null) }
    var lightboxMetadata by remember { mutableStateOf<String?>(null) }

    var titleText by rememberSaveable { mutableStateOf("") }
    var contentText by rememberSaveable { mutableStateOf("") }
    var locationText by rememberSaveable { mutableStateOf("") }
    var selectedCityCode by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCityName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf("life") }
    var darkroomOccurredAt by rememberSaveable(stateSaver = BeijingDateTimeSaver) {
        mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone))
    }

    // 槽位变化即同步路径快照；photoSlots 为空时清空快照（用户手动移除相纸的语义）
    persistedSlotPaths = photoSlots.mapNotNull { it.savedOutboxFile?.absolutePath }

    // 旋转 / 进程死亡恢复：组合进入且尚无槽位时，从落盘路径一次性重建
    LaunchedEffect(Unit) {
        if (photoSlots.isEmpty() && persistedSlotPaths.isNotEmpty()) {
            photoSlots = persistedSlotPaths.mapNotNull { restoreSlotFromPath(it) }
        }
    }

    val fetchCurrentLocation: () -> Unit = {
        scope.launch {
            val res = LocationHelper.getCurrentLocation(context)
            if (res != null) {
                if (selectedCityCode.isNullOrBlank() && !res.cityCode.isNullOrBlank()) {
                    selectedCityCode = res.cityCode
                    selectedCityName = res.cityName
                }
                if (locationText.isBlank() && res.locationName.isNotBlank()) {
                    locationText = res.locationName
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchCurrentLocation()
        }
    }

    LaunchedEffect(Unit) {
        // 已授权才静默补全；未授权由用户点「使用当前定位」时再申请，避免一进页面弹权限框
        if (LocationHelper.hasLocationPermission(context)) {
            fetchCurrentLocation()
        }
    }

    // 未归册就离开暗房：删掉已压制的相纸，避免 filesDir/outbox_media 无限堆积
    DisposableEffect(Unit) {
        onDispose {
            if (!entrySubmitted) {
                photoSlots.forEach { slot ->
                    runCatching { slot.savedOutboxFile?.delete() }
                }
            }
        }
    }

    // 临时快门拍照文件引用
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // 1. 现场快门直拍（原片写入 cache/camera 作为一次性中转，压制完成后立即删除）
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        if (success && file != null && uri != null && file.exists() && file.length() > 0) {
            scope.launch {
                isProcessing = true
                try {
                    val processed = ImagePipeline.processImage(context, uri)
                    val savedFile = syncManager.saveCompressedImageToOutboxDir(processed.compressedBytes)
                    val fileUri = "file://${savedFile.absolutePath}"
                    val newSlot = DarkroomPhotoSlot(previewUri = fileUri, processed = processed, savedOutboxFile = savedFile)
                    val updated = (photoSlots + newSlot).take(9)
                    photoSlots = updated
                    activeSlotIndex = updated.lastIndex.coerceAtLeast(0)
                    if (titleText.isBlank()) {
                        titleText = "现场快门 · " + (processed.exif.model ?: "胶片相纸")
                    }
                    if (selectedCityCode.isNullOrBlank()) {
                        val inferredCode = processed.exif.inferredCityCode
                        val inferredCity = processed.exif.inferredCityName
                            ?: inferredCode?.let { CityDictionary.cityNameForCode(it) }
                        if (!inferredCode.isNullOrBlank()) {
                            selectedCityCode = inferredCode
                            selectedCityName = inferredCity
                        } else if (!inferredCity.isNullOrBlank()) {
                            val code = CityDictionary.codeForLocation(inferredCity)
                            selectedCityCode = code
                            selectedCityName = inferredCity
                        }
                    }
                    if (locationText.isBlank()) {
                        val inferredCity = processed.exif.inferredCityName
                            ?: processed.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                        if (!inferredCity.isNullOrBlank()) {
                            locationText = inferredCity
                        }
                    }
                    processed.exif.dateTimeOriginal?.let { raw ->
                        FormatUtils.parseToLocalDateTimeOrNull(raw)?.let { darkroomOccurredAt = it }
                    }
                    ArchiveToastManager.show("胶片显影完成，已装入暗房")
                } catch (e: Exception) {
                    ArchiveToastManager.show("快门相纸显影失败: ${e.message}", isError = true)
                } finally {
                    isProcessing = false
                    runCatching { file.delete() }
                    pendingCaptureFile = null
                    pendingCaptureUri = null
                }
            }
        } else {
            runCatching { file?.delete() }
            pendingCaptureFile = null
            pendingCaptureUri = null
        }
    }

    fun launchCameraCapture() {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val tempFile = File(cameraDir, "shutter_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            pendingCaptureFile = tempFile
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            ArchiveToastManager.show("调起相机失败: ${e.message}", isError = true)
        }
    }

    // 2. 系统相册多图选择器 (最多 9 张)
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                try {
                    val targetUris = uris.take(9 - photoSlots.size)
                    val semaphore = Semaphore(2)
                    val results = withContext(Dispatchers.IO) {
                        targetUris.map { uri ->
                            async {
                                semaphore.withPermit {
                                    // 单张失败不拖垮整批；成功的那几张仍然保留在槽位里
                                    runCatching {
                                        val res = ImagePipeline.processImage(context, uri)
                                        val savedFile = syncManager.saveCompressedImageToOutboxDir(res.compressedBytes)
                                        DarkroomPhotoSlot(
                                            previewUri = "file://${savedFile.absolutePath}",
                                            processed = res,
                                            savedOutboxFile = savedFile
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    val added = results.mapNotNull { it.getOrNull() }
                    val failedCount = results.count { it.isFailure }
                    if (failedCount > 0) {
                        ArchiveToastManager.show("$failedCount 张胶片压制失败，已跳过", isError = true)
                    }
                    if (added.isNotEmpty()) {
                        val firstRes = added.first().processed
                        if (titleText.isBlank()) {
                            titleText = "现场记录 · " + (firstRes.exif.model ?: "胶片相纸")
                        }
                        if (selectedCityCode.isNullOrBlank()) {
                            val inferredCode = firstRes.exif.inferredCityCode
                            val inferredCity = firstRes.exif.inferredCityName
                                ?: inferredCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!inferredCode.isNullOrBlank()) {
                                selectedCityCode = inferredCode
                                selectedCityName = inferredCity
                            } else if (!inferredCity.isNullOrBlank()) {
                                val code = CityDictionary.codeForLocation(inferredCity)
                                selectedCityCode = code
                                selectedCityName = inferredCity
                            }
                        }
                        if (locationText.isBlank()) {
                            val inferredCity = firstRes.exif.inferredCityName
                                ?: firstRes.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!inferredCity.isNullOrBlank()) {
                                locationText = inferredCity
                            }
                        }
                        firstRes.exif.dateTimeOriginal?.let { raw ->
                            FormatUtils.parseToLocalDateTimeOrNull(raw)?.let { darkroomOccurredAt = it }
                        }
                        val merged = (photoSlots + added).take(9)
                        photoSlots = merged
                        activeSlotIndex = merged.lastIndex.coerceAtLeast(0)
                        ArchiveToastManager.show("已装入 ${added.size} 张胶片底片")
                    }
                } catch (e: Exception) {
                    ArchiveToastManager.show("图片压制失败: ${e.message}", isError = true)
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val activeSlot = photoSlots.getOrNull(activeSlotIndex) ?: photoSlots.firstOrNull()

    NoiseBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 顶栏标头
            Text(
                text = "随行暗房",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "银盐相纸装裱与现场手记",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 现场快门直拍
            Button(
                onClick = {
                    launchCameraCapture()
                },
                enabled = !isProcessing && photoSlots.size < 9,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentWarm,
                    contentColor = colors.archivePaper
                ),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "现场快门直拍",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "现场快门直拍",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 相纸装裱画框区域
            if (activeSlot != null) {
                val metadata = activeSlot.processed.exif.toFilmMetadataString()
                FilmFrame(
                    imageUrl = activeSlot.previewUri,
                    metadataText = metadata,
                    aspectRatio = if (activeSlot.processed.height > 0) {
                        activeSlot.processed.width.toFloat() / activeSlot.processed.height.toFloat()
                    } else 4f / 3f,
                    onClick = {
                        lightboxPhotoUrl = activeSlot.previewUri
                        lightboxMetadata = metadata
                    }
                )

                // 多张底片缩略图接触印相条 (Contact Sheet Strip)
                if (photoSlots.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        photoSlots.forEachIndexed { idx, slot ->
                            val isSelected = idx == activeSlotIndex
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) colors.accentWarm else colors.borderDivider,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .clickable { activeSlotIndex = idx }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(slot.previewUri)
                                        .crossfade(true)
                                        .memoryCacheKey(slot.previewUri)
                                        .diskCacheKey(slot.previewUri)
                                        .build(),
                                    contentDescription = "相纸 #${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        // 18dp 的点击区在拇指下几乎点不中；放大到 28dp 并保留视觉圆点
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.archivePaper.copy(alpha = 0.85f))
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                            val next = photoSlots.toMutableList().also { it.removeAt(idx) }
                                            photoSlots = next
                                            activeSlotIndex = activeSlotIndex.coerceAtMost((next.size - 1).coerceAtLeast(0))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "移除",
                                        tint = colors.inkPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 空态装裱框占位
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.archiveSubtle)
                        .border(1.dp, colors.borderDivider, RoundedCornerShape(2.dp))
                        .clickable {
                            multiPhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = "选择照片",
                            tint = colors.accentWarm,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isProcessing) "正在暗房显影压制..." else "轻触装入胶片相纸 (支持最多 9 张)",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "可直拍快门或从相册选取 RAW/HEIC/JPEG 照片",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 手账随笔内容卡片
            PaperCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "现场手记",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (photoSlots.isNotEmpty()) {
                            Text(
                                text = "已装入 ${photoSlots.size} 张相纸",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("life" to "日常", "travel" to "游历").forEach { (k, label) ->
                            PaperFilterPill(
                                selected = selectedType == k,
                                onClick = { selectedType = k },
                                label = label
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    PaperTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        placeholder = "标题…",
                        isUnderline = true,
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PaperTextEditor(
                        value = contentText,
                        onValueChange = { contentText = it },
                        placeholder = "随笔手记…",
                        minHeight = 96.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 发生时刻（拆分日期与时刻独立槽位）
                    Spacer(modifier = Modifier.height(10.dp))
                    PaperDateTimeSplitRow(
                        selectedDateTime = darkroomOccurredAt,
                        onDateTimeSelected = { darkroomOccurredAt = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PaperCityPickerField(
                        selectedCityCode = selectedCityCode,
                        selectedCityName = selectedCityName,
                        onCitySelected = { code, name ->
                            selectedCityCode = code
                            selectedCityName = name
                        },
                        placeholder = "选择城市…"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PaperLocationField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        placeholder = "具体地点 (选填)…"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    fetchCurrentLocation()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "使用当前定位",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 提交归册按钮：0ms 乐观入库与静默后台上传直传 R2
            TerracottaStampButton(
                text = if (isSaving) "正在冲洗装裱..." else "完成记录",
                enabled = !isSaving && !isProcessing && (titleText.isNotBlank() || contentText.isNotBlank() || photoSlots.isNotEmpty()),
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val primaryExif = photoSlots.firstOrNull()?.processed?.exif
                            val cityCode = selectedCityCode
                                ?: CityDictionary.codeForLocation(locationText)
                                ?: primaryExif?.inferredCityCode
                            val cityName = selectedCityName
                                ?: cityCode?.let { CityDictionary.cityNameForCode(it) }
                                ?: primaryExif?.inferredCityName
                            val finalTitle = titleText.ifBlank { "现场随笔" }
                            val rawContent = contentText.ifBlank { "无文字记录" }
                            val cameraMeta = primaryExif?.toFilmMetadataString()
                            val finalContent = if (!cameraMeta.isNullOrBlank()) {
                                if (rawContent == "无文字记录") "> $cameraMeta" else "$rawContent\n\n> $cameraMeta"
                            } else rawContent
                            val entryType = selectedType
                            val occurredIso = FormatUtils.toBeijingIsoString(darkroomOccurredAt)

                            if (photoSlots.isNotEmpty()) {
                                val savedFiles = photoSlots.map { slot ->
                                    val f = slot.savedOutboxFile ?: syncManager.saveCompressedImageToOutboxDir(slot.processed.compressedBytes)
                                    OfflineMediaFileItem(
                                        filePath = f.absolutePath,
                                        mimeType = slot.processed.mimeType,
                                        width = slot.processed.width,
                                        height = slot.processed.height
                                    )
                                }
                                val firstMedia = savedFiles.first()
                                val offlineReq = CreateEntryRequest(
                                    type = entryType,
                                    title = finalTitle,
                                    content = finalContent,
                                    occurredAt = occurredIso,
                                    location = locationText.takeIf { it.isNotBlank() } ?: cityName,
                                    cityCode = cityCode,
                                    cityName = cityName
                                )
                                // 「入队即接管文件所有权」：必须在 await 前置位。
                                // 若放到 createMediaEntryOffline 之后，用户在保存过程中离开暗房，
                                // onDispose 会删掉文件，Room 里留下引用已删文件的记录 → 上传永久失败。
                                entrySubmitted = true
                                syncManager.createMediaEntryOffline(
                                    localImageFilePath = firstMedia.filePath,
                                    mimeType = firstMedia.mimeType,
                                    width = firstMedia.width,
                                    height = firstMedia.height,
                                    req = offlineReq,
                                    additionalMediaFiles = savedFiles.drop(1)
                                )
                            } else {
                                entrySubmitted = true
                                syncManager.createEntryOptimistic(
                                    type = entryType,
                                    title = finalTitle,
                                    content = finalContent,
                                    occurredAt = occurredIso,
                                    location = locationText.takeIf { it.isNotBlank() },
                                    cityCode = cityCode,
                                    cityName = cityName
                                )
                            }
                            ArchiveToastManager.show("已装裱归入生活时间流")
                            onEntrySaved()
                        } catch (e: Exception) {
                            // 归档失败：收回接管标记，让 onDispose 正常清理相纸
                            entrySubmitted = false
                            ArchiveToastManager.show("归档异常: ${e.message}", isError = true)
                        } finally {
                            isSaving = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 全屏底片灯箱查看器
    lightboxPhotoUrl?.let { url ->
        ArchiveLightboxViewer(
            imageUrl = url,
            metadataText = lightboxMetadata,
            onDismiss = {
                lightboxPhotoUrl = null
                lightboxMetadata = null
            }
        )
    }
}
}
