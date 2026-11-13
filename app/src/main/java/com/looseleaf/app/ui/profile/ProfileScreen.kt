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
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.ui.settings.SettingsScreen
import com.looseleaf.app.ui.stream.EntryDetailSheetDialog
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
    onNavigateToRoute: ((String) -> Unit)? = null,
    /** 「有图手记」：跳时间流并只看带图记录（RG-30） */
    onOpenImageEntries: (() -> Unit)? = null,
    /** 「随行装备」：跳账本资产大盘并定位该器物（RG-30） */
    onOpenGear: ((String) -> Unit)? = null,
    /** 二级设置页显隐通知宿主控制底栏显隐 */
    onBottomBarVisibilityChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val dayCounts by syncManager.dayCountsFlow.collectAsStateWithLifecycle()

    val cachedProfile = remember { ProfileRepository.getProfile(context) }
    val repoProfile by ProfileRepository.profileState.collectAsStateWithLifecycle()

    var profile by remember(repoProfile) { mutableStateOf(repoProfile ?: cachedProfile) }
    var localAvatarPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var appearanceMode by remember { mutableStateOf(credentialStore.appearanceMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var enableDecorations by remember { mutableStateOf(credentialStore.enableDecorations) }
    var isInstallmentReminderEnabled by remember { mutableStateOf(credentialStore.isInstallmentReminderEnabled) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var selectedAlmanacEntry by remember { mutableStateOf<LifeEntry?>(null) }
    var almanacEntryToDelete by remember { mutableStateOf<LifeEntry?>(null) }
    LaunchedEffect(showSettingsScreen) {
        onBottomBarVisibilityChanged?.invoke(!showSettingsScreen)
    }
    DisposableEffect(Unit) {
        onDispose { onBottomBarVisibilityChanged?.invoke(true) }
    }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showAlmanacDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRenameDeviceDialog by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var globalStats by remember { mutableStateOf<GlobalStats?>(null) }
    var isLoadingInitialStats by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun refreshShowcase() {
        isRefreshing = true
        val result = try {
            val remoteProfile = apiClient.fetchCuratorProfile()
            ProfileRepository.updateProfile(context, remoteProfile)
            profile = remoteProfile
            runCatching { globalStats = apiClient.fetchGlobalStats() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onSuccess {
            SyncManager.markSessionPullDone("profile")
        }.onFailure { err ->
            if (err !is CancellationException) {
                ArchiveToastManager.show("档案刷新失败: ${err.message}", isError = true)
            }
        }
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        launch {
            isLoadingInitialStats = true
            runCatching {
                globalStats = apiClient.fetchGlobalStats()
            }
            isLoadingInitialStats = false
        }
        if (!SyncManager.isSessionPullDone("profile")) {
            val result = try {
                val remoteProfile = apiClient.fetchCuratorProfile()
                ProfileRepository.updateProfile(context, remoteProfile)
                profile = remoteProfile
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

    // 解绑确认对话框要交代的「未同步记录」数量：打开对话框时现查，
    // 让用户在清库前明确知道有 N 条离线内容将被删除。
    var pendingOutboxCount by remember { mutableStateOf(0) }
    LaunchedEffect(showUnpairDialog) {
        if (showUnpairDialog) {
            // 失败态事件同样只在本机，清库后无法找回，必须计入警告（含账本库）。
            pendingOutboxCount = com.looseleaf.app.data.sync.LocalDataResetCoordinator.getUnsyncedCount(context)
        }
    }


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
                ArchivePageHeader(
                    title = "展台",
                    subtitle = null,
                    actions = {
                        // 展台只负责「看」（档案 / 统计 / 热力图 / 装备）；
                        // 外观、安全、设备、存储灾备、更新、解绑这些「改」的动作
                        // 全部收进设置页，不再用 ⋮ 菜单和设置抽屉重复三份入口。
                        IconButton(onClick = { showSettingsScreen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )

                val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { scope.launch { refreshShowcase() } },
                    state = pullToRefreshState,
                    indicator = {
                        PaperclipPullIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                        color = colors.accentSmallText
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
                                    label = "有图手记",
                                    value = filmsCountText,
                                    contentDesc = "有图手记共 $filmsCountText 篇，点击只看带图记录",
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        if (onOpenImageEntries != null) onOpenImageEntries()
                                        else onNavigateToRoute?.invoke("stream")
                                    }
                                )
                                StatColumnItem(
                                    label = "造物项目",
                                    value = projectsCountText,
                                    contentDesc = "造物项目共 $projectsCountText 个，点击跳转工坊造物",
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    onClick = {
                                        onNavigateToRoute?.invoke("projects")
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

                            // 统一复用 ArchiveGearRow 组件：右侧呈现温暖陪伴天数，点击进入账本装备资产管理
                            profile.edcGear.forEachIndexed { index, gear ->
                                key(gear.id) {
                                    if (index > 0) {
                                        ArchiveDivider()
                                    }
                                    ArchiveGearRow(
                                        gear = gear,
                                        trailingText = formatGearCompanionDays(gear.purchaseDate),
                                        trailingColor = colors.inkSecondary,
                                        showShortDateInSubtitle = false,
                                        onClick = {
                                            val gearId = gear.id
                                            if (gearId != null && onOpenGear != null) {
                                                onOpenGear(gearId)
                                            } else {
                                                onNavigateToRoute?.invoke("ledger")
                                            }
                                        }
                                    )
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
            }

            // ==========================================
            // 顶层提升（Hoisted）弹窗与抽屉容器
            // 解决 LazyColumn 惰性视口导致弹窗无法弹出的严重缺陷
            // ==========================================

            // 偏好设置与系统运维独立抽屉 (Atelier vs Settings 分流)
            if (showSettingsScreen) {
                SettingsScreen(
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
                        if (enabled) {
                            com.looseleaf.app.reminder.InstallmentReminderWorker.enqueuePeriodicWork(context)
                        } else {
                            com.looseleaf.app.reminder.InstallmentReminderWorker.cancelWork(context)
                        }
                    },
                    localDeviceName = credentialStore.deviceName,
                    localDeviceId = credentialStore.deviceId ?: "",
                    onRenameLocalDevice = { showRenameDeviceDialog = true },
                    isCheckingUpdate = isCheckingUpdate,
                    // 与 ⋮ 菜单共用同一个动作，避免两处各写一遍检查逻辑
                    onCheckUpdate = checkUpdate,
                    onUnpair = { showUnpairDialog = true },
                    onBack = { showSettingsScreen = false }
                )
            }

            // 年度生活年鉴五幕弹窗
            if (showAlmanacDialog) {
                AlmanacDialog(
                    apiClient = apiClient,
                    earliestEntryYear = globalStats?.currentYear,
                    onDismiss = { showAlmanacDialog = false },
                    onSelectEntry = { entry -> selectedAlmanacEntry = entry }
                )
            }

            selectedAlmanacEntry?.let { activeEntry ->
                EntryDetailSheetDialog(
                    entry = activeEntry,
                    apiClient = apiClient,
                    onDismiss = { selectedAlmanacEntry = null },
                    onSaveEdit = { req, attachments ->
                        scope.launch {
                            runCatching {
                                val written = syncManager.updateEntryOptimistic(activeEntry.id, req, attachments)
                                if (written) ArchiveToastManager.show("手记已修订")
                                else ArchiveToastManager.show("未能定位手记", isError = true)
                                selectedAlmanacEntry = null
                            }.onFailure { err ->
                                ArchiveToastManager.show("修订失败: ${err.message}", isError = true)
                            }
                        }
                    },
                    onDelete = { almanacEntryToDelete = activeEntry },
                    onPhotoClick = { _, _ -> }
                )
            }

            almanacEntryToDelete?.let { deleting ->
                ArchiveConfirmDialog(
                    title = "删除散页手记",
                    content = "确认删除《${deleting.title.ifBlank { "无题随笔" }}》？此操作不可逆。",
                    confirmText = "确认删除",
                    isDestructive = true,
                    onConfirm = {
                        val id = deleting.id
                        almanacEntryToDelete = null
                        selectedAlmanacEntry = null
                        scope.launch {
                            runCatching {
                                syncManager.deleteEntry(id)
                                ArchiveToastManager.show("手记已删除")
                            }.onFailure { err ->
                                ArchiveToastManager.show("删除失败: ${err.message}", isError = true)
                            }
                        }
                    },
                    onDismiss = { almanacEntryToDelete = null }
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
                            com.looseleaf.app.data.sync.LocalDataResetCoordinator.resetAll(
                                context = context,
                                syncManager = syncManager,
                                reason = com.looseleaf.app.data.sync.ResetReason.UNPAIR
                            )
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
            fontSize = ArchiveTypeScale.caption,
            color = colors.inkSecondary,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = ArchiveTypeScale.title,
            fontWeight = FontWeight.Bold,
            color = colors.inkPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
