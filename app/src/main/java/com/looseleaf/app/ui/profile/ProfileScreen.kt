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
import kotlinx.coroutines.CancellationException
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
import com.looseleaf.feature.ledger.data.LedgerRepository
import com.looseleaf.feature.ledger.theme.LedgerFormat
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
import com.looseleaf.app.ui.stream.LifeActivityHeatmap
import com.looseleaf.app.ui.stream.AlmanacDialog
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.BuildConfig
import com.looseleaf.app.R
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.debug.DemoDataSeeder
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
    onEnableDecorationsChanged: ((Boolean) -> Unit)? = null,
    onNavigateToRoute: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val dayCounts by syncManager.dayCountsFlow.collectAsStateWithLifecycle()

    val cachedProfile = remember { ProfileRepository.getProfile(context) }
    val cachedSiteConfig = remember { ProfileRepository.getSiteConfig(context) }
    val repoProfile by ProfileRepository.profileState.collectAsStateWithLifecycle()
    val repoSiteConfig by ProfileRepository.siteConfigState.collectAsStateWithLifecycle()

    var profile by remember(repoProfile) { mutableStateOf(repoProfile ?: cachedProfile) }
    var siteConfig by remember(repoSiteConfig) { mutableStateOf(repoSiteConfig ?: cachedSiteConfig) }
    var localAvatarPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var appearanceMode by remember { mutableStateOf(credentialStore.appearanceMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var enableDecorations by remember { mutableStateOf(credentialStore.enableDecorations) }
    var isInstallmentReminderEnabled by remember { mutableStateOf(credentialStore.isInstallmentReminderEnabled) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showStorageBackupDialog by remember { mutableStateOf(false) }
    var showOtherDevicesSheet by remember { mutableStateOf(false) }
    var showAlmanacDialog by remember { mutableStateOf(false) }
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
    var isLoadingInitialStats by remember { mutableStateOf(true) }

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
        isLoadingInitialStats = true
        runCatching {
            globalStats = apiClient.fetchGlobalStats()
        }
        isLoadingInitialStats = false
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
                } catch (e: CancellationException) {
                    throw e
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
        // 与时间流/账本页同口径：仅成功才标记门禁，失败可重试且给轻提示。
        if (!SyncManager.isSessionPullDone("profile")) {
            val result = try {
                val remoteProfile = apiClient.fetchCuratorProfile()
                ProfileRepository.updateProfile(context, remoteProfile)
                profile = remoteProfile
                val remoteConfig = apiClient.fetchSiteConfig()
                ProfileRepository.updateSiteConfig(context, remoteConfig)
                siteConfig = remoteConfig
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.onSuccess {
                SyncManager.markSessionPullDone("profile")
            }
            result.onFailure { err ->
                if (err is CancellationException) return@onFailure
                ArchiveToastManager.show("档案刷新失败: ${err.message}", isError = true)
            }
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

    var showMoreMenu by remember { mutableStateOf(false) }

    val checkUpdate: () -> Unit = {
        scope.launch {
            isCheckingUpdate = true
            try {
                when (val result = UpdateChecker.checkForUpdates()) {
                    is UpdateCheckResult.HasUpdate -> {
                        availableUpdate = result.release
                        ArchiveToastManager.show("发现新版本: v${result.release.versionName}")
                    }
                    is UpdateCheckResult.AlreadyLatest -> {
                        ArchiveToastManager.show("当前已是最新版本 (v${UpdateChecker.currentVersionName})")
                    }
                    is UpdateCheckResult.Error -> {
                        ArchiveToastManager.show("检测更新失败: ${result.message}", isError = true)
                    }
                }
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    val seedDemoData: () -> Unit = {
        scope.launch {
            try {
                val count = DemoDataSeeder.seedDemoData(context, AppDatabase.getInstance(context))
                ArchiveToastManager.show("成功灌入 $count 条演示手账记录")
            } catch (e: Exception) {
                ArchiveToastManager.show("灌入失败: ${e.message}", isError = true)
            }
        }
    }

    val clearDemoData: () -> Unit = {
        scope.launch {
            try {
                val count = DemoDataSeeder.clearDemoData(context, AppDatabase.getInstance(context))
                ArchiveToastManager.show("成功清除 $count 条演示记录")
            } catch (e: Exception) {
                ArchiveToastManager.show("清除失败: ${e.message}", isError = true)
            }
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
                    .widthIn(max = 680.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 展台顶栏：标题与独立 ⋮ 菜单入口 (与账本、工坊同一规格)
                ArchiveTopAppBar(
                    title = "展台",
                    subtitle = null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(colors.archiveCard)
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp)),
                                shape = RoundedCornerShape(4.dp),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "偏好与安全",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            color = colors.inkPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showSettingsSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "受信任设备",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            color = colors.inkPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showOtherDevicesSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "存储配置与灾备",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            color = colors.inkPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showStorageBackupDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "检测新版本",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            color = colors.inkPrimary
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        checkUpdate()
                                    }
                                )
                                if (BuildConfig.DEBUG) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "灌入演示数据",
                                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                color = colors.inkPrimary
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            seedDemoData()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "清除演示数据",
                                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                color = colors.inkPrimary
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            clearDemoData()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "解除本机配对",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            color = colors.alertRed
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showUnpairDialog = true
                                    }
                                )
                            }
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        // 主理人个人信息区（无卡片、无背景描边、无圆角容器）
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 主理人资料行：左 64dp 圆形头像 + 中间昵称与副标题 + 右侧"编辑"文字按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 64dp 圆形头像（去掉相机徽标，点击打开编辑资料弹层）
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            onClickLabel = "修改头像与资料",
                                            role = androidx.compose.ui.semantics.Role.Button
                                        ) {
                                            showEditProfileDialog = true
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
                                            contentDescription = "主理人头像",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .border(1.dp, colors.borderSubtle, CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(colors.accentWarm.copy(alpha = 0.08f))
                                                .border(1.dp, colors.borderSubtle, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = profile.name.take(1).ifBlank { "散" },
                                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 26.sp,
                                                color = colors.accentWarm
                                            )
                                        }
                                    }

                                    if (isUploadingAvatar) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(colors.inkPrimary.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = colors.archivePaper,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 昵称（衬线 SemiBold，大号）与副标题（次要色，单行省略）
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = profile.name.ifBlank { "散页主理人" },
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 20.sp,
                                        color = colors.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val displaySubtitle = profile.title.ifBlank { "生活记录者 · 独立思想家" }
                                    Text(
                                        text = displaySubtitle,
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 13.sp,
                                        color = colors.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 整行右侧文字按钮"编辑"（赤陶，热区 >= 48dp）
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            onClickLabel = "编辑资料",
                                            role = androidx.compose.ui.semantics.Role.Button
                                        ) {
                                            showEditProfileDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "编辑",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.accentWarm
                                    )
                                }
                            }

                            // 其下一行名言（衬线，正文大小，次要色，不用赤陶，不用斜体）
                            val displayMotto = if (profile.motto.isNotBlank()) profile.motto else "在散落的日常里，拾取有光芒的瞬间"
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = displayMotto,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = colors.inkSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 统计一行四项：
                            // 标签在上（小字、次要色、衬线），数字在下（等宽、大号、正文色）；
                            // 数值为 0 显示"0"，加载中显示占位，只有"数据不可用"才显示"—"并带说明；
                            // 金额使用与账本相同的格式化函数（"¥0.00"，不留空格）
                            Spacer(modifier = Modifier.height(20.dp))
                            ArchiveDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val localTotalEntries = remember(dayCounts) { dayCounts.values.sum() }
                                val draftsCountText = when {
                                    globalStats != null -> "${globalStats?.totalEntries ?: 0}"
                                    localTotalEntries > 0 -> "$localTotalEntries"
                                    isLoadingInitialStats -> "..."
                                    else -> "0"
                                }
                                val filmsCountText = when {
                                    globalStats != null -> "${globalStats?.totalPhotos ?: 0}"
                                    isLoadingInitialStats -> "..."
                                    else -> "0"
                                }
                                val projectsCountText = when {
                                    globalStats != null -> "${globalStats?.totalProjects ?: 0}"
                                    isLoadingInitialStats -> "..."
                                    else -> "0"
                                }
                                val expenseText = when {
                                    globalStats != null -> LedgerFormat.formatMoney(globalStats?.monthExpense ?: 0L)
                                    isLoadingInitialStats -> "..."
                                    else -> "¥0.00"
                                }

                                StatColumnItem(
                                    label = "散页手稿",
                                    value = draftsCountText,
                                    contentDesc = "散页手稿共 $draftsCountText 篇，点击跳转时间流",
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        onNavigateToRoute?.invoke("stream")
                                    }
                                )
                                StatColumnItem(
                                    label = "暗房底片",
                                    value = filmsCountText,
                                    contentDesc = "暗房底片共 $filmsCountText 张",
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        ArchiveToastManager.show("暗房底片已整合至时间流手记")
                                        onNavigateToRoute?.invoke("stream")
                                    }
                                )
                                StatColumnItem(
                                    label = "造物项目",
                                    value = projectsCountText,
                                    contentDesc = "造物项目共 $projectsCountText 个，点击跳转工坊",
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        onNavigateToRoute?.invoke("workshop")
                                    }
                                )
                                StatColumnItem(
                                    label = "当月支出",
                                    value = expenseText,
                                    contentDesc = "当月支出 $expenseText，点击跳转账本",
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        onNavigateToRoute?.invoke("ledger")
                                    }
                                )
                            }
                        }
                    }

                // 区块间 32dp 间距与 1dp 淡线分隔
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    ArchiveDivider()
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    // 阶段 3：时光刻度（热力图）
                    ShowcaseHeatmap(
                        dayCounts = dayCounts,
                        totalEntryCount = globalStats?.totalEntries ?: 0,
                        onOpenAlmanac = { showAlmanacDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 阶段 4：随行装备展台
                if (profile.edcGear.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        ArchiveDivider()
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // SectionHeader：左"随行装备"，后接次要色等宽小字数量（不用括号），右侧文字按钮"管理"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 40.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = "随行装备",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = colors.inkPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${profile.edcGear.size}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 13.sp,
                                        color = colors.inkSecondary
                                    )
                                }

                                ArchiveTextButton(
                                    text = "管理",
                                    onClick = {
                                        onNavigateToRoute?.invoke("ledger")
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 条目：第一行名称（衬线 SemiBold，单行省略），第二行规格（次要色、等宽、单行省略），无规格不留空行；条目之间 1dp 淡线，上下内边距 12dp；无卡片
                            profile.edcGear.forEachIndexed { index, gear ->
                                key(gear.id) {
                                    if (index > 0) {
                                        ArchiveDivider()
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = gear.name,
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = colors.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (gear.spec.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = gear.spec,
                                                fontFamily = ArchiveFontFamilies.MonoScale,
                                                fontSize = 12.sp,
                                                color = colors.inkSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
            }

            // ==========================================
            // 顶层提升（Hoisted）弹窗与抽屉容器
            // 解决 LazyColumn 惰性视口导致弹窗无法弹出的严重缺陷
            // ==========================================

            // 偏好设置与系统运维独立抽屉 (Atelier vs Settings 分流)
            if (showSettingsSheet) {
                SettingsSheet(
                    appearanceMode = appearanceMode,
                    onAppearanceModeChanged = { mode ->
                        appearanceMode = mode
                        credentialStore.appearanceMode = mode
                        onAppearanceModeChanged(mode)
                    },
                    isBiometricsEnabled = isBiometricsEnabled,
                    onBiometricsChanged = { enabled ->
                        isBiometricsEnabled = enabled
                        credentialStore.isBiometricsEnabled = enabled
                    },
                    showBinderHoles = credentialStore.showBinderHoles,
                    onShowBinderHolesChanged = { enabled ->
                        credentialStore.showBinderHoles = enabled
                    },
                    enableDecorations = enableDecorations,
                    onEnableDecorationsChanged = { enabled ->
                        enableDecorations = enabled
                        credentialStore.enableDecorations = enabled
                        onEnableDecorationsChanged?.invoke(enabled)
                    },
                    isInstallmentReminderEnabled = isInstallmentReminderEnabled,
                    onInstallmentReminderChanged = { enabled ->
                        isInstallmentReminderEnabled = enabled
                        credentialStore.isInstallmentReminderEnabled = enabled
                    },
                    localDeviceName = credentialStore.deviceName,
                    localDeviceId = credentialStore.deviceId ?: "",
                    onRenameLocalDevice = { showRenameDeviceDialog = true },
                    otherDevices = trustedDevices.filter { it.deviceId != credentialStore.deviceId },
                    onOpenOtherDevices = { showOtherDevicesSheet = true },
                    isIssuingPairTicket = isIssuingPairTicket,
                    onIssuePairTicket = {
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
                    issuedPairTicket = issuedPairTicket,
                    onClosePairTicket = { issuedPairTicket = null },
                    onCopyPairTicket = { link ->
                        clipboardManager.setText(AnnotatedString(link))
                        ArchiveToastManager.show("已复制配对链接至剪贴板")
                    },
                    onOpenStorageBackup = { showStorageBackupDialog = true },
                    isCheckingUpdate = isCheckingUpdate,
                    onCheckUpdate = {
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
                    onUnpair = { showUnpairDialog = true },
                    onSeedDemoData = if (BuildConfig.DEBUG) {
                        {
                            scope.launch {
                                try {
                                    val count = DemoDataSeeder.seedDemoData(context, AppDatabase.getInstance(context))
                                    ArchiveToastManager.show("成功灌入 $count 条演示手账记录")
                                } catch (e: Exception) {
                                    ArchiveToastManager.show("灌入失败: ${e.message}", isError = true)
                                }
                            }
                        }
                    } else null,
                    onClearDemoData = if (BuildConfig.DEBUG) {
                        {
                            scope.launch {
                                try {
                                    val count = DemoDataSeeder.clearDemoData(context, AppDatabase.getInstance(context))
                                    ArchiveToastManager.show("已彻底清除 $count 条演示记录与资源")
                                } catch (e: Exception) {
                                    ArchiveToastManager.show("清除失败: ${e.message}", isError = true)
                                }
                            }
                        }
                    } else null,
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // 其他受信任设备组二级管理底抽
            if (showOtherDevicesSheet) {
                OtherDevicesSheet(
                    otherDevices = trustedDevices.filter { it.deviceId != credentialStore.deviceId },
                    onDismiss = { showOtherDevicesSheet = false },
                    onRename = { dev ->
                        showOtherDevicesSheet = false
                        deviceToRename = dev
                    },
                    onRevoke = { dev ->
                        showOtherDevicesSheet = false
                        deviceToRevoke = dev
                    }
                )
            }

            // 年度生活年鉴五幕弹窗
            if (showAlmanacDialog) {
                AlmanacDialog(
                    apiClient = apiClient,
                    earliestEntryYear = globalStats?.currentYear,
                    onDismiss = { showAlmanacDialog = false }
                )
            }

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
                            // 以及两处表单草稿（修订草稿 + 速记草稿）——
                            // 与确认框「连同删除且无法找回」的承诺一致，原样留在本机就是可还原内容
                            runCatching {
                                context.getSharedPreferences(
                                    "looseleaf_form_drafts", android.content.Context.MODE_PRIVATE
                                ).edit().clear().apply()
                                context.getSharedPreferences(
                                    "quick_note_draft", android.content.Context.MODE_PRIVATE
                                ).edit().clear().apply()
                            }
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
                                showEditProfileDialog = false
                            }.onFailure { err ->
                                ArchiveToastManager.show("保存档案失败: ${err.message}", isError = true)
                            }
                        }
                    }
                )
            }

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

            if (showStorageBackupDialog) {
                StorageBackupSheetDialog(
                    apiClient = apiClient,
                    onDismiss = { showStorageBackupDialog = false },
                    onRestored = {
                        scope.launch {
                            syncManager.pullRemoteData(forceFull = true)
                                .onFailure { err ->
                                    ArchiveToastManager.show(
                                        "恢复完成，但拉取中枢数据失败: ${err.message}，请下拉刷新",
                                        isError = true
                                    )
                                }
                            reloadDevices()
                        }
                    }
                )
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
    }
}

@Composable
private fun StatColumnItem(
    label: String,
    value: String,
    contentDesc: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClickLabel = contentDesc,
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.5.sp,
            color = colors.inkSecondary,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.inkPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
