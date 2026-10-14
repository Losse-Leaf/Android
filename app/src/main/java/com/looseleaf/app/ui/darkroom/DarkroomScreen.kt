package com.looseleaf.app.ui.darkroom

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.looseleaf.app.darkroom.ExtractedExif
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.FilmFrame
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.ui.components.TerracottaStampButton
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

/**
 * 随行暗房与现场拍照记录界面 (DarkroomScreen) - docs/mobile.md §3.2
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

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var processedResult by remember { mutableStateOf<ProcessedImageResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf("") }

    // 系统相册选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            scope.launch {
                isProcessing = true
                try {
                    val res = ImagePipeline.processImage(context, uri)
                    processedResult = res
                    if (titleText.isBlank()) {
                        titleText = "现场记录 · " + (res.exif.model ?: "胶片相纸")
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "图片压制失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
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
                text = "原图端侧无损压制至 4K · 自动提取机型镜头 EXIF",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 相纸装裱画框区域
            if (selectedImageUri != null) {
                val metadata = processedResult?.exif?.toFilmMetadataString() ?: "正在解析银盐元数据..."
                FilmFrame(
                    imageUrl = selectedImageUri.toString(),
                    metadataText = metadata,
                    aspectRatio = if (processedResult != null && processedResult!!.height > 0) {
                        processedResult!!.width.toFloat() / processedResult!!.height.toFloat()
                    } else 4f / 3f,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                // 空态装裱框占位
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.archiveSubtle)
                        .border(1.dp, colors.borderDivider, RoundedCornerShape(2.dp))
                        .clickable {
                            photoPickerLauncher.launch(
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
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "轻触装入胶片相纸",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "从系统相册选取 RAW/HEIC/JPEG 照片",
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
                    Text(
                        text = "标头随笔",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        placeholder = { Text("记录此刻命名...", color = colors.inkMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentWarm,
                            unfocusedBorderColor = colors.borderDivider,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "当下心绪或胶片手记",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        placeholder = { Text("记录现场气味、光影、或片刻偶遇...", color = colors.inkMuted) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentWarm,
                            unfocusedBorderColor = colors.borderDivider,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "所处地点 (可留空，自动匹配城市代码)",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        placeholder = { Text("例如: 大理古城、上海武康路", color = colors.inkMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentWarm,
                            unfocusedBorderColor = colors.borderDivider,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 提交归册按钮
            TerracottaStampButton(
                text = if (isSaving) "正在冲洗装裱..." else "盖印归册 (ARCHIVE)",
                enabled = !isSaving && !isProcessing && (titleText.isNotBlank() || contentText.isNotBlank() || selectedImageUri != null),
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            var uploadedAttachmentId: Long? = null
                            val processed = processedResult
                            if (processed != null) {
                                val attachment = ImagePipeline.uploadImage(apiClient, processed)
                                uploadedAttachmentId = attachment.id
                            }

                            val cityCode = CityDictionary.codeForLocation(locationText)
                            val finalTitle = titleText.ifBlank { "现场随笔" }
                            val finalContent = contentText.ifBlank { "无文字记录" }

                            syncManager.createEntryOptimistic(
                                type = if (locationText.isNotBlank()) "travel" else "life",
                                title = finalTitle,
                                content = finalContent,
                                location = locationText.takeIf { it.isNotBlank() },
                                cityCode = cityCode,
                                attachmentIds = uploadedAttachmentId?.let { listOf(it) },
                                camera = processed?.exif?.toFilmMetadataString()
                            )

                            Toast.makeText(context, "已装裱归入生活时间流", Toast.LENGTH_SHORT).show()
                            onEntrySaved()
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
