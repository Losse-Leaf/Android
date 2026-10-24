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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.local.LedgerRepository
import com.looseleaf.app.data.local.ProfileRepository
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.GlobalStats
import com.looseleaf.app.model.SiteConfig
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.model.TrustedDevice
import com.looseleaf.app.model.PairTicketResponse
import com.looseleaf.app.utils.CityDictionary
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
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
/**
 * 主理人展台与设备中枢设置 (ProfileScreen)
 * 严格遵循 docs/backend.md §一.5 全数据后端驱动与严禁前端写死原则 (Dynamic Data Sovereignty)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
    onAppearanceModeChanged: (AppearanceMode) -> Unit = {},
    /** MOB-P2-45：高对比度模式 */
    highContrast: Boolean = false,
    onHighContrastChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }

    val cachedProfile = remember { ProfileRepository.getProfile(context) }
    val cachedSiteConfig = remember { ProfileRepository.getSiteConfig(context) }
    val repoProfile by ProfileRepository.profileState.collectAsStateWithLifecycle()
    val repoSiteConfig by ProfileRepository.siteConfigState.collectAsStateWithLifecycle()

    var profile by remember(repoProfile) { mutableStateOf(repoProfile ?: cachedProfile) }
    var siteConfig by remember(repoSiteConfig) { mutableStateOf(repoSiteConfig ?: cachedSiteConfig) }
    var localAvatarPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var appearanceMode by remember { mutableStateOf(credentialStore.appearanceMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showStorageBackupDialog by remember { mutableStateOf(false) }
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
                            // MOB-P2-67：16bit 色深会让照片出现明显色带（尤其肤色与渐变）
                            inPreferredConfig = Bitmap.Config.ARGB_8888
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

    LaunchedEffect(Unit) {
        // 会话门禁改为**进程级**标志：原先 rememberSaveable 在系统回收进程后恢复时
        // 仍为 true，新会话里展台页不再首拉档案，用户对着陈旧数据无提示。
        if (!SyncManager.isSessionPullDone("profile")) {
            runCatching {
                val remoteProfile = apiClient.fetchCuratorProfile()
                ProfileRepository.updateProfile(context, remoteProfile)
                profile = remoteProfile
                val remoteConfig = apiClient.fetchSiteConfig()
                ProfileRepository.updateSiteConfig(context, remoteConfig)
                siteConfig = remoteConfig
            }
            SyncManager.markSessionPullDone("profile")
        }
    }

    // 解绑确认对话框要交代的「未同步记录」数量：打开对话框时现查，
    // 让用户在清库前明确知道有 N 条离线内容将被删除。
    var pendingOutboxCount by remember { mutableStateOf(0) }
    LaunchedEffect(showUnpairDialog) {
        if (showUnpairDialog) {
            // 失败态事件同样只在本机，清库后无法找回，必须计入警告。
            pendingOutboxCount = syncManager.unsyncedEventCountNow()
        }
    }

    NoiseBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                // MOB-P2-75：补顶栏标题 —— 此前展台页直接进入藏书票卡片，
                // 切过来时看不到 App 名与页面名，与其它页面的语言不一致。
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.profile_screen_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.profile_screen_text_02),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                }
                item {
                // 主理人名片拓片卡片 (藏书票 Ex-libris 紧凑型架构)
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colors.archivePaper,
                    borderColor = colors.accentWarm.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
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
                                        contentDescription = stringResource(R.string.profile_screen_contentDescription_01),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, colors.accentWarm.copy(alpha = 0.45f), CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentWarm.copy(alpha = 0.1f))
                                            .border(1.dp, colors.accentWarm.copy(alpha = 0.35f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).ifBlank { "散" },
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 24.sp,
                                            color = colors.accentWarm
                                        )
                                    }
                                }

                                if (isUploadingAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(colors.inkPrimary.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.archivePaper,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentWarm)
                                        .border(1.dp, colors.archivePaper, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_camera_alt),
                                        contentDescription = stringResource(R.string.profile_screen_contentDescription_02),
                                        tint = colors.archivePaper,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // 右侧信息流：称谓 + 身份 + 城市 + 编辑铅笔图标
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = profile.name.ifBlank { "散页主理人" },
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp,
                                        color = colors.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    IconButton(
                                        onClick = { showEditProfileDialog = true },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.profile_screen_contentDescription_03),
                                            tint = colors.inkMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                val displayTitle = profile.title.ifBlank { "生活记录者 · 独立思想家" }
                                Text(
                                    text = displayTitle,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val rawCity = profile.currentCity.takeIf { it.isNotBlank() } ?: profile.location.takeIf { it.isNotBlank() } ?: ""
                                val displayCity = if (rawCity.isNotBlank()) {
                                    val cCode = CityDictionary.codeForLocation(rawCity)
                                    val std = cCode?.let { c -> CityDictionary.standardCities.firstOrNull { it.code == c } }
                                    if (std != null) "${std.province} · ${std.name}" else rawCity
                                } else ""

                                if (displayCity.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = colors.accentWarm,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = displayCity,
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            fontSize = 11.sp,
                                            color = colors.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // 生活格言
                        val displayMotto = if (profile.motto.isNotBlank()) profile.motto else "在散落的日常里，拾取有光芒的瞬间"
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.35f))
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.profile_screen_text_tpl_01, displayMotto),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            letterSpacing = 0.3.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.accentWarm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                }
                item {
                // 全局档案指标概览卡片 (GlobalStats)
                Spacer(modifier = Modifier.height(16.dp))
                }
                item {
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
                                text = stringResource(R.string.profile_screen_text_03),
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.95f)) {
                                Text(stringResource(R.string.profile_screen_drafts), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(2.dp))
                                // MOB-P2-69：加载完成前显示占位符，避免「0」被误读为「真的没有」
                                Text(
                                    if (globalStats == null) "—" else "${globalStats?.totalEntries}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (globalStats == null) colors.inkMuted else colors.inkPrimary,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.95f)) {
                                Text(stringResource(R.string.profile_screen_films), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (globalStats == null) "—" else "${globalStats?.totalPhotos}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (globalStats == null) colors.inkMuted else colors.accentWarm,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.95f)) {
                                Text(stringResource(R.string.profile_screen_projects), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${globalStats?.totalProjects ?: 0}", fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.accentMoss, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.15f)) {
                                Text(stringResource(R.string.profile_screen_expense), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.sp, color = colors.inkMuted, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.centsToYuanString(globalStats?.monthExpense ?: 0),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }
                }
                item {
                // EDC 常用随行装备展台
                if (profile.edcGear.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = stringResource(R.string.profile_screen_text_tpl_02, profile.edcGear.size),
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
                }
                item {
                Spacer(modifier = Modifier.height(20.dp))
                }
                item {
                // 受信任设备组卡片 (Trusted Devices Group Management)
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_screen_text_tpl_03, trustedDevices.size),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isIssuingPairTicket = true
                                        runCatching {
                                            issuedPairTicket = apiClient.initPairing()
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
                                Text(if (isIssuingPairTicket) stringResource(R.string.profile_screen_issuing_ticket) else stringResource(R.string.profile_screen_pair_new_device), fontSize = 11.5.sp, fontFamily = ArchiveFontFamilies.BodySans, color = colors.accentWarm)
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
                                            text = stringResource(R.string.profile_screen_text_04),
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.inkPrimary
                                        )
                                        TextButton(
                                            onClick = { issuedPairTicket = null },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(stringResource(R.string.profile_screen_collapse), fontSize = 11.sp, color = colors.inkMuted)
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
                                        Text(stringResource(R.string.profile_screen_copy_link), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. 本机设备卡片
                        TrustedDeviceRow(
                            deviceName = credentialStore.deviceName.ifBlank { "Android 随行设备" },
                            deviceId = credentialStore.deviceId ?: "未初始化",
                            platform = "android",
                            isLocal = true,
                            lastActiveAt = null,
                            onRename = { showRenameDeviceDialog = true },
                            onRevokeOrUnpair = { showUnpairDialog = true }
                        )

                        // 3. 其他受信任设备列表
                        val otherDevices = trustedDevices.filter { it.deviceId != credentialStore.deviceId }
                        if (isLoadingDevices) {
                            // MOB-P2-70：此前 isLoadingDevices 只写不读 ——
                            // 加载期间设备组静默只显示「本机」，用户以为其它设备被踢了。
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = colors.inkMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.profile_screen_text_05),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.inkMuted
                                )
                            }
                        }
                        if (otherDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = colors.borderDivider.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.profile_screen_text_tpl_04, otherDevices.size),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 10.sp,
                                color = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                otherDevices.forEach { dev ->
                                    TrustedDeviceRow(
                                        deviceName = dev.name.ifBlank { dev.deviceId },
                                        deviceId = dev.deviceId,
                                        platform = dev.platform,
                                        isLocal = false,
                                        lastActiveAt = dev.lastActiveAt,
                                        onRename = { deviceToRename = dev },
                                        onRevokeOrUnpair = { deviceToRevoke = dev }
                                    )
                                }
                            }
                        }
                    }
                }
                }
                item {
                Spacer(modifier = Modifier.height(20.dp))
                }
                item {
                // 移动端个性化开关
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.profile_screen_text_06),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.profile_screen_appearance), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 13.5.sp, color = colors.inkPrimary)
                            Text(stringResource(R.string.profile_screen_appearance_desc), fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val options = listOf(
                                    AppearanceMode.SYSTEM to "跟随系统",
                                    AppearanceMode.PAPER to "暖色棉纸",
                                    AppearanceMode.DARKROOM to "暗房"
                                )
                                options.forEach { (mode, label) ->
                                    val selected = appearanceMode == mode
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) colors.accentWarm else colors.archiveSubtle)
                                            .border(
                                                0.5.dp,
                                                if (selected) colors.accentWarm else colors.borderDivider,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                appearanceMode = mode
                                                credentialStore.appearanceMode = mode
                                                onAppearanceModeChanged(mode)
                                            }
                                            .padding(vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontSize = 12.sp,
                                            color = if (selected) colors.archivePaper else colors.inkSecondary
                                        )
                                    }
                                }
                            }

                            // MOB-P2-45：高对比度模式开关（次级文字与分隔线显著加强）
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.profile_screen_text_07),
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 13.5.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.profile_screen_text_08),
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                Switch(
                                    checked = highContrast,
                                    onCheckedChange = onHighContrastChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.accentWarm,
                                        checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(stringResource(R.string.profile_screen_biometrics), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 13.5.sp, color = colors.inkPrimary)
                                Text(stringResource(R.string.profile_screen_biometrics_desc), fontSize = 11.sp, color = colors.inkMuted)
                            }
                            Switch(
                                checked = isBiometricsEnabled,
                                onCheckedChange = {
                                    isBiometricsEnabled = it
                                    credentialStore.isBiometricsEnabled = it
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = colors.accentWarm, checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
                }
                item {
                Spacer(modifier = Modifier.height(20.dp))
                }
                item {
                // 存储配置与快照灾备中枢卡片 (对齐 Web StorageBackupModal)
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_screen_text_09),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )

                            TextButton(
                                onClick = { showStorageBackupDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_tune), contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.profile_screen_config_manage), fontSize = 11.5.sp, fontFamily = ArchiveFontFamilies.BodySans, color = colors.accentWarm)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_screen_text_10),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 11.5.sp,
                            color = colors.inkSecondary
                        )
                    }
                }
                }
                item {
                Spacer(modifier = Modifier.height(20.dp))
                }
                item {
                // 版本信息与检测更新卡片
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.profile_screen_text_11),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ProfileInfoRow(label = stringResource(R.string.profile_screen_label_01), value = "v${UpdateChecker.currentVersionName}")
                        ProfileInfoRow(label = stringResource(R.string.profile_screen_label_02), value = "GitHub Releases")

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
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                }
                item {
                Spacer(modifier = Modifier.height(24.dp))
                }
                item {
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
                    Icon(painter = painterResource(R.drawable.ic_link_off), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_screen_unbind_device), fontFamily = ArchiveFontFamilies.BodySans)
                }
                }
                item {
                // MOB-P2-62：原先底部连续放了两个 96dp Spacer（恒定浪费 192dp 空白）
                Spacer(modifier = Modifier.height(24.dp))
                }
                item {
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
                                    versionName = update.versionName,
                                    expectedSha256 = update.apkSha256
                                ) { p, _, _ ->
                                    downloadProgress = p
                                }
                                isDownloading = false
                                res.onSuccess { f ->
                                    if (!UpdateChecker.installApk(context, f)) {
                                        ArchiveToastManager.show(
                                            "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                                            isError = true
                                        )
                                    }
                                }.onFailure { err ->
                                    ArchiveToastManager.show("下载失败: ${err.message}", isError = true)
                                }
                            }
                        }
                    )
                }
                }
                item {
                if (showUnpairDialog) {
                    ArchiveConfirmDialog(
                        title = stringResource(R.string.profile_screen_title_01),
                        content = if (pendingOutboxCount > 0) {
                            "解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。" +
                                "注意：本机还有 $pendingOutboxCount 条未同步记录（含离线日记/账目/相纸），" +
                                "解绑将连同删除且**无法找回**。建议先联网同步完成后再解绑。"
                        } else {
                            "解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。"
                        },
                        confirmText = stringResource(R.string.profile_screen_confirmText_01),
                        isDestructive = true,
                        onConfirm = {
                            val currentDeviceId = credentialStore.deviceId
                            showUnpairDialog = false
                            scope.launch {
                                if (!currentDeviceId.isNullOrBlank()) {
                                    val revoke = runCatching { apiClient.revokeDevice(currentDeviceId) }
                                    if (revoke.isFailure) {
                                        val code = (revoke.exceptionOrNull() as? APIException)?.code
                                        // 401/404 = 服务端已经不认识这台设备，等价于已吊销。
                                        if (code !in listOf(401, 404)) {
                                            ArchiveToastManager.show(
                                                "解除失败：无法联系中枢，本机数据与待同步记录均未清除，请联网后重试",
                                                isError = true
                                            )
                                            return@launch
                                        }
                                    }
                                }
                                syncManager.cancelScheduledSync()
                                runCatching { syncManager.clearAllLocalData() }
                                credentialStore.clearCredentials()
                                ProfileRepository.clearCache(context)
                                LedgerRepository.clear()
                                // 解绑必须清掉可还原内容的本地痕迹：图片缓存与已下载安装包
                                runCatching {
                                    File(context.cacheDir, "image_cache").deleteRecursively()
                                    context.getExternalFilesDir(null)?.let { dir ->
                                        File(dir, "Download").deleteRecursively()
                                    }
                                }
                                onUnpair()
                            }
                        },
                        onDismiss = { showUnpairDialog = false }
                    )
                }
                }
                item {
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
                }
                item {
                if (showRenameDeviceDialog) {
                    ArchivePromptDialog(
                        title = stringResource(R.string.profile_screen_title_02),
                        initialValue = credentialStore.deviceName,
                        placeholder = stringResource(R.string.pairing_screen_placeholder_02),
                        label = stringResource(R.string.profile_screen_label_03),
                        confirmText = stringResource(R.string.profile_screen_confirmText_02),
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
                }
                item {
                deviceToRevoke?.let { dev ->
                    ArchiveConfirmDialog(
                        title = stringResource(R.string.profile_screen_title_03),
                        content = "吊销后设备「${dev.name.ifBlank { dev.deviceId }}」将立即失去中枢访问凭据，无法再读取或提交任何档案。此操作不可逆。",
                        confirmText = stringResource(R.string.profile_screen_confirmText_03),
                        isDestructive = true,
                        onConfirm = {
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
                }
                item {
                deviceToRename?.let { dev ->
                    ArchivePromptDialog(
                        title = stringResource(R.string.profile_screen_title_04),
                        initialValue = dev.name,
                        placeholder = stringResource(R.string.profile_screen_placeholder_01),
                        label = stringResource(R.string.profile_screen_label_tpl_01, dev.deviceId),
                        confirmText = stringResource(R.string.profile_screen_confirmText_02),
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
                }
                item {
                if (showStorageBackupDialog) {
                    StorageBackupSheetDialog(
                        apiClient = apiClient,
                        onDismiss = { showStorageBackupDialog = false },
                        onRestored = {
                            scope.launch {
                                syncManager.pullRemoteData(forceFull = true)
                                reloadDevices()
                            }
                        }
                    )
                }
                }
            }
    }
}
}
