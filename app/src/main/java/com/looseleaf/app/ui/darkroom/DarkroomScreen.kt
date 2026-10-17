package com.looseleaf.app.ui.darkroom

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private data class DarkroomPhotoSlot(
    val previewUri: String,
    val processed: ProcessedImageResult,
    val savedOutboxFile: File? = null
)

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
    val haptics = remember { HapticsEngine.getInstance(context) }

    var photoSlots by remember { mutableStateOf<List<DarkroomPhotoSlot>>(emptyList()) }
    var activeSlotIndex by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var lightboxPhotoUrl by remember { mutableStateOf<String?>(null) }
    var lightboxMetadata by remember { mutableStateOf<String?>(null) }

    var titleText by rememberSaveable { mutableStateOf("") }
    var contentText by rememberSaveable { mutableStateOf("") }
    var locationText by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf("life") }

    // 临时快门拍照文件引用
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // 1. 现场快门全像素直拍启动器 (TakePicture + FileProvider 4K 全高清原片)
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
                    if (locationText.isBlank()) {
                        val inferredCity = processed.exif.inferredCityName
                            ?: processed.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                        if (!inferredCity.isNullOrBlank()) {
                            locationText = inferredCity
                        }
                    }
                    haptics.shutterRelease()
                    AudioEngine.playShutter()
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
                    val added = withContext(Dispatchers.IO) {
                        targetUris.map { uri ->
                            async {
                                val res = ImagePipeline.processImage(context, uri)
                                val savedFile = syncManager.saveCompressedImageToOutboxDir(res.compressedBytes)
                                val fileUri = "file://${savedFile.absolutePath}"
                                DarkroomPhotoSlot(previewUri = fileUri, processed = res, savedOutboxFile = savedFile)
                            }
                        }.awaitAll()
                    }
                    if (added.isNotEmpty()) {
                        val firstRes = added.first().processed
                        if (titleText.isBlank()) {
                            titleText = "现场记录 · " + (firstRes.exif.model ?: "胶片相纸")
                        }
                        if (locationText.isBlank()) {
                            val inferredCity = firstRes.exif.inferredCityName
                                ?: firstRes.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!inferredCity.isNullOrBlank()) {
                                locationText = inferredCity
                            }
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
                    haptics.tapKey()
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
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(colors.archivePaper.copy(alpha = 0.85f))
                                        .clickable {
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
                                        modifier = Modifier.size(12.dp)
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
                    PaperTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        placeholder = "记录此刻命名...",
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
                        placeholder = "记录现场气味、光影、或片刻偶遇...",
                        minHeight = 96.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PaperTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        placeholder = "所处地点 (如: 大理古城、上海武康路)",
                        isUnderline = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(16.dp))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            val cityCode = CityDictionary.codeForLocation(locationText)
                                ?: primaryExif?.inferredCityCode
                            val cityName = cityCode?.let { CityDictionary.cityNameForCode(it) }
                                ?: primaryExif?.inferredCityName
                            val rawContent = contentText.ifBlank { "无文字记录" }
                            val cameraMeta = primaryExif?.toFilmMetadataString()
                            val finalContent = if (!cameraMeta.isNullOrBlank()) {
                                if (rawContent == "无文字记录") "> $cameraMeta" else "$rawContent\n\n> $cameraMeta"
                            } else rawContent
                            val entryType = selectedType
                            val occurredIso = FormatUtils.beijingNowIsoString()

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
                                syncManager.createMediaEntryOffline(
                                    localImageFilePath = firstMedia.filePath,
                                    mimeType = firstMedia.mimeType,
                                    width = firstMedia.width,
                                    height = firstMedia.height,
                                    req = offlineReq,
                                    additionalMediaFiles = savedFiles.drop(1)
                                )
                            } else {
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
                            haptics.shutterRelease()
                            AudioEngine.playShutter()
                            ArchiveToastManager.show("已装裱归入生活时间流")
                            onEntrySaved()
                        } catch (e: Exception) {
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
