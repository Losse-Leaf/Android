package com.looseleaf.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var showCategoryManagerDialog by remember { mutableStateOf(false) }

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
                text = "主理人展台与设备中枢",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "生活自留地 · 主理人档案与随身设备中枢",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.sp,
                color = colors.inkMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 主理人肖像与自白卡片
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showEditProfileDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("编辑档案", fontSize = 11.sp, color = colors.accentWarm)
                        }
                    }

                    val avatarUrl = apiClient.resolveMediaUrl(profile.avatarUrl)
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "主理人肖像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.borderDivider, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = profile.name.ifBlank { siteConfig.siteName },
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 19.sp,
                        color = colors.inkPrimary
                    )

                    if (profile.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.title,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }

                    if (profile.motto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "“ ${profile.motto} ”",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp,
                            color = colors.accentWarm
                        )
                    }

                    if (profile.currentCity.isNotBlank() || profile.location.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PinDrop, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = listOf(profile.currentCity, profile.location).filter { it.isNotBlank() }.joinToString(" · "),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    if (profile.manifesto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile.manifesto,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.inkMuted
                        )
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
                                Text(
                                    text = gear.category,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
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
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileInfoRow(label = "设备编号", value = credentialStore.deviceId ?: "未绑定")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "设备名称", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = credentialStore.deviceName, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = { showRenameDeviceDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("重命名", fontSize = 10.sp, color = colors.accentWarm)
                            }
                        }
                    }
                    ProfileInfoRow(label = "中枢 Hub", value = apiClient.baseUrl)
                    ProfileInfoRow(
                        label = "授权状态",
                        value = if (credentialStore.isPaired()) "已信任 (Active)" else "未配对"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 分类标签字典管理
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("分类标签字典", fontFamily = ArchiveFontFamilies.EditorialSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.inkPrimary)
                        Text("管理收支、装备与造物标签", fontSize = 11.sp, color = colors.inkMuted)
                    }
                    TextButton(onClick = { showCategoryManagerDialog = true }) {
                        Text("管理分类", fontSize = 12.sp, color = colors.accentWarm)
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
                Text("解除此设备绑定 (UNPAIR)", fontFamily = ArchiveFontFamilies.EditorialSerif)
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
                EditProfileDialog(
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

            if (showCategoryManagerDialog) {
                CategoryManagerDialog(
                    apiClient = apiClient,
                    onDismiss = { showCategoryManagerDialog = false }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

@Composable
private fun EditProfileDialog(
    profile: CuratorProfile,
    onDismiss: () -> Unit,
    onSave: (UpdateProfileRequest) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var name by remember(profile) { mutableStateOf(profile.name) }
    var title by remember(profile) { mutableStateOf(profile.title) }
    var currentCity by remember(profile) { mutableStateOf(profile.currentCity) }
    var motto by remember(profile) { mutableStateOf(profile.motto) }
    var manifesto by remember(profile) { mutableStateOf(profile.manifesto) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑主理人档案", fontFamily = ArchiveFontFamilies.EditorialSerif) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主理人称谓") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("头衔/身份标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentCity,
                    onValueChange = { currentCity = it },
                    label = { Text("常居城市/坐标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = motto,
                    onValueChange = { motto = it },
                    label = { Text("生活格言") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manifesto,
                    onValueChange = { manifesto = it },
                    label = { Text("主理人自白手记") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
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
                }
            ) {
                Text("保存档案", color = colors.accentWarm)
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
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("设备名称") },
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
private fun CategoryManagerDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var selectedScope by remember { mutableStateOf("expense") }
    var categories by remember { mutableStateOf<List<CategoryOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newCategoryInput by remember { mutableStateOf("") }

    suspend fun loadCats() {
        isLoading = true
        runCatching {
            categories = apiClient.fetchCategories(selectedScope)
        }
        isLoading = false
    }

    LaunchedEffect(selectedScope) {
        loadCats()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("分类标签字典管理", fontFamily = ArchiveFontFamilies.EditorialSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = colors.inkPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scope 切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("expense" to "支出", "income" to "收入", "gear" to "装备", "project" to "造物").forEach { (k, label) ->
                        FilterChip(
                            selected = selectedScope == k,
                            onClick = { selectedScope = k },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 添加新分类行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        placeholder = { Text("输入新分类标签…", fontSize = 12.sp, color = colors.inkMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = newCategoryInput.isNotBlank(),
                        onClick = {
                            val name = newCategoryInput.trim()
                            scope.launch {
                                runCatching {
                                    apiClient.addCategoryOption(selectedScope, name)
                                    newCategoryInput = ""
                                    loadCats()
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                    ) {
                        Text("+ 添加", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 分类芯片列表
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.name, fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp, color = colors.inkPrimary)
                                    if (cat.isBuiltin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        PaperBadge(text = "系统预置")
                                    }
                                }
                                if (!cat.isBuiltin) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    apiClient.deleteCategoryOption(cat.id)
                                                    loadCats()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "删除分类", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                                    }
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
