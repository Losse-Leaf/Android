package com.looseleaf.app.ui.stream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.Manifest
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
/** 进程级一次性标记：避免每次进入时间流都请求 GitHub Releases */
private var updateCheckedThisProcess = false

/** 热力图单元格步长（dp） */

/**
 * 生活时间流主展厅 (StreamScreen) - docs/mobile.md §3 & docs/ecosystem.md §4.1
 * 承载生命之流母体记录，支持相纸画廊、那年今日回响、年度生活年鉴、详情查阅与行内修订、岁月留白切线
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }

    val entries by syncManager.entriesFlow.collectAsStateWithLifecycle()
    val pendingCount by syncManager.pendingEventCountFlow.collectAsStateWithLifecycle()
    val failedCount by syncManager.failedEventCountFlow.collectAsStateWithLifecycle()

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }
    var isRefreshing by remember { mutableStateOf(false) }
    // 更新横幅走 JSON 持久化（ReleaseInfo 含非 Saveable 字段）：旋转后不再永久消失
    var cloudUpdateJson by rememberSaveable { mutableStateOf<String?>(null) }
    val cloudUpdate: ReleaseInfo? = remember(cloudUpdateJson) {
        cloudUpdateJson?.let {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ReleaseInfo>(it) }.getOrNull()
        }
    }

    // 那年今日状态
    var remoteOnThisDay by remember { mutableStateOf<List<LifeEntry>>(emptyList()) }
    var isOnThisDayExpanded by rememberSaveable { mutableStateOf(true) }

    // 年度年鉴弹窗状态
    var showAlmanacDialog by remember { mutableStateOf(false) }

    // 全局检索与速记随笔状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 失败事件处置弹窗（重试 / 放弃）
    var showFailedEventsDialog by remember { mutableStateOf(false) }

    // 单条档案详情与编辑弹窗状态
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }
    var lightboxPhoto by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // 更新检查：整个进程只查一次，失败静默（避免每次切 Tab 都打 GitHub API）
    LaunchedEffect(Unit) {
        if (!updateCheckedThisProcess && cloudUpdate == null) {
            updateCheckedThisProcess = true
            when (val res = UpdateChecker.checkForUpdates()) {
                is UpdateCheckResult.HasUpdate ->
                    cloudUpdateJson = Json { ignoreUnknownKeys = true }.encodeToString(res.release)
                else -> Unit
            }
        }
    }

    // 那年今日：每次进入时间流都向服务端对齐一次
    LaunchedEffect(Unit) {
        runCatching { remoteOnThisDay = apiClient.fetchOnThisDay().entries }
    }

    // 合并云端那年今日与本地往年同月同日条目：
    // 本地匹配已在 SyncManager 的 Default 线程管道里完成（AOPT-03c），此处只做选择
    val localOnThisDay by syncManager.localOnThisDayFlow.collectAsStateWithLifecycle()
    val onThisDayEntries = remember(localOnThisDay, remoteOnThisDay) {
        remoteOnThisDay.ifEmpty { localOnThisDay }
    }

    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

    // 年份列表同样来自后台派生流，不在 composition 里遍历全表
    val availableYears by syncManager.availableYearsFlow.collectAsStateWithLifecycle()

    val filteredEntries = remember(entries, selectedFilter, selectedYear) {
        var list = entries
        if (selectedYear != null) {
            list = list.filter {
                it.occurredAt.length >= 4 && it.occurredAt.substring(0, 4).toIntOrNull() == selectedYear
            }
        }
        list.filter { entryMatchesFilter(it, selectedFilter) }
    }

    // 把「与上一条的间隔天数」一次性算好：LazyColumn 滚动期只读字段，
    // 不再每条都在主线程解析两次 ISO 时间串（AOPT-03b）
    val streamRows = remember(filteredEntries) {
        filteredEntries.mapIndexed { index, entry ->
            StreamRowItem(
                entry = entry,
                gapDays = if (index == 0) 0L else FormatUtils.calculateDaysBetween(
                    entry.occurredAt,
                    filteredEntries[index - 1].occurredAt
                )
            )
        }
    }

    // 下拉刷新与顶栏按钮共用的同步动作（AOPT-06）
    val refreshArchive: () -> Unit = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                val res = syncManager.pullRemoteData()
                runCatching { remoteOnThisDay = apiClient.fetchOnThisDay().entries }
                isRefreshing = false
                if (res.isSuccess) {
                    ArchiveToastManager.show("档案已增量同步")
                } else {
                    ArchiveToastManager.show(res.exceptionOrNull()?.message ?: "同步未完成", isError = true)
                }
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
                    .widthIn(max = 680.dp)
            ) {
            // 顶栏：标尺、年度年鉴与同步指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "散页 · 随行手账",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "生活自留地 · 真实时间轴",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pendingCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentWarm.copy(alpha = 0.12f))
                                .clickable(role = Role.Button) {
                                    scope.launch {
                                        // 徽标文案是「待同步」：点击应立即推进发件箱（排队 Worker），
                                        // 而不是拉一整轮远端数据
                                        ArchiveToastManager.show("发件箱已重新排队，联网后自动上传 ($pendingCount 项待重放)")
                                        syncManager.scheduleSync()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentWarm)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "待同步 $pendingCount",
                                fontSize = 10.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (failedCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentWarm.copy(alpha = 0.18f))
                                .clickable(role = Role.Button) {
                                    // 弹二选一而不是直接重试：永久 4xx / 重试耗尽的事件
                                    // 只会再失败一轮，用户必须有一条「放弃」的出路
                                    showFailedEventsDialog = true
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "同步失败 $failedCount",
                                fontSize = 10.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // 全局检索按钮
                    IconButton(
                        onClick = {
                            showSearchDialog = true
                        },
                        // 40dp 而非 32dp：右上角本就是单手最难触达的盲区，热区不能再缩
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "检索",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 年度年鉴入口按钮
                    IconButton(
                        onClick = {
                            showAlmanacDialog = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = "年度年鉴",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 同步刷新按钮
                    IconButton(
                        onClick = refreshArchive,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "同步刷新",
                            tint = if (isRefreshing) colors.accentWarm else colors.inkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 新版本可用提示卡片
            cloudUpdate?.let { update ->
                var downloadProgress by remember { mutableStateOf(0f) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.accentWarm.copy(alpha = 0.08f))
                        .border(0.5.dp, colors.accentWarm, RoundedCornerShape(3.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "检测到新版本 ${update.tagName}（当前 v${UpdateChecker.currentVersionName}）",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.accentWarm
                        )
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = colors.accentWarm,
                                trackColor = colors.accentWarm.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "正在应用内下载: ${(downloadProgress * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.inkMuted
                            )
                        } else if (downloadedFile != null) {
                            Text(
                                text = "下载完成，准备安装",
                                fontSize = 11.sp,
                                color = colors.accentMoss
                            )
                        } else if (update.body.isNotBlank()) {
                            Text(
                                text = update.body.lines().firstOrNull() ?: "",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    if (downloadedFile != null) {
                        Button(
                            onClick = {
                                downloadedFile?.let { file ->
                                    if (!UpdateChecker.installApk(context, file)) {
                                        ArchiveToastManager.show(
                                            "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                                            isError = true
                                        )
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                        ) {
                            Text("立即安装", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    } else if (isDownloading) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            color = colors.accentWarm,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Button(
                            onClick = {
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
                                        downloadedFile = f
                                        val launched = UpdateChecker.installApk(context, f)
                                        if (!launched) {
                                            ArchiveToastManager.show(
                                                "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                                                isError = true
                                            )
                                        }
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("下载失败: ${err.message}", isError = true)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                        ) {
                            Text("应用内更新", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    }
                }
            }

            // 分类筛选横滑栏
            StreamFilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = {
                    selectedFilter = it
                }
            )

            // 年份归档切换栏
            YearFilterBar(
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = {
                    selectedYear = it
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 时间流列表
            if (filteredEntries.isEmpty() && onThisDayEntries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "岁月静好，暂无此分类记录",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            } else {
                // 原生下拉刷新手势（AOPT-06）：列表滚到顶继续下拉即可同步，
                // 不再依赖右上角那颗 40dp 图标
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refreshArchive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    // 外层 Scaffold 已按 LooseLeafBottomBar 扣除 innerPadding；
                    // 这里的底部留白专门留给右下角「记随笔」浮动印章（56dp FAB + 16dp 边距 + 16dp 呼吸），
                    // 否则滚到底时最后一张卡片会被 FAB 压住
                    contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
                ) {
                    // 52周时光生活刻度热力图
                    item(key = "life_activity_heatmap") {
                        LifeActivityHeatmap(
                            entries = entries,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // 那年今日 · 岁月回响 折叠卡片
                    if (onThisDayEntries.isNotEmpty()) {
                        item(key = "on_this_day_banner") {
                            OnThisDayCard(
                                entries = onThisDayEntries,
                                isExpanded = isOnThisDayExpanded,
                                onToggleExpand = {
                                    isOnThisDayExpanded = !isOnThisDayExpanded
                                },
                                onSelectEntry = { entry ->
                                    selectedEntryForDetail = entry
                                },
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }

                    // 滚动期的日期解析全部预计算：原来在 itemsIndexed 渲染块里
                    // 每条都要解析两次 ISO 字符串，快速滑动时主线程出现帧毛刺（AOPT-03b）
                    items(
                        items = streamRows,
                        key = { row -> row.entry.id },
                        contentType = { row ->
                            if (row.entry.attachments.isEmpty()) "text_entry" else "media_entry"
                        }
                    ) { row ->
                        // 岁月留白刻度：若与上一条时间跨度 >= 14 天，自动插入切线
                        if (row.gapDays >= 14) {
                            TemporalGapSeparator(days = row.gapDays)
                        }

                        StreamEntryCard(
                            entry = row.entry,
                            apiClient = apiClient,
                            onClick = {
                                selectedEntryForDetail = row.entry
                            },
                            onDelete = {
                                entryToDelete = row.entry
                            },
                            onPhotoClick = { url, meta ->
                                lightboxPhoto = url to meta
                            },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                }
            }
        }

        // 「记随笔」纸上浮动印章：右下角单手拇指热区，替代原顶栏右上角 32dp 图标
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(56.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    spotColor = colors.frameShadowSpot,
                    ambientColor = colors.frameShadowAmbient
                )
                .clip(CircleShape)
                .background(colors.accentWarm)
                .clickable(role = Role.Button) { showQuickNoteDialog = true }
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "记随笔",
                tint = colors.archivePaper,
                modifier = Modifier.size(24.dp)
            )
        }

        // 单条记录详情与编辑 Sheet/Dialog
        selectedEntryForDetail?.let { activeEntry ->
            EntryDetailSheetDialog(
                entry = activeEntry,
                apiClient = apiClient,
                onDismiss = { selectedEntryForDetail = null },
                onSaveEdit = { req ->
                    scope.launch {
                        syncManager.updateEntryOptimistic(activeEntry.id, req)
                        ArchiveToastManager.show("已修订归册")
                        selectedEntryForDetail = null
                    }
                },
                onDelete = {
                    entryToDelete = activeEntry
                },
                onPhotoClick = { url, meta ->
                    lightboxPhoto = url to meta
                }
            )
        }

        // 失败事件处置：重试或放弃（放弃仅移除发件箱事件，本地记录保留）
        // 统一为纸墨 Dialog（AOPT-08b）：原 Material3 AlertDialog 的圆角/排版与全站不一致
        if (showFailedEventsDialog) {
            Dialog(
                onDismissRequest = { showFailedEventsDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 380.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(4.dp),
                                spotColor = colors.frameShadowSpot,
                                ambientColor = colors.frameShadowAmbient
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archivePaper)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 22.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "处置 $failedCount 条失败记录",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "重试会清空失败态并重新排队；放弃则移除这些发件箱事件" +
                                "（本地记录与「待同步」标记保留，便于排查）。点卡片外部可稍后处理。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                showFailedEventsDialog = false
                                scope.launch {
                                    syncManager.discardFailedEvents()
                                    ArchiveToastManager.show("已放弃失败事件；本地记录保留")
                                }
                            }) {
                                Text("放弃", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp, color = colors.inkMuted)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    showFailedEventsDialog = false
                                    scope.launch {
                                        ArchiveToastManager.show("正在重试 $failedCount 条失败记录…")
                                        syncManager.retryFailedEvents()
                                    }
                                },
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentWarm,
                                    contentColor = colors.archivePaper
                                )
                            ) {
                                Text("全部重试", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 撕除确认弹窗
        entryToDelete?.let { entry ->
            ArchiveConfirmDialog(
                title = "撕除手记？",
                content = "确认从生活时间流中彻底移除《${entry.title.ifBlank { "无题随笔" }}》？",
                confirmText = "确认撕除",
                isDestructive = true,
                onConfirm = {
                    // docs/mobile.md §3.4：删除重要日记需要生物识别二次确认
                    val performDelete: () -> Unit = {
                        scope.launch {
                            syncManager.deleteEntry(entry.id)
                            ArchiveToastManager.show("已撕除记录")
                            entryToDelete = null
                            if (selectedEntryForDetail?.id == entry.id) {
                                selectedEntryForDetail = null
                            }
                        }
                    }
                    val activity = context as? FragmentActivity
                    if (credentialStore.isBiometricsEnabled && activity != null &&
                        BiometricHelper.canAuthenticate(activity)
                    ) {
                        BiometricHelper.authenticate(
                            activity = activity,
                            title = "验证以撕除记录",
                            subtitle = "删除私人档案需要二次身份确认",
                            onSuccess = { performDelete() },
                            onError = { msg ->
                                ArchiveToastManager.show(msg, isError = true)
                                entryToDelete = null
                            }
                        )
                    } else {
                        performDelete()
                    }
                },
                onDismiss = { entryToDelete = null }
            )
        }

        // 全屏照片灯箱
        lightboxPhoto?.let { (url, meta) ->
            ArchiveLightboxViewer(
                imageUrl = url,
                metadataText = meta,
                onDismiss = { lightboxPhoto = null }
            )
        }

        // 全局检索对话框
        if (showSearchDialog) {
            GlobalSearchDialog(
                apiClient = apiClient,
                onDismiss = { showSearchDialog = false },
                onSelectEntry = { selectedEntryForDetail = it }
            )
        }

        // 随手写散页与图文装裱底抽信笺
        if (showQuickNoteDialog) {
            QuickNoteSheet(
                onDismiss = { showQuickNoteDialog = false },
                syncManager = syncManager,
                apiClient = apiClient,
                onSuccess = { showQuickNoteDialog = false }
            )
        }

        // 年度生活年鉴五幕弹窗
        if (showAlmanacDialog) {
            AlmanacDialog(
                apiClient = apiClient,
                entries = entries,
                onDismiss = { showAlmanacDialog = false }
            )
        }
    }
}
}
