package com.looseleaf.app.ui.darkroom

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.looseleaf.app.ui.components.FilmFrame
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperBadge
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.ui.components.TerracottaStampButton
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.io.IOException

private data class DarkroomPhotoSlot(
    val previewUri: String,
    val processed: ProcessedImageResult
)

/**
 * 随行暗房与现场拍照记录界面 (DarkroomScreen) - docs/mobile.md §3.2
 * 支持：现场快门直拍、相册多图装入 (最多 9 张)、EXIF 经纬度自动推断城市、弱网/飞行模式相纸发件箱自动兜底
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

    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("life") }

    // 1. 现场快门直拍启动器 (TakePicturePreview)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                isProcessing = true
                try {
                    val processed = ImagePipeline.processCapturedBitmap(bitmap)
                    val savedFile = syncManager.saveCompressedImageToOutboxDir(processed.compressedBytes)
                    val fileUri = "file://${savedFile.absolutePath}"
                    val newSlot = DarkroomPhotoSlot(previewUri = fileUri, processed = processed)
                    val updated = (photoSlots + newSlot).take(9)
                    photoSlots = updated
                    activeSlotIndex = updated.lastIndex.coerceAtLeast(0)
                    if (titleText.isBlank()) {
                        titleText = "现场快门 · " + (processed.exif.model ?: "胶片相纸")
                    }
                    haptics.shutterRelease()
                    AudioEngine.playShutter()
                } catch (e: Exception) {
                    Toast.makeText(context, "快门相纸显影失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
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
                    val added = mutableListOf<DarkroomPhotoSlot>()
                    for (uri in uris.take(9 - photoSlots.size)) {
                        val res = ImagePipeline.processImage(context, uri)
                        added.add(DarkroomPhotoSlot(previewUri = uri.toString(), processed = res))

                        if (titleText.isBlank()) {
                            titleText = "现场记录 · " + (res.exif.model ?: "胶片相纸")
                        }
                        if (locationText.isBlank()) {
                            val inferredCity = res.exif.inferredCityName
                                ?: res.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!inferredCity.isNullOrBlank()) {
                                locationText = inferredCity
                            }
                        }
                    }
                    if (added.isNotEmpty()) {
                        val merged = (photoSlots + added).take(9)
                        photoSlots = merged
                        activeSlotIndex = merged.lastIndex.coerceAtLeast(0)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "图片压制失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                text = "随行暗房 · 现场相纸",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "原图端侧无损压制至 4K · 自动提取 EXIF 与 GPS 城市坐标",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 双联装片按钮：现场快门直拍 + 相册装入相纸
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tapKey()
                        cameraLauncher.launch(null)
                    },
                    enabled = !isProcessing && photoSlots.size < 9,
                    shape = RoundedCornerShape(2.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(colors.accentWarm)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.archiveSubtle,
                        contentColor = colors.accentWarm
                    ),
                    modifier = Modifier.weight(1f)
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

                OutlinedButton(
                    onClick = {
                        haptics.tapKey()
                        multiPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isProcessing && photoSlots.size < 9,
                    shape = RoundedCornerShape(2.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(colors.borderDivider)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.archiveSubtle,
                        contentColor = colors.inkPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "相册装入相纸",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "相册装入相纸",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp
                    )
                }
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
                        multiPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
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
                                    model = slot.previewUri,
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
                            text = "手账手记与分类",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (photoSlots.isNotEmpty()) {
                            PaperBadge(text = "已装入 ${photoSlots.size} 张相纸")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("life" to "日常随笔", "travel" to "山川游历", "project" to "造物日志", "wish" to "心愿印记").forEach { (k, label) ->
                            FilterChip(
                                selected = selectedType == k,
                                onClick = {
                                    haptics.tapKey()
                                    selectedType = k
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    PaperTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        placeholder = "记录此刻命名...",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PaperTextEditor(
                        value = contentText,
                        onValueChange = { contentText = it },
                        placeholder = "记录现场气味、光影、或片刻偶遇...",
                        minHeight = 88.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PaperTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        placeholder = "所处地点 (如: 大理古城、上海武康路，自动提取 EXIF 城市坐标)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 提交归册按钮
            TerracottaStampButton(
                text = if (isSaving) "正在冲洗装裱..." else "盖印归册 (ARCHIVE)",
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
                            val finalTitle = titleText.ifBlank { "现场随笔" }
                            val finalContent = contentText.ifBlank { "无文字记录" }
                            val entryType = selectedType
                            val cameraMeta = primaryExif?.toFilmMetadataString()
                            val occurredIso = FormatUtils.beijingNowIsoString()

                            if (photoSlots.isNotEmpty()) {
                                try {
                                    val uploadedList = mutableListOf<Attachment>()
                                    for (slot in photoSlots) {
                                        val att = ImagePipeline.uploadImage(apiClient, slot.processed)
                                        uploadedList.add(att)
                                    }
                                    syncManager.createEntryOptimistic(
                                        type = entryType,
                                        title = finalTitle,
                                        content = finalContent,
                                        occurredAt = occurredIso,
                                        location = locationText.takeIf { it.isNotBlank() } ?: cityName,
                                        cityCode = cityCode,
                                        cityName = cityName,
                                        attachmentIds = uploadedList.map { it.id },
                                        attachments = uploadedList,
                                        camera = cameraMeta
                                    )
                                    haptics.shutterRelease()
                                    AudioEngine.playShutter()
                                    Toast.makeText(context, "已装裱归入生活时间流", Toast.LENGTH_SHORT).show()
                                    onEntrySaved()
                                } catch (uploadErr: Exception) {
                                    val isNetworkErr = uploadErr is IOException ||
                                        (uploadErr is APIException && uploadErr.code == -1) ||
                                        uploadErr.cause is IOException
                                    if (isNetworkErr) {
                                        // 自动离线降级：暂存相纸至本地 outbox_media 并创建离线图文记录
                                        val savedFiles = photoSlots.map { slot ->
                                            val f = syncManager.saveCompressedImageToOutboxDir(slot.processed.compressedBytes)
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
                                            camera = cameraMeta,
                                            additionalMediaFiles = savedFiles.drop(1)
                                        )
                                        haptics.shutterRelease()
                                        AudioEngine.playShutter()
                                        Toast.makeText(context, "当前离线，相纸已暂存至本地发件箱", Toast.LENGTH_LONG).show()
                                        onEntrySaved()
                                    } else {
                                        throw uploadErr
                                    }
                                }
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
                                haptics.shutterRelease()
                                AudioEngine.playShutter()
                                Toast.makeText(context, "已装裱归入生活时间流", Toast.LENGTH_SHORT).show()
                                onEntrySaved()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "归档异常: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSaving = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}
