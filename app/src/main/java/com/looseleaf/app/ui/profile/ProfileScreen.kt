package com.looseleaf.app.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.SiteConfig
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 主理人展台与设备中枢设置 (ProfileScreen)
 * 严格遵循 docs/backend.md §一.5 全数据后端驱动与严禁前端写死原则 (Dynamic Data Sovereignty)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
    onDarkroomModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    var profile by remember { mutableStateOf(CuratorProfile()) }
    var siteConfig by remember { mutableStateOf(SiteConfig()) }
    var isDarkroomMode by remember { mutableStateOf(credentialStore.isDarkroomMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRenameDeviceDialog by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingAvatar = true
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    val bytes = input?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.isNotEmpty()) {
                        val att = apiClient.uploadUnifiedFallback(
                            fileBytes = bytes,
                            filename = "avatar_${System.currentTimeMillis()}.jpg",
                            mimeType = "image/jpeg",
                            folder = "avatar"
                        )
                        val updated = apiClient.updateCuratorProfile(
                            UpdateProfileRequest(avatarUrl = att.objectKey)
                        )
                        profile = updated
                        Toast.makeText(context, "肖像照片已更新", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "头像上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            profile = apiClient.fetchCuratorProfile()
            siteConfig = apiClient.fetchSiteConfig()
        }
    }

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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 顶栏
            Text(
                text = "主理人展台",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "生活自留地 · 独立档案",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.sp,
                color = colors.inkMuted
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 主理人名片拓片卡片
            PaperCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.archivePaper,
                borderColor = colors.accentWarm.copy(alpha = 0.25f),
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 头像区域（带可点击上传提示与相机微图标）
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .clickable {
                                if (!isUploadingAvatar) {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                    ) {
                        val avatarUrl = apiClient.resolveMediaUrl(profile.avatarUrl)
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "主理人肖像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, colors.accentWarm.copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentWarm.copy(alpha = 0.1f))
                                    .border(1.5.dp, colors.accentWarm.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name.take(1).ifBlank { "散" },
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    color = colors.accentWarm
                                )
                            }
                        }

                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = colors.archivePaper,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // 相机微胶囊
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.accentWarm)
                                .border(1.5.dp, colors.archivePaper, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "更换头像",
                                tint = colors.archivePaper,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 称谓大标题
                    Text(
                        text = profile.name.ifBlank { siteConfig.siteName.ifBlank { "散页主理人" } },
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )

                    // 身份/头衔
                    val displayTitle = profile.title.ifBlank { "生活记录者 · 独立思想家" }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayTitle,
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )

                    // 城市坐标
                    val displayCity = listOfNotNull(profile.currentCity.takeIf { it.isNotBlank() }, profile.location.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (displayCity.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayCity,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 生活格言
                    val displayMotto = if (profile.motto.isNotBlank()) profile.motto else "在散落的日常里，拾取有光芒的瞬间"
                    Text(
                        text = "“ $displayMotto ”",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        color = colors.accentWarm
                    )

                    // 自白长文
                    if (profile.manifesto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile.manifesto,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            color = colors.inkMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 底部双联操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isUploadingAvatar) {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = SolidColor(colors.borderDivider)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkSecondary),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("更换肖像", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }

                        Button(
                            onClick = { showEditProfileDialog = true },
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentWarm,
                                contentColor = colors.archivePaper
                            ),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("编辑档案", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    }
                }
            }

            // EDC 常用随行装备展台
            if (profile.edcGear.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "随行 EDC 装备 (${profile.edcGear.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        for (gear in profile.edcGear) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = gear.name,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    if (gear.spec.isNotBlank()) {
                                        Text(
                                            text = gear.spec,
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 11.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 本机入网受信任凭据卡片
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "受信任设备凭据",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "设备别名", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = credentialStore.deviceName, fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp, color = colors.inkPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = { showRenameDeviceDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("重命名", fontSize = 11.sp, color = colors.accentWarm)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 移动端个性化开关
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "随行偏好与隐私保护",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("胶片暗房模式 (Darkroom)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 14.sp, color = colors.inkPrimary)
                            Text("碳黑相纸底色与暗房安全灯赤红", fontSize = 11.sp, color = colors.inkMuted)
                        }
                        Switch(
                            checked = isDarkroomMode,
                            onCheckedChange = {
                                haptics.tapKey()
                                isDarkroomMode = it
                                credentialStore.isDarkroomMode = it
                                onDarkroomModeChanged(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentWarm, checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("生物识别保护 (Biometrics)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 14.sp, color = colors.inkPrimary)
                            Text("解锁账本与敏感收支柱状图", fontSize = 11.sp, color = colors.inkMuted)
                        }
                        Switch(
                            checked = isBiometricsEnabled,
                            onCheckedChange = {
                                haptics.tapKey()
                                isBiometricsEnabled = it
                                credentialStore.isBiometricsEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentWarm, checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 版本信息与检测更新卡片
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "版本与更新",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileInfoRow(label = "当前版本", value = "v${UpdateChecker.currentVersionName}")
                    ProfileInfoRow(label = "分发渠道", value = "GitHub Releases")

                    Spacer(modifier = Modifier.height(10.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isCheckingUpdate) "正在连接 GitHub 检索更新..." else "检测云端最新发布",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    haptics.tapKey()
                                    when (val result = UpdateChecker.checkForUpdates()) {
                                        is UpdateCheckResult.HasUpdate -> {
                                            availableUpdate = result.release
                                        }
                                        is UpdateCheckResult.AlreadyLatest -> {
                                            Toast.makeText(
                                                context,
                                                "当前已是最新版本 (v${UpdateChecker.currentVersionName})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is UpdateCheckResult.Error -> {
                                            Toast.makeText(
                                                context,
                                                "检测更新失败: ${result.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    isCheckingUpdate = false
                                }
                            },
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentWarm,
                                contentColor = colors.archivePaper
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isCheckingUpdate) "检测中…" else "检测新版本",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 吊销此设备按钮
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentWarm),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(colors.accentWarm)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("解除此设备绑定", fontFamily = ArchiveFontFamilies.EditorialSerif)
            }

            if (availableUpdate != null) {
                val update = availableUpdate!!
                var downloadProgress by remember { mutableStateOf(0f) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

                AlertDialog(
                    onDismissRequest = {
                        if (!isDownloading) availableUpdate = null
                    },
                    title = {
                        Text(
                            text = "🎉 发现新版本 ${update.tagName}",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "发布时间: ${update.publishedAt.take(10)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = update.body.ifBlank { "包含功能迭代与性能稳定性增强。" },
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = colors.inkSecondary
                            )
                            if (isDownloading) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = colors.accentWarm,
                                    trackColor = colors.accentWarm.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "应用内高速下载中: ${(downloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    color = colors.accentWarm
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (downloadedFile != null) {
                            Button(
                                onClick = {
                                    UpdateChecker.installApk(context, downloadedFile!!)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentWarm,
                                    contentColor = colors.archivePaper
                                ),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text("立即安装", fontFamily = ArchiveFontFamilies.EditorialSerif)
                            }
                        } else {
                            Button(
                                enabled = !isDownloading,
                                onClick = {
                                    scope.launch {
                                        isDownloading = true
                                        val res = UpdateChecker.downloadApkStream(
                                            context = context,
                                            apkUrl = update.apkUrl,
                                            versionName = update.versionName
                                        ) { p, _, _ ->
                                            downloadProgress = p
                                        }
                                        isDownloading = false
                                        res.onSuccess { f ->
                                            downloadedFile = f
                                            UpdateChecker.installApk(context, f)
                                        }.onFailure { err ->
                                            Toast.makeText(context, "下载失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentWarm,
                                    contentColor = colors.archivePaper
                                ),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text(if (isDownloading) "正在下载 ${(downloadProgress * 100).toInt()}%" else "应用内直接更新", fontFamily = ArchiveFontFamilies.EditorialSerif)
                            }
                        }
                    },
                    dismissButton = {
                        if (!isDownloading) {
                            TextButton(onClick = { availableUpdate = null }) {
                                Text("稍后再说", color = colors.inkSecondary)
                            }
                        }
                    }
                )
            }

            if (showUnpairDialog) {
                AlertDialog(
                    onDismissRequest = { showUnpairDialog = false },
                    title = { Text("解除受信任设备？", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                    text = { Text("解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。", fontFamily = ArchiveFontFamilies.BodySans) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics.tearDelete()
                                val currentDeviceId = credentialStore.deviceId
                                showUnpairDialog = false
                                scope.launch {
                                    if (!currentDeviceId.isNullOrBlank()) {
                                        runCatching { apiClient.revokeDevice(currentDeviceId) }
                                    }
                                    runCatching { syncManager.clearAllLocalData() }
                                    credentialStore.clearCredentials()
                                    onUnpair()
                                }
                            }
                        ) {
                            Text("确认解除", color = colors.accentWarm)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUnpairDialog = false }) {
                            Text("取消", color = colors.inkSecondary)
                        }
                    }
                )
            }

            if (showEditProfileDialog) {
                EditProfileSheet(
                    profile = profile,
                    onDismiss = { showEditProfileDialog = false },
                    onSave = { req ->
                        scope.launch {
                            runCatching {
                                profile = apiClient.updateCuratorProfile(req)
                                Toast.makeText(context, "主理人档案已保存", Toast.LENGTH_SHORT).show()
                            }
                            showEditProfileDialog = false
                        }
                    }
                )
            }

            if (showRenameDeviceDialog) {
                RenameDeviceDialog(
                    currentName = credentialStore.deviceName,
                    onDismiss = { showRenameDeviceDialog = false },
                    onConfirm = { newName ->
                        val devId = credentialStore.deviceId
                        if (!devId.isNullOrBlank()) {
                            scope.launch {
                                runCatching {
                                    apiClient.updateDeviceName(devId, newName)
                                    credentialStore.deviceName = newName
                                    Toast.makeText(context, "设备名称已更新", Toast.LENGTH_SHORT).show()
                                }
                                showRenameDeviceDialog = false
                            }
                        } else {
                            credentialStore.deviceName = newName
                            showRenameDeviceDialog = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: CuratorProfile,
    onDismiss: () -> Unit,
    onSave: (UpdateProfileRequest) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var name by remember(profile) { mutableStateOf(profile.name) }
    var title by remember(profile) { mutableStateOf(profile.title ?: "") }
    var currentCity by remember(profile) { mutableStateOf(profile.currentCity ?: "") }
    var motto by remember(profile) { mutableStateOf(profile.motto ?: "") }
    var manifesto by remember(profile) { mutableStateOf(profile.manifesto ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏：取消 / 标题 / 保存
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("取消", color = colors.inkSecondary, fontSize = 14.sp)
                }

                Text(
                    text = "《主理人手卷》",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )

                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            UpdateProfileRequest(
                                name = name.trim(),
                                title = title.trim().takeIf { it.isNotBlank() },
                                currentCity = currentCity.trim().takeIf { it.isNotBlank() },
                                motto = motto.trim().takeIf { it.isNotBlank() },
                                manifesto = manifesto.trim().takeIf { it.isNotBlank() }
                            )
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "保存档案",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (name.isNotBlank()) colors.accentWarm else colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 称谓大标题输入
            Text("称谓", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.sp, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (name.isEmpty()) {
                        Text(
                            text = "输入主理人称谓…",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 20.sp,
                            color = colors.inkMuted.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // 头衔与身份
            Text("头衔与身份", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.sp, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = "如：生活记录者、独立摄影师…",
                            fontSize = 15.sp,
                            color = colors.inkMuted.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // 常居城市
            Text("常居城市", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.sp, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = currentCity,
                onValueChange = { currentCity = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (currentCity.isEmpty()) {
                        Text(
                            text = "如：大理、杭州、上海…",
                            fontSize = 15.sp,
                            color = colors.inkMuted.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // 生活信条与格言
            Text("生活格言", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.sp, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = motto,
                onValueChange = { motto = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (motto.isEmpty()) {
                        Text(
                            text = "写下一句指引你的生活信条…",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            color = colors.inkMuted.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // 自白手记正文（开阔纯净书写画布）
            Text("自白手记", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.sp, color = colors.inkMuted)
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = manifesto,
                onValueChange = { manifesto = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp),
                textStyle = TextStyle(
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 14.5.sp,
                    lineHeight = 24.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.accentWarm),
                decorationBox = { innerTextField ->
                    if (manifesto.isEmpty()) {
                        Text(
                            text = "关于你自己、关于时间、关于生活的自白散篇…",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 14.5.sp,
                            lineHeight = 24.sp,
                            color = colors.inkMuted.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var nameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名此设备", fontFamily = ArchiveFontFamilies.EditorialSerif) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("为当前随行设备设置识别别名 (如: 主理人的随行手机)：", fontSize = 12.sp, color = colors.inkSecondary)
                PaperTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = "设备名称",
                    isUnderline = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameInput.isNotBlank(),
                onClick = { onConfirm(nameInput.trim()) }
            ) {
                Text("确认更名", color = colors.accentWarm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.inkSecondary)
            }
        }
    )
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
        Text(text = value, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkSecondary)
    }
}
