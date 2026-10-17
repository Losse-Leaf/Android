package com.looseleaf.app.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.ProfileRepository
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.GlobalStats
import com.looseleaf.app.model.SiteConfig
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.model.TrustedDevice
import com.looseleaf.app.model.PairTicketResponse
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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

    val cachedProfile = remember { ProfileRepository.getProfile(context) }
    val cachedSiteConfig = remember { ProfileRepository.getSiteConfig(context) }
    val repoProfile by ProfileRepository.profileState.collectAsState()
    val repoSiteConfig by ProfileRepository.siteConfigState.collectAsState()

    var profile by remember(repoProfile) { mutableStateOf(repoProfile ?: cachedProfile) }
    var siteConfig by remember(repoSiteConfig) { mutableStateOf(repoSiteConfig ?: cachedSiteConfig) }
    var localAvatarPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var isDarkroomMode by remember { mutableStateOf(credentialStore.isDarkroomMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var isHapticsEnabled by remember { mutableStateOf(credentialStore.isHapticsEnabled) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRenameDeviceDialog by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var trustedDevices by remember { mutableStateOf<List<TrustedDevice>>(emptyList()) }
    var isLoadingDevices by remember { mutableStateOf(false) }
    var deviceToRevoke by remember { mutableStateOf<TrustedDevice?>(null) }
    var deviceToRename by remember { mutableStateOf<TrustedDevice?>(null) }
    var issuedPairTicket by remember { mutableStateOf<PairTicketResponse?>(null) }
    var isIssuingPairTicket by remember { mutableStateOf(false) }
    var globalStats by remember { mutableStateOf<GlobalStats?>(null) }

    val reloadDevices: () -> Unit = {
        scope.launch {
            isLoadingDevices = true
            runCatching {
                trustedDevices = apiClient.fetchTrustedDevices()
            }
            isLoadingDevices = false
        }
    }

    LaunchedEffect(Unit) {
        reloadDevices()
        runCatching {
            globalStats = apiClient.fetchGlobalStats()
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            localAvatarPreviewUri = uri
            scope.launch {
                isUploadingAvatar = true
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, boundsOptions)
                        }
                        val origW = boundsOptions.outWidth
                        val origH = boundsOptions.outHeight
                        val maxDim = 512
                        var sampleSize = 1
                        while (origW / (sampleSize * 2) >= maxDim || origH / (sampleSize * 2) >= maxDim) {
                            sampleSize *= 2
                        }
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        val decoded = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, decodeOptions)
                        }
                        if (decoded != null) {
                            val (targetW, targetH) = if (decoded.width > decoded.height) {
                                val h = (decoded.height * maxDim.toFloat() / decoded.width).toInt().coerceAtLeast(1)
                                maxDim to h
                            } else {
                                val w = (decoded.width * maxDim.toFloat() / decoded.height).toInt().coerceAtLeast(1)
                                w to maxDim
                            }
                            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                            if (scaled !== decoded) decoded.recycle()
                            val stream = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
                            scaled.recycle()
                            stream.toByteArray()
                        } else null
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        val att = apiClient.uploadUnifiedFallback(
                            fileBytes = bytes,
                            filename = "avatar_${System.currentTimeMillis()}.jpg",
                            mimeType = "image/jpeg",
                            folder = "avatar"
                        )
                        val updated = apiClient.updateCuratorProfile(
                            UpdateProfileRequest(avatarKey = att.objectKey)
                        )
                        ProfileRepository.updateProfile(context, updated)
                        profile = updated
                        localAvatarPreviewUri = null
                        ArchiveToastManager.show("肖像照片已更新")
                    } else {
                        localAvatarPreviewUri = null
                        ArchiveToastManager.show("头像解析失败", isError = true)
                    }
                } catch (e: Exception) {
                    localAvatarPreviewUri = null
                    ArchiveToastManager.show("头像上传失败: ${e.message}", isError = true)
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    var hasInitializedProfile by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasInitializedProfile) {
        if (!hasInitializedProfile) {
            runCatching {
                val remoteProfile = apiClient.fetchCuratorProfile()
                ProfileRepository.updateProfile(context, remoteProfile)
                profile = remoteProfile
                val remoteConfig = apiClient.fetchSiteConfig()
                ProfileRepository.updateSiteConfig(context, remoteConfig)
                siteConfig = remoteConfig
            }
            hasInitializedProfile = true
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
                        val avatarModel: Any? = localAvatarPreviewUri ?: avatarUrl.takeIf { it.isNotBlank() }

                        if (avatarModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarModel)
                                    .crossfade(true)
                                    .memoryCacheKey(avatarUrl.takeIf { localAvatarPreviewUri == null })
                                    .diskCacheKey(avatarUrl.takeIf { localAvatarPreviewUri == null })
                                    .build(),
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

            // 全局档案指标概览卡片 (GlobalStats)
            Spacer(modifier = Modifier.height(16.dp))
            PaperCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.archivePaper,
                borderColor = colors.borderDivider.copy(alpha = 0.5f),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全局档案指标",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        )
                        val ym = listOfNotNull(globalStats?.currentYear?.takeIf { it > 0 }?.let { "${it}年" }, globalStats?.currentMonth?.takeIf { it > 0 }?.let { "${it}月" }).joinToString("")
                        if (ym.isNotBlank()) {
                            Text(
                                text = ym,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("散页手稿", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${globalStats?.totalEntries ?: 0}", fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.inkPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("暗房底片", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${globalStats?.totalPhotos ?: 0}", fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.accentWarm)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("造物项目", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${globalStats?.totalProjects ?: 0}", fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.accentMoss)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("当月支出", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("¥${FormatUtils.centsToYuanString(globalStats?.monthExpense ?: 0)}", fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.inkPrimary)
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

            // 受信任设备组卡片 (Trusted Devices Group Management)
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "受信任设备组 (${trustedDevices.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { reloadDevices() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(if (isLoadingDevices) "刷新中…" else "刷新", fontSize = 11.sp, color = colors.inkSecondary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isIssuingPairTicket = true
                                        runCatching {
                                            issuedPairTicket = apiClient.initPairing()
                                            haptics.snapConfirm()
                                            ArchiveToastManager.show("已生成 5 分钟有效配对工单")
                                        }.onFailure { err ->
                                            ArchiveToastManager.show("生成配对失败: ${err.message}", isError = true)
                                        }
                                        isIssuingPairTicket = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                enabled = !isIssuingPairTicket
                            ) {
                                Text(if (isIssuingPairTicket) "签发中…" else "发起配对", fontSize = 11.sp, color = colors.accentWarm)
                            }
                        }
                    }

                    // 1. 若当前签发了配对工单，展示工单与一键复制卡片
                    issuedPairTicket?.let { ticket ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.archiveSubtle, RoundedCornerShape(4.dp))
                                .border(0.5.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "新设备配对链接（5分钟有效）",
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkPrimary
                                    )
                                    TextButton(
                                        onClick = { issuedPairTicket = null },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("收起", fontSize = 11.sp, color = colors.inkMuted)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ticket.qrPayload,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 10.sp,
                                    color = colors.inkSecondary,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(ticket.qrPayload))
                                        haptics.snapConfirm()
                                        ArchiveToastManager.show("已复制配对链接至剪贴板")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.inkPrimary,
                                        contentColor = colors.archivePaper
                                    ),
                                    shape = RoundedCornerShape(3.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("一键复制配对链接", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. 本机设备卡片
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = credentialStore.deviceName.ifBlank { "Android 随行设备" },
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(colors.accentMoss.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("本机", fontSize = 9.sp, fontFamily = ArchiveFontFamilies.MonoScale, color = colors.accentMoss)
                                }
                            }
                            Text(
                                text = "标识: ${credentialStore.deviceId ?: "未初始化"}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 10.5.sp,
                                color = colors.inkMuted
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showRenameDeviceDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("改名", fontSize = 11.sp, color = colors.inkSecondary)
                            }
                            TextButton(
                                onClick = { showUnpairDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("解除", fontSize = 11.sp, color = colors.accentWarm)
                            }
                        }
                    }

                    // 3. 其他受信任设备列表
                    val otherDevices = trustedDevices.filter { it.deviceId != credentialStore.deviceId }
                    if (otherDevices.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = colors.borderDivider.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                        Text(
                            text = "其他接入端 (${otherDevices.size})",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 10.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        otherDevices.forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dev.name.ifBlank { dev.deviceId },
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp,
                                            color = colors.inkPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(colors.archiveSubtle, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = dev.platform.uppercase(),
                                                fontSize = 9.sp,
                                                fontFamily = ArchiveFontFamilies.MonoScale,
                                                color = colors.inkSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${dev.deviceId} · 最近活跃 ${FormatUtils.formatDate(dev.lastActiveAt)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { deviceToRename = dev },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("改名", fontSize = 11.sp, color = colors.inkSecondary)
                                    }
                                    TextButton(
                                        onClick = { deviceToRevoke = dev },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("踢除", fontSize = 11.sp, color = colors.accentWarm)
                                    }
                                }
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

                    Spacer(modifier = Modifier.height(8.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("物理触感反馈 (Tactile Haptics)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 14.sp, color = colors.inkPrimary)
                            Text("打字机键帽触击、印章盖下沉稳震动与快门反冲", fontSize = 11.sp, color = colors.inkMuted)
                        }
                        Switch(
                            checked = isHapticsEnabled,
                            onCheckedChange = {
                                isHapticsEnabled = it
                                credentialStore.isHapticsEnabled = it
                                if (it) haptics.tapKey()
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
                                            ArchiveToastManager.show("当前已是最新版本 (v${UpdateChecker.currentVersionName})")
                                        }
                                        is UpdateCheckResult.Error -> {
                                            ArchiveToastManager.show("检测更新失败: ${result.message}", isError = true)
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

                ArchiveReleaseDialog(
                    release = update,
                    isDownloading = isDownloading,
                    downloadProgress = if (isDownloading) downloadProgress else null,
                    onDismiss = {
                        if (!isDownloading) availableUpdate = null
                    },
                    onDownloadAndInstall = {
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
                                UpdateChecker.installApk(context, f)
                            }.onFailure { err ->
                                ArchiveToastManager.show("下载失败: ${err.message}", isError = true)
                            }
                        }
                    }
                )
            }

            if (showUnpairDialog) {
                ArchiveConfirmDialog(
                    title = "解除受信任设备？",
                    content = "解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。",
                    confirmText = "确认解除",
                    isDestructive = true,
                    onConfirm = {
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
                    },
                    onDismiss = { showUnpairDialog = false }
                )
            }

            if (showEditProfileDialog) {
                EditProfileSheet(
                    profile = profile,
                    onDismiss = { showEditProfileDialog = false },
                    onSave = { req ->
                        scope.launch {
                            runCatching {
                                val updated = apiClient.updateCuratorProfile(req)
                                ProfileRepository.updateProfile(context, updated)
                                profile = updated
                                ArchiveToastManager.show("主理人档案已保存")
                            }.onFailure { err ->
                                ArchiveToastManager.show("保存档案失败: ${err.message}", isError = true)
                            }
                            showEditProfileDialog = false
                        }
                    }
                )
            }

            if (showRenameDeviceDialog) {
                ArchivePromptDialog(
                    title = "重命名此设备",
                    initialValue = credentialStore.deviceName,
                    placeholder = "设备名称",
                    label = "为当前随行设备设置识别别名：",
                    confirmText = "确认更名",
                    onDismiss = { showRenameDeviceDialog = false },
                    onConfirm = { newName ->
                        val devId = credentialStore.deviceId
                        if (!devId.isNullOrBlank()) {
                            scope.launch {
                                runCatching {
                                    apiClient.updateDeviceName(devId, newName)
                                    credentialStore.deviceName = newName
                                    ArchiveToastManager.show("设备名称已更新")
                                    reloadDevices()
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

            deviceToRevoke?.let { dev ->
                ArchiveConfirmDialog(
                    title = "吊销该接入设备？",
                    content = "吊销后设备「${dev.name.ifBlank { dev.deviceId }}」将立即失去中枢访问凭据，无法再读取或提交任何档案。此操作不可逆。",
                    confirmText = "确认吊销",
                    isDestructive = true,
                    onConfirm = {
                        haptics.tearDelete()
                        val targetId = dev.deviceId
                        deviceToRevoke = null
                        scope.launch {
                            runCatching {
                                apiClient.revokeDevice(targetId)
                                ArchiveToastManager.show("已成功吊销设备「${dev.name}」")
                                reloadDevices()
                            }.onFailure { err ->
                                ArchiveToastManager.show("吊销失败: ${err.message}", isError = true)
                            }
                        }
                    },
                    onDismiss = { deviceToRevoke = null }
                )
            }

            deviceToRename?.let { dev ->
                ArchivePromptDialog(
                    title = "重命名受信任设备",
                    initialValue = dev.name,
                    placeholder = "新设备名称",
                    label = "修改设备「${dev.deviceId}」的识别别名：",
                    confirmText = "确认更名",
                    onDismiss = { deviceToRename = null },
                    onConfirm = { newName ->
                        val targetId = dev.deviceId
                        deviceToRename = null
                        scope.launch {
                            runCatching {
                                apiClient.updateDeviceName(targetId, newName)
                                ArchiveToastManager.show("设备名称已更新")
                                reloadDevices()
                            }.onFailure { err ->
                                ArchiveToastManager.show("更名失败: ${err.message}", isError = true)
                            }
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
    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "《主理人手卷》",
        actionButtonText = "保存档案",
        isActionEnabled = name.isNotBlank(),
        onActionClick = {
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
