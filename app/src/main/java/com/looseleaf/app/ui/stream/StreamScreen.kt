package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.looseleaf.app.audio.AudioEngine
import android.Manifest
import android.net.Uri
import androidx.core.content.FileProvider
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
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

/**
 * 生活时间流主展厅 (StreamScreen) - docs/mobile.md §3 & docs/ecosystem.md §4.1
 * 承载生命之流母体记录，支持相纸画廊、那年今日回响、年度生活年鉴、详情查阅与行内修订、岁月留白切线
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onNavigateToDarkroom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    val entries by syncManager.entriesFlow.collectAsState()
    val pendingCount by syncManager.pendingEventCountFlow.collectAsState()

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }
    var isRefreshing by remember { mutableStateOf(false) }
    var cloudUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }

    // 那年今日状态
    var remoteOnThisDay by remember { mutableStateOf<List<LifeEntry>>(emptyList()) }
    var isOnThisDayExpanded by rememberSaveable { mutableStateOf(true) }
    var hasInitializedStream by rememberSaveable { mutableStateOf(false) }

    // 年度年鉴弹窗状态
    var showAlmanacDialog by remember { mutableStateOf(false) }

    // 全局检索与速记随笔状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 单条档案详情与编辑弹窗状态
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }
    var lightboxPhoto by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(hasInitializedStream) {
        if (!hasInitializedStream) {
            launch {
                when (val res = UpdateChecker.checkForUpdates()) {
                    is UpdateCheckResult.HasUpdate -> cloudUpdate = res.release
                    else -> {}
                }
            }
            launch {
                runCatching {
                    val resp = apiClient.fetchOnThisDay()
                    remoteOnThisDay = resp.entries
                }
            }
            hasInitializedStream = true
        }
    }

    // 合并云端那年今日与本地往年同月同日条目
    val onThisDayEntries = remember(entries, remoteOnThisDay) {
        if (remoteOnThisDay.isNotEmpty()) {
            remoteOnThisDay
        } else {
            val today = LocalDate.now(FormatUtils.BeijingZone)
            val todayMonthDay = String.format("%02d.%02d", today.monthValue, today.dayOfMonth)
            val thisYearPrefix = "${today.year}."
            entries.filter { entry ->
                val formatted = FormatUtils.formatDisplayDate(entry.occurredAt)
                formatted.length >= 10 &&
                    !formatted.startsWith(thisYearPrefix) &&
                    formatted.substring(5, 10) == todayMonthDay
            }
        }
    }

    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

    val availableYears = remember(entries) {
        entries.mapNotNull { entry ->
            val date = entry.occurredAt
            if (date.length >= 4) date.substring(0, 4).toIntOrNull() else null
        }.distinct().sortedDescending()
    }

    val filteredEntries = remember(entries, selectedFilter, selectedYear) {
        var list = entries
        if (selectedYear != null) {
            list = list.filter {
                it.occurredAt.length >= 4 && it.occurredAt.substring(0, 4).toIntOrNull() == selectedYear
            }
        }
        list.filter { entryMatchesFilter(it, selectedFilter) }
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (pendingCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentWarm.copy(alpha = 0.12f))
                                .clickable {
                                    haptics.tapKey()
                                    scope.launch {
                                        ArchiveToastManager.show("正在同步发件箱 ($pendingCount 项待重放)…")
                                        syncManager.pullRemoteData()
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

                    // 全局检索按钮
                    IconButton(
                        onClick = {
                            haptics.tapKey()
                            showSearchDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "检索",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // + 记随笔入口
                    IconButton(
                        onClick = {
                            haptics.tapKey()
                            showQuickNoteDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "记随笔",
                            tint = colors.accentWarm,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 年度年鉴入口按钮
                    IconButton(
                        onClick = {
                            haptics.tapKey()
                            showAlmanacDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = "年度年鉴",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 同步刷新按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                val res = syncManager.pullRemoteData()
                                runCatching {
                                    remoteOnThisDay = apiClient.fetchOnThisDay().entries
                                }
                                isRefreshing = false
                                if (res.isSuccess) {
                                    ArchiveToastManager.show("档案已增量同步")
                                } else {
                                    ArchiveToastManager.show(res.exceptionOrNull()?.message ?: "同步未完成", isError = true)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "同步刷新",
                            tint = if (isRefreshing) colors.accentWarm else colors.inkSecondary,
                            modifier = Modifier.size(18.dp)
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
                            onClick = { UpdateChecker.installApk(context, downloadedFile!!) },
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
                                        versionName = update.versionName
                                    ) { p, _, _ ->
                                        downloadProgress = p
                                    }
                                    isDownloading = false
                                    res.onSuccess { f ->
                                        downloadedFile = f
                                        UpdateChecker.installApk(context, f)
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
                    haptics.tapKey()
                    selectedFilter = it
                }
            )

            // 年份归档切换栏
            YearFilterBar(
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = {
                    haptics.tapKey()
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToDarkroom,
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.archiveSubtle,
                                contentColor = colors.inkPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = SolidColor(colors.borderDivider)
                            )
                        ) {
                            Text("开启随行暗房捕捉", fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
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
                                    haptics.tapKey()
                                    isOnThisDayExpanded = !isOnThisDayExpanded
                                },
                                onSelectEntry = { entry ->
                                    haptics.tapKey()
                                    selectedEntryForDetail = entry
                                },
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }

                    itemsIndexed(
                        items = filteredEntries,
                        key = { _, entry -> entry.id }
                    ) { index, entry ->
                        // 岁月留白刻度：若与上一条时间跨度 >= 14 天，自动插入切线
                        if (index > 0) {
                            val prev = filteredEntries[index - 1]
                            val daysBetween = FormatUtils.calculateDaysBetween(entry.occurredAt, prev.occurredAt)
                            if (daysBetween >= 14) {
                                TemporalGapSeparator(days = daysBetween)
                            }
                        }

                        StreamEntryCard(
                            entry = entry,
                            apiClient = apiClient,
                            onClick = {
                                haptics.tapKey()
                                selectedEntryForDetail = entry
                            },
                            onDelete = {
                                entryToDelete = entry
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

        // 单条记录详情与编辑 Sheet/Dialog
        selectedEntryForDetail?.let { activeEntry ->
            EntryDetailSheetDialog(
                entry = activeEntry,
                apiClient = apiClient,
                onDismiss = { selectedEntryForDetail = null },
                onSaveEdit = { req ->
                    scope.launch {
                        haptics.stampSave()
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

        // 撕除确认弹窗
        entryToDelete?.let { entry ->
            ArchiveConfirmDialog(
                title = "撕除手记？",
                content = "确认从生活时间流中彻底移除《${entry.title.ifBlank { "无题随笔" }}》？",
                confirmText = "确认撕除",
                isDestructive = true,
                onConfirm = {
                    scope.launch {
                        haptics.tearDelete()
                        syncManager.deleteEntry(entry.id)
                        ArchiveToastManager.show("已撕除记录")
                        entryToDelete = null
                        if (selectedEntryForDetail?.id == entry.id) {
                            selectedEntryForDetail = null
                        }
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

@Composable
private fun OnThisDayCard(
    entries: List<LifeEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    PaperCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = colors.accentWarm.copy(alpha = 0.06f),
        borderColor = colors.accentWarm.copy(alpha = 0.4f)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "那年今日 · 岁月回响 (${entries.size})",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.accentWarm
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "展开或收起",
                    tint = colors.accentWarm,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                PaperDivider(color = colors.accentWarm.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                entries.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onSelectEntry(item) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            if (item.content.isNotBlank()) {
                                Text(
                                    text = item.content,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatDisplayDate(item.occurredAt),
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

@Composable
private fun EntryDetailSheetDialog(
    entry: LifeEntry,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaveEdit: (CreateEntryRequest) -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String, String?) -> Unit
) {
    val colors = LooseLeafTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editType by remember(entry) { mutableStateOf(entry.type) }
    var editTitle by remember(entry) { mutableStateOf(entry.title) }
    var editContent by remember(entry) { mutableStateOf(entry.content) }
    var editCityCode by remember(entry) { mutableStateOf(entry.cityCode ?: "") }
    var editCityName by remember(entry) { mutableStateOf(entry.cityName ?: entry.location ?: "") }
    var editOccurredAt by remember(entry) { mutableStateOf(FormatUtils.parseToLocalDateTime(entry.occurredAt)) }
    var editFeatured by remember(entry) { mutableStateOf(entry.featured ?: false) }
    var editAttachments by remember(entry) { mutableStateOf(entry.attachments) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isUploadingPhoto = true
                try {
                    for (uri in uris) {
                        val processed = ImagePipeline.processImage(context, uri)
                        val att = ImagePipeline.uploadImage(apiClient, processed)
                        editAttachments = editAttachments + att
                    }
                } catch (e: Exception) {
                    ArchiveToastManager.show("相纸照片上传失败: ${e.message ?: "网络异常"}")
                } finally {
                    isUploadingPhoto = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 弹窗顶部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEditing) "修订散页档案" else "散页档案阅览",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val targetFeatured = !(entry.featured ?: false)
                            val code = entry.cityCode
                            val cityName = entry.cityName ?: entry.location
                            val req = CreateEntryRequest(
                                type = entry.type,
                                title = entry.title,
                                content = entry.content,
                                occurredAt = entry.occurredAt,
                                location = cityName,
                                cityCode = code,
                                cityName = cityName,
                                projectId = entry.projectId,
                                wishId = entry.wishId,
                                footprintId = entry.footprintId,
                                featured = targetFeatured,
                                attachmentIds = entry.attachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
                            )
                            onSaveEdit(req)
                        }) {
                            Icon(
                                imageVector = if (entry.featured == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (entry.featured == true) "取消精选" else "设为精选",
                                tint = if (entry.featured == true) colors.featuredGold else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "编辑",
                                tint = if (isEditing) colors.accentWarm else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = colors.accentWarm,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                PaperDivider()
                Spacer(modifier = Modifier.height(12.dp))

                if (isEditing) {
                    // 类型切换
                    val typeOptions = listOf(
                        "life" to "日常随笔",
                        "travel" to "山川游历",
                        "project" to "造物日志",
                        "wish" to "心愿印记"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((k, label) in typeOptions) {
                            val selected = editType == k
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.accentWarm else colors.archivePaper)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
                                    .clickable { editType = k }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 11.sp,
                                    color = if (selected) colors.archivePaper else colors.inkSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PaperTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        placeholder = "标题",
                        isUnderline = true,
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PaperTextEditor(
                        value = editContent,
                        onValueChange = { editContent = it },
                        placeholder = "正文手记...",
                        minHeight = 110.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PaperDateTimeSplitRow(
                        selectedDateTime = editOccurredAt,
                        onDateTimeSelected = { editOccurredAt = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PaperCityPickerField(
                        selectedCityCode = editCityCode,
                        selectedCityName = editCityName,
                        onCitySelected = { code, name ->
                            editCityCode = code
                            editCityName = name
                        },
                        placeholder = "选择城市…"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 照片附件管理与增删
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "手账影像相纸 (${editAttachments.size})",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            )
                            if (isUploadingPhoto) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = colors.accentWarm
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "暗房直传中…",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 11.sp,
                                        color = colors.accentWarm
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "+ 补选相纸照片",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 12.sp,
                                        color = colors.accentWarm
                                    )
                                }
                            }
                        }

                        if (editAttachments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (att in editAttachments) {
                                    val fullUrl = apiClient.resolveMediaUrl(att.url)
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                                    ) {
                                        AsyncImage(
                                            model = fullUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(colors.inkPrimary.copy(alpha = 0.7f))
                                                .clickable {
                                                    editAttachments = editAttachments.filter { it.id != att.id }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "撕除此照片",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 精选印记标记
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editFeatured = !editFeatured }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editFeatured,
                            onCheckedChange = { editFeatured = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentWarm,
                                uncheckedColor = colors.inkMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "标记为精选印记（收录于精选特刊）",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TerracottaStampButton(
                        text = "保存修订",
                        enabled = editTitle.isNotBlank() || editContent.isNotBlank(),
                        onClick = {
                            val code = editCityCode.ifBlank { entry.cityCode }
                            val cityName = editCityName.ifBlank { entry.cityName }
                            val req = CreateEntryRequest(
                                type = editType,
                                title = editTitle.ifBlank { "无题随笔" },
                                content = editContent.ifBlank { "无文字记录" },
                                occurredAt = FormatUtils.toBeijingIsoString(editOccurredAt),
                                location = cityName,
                                cityCode = code,
                                cityName = cityName,
                                projectId = entry.projectId,
                                wishId = entry.wishId,
                                footprintId = entry.footprintId,
                                featured = editFeatured,
                                attachmentIds = editAttachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
                            )
                            onSaveEdit(req)
                        }
                    )
                } else {
                    // 阅览模式
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                        if (entry.featured == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.featuredGoldBg)
                                    .border(0.5.dp, colors.featuredGoldBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "★ 精选印记",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 10.sp,
                                    color = colors.featuredGold,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    val detailPlace = listOfNotNull(entry.cityName, entry.location).distinct().filter { it.isNotBlank() }.joinToString(" · ")
                    if (detailPlace.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = detailPlace,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = entry.title,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = entry.content,
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkSecondary
                    )

                    if (entry.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        EntryAttachmentsGrid(
                            attachments = entry.attachments,
                            apiClient = apiClient,
                            onPhotoClick = onPhotoClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlmanacDialog(
    apiClient: APIClient,
    entries: List<LifeEntry>,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val currentYear = remember { LocalDate.now(FormatUtils.BeijingZone).year }
    val earliestYear = remember(entries) {
        val entryYears = entries.mapNotNull { it.occurredAt.take(4).toIntOrNull() }
        if (entryYears.isNotEmpty()) minOf(entryYears.min(), currentYear) else currentYear
    }
    val availableYears = remember(earliestYear, currentYear) {
        (earliestYear..currentYear).toList().reversed()
    }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var summary by remember { mutableStateOf<AlmanacSummaryResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedYear) {
        isLoading = true
        errorMsg = null
        try {
            summary = apiClient.fetchAlmanacSummary(selectedYear)
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$selectedYear 年度生活年鉴",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "五幕生命叙事 · 岁月卷首至柴米油盐",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 年份切换胶囊 (从刚进入的年份至当前年份，支持横滑)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (yr in availableYears) {
                        val isSelected = yr == selectedYear
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.accentWarm else colors.archivePaper)
                                .border(0.5.dp, if (isSelected) colors.accentWarm else colors.borderDivider, RoundedCornerShape(12.dp))
                                .clickable { selectedYear = yr }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$yr 年",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = if (isSelected) colors.archivePaper else colors.inkSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PaperDivider()
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = "获取年鉴失败: $errorMsg",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                } else if (summary != null) {
                    val data = summary!!

                    // 第一幕：岁月卷首
                    AlmanacChapterSection(
                        chapterNumber = "第一幕",
                        title = "岁月卷首 · 书写刻度"
                    ) {
                        Text(
                            text = "全年共落笔 ${data.prologue.totalEntries} 篇散页，累计 ${data.prologue.totalWords} 字，点亮 ${data.prologue.activeDays} 个真实日子。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        if (data.prologue.firstDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "起止刻度: ${data.prologue.firstDate} — ${data.prologue.lastDate}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第二幕：山川行迹
                    AlmanacChapterSection(
                        chapterNumber = "第二幕",
                        title = "山川行迹 · 步履所至 (${data.atlas.totalCities} 城)"
                    ) {
                        if (data.atlas.cities.isEmpty()) {
                            Text("本年度暂无远行足迹记录", fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            Text(
                                text = data.atlas.cities.joinToString(" · ") { it.cityName },
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第三幕：心愿圆满
                    AlmanacChapterSection(
                        chapterNumber = "第三幕",
                        title = "心愿圆满 · 念念回响"
                    ) {
                        Text(
                            text = "圆满达成 ${data.wishes.fulfilledCount} 项心愿 · 仍有 ${data.wishes.wantingCount} 项向往中",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (data.wishes.totalActual > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "心愿兑现投入: ${FormatUtils.centsToYuanString(data.wishes.totalActual)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第四幕：浮光掠影
                    AlmanacChapterSection(
                        chapterNumber = "第四幕",
                        title = "浮光掠影 · 四季相纸 (${data.highlights.photos.size} 帧)"
                    ) {
                        val seasonItems = listOfNotNull(
                            data.highlights.spring?.let { "春 · ${it.title}" },
                            data.highlights.summer?.let { "夏 · ${it.title}" },
                            data.highlights.autumn?.let { "秋 · ${it.title}" },
                            data.highlights.winter?.let { "冬 · ${it.title}" }
                        )
                        if (seasonItems.isEmpty()) {
                            Text("四季流转，静待更多影像沉淀", fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            seasonItems.forEach { line ->
                                Text(
                                    text = line,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 第五幕：柴米油盐
                    AlmanacChapterSection(
                        chapterNumber = "第五幕",
                        title = "柴米油盐 · 生活光谱"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "年度总支出: ${FormatUtils.centsToYuanString(data.finances.totalExpense)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                            Text(
                                text = "年度总收入: ${FormatUtils.centsToYuanString(data.finances.totalIncome)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentMoss
                            )
                        }
                        if (data.finances.categories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            data.finances.categories.take(4).forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, fontSize = 12.sp, color = colors.inkSecondary)
                                    Text(
                                        FormatUtils.centsToYuanString(cat.amount),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
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
private fun AlmanacChapterSection(
    chapterNumber: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archivePaper)
            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chapterNumber,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                color = colors.accentWarm
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.inkPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StreamFilterBar(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "all" to "全部印记",
        "daily" to "日常随笔",
        "travel" to "山川游历",
        "project" to "造物日志",
        "wish" to "心愿印记"
    )
    val colors = LooseLeafTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((key, label) in filters) {
            val isSelected = key == selectedFilter
            val shape = RoundedCornerShape(14.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSelected) colors.inkPrimary else colors.archiveSubtle, shape)
                    .border(0.5.dp, if (isSelected) colors.inkPrimary else colors.borderDivider, shape)
                    .clickable { onFilterSelected(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.archivePaper else colors.inkSecondary
                )
            }
        }
    }
}

/**
 * 自然下渗分类过滤：遵循 docs/README.md §三.8 规范，带城市属地归入游历，带项目归入造物，带心愿归入心愿
 */
private fun entryMatchesFilter(entry: LifeEntry, filter: String): Boolean {
    if (filter == "all") return true
    val t = entry.type.lowercase()
    if (filter == "travel") {
        return t == "travel" || !entry.cityCode.isNullOrBlank()
    }
    if (filter == "project") {
        return t == "project" || (entry.projectId != null && entry.projectId > 0)
    }
    if (filter == "wish") {
        return t == "wish" || (entry.wishId != null && entry.wishId > 0)
    }
    if (filter == "daily" || filter == "life") {
        return (t == "life" || t == "daily") && entry.cityCode.isNullOrBlank() && (entry.projectId == null || entry.projectId <= 0) && (entry.wishId == null || entry.wishId <= 0)
    }
    return t == filter
}

@Composable
private fun YearFilterBar(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    if (availableYears.isEmpty()) return
    val colors = LooseLeafTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "年份",
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = 11.sp,
            color = colors.inkMuted,
            modifier = Modifier.padding(end = 4.dp)
        )

        // 全部年份
        val isAll = selectedYear == null
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isAll) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle)
                .border(0.5.dp, if (isAll) colors.accentWarm else colors.borderDivider, RoundedCornerShape(10.dp))
                .clickable { onYearSelected(null) }
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "全集",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 11.sp,
                color = if (isAll) colors.accentWarm else colors.inkSecondary
            )
        }

        for (year in availableYears) {
            val isSelected = selectedYear == year
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle)
                    .border(0.5.dp, if (isSelected) colors.accentWarm else colors.borderDivider, RoundedCornerShape(10.dp))
                    .clickable { onYearSelected(year) }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${year}年",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.sp,
                    color = if (isSelected) colors.accentWarm else colors.inkSecondary
                )
            }
        }
    }
}

@Composable
private fun LifeActivityHeatmap(
    entries: List<LifeEntry>,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val dateCountMap = remember(entries) {
        entries.groupingBy {
            val d = it.occurredAt
            if (d.length >= 10) d.substring(0, 10).replace("-", ".") else ""
        }.eachCount()
    }

    val weeksCount = 52
    val calWeeks = remember(weeksCount) {
        val today = LocalDate.now(FormatUtils.BeijingZone)
        val list = mutableListOf<List<Pair<String, Boolean>>>()
        for (w in (0 until weeksCount).reversed()) {
            val days = mutableListOf<Pair<String, Boolean>>()
            for (d in 0 until 7) {
                val daysAgo = w * 7 + (6 - d)
                val targetDate = today.minusDays(daysAgo.toLong())
                val key = String.format("%04d.%02d.%02d", targetDate.year, targetDate.monthValue, targetDate.dayOfMonth)
                days.add(key to (daysAgo == 0))
            }
            list.add(days)
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.archiveSubtle.copy(alpha = 0.5f))
            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = colors.accentMoss,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "时光生活刻度",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkPrimary
                )
                Text(
                    text = " · 共 ${entries.size} 篇手记",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }

            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isExpanded) "收起刻度" else "展开网格",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 11.sp,
                    color = colors.inkSecondary
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                for (week in calWeeks) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 1.5.dp)
                    ) {
                        for ((dateKey, isToday) in week) {
                            val count = dateCountMap[dateKey] ?: 0
                            val cellColor = when {
                                count >= 3 -> colors.accentWarm
                                count == 2 -> colors.accentWarm.copy(alpha = 0.65f)
                                count == 1 -> colors.accentWarm.copy(alpha = 0.35f)
                                else -> colors.archivePaper
                            }
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(cellColor)
                                    .border(
                                        width = if (isToday) 0.8.dp else 0.3.dp,
                                        color = if (isToday) colors.accentWarm else colors.borderDivider.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                                    .clickable {
                                        ArchiveToastManager.show("$dateKey: ${if (count > 0) "$count 篇手记" else "静默留白"}")
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryAttachmentsGrid(
    attachments: List<Attachment>,
    apiClient: APIClient,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(modifier = modifier) {
        val count = attachments.size
        when (count) {
            1 -> {
                val att = attachments.first()
                val fullUrl = apiClient.resolveMediaUrl(att.url)
                val meta = att.originalFilename
                FilmFrame(
                    imageUrl = fullUrl,
                    metadataText = meta,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier.padding(vertical = 4.dp),
                    onClick = { onPhotoClick(fullUrl, meta) }
                )
            }
            2, 4 -> {
                attachments.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (att in row) {
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            FilmFrame(
                                imageUrl = fullUrl,
                                metadataText = null,
                                aspectRatio = 1f,
                                modifier = Modifier.weight(1f),
                                onClick = { onPhotoClick(fullUrl, meta) }
                            )
                        }
                    }
                }
            }
            else -> {
                attachments.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (att in row) {
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = att.originalFilename
                            FilmFrame(
                                imageUrl = fullUrl,
                                metadataText = null,
                                aspectRatio = 1f,
                                modifier = Modifier.weight(1f),
                                onClick = { onPhotoClick(fullUrl, meta) }
                            )
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamEntryCard(
    entry: LifeEntry,
    apiClient: APIClient,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val cardBg = when (entry.type) {
        "travel" -> colors.accentWarm.copy(alpha = 0.04f)
        "project" -> colors.accentMoss.copy(alpha = 0.04f)
        "wish" -> colors.archivePaper
        else -> colors.archiveSubtle
    }
    val cardBorder = when (entry.type) {
        "travel" -> colors.accentWarm.copy(alpha = 0.35f)
        "project" -> colors.accentMoss.copy(alpha = 0.35f)
        "wish" -> colors.accentWarm.copy(alpha = 0.25f)
        else -> colors.borderDivider
    }

    PaperCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = cardBg,
        borderColor = cardBorder
    ) {
        Column {
            // 头部：时间戳、地点与操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        softWrap = false
                    )

                    val placeString = listOfNotNull(entry.cityName, entry.location).distinct().filter { it.isNotBlank() }.joinToString(" · ")
                    if (placeString.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = if (entry.type == "travel") colors.accentWarm else colors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = placeString,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (entry.type == "travel") colors.accentWarm else colors.inkSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.syncStatus != "SYNCED") {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accentWarm)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除记录",
                            tint = colors.inkMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 标题
            Text(
                text = entry.title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp,
                color = colors.inkPrimary
            )

            // 正文内容
            if (entry.content.isNotBlank() && entry.content != "无文字记录") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.content,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = colors.inkSecondary
                )
            }

            // 关联胶片相纸附件渲染 (多图网格与触击放大灯箱)
            if (entry.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                EntryAttachmentsGrid(
                    attachments = entry.attachments,
                    apiClient = apiClient,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSelectEntry: (LifeEntry) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<com.looseleaf.app.model.GlobalSearchResponse?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var viewingDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    viewingDetail?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { viewingDetail = null },
            containerColor = colors.archivePaper,
            title = {
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Text(
                    text = content,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewingDetail = null }) {
                    Text("合上", color = colors.accentWarm)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "全库检索生活记忆",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                PaperTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        searchJob?.cancel()
                        if (q.trim().length >= 2) {
                            searchJob = scope.launch {
                                kotlinx.coroutines.delay(280)
                                isSearching = true
                                runCatching {
                                    searchResult = apiClient.searchGlobalArchive(q)
                                }
                                isSearching = false
                            }
                        } else {
                            searchResult = null
                            isSearching = false
                        }
                    },
                    placeholder = "搜索……",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.accentWarm)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                val res = searchResult
                if (res == null || (res.entries.isEmpty() && res.footprints.isEmpty() && res.wishes.isEmpty() && res.projects.isEmpty())) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "输入关键词检索所有归册记忆" else "未找到匹配的生活碎片",
                            fontSize = 12.sp,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            color = colors.inkMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (res.entries.isNotEmpty()) {
                            item {
                                Text("生活印记 (${res.entries.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.entries, key = { "e_${it.id}" }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            onSelectEntry(entry)
                                            onDismiss()
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title.ifBlank { "无题随笔" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.inkPrimary)
                                        if (entry.content.isNotBlank()) {
                                            Text(entry.content, fontSize = 11.sp, color = colors.inkSecondary, maxLines = 1)
                                        }
                                    }
                                    Text(FormatUtils.formatDisplayDate(entry.occurredAt), fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.footprints.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("山川足迹 (${res.footprints.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.footprints, key = { "f_${it.id}" }) { foot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "山川足迹 · ${foot.cityName}" to listOfNotNull(
                                                foot.title?.takeIf { it.isNotBlank() },
                                                "到访时刻: ${FormatUtils.formatDisplayDate(foot.visitedAt)}",
                                                foot.note?.takeIf { it.isNotBlank() }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${foot.cityName} · ${foot.title ?: "足迹"}", fontSize = 12.sp, color = colors.inkPrimary)
                                    Text(foot.visitedAt?.take(10) ?: "", fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }

                        if (res.wishes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("心愿清单 (${res.wishes.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.wishes, key = { "w_${it.id}" }) { wish ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "心愿清单 · ${wish.title}" to listOfNotNull(
                                                "心愿类别: ${if (wish.kind == "travel") "山川旅程" else if (wish.kind == "milestone") "里程碑" else "好物"}",
                                                if (wish.budget > 0) "意向预算: ${FormatUtils.centsToYuanString(wish.budget)}" else null,
                                                wish.cityName?.takeIf { it.isNotBlank() }?.let { "目的地: $it" },
                                                wish.note?.takeIf { it.isNotBlank() }?.let { "手记备忘: $it" }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(wish.title, fontSize = 12.sp, color = colors.inkPrimary)
                                    if (wish.budget > 0) {
                                        Text(FormatUtils.centsToYuanString(wish.budget), fontSize = 11.sp, color = colors.accentWarm)
                                    }
                                }
                            }
                        }

                        if (res.projects.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("造物项目 (${res.projects.size})", fontSize = 11.sp, color = colors.inkMuted)
                            }
                            items(res.projects, key = { "p_${it.id}" }) { proj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle)
                                        .clickable {
                                            viewingDetail = "造物项目 · ${proj.title}" to listOfNotNull(
                                                proj.subtitle?.takeIf { it.isNotBlank() }?.let { "副标题: $it" },
                                                proj.status?.takeIf { it.isNotBlank() }?.let { "状态: $it" },
                                                proj.description?.takeIf { it.isNotBlank() }?.let { "描述: $it" },
                                                proj.story?.takeIf { it.isNotBlank() }?.let { "造物故事: $it" },
                                                proj.githubUrl?.takeIf { it.isNotBlank() }?.let { "源码仓库: $it" }
                                            ).joinToString("\n\n")
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(proj.title, fontSize = 12.sp, color = colors.inkPrimary)
                                    Text(proj.status ?: "", fontSize = 10.sp, color = colors.inkMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AttachedPhotoItem(
    val previewUri: String,
    val processed: ProcessedImageResult,
    val savedFile: File
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteSheet(
    onDismiss: () -> Unit,
    syncManager: SyncManager,
    apiClient: APIClient,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val haptics = remember { HapticsEngine.getInstance(context) }

    val projects by syncManager.projectsFlow.collectAsState()
    val footprints by syncManager.footprintsFlow.collectAsState()

    var noteType by remember { mutableStateOf("life") } // "life", "travel", "project", "wish"

    // 日常与通用手记字段
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var noteCityCode by remember { mutableStateOf<String?>(null) }
    var noteCityName by remember { mutableStateOf<String?>(null) }
    var noteLocation by remember { mutableStateOf("") }
    var isLocating by remember { mutableStateOf(false) }
    var noteFeatured by remember { mutableStateOf(false) }
    var noteOccurredAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone)) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 游历专属字段
    var selectedFootprintId by remember { mutableStateOf<Long?>(null) }

    // 造物专属字段
    var projectMode by remember { mutableStateOf("entry") } // "entry" (进展手记) vs "new" (立项新造物)
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var projTitle by remember { mutableStateOf("") }
    var projSubtitle by remember { mutableStateOf("") }
    var projCategory by remember { mutableStateOf("软件") }
    var projStatus by remember { mutableStateOf("Active") }
    var projDescription by remember { mutableStateOf("") }
    var projStory by remember { mutableStateOf("") }
    var projGithubUrl by remember { mutableStateOf("") }

    // 心愿专属字段
    var wishKind by remember { mutableStateOf("item") } // "item", "travel", "milestone"
    var wishTitle by remember { mutableStateOf("") }
    var wishBudgetYuan by remember { mutableStateOf("") }
    var wishSpec by remember { mutableStateOf("") }
    var wishTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var wishNote by remember { mutableStateOf("") }

    // 照片装裱状态
    var attachedPhotos by remember { mutableStateOf<List<AttachedPhotoItem>>(emptyList()) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            selectedProjectId = projects.first().id
        }
    }

    // 定位获取与逆地理反查
    val fetchCurrentLocation: () -> Unit = {
        scope.launch {
            isLocating = true
            val res = LocationHelper.getCurrentLocation(context)
            if (res != null) {
                if (noteCityCode.isNullOrBlank() && !res.cityCode.isNullOrBlank()) {
                    noteCityCode = res.cityCode
                    noteCityName = res.cityName
                }
                if (noteLocation.isBlank() && res.locationName.isNotBlank()) {
                    noteLocation = res.locationName
                }
            }
            isLocating = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchCurrentLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // 相机直拍
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            scope.launch {
                isProcessingPhoto = true
                try {
                    val uri = Uri.fromFile(file)
                    val processed = withContext(Dispatchers.IO) {
                        ImagePipeline.processImage(context, uri)
                    }
                    val savedFile = withContext(Dispatchers.IO) {
                        syncManager.saveCompressedImageToOutboxDir(processed.compressedBytes)
                    }
                    val fileUri = "file://${savedFile.absolutePath}"
                    val newPhoto = AttachedPhotoItem(previewUri = fileUri, processed = processed, savedFile = savedFile)
                    attachedPhotos = (attachedPhotos + newPhoto).take(9)

                    // EXIF 自动回溯
                    if (noteTitle.isBlank()) {
                        noteTitle = "现场快门 · " + (processed.exif.model ?: "胶片相纸")
                    }
                    if (noteCityCode.isNullOrBlank()) {
                        val inferredCode = processed.exif.inferredCityCode
                        val inferredCity = processed.exif.inferredCityName ?: inferredCode?.let { CityDictionary.cityNameForCode(it) }
                        if (!inferredCode.isNullOrBlank()) {
                            noteCityCode = inferredCode
                            noteCityName = inferredCity
                        }
                    }
                    if (noteLocation.isBlank()) {
                        val inferredCity = processed.exif.inferredCityName ?: processed.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                        if (!inferredCity.isNullOrBlank()) {
                            noteLocation = inferredCity
                        }
                    }
                    processed.exif.dateTimeOriginal?.let { raw ->
                        runCatching { noteOccurredAt = FormatUtils.parseToLocalDateTime(raw) }
                    }
                    haptics.shutterRelease()
                    AudioEngine.playShutter()
                    ArchiveToastManager.show("相纸已装裱")
                } catch (e: Exception) {
                    ArchiveToastManager.show("快门相纸装裱失败: ${e.message}", isError = true)
                } finally {
                    isProcessingPhoto = false
                    runCatching { file.delete() }
                    pendingCameraFile = null
                }
            }
        } else {
            runCatching { file?.delete() }
            pendingCameraFile = null
        }
    }

    // 相册加选
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessingPhoto = true
                try {
                    val remainingSlots = 9 - attachedPhotos.size
                    val targetUris = uris.take(remainingSlots)
                    val newItems = withContext(Dispatchers.IO) {
                        targetUris.map { uri ->
                            async {
                                val res = ImagePipeline.processImage(context, uri)
                                val saved = syncManager.saveCompressedImageToOutboxDir(res.compressedBytes)
                                AttachedPhotoItem(previewUri = "file://${saved.absolutePath}", processed = res, savedFile = saved)
                            }
                        }.awaitAll()
                    }
                    if (newItems.isNotEmpty()) {
                        attachedPhotos = (attachedPhotos + newItems).take(9)
                        val first = newItems.first()
                        if (noteTitle.isBlank()) {
                            noteTitle = "现场记录 · " + (first.processed.exif.model ?: "相纸影像")
                        }
                        if (noteCityCode.isNullOrBlank()) {
                            val code = first.processed.exif.inferredCityCode
                            val name = first.processed.exif.inferredCityName ?: code?.let { CityDictionary.cityNameForCode(it) }
                            if (!code.isNullOrBlank()) {
                                noteCityCode = code
                                noteCityName = name
                            }
                        }
                        if (noteLocation.isBlank()) {
                            val city = first.processed.exif.inferredCityName ?: first.processed.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!city.isNullOrBlank()) {
                                noteLocation = city
                            }
                        }
                        first.processed.exif.dateTimeOriginal?.let { raw ->
                            runCatching { noteOccurredAt = FormatUtils.parseToLocalDateTime(raw) }
                        }
                        haptics.snapConfirm()
                        ArchiveToastManager.show("已装裱 ${newItems.size} 张相纸")
                    }
                } catch (e: Exception) {
                    ArchiveToastManager.show("相纸装裱失败: ${e.message}", isError = true)
                } finally {
                    isProcessingPhoto = false
                }
            }
        }
    }

    val canSave = when (noteType) {
        "life" -> (noteTitle.isNotBlank() || noteContent.isNotBlank() || attachedPhotos.isNotEmpty()) && !isSubmitting
        "travel" -> noteContent.isNotBlank() && (!noteCityCode.isNullOrBlank() || !noteCityName.isNullOrBlank() || noteLocation.isNotBlank()) && !isSubmitting
        "project" -> if (projectMode == "entry") {
            selectedProjectId != null && noteContent.isNotBlank() && !isSubmitting
        } else {
            projTitle.isNotBlank() && projDescription.isNotBlank() && !isSubmitting
        }
        "wish" -> wishTitle.isNotBlank() && !isSubmitting
        else -> false
    }

    val headerTitle = when (noteType) {
        "life" -> "《新散页》"
        "travel" -> "《游历手记》"
        "project" -> if (projectMode == "entry") "《造物手记》" else "《立项新造物》"
        "wish" -> "《封存心愿》"
        else -> "《散页》"
    }

    val submitText = if (isSubmitting) "归册中…" else when (noteType) {
        "life" -> "存入散页"
        "travel" -> "归册游记"
        "project" -> if (projectMode == "entry") "存入手记" else "确认立项"
        "wish" -> "封存心愿"
        else -> "归册"
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 顶栏：取消 / 标题 / 提交
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
                    text = headerTitle,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )

                TextButton(
                    enabled = canSave,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            haptics.stampSave()
                            AudioEngine.playShutter()

                            try {
                                val finalCode = noteCityCode ?: CityDictionary.codeForLocation(noteLocation)
                                val finalCityName = noteCityName ?: finalCode?.let { CityDictionary.cityNameForCode(it) }

                                when (noteType) {
                                    "life" -> {
                                        val effectiveTitle = noteTitle.ifBlank {
                                            if (attachedPhotos.isNotEmpty()) "现场快门 · " + (attachedPhotos.first().processed.exif.model ?: "胶片相纸")
                                            else "无题随笔"
                                        }
                                        if (attachedPhotos.isNotEmpty()) {
                                            val primary = attachedPhotos.first()
                                            val extraFiles = attachedPhotos.drop(1).map {
                                                OfflineMediaFileItem(
                                                    filePath = it.savedFile.absolutePath,
                                                    mimeType = it.processed.mimeType,
                                                    width = it.processed.width,
                                                    height = it.processed.height
                                                )
                                            }
                                            val req = CreateEntryRequest(
                                                type = "life",
                                                title = effectiveTitle,
                                                content = noteContent.trim(),
                                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                cityCode = finalCode,
                                                cityName = finalCityName,
                                                featured = noteFeatured
                                            )
                                            syncManager.createMediaEntryOffline(
                                                localImageFilePath = primary.savedFile.absolutePath,
                                                mimeType = primary.processed.mimeType,
                                                width = primary.processed.width,
                                                height = primary.processed.height,
                                                req = req,
                                                additionalMediaFiles = extraFiles
                                            )
                                            ArchiveToastManager.show("图文散页已装裱归册")
                                        } else {
                                            syncManager.createEntryOptimistic(
                                                type = "life",
                                                title = effectiveTitle,
                                                content = noteContent.trim(),
                                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                cityCode = finalCode,
                                                cityName = finalCityName,
                                                featured = noteFeatured
                                            )
                                            ArchiveToastManager.show("随笔已归册")
                                        }
                                    }

                                    "travel" -> {
                                        val effectiveTitle = noteTitle.ifBlank {
                                            val dest = finalCityName ?: "远方"
                                            "游历 · $dest"
                                        }
                                        if (attachedPhotos.isNotEmpty()) {
                                            val primary = attachedPhotos.first()
                                            val extraFiles = attachedPhotos.drop(1).map {
                                                OfflineMediaFileItem(
                                                    filePath = it.savedFile.absolutePath,
                                                    mimeType = it.processed.mimeType,
                                                    width = it.processed.width,
                                                    height = it.processed.height
                                                )
                                            }
                                            val req = CreateEntryRequest(
                                                type = "travel",
                                                title = effectiveTitle,
                                                content = noteContent.trim(),
                                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                cityCode = finalCode,
                                                cityName = finalCityName,
                                                featured = noteFeatured,
                                                footprintId = selectedFootprintId
                                            )
                                            syncManager.createMediaEntryOffline(
                                                localImageFilePath = primary.savedFile.absolutePath,
                                                mimeType = primary.processed.mimeType,
                                                width = primary.processed.width,
                                                height = primary.processed.height,
                                                req = req,
                                                additionalMediaFiles = extraFiles
                                            )
                                            ArchiveToastManager.show("游历图文手记已归册")
                                        } else {
                                            syncManager.createEntryOptimistic(
                                                type = "travel",
                                                title = effectiveTitle,
                                                content = noteContent.trim(),
                                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                cityCode = finalCode,
                                                cityName = finalCityName,
                                                featured = noteFeatured,
                                                footprintId = selectedFootprintId
                                            )
                                            ArchiveToastManager.show("游历手记已归册")
                                        }
                                    }

                                    "project" -> {
                                        if (projectMode == "entry") {
                                            val effectiveTitle = noteTitle.ifBlank {
                                                val proj = projects.firstOrNull { it.id == selectedProjectId }
                                                if (proj != null) "${proj.title} · 进展记录" else "造物手记"
                                            }
                                            if (attachedPhotos.isNotEmpty()) {
                                                val primary = attachedPhotos.first()
                                                val extraFiles = attachedPhotos.drop(1).map {
                                                    OfflineMediaFileItem(
                                                        filePath = it.savedFile.absolutePath,
                                                        mimeType = it.processed.mimeType,
                                                        width = it.processed.width,
                                                        height = it.processed.height
                                                    )
                                                }
                                                val req = CreateEntryRequest(
                                                    type = "project",
                                                    title = effectiveTitle,
                                                    content = noteContent.trim(),
                                                    occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                    location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                    cityCode = finalCode,
                                                    cityName = finalCityName,
                                                    projectId = selectedProjectId
                                                )
                                                syncManager.createMediaEntryOffline(
                                                    localImageFilePath = primary.savedFile.absolutePath,
                                                    mimeType = primary.processed.mimeType,
                                                    width = primary.processed.width,
                                                    height = primary.processed.height,
                                                    req = req,
                                                    additionalMediaFiles = extraFiles
                                                )
                                                ArchiveToastManager.show("造物进展手记已装裱归册")
                                            } else {
                                                syncManager.createEntryOptimistic(
                                                    type = "project",
                                                    title = effectiveTitle,
                                                    content = noteContent.trim(),
                                                    occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                                    location = noteLocation.trim().takeIf { it.isNotBlank() },
                                                    cityCode = finalCode,
                                                    cityName = finalCityName,
                                                    projectId = selectedProjectId
                                                )
                                                ArchiveToastManager.show("造物进展已归册")
                                            }
                                        } else {
                                            val req = CreateProjectRequest(
                                                title = projTitle.trim(),
                                                subtitle = projSubtitle.trim().takeIf { it.isNotBlank() },
                                                description = projDescription.trim(),
                                                story = projStory.trim().takeIf { it.isNotBlank() },
                                                category = projCategory.trim().takeIf { it.isNotBlank() },
                                                status = projStatus,
                                                githubUrl = projGithubUrl.trim().takeIf { it.isNotBlank() }
                                            )
                                            withContext(Dispatchers.IO) {
                                                apiClient.createProject(req)
                                                syncManager.pullRemoteData()
                                            }
                                            ArchiveToastManager.show("新造物「${projTitle.trim()}」已成功立项")
                                        }
                                    }

                                    "wish" -> {
                                        val budgetCents = (wishBudgetYuan.toDoubleOrNull()?.let { (it * 100).toLong() }) ?: 0L
                                        val targetIso = wishTargetDate?.atStartOfDay()?.let { FormatUtils.toBeijingIsoString(it) }
                                        val req = CreateWishRequest(
                                            title = wishTitle.trim(),
                                            kind = wishKind,
                                            note = wishNote.trim().takeIf { it.isNotBlank() },
                                            spec = wishSpec.trim().takeIf { it.isNotBlank() && wishKind == "item" },
                                            cityCode = if (wishKind == "travel") finalCode else null,
                                            cityName = if (wishKind == "travel") finalCityName else null,
                                            budget = budgetCents,
                                            targetDate = targetIso
                                        )
                                        withContext(Dispatchers.IO) {
                                            apiClient.createWish(req)
                                            syncManager.pullRemoteData()
                                        }
                                        ArchiveToastManager.show("心愿「${wishTitle.trim()}」已封存入册")
                                    }
                                }
                                onSuccess()
                            } catch (e: Exception) {
                                ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = submitText,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (canSave) colors.accentWarm else colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 四种生活形态胶囊切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("life" to "日常", "travel" to "游历", "project" to "造物", "wish" to "心愿").forEach { (k, label) ->
                    PaperFilterPill(
                        selected = noteType == k,
                        onClick = { noteType = k },
                        label = label
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 相纸装裱槽位辅助 Composable
            @Composable
            fun PhotoMountingSection() {
                if (attachedPhotos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已装裱相纸 (${attachedPhotos.size}/9)",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                        if (attachedPhotos.size < 9) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                        val f = File(dir, "snap_${System.currentTimeMillis()}.jpg")
                                        pendingCameraFile = f
                                        val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        cameraLauncher.launch(u)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CameraAlt,
                                        contentDescription = null,
                                        tint = colors.accentWarm,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("快门直拍", fontSize = 11.5.sp, color = colors.accentWarm)
                                }
                                TextButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = null,
                                        tint = colors.accentWarm,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("装裱相纸", fontSize = 11.5.sp, color = colors.accentWarm)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedPhotos.forEachIndexed { idx, photo ->
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            ) {
                                AsyncImage(
                                    model = photo.previewUri,
                                    contentDescription = "装裱相纸",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.inkPrimary.copy(alpha = 0.65f))
                                        .clickable {
                                            attachedPhotos = attachedPhotos.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "移除相纸",
                                        tint = colors.archivePaper,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp)
                                        .background(colors.inkPrimary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 9.sp,
                                        color = colors.archivePaper
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archiveSubtle.copy(alpha = 0.4f))
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProcessingPhoto) "相纸显影处理中…" else "装裱现场相纸 (图文一体)",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                    val f = File(dir, "snap_${System.currentTimeMillis()}.jpg")
                                    pendingCameraFile = f
                                    val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                    cameraLauncher.launch(u)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.archivePaper,
                                    contentColor = colors.inkPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(colors.borderDivider)),
                                shape = RoundedCornerShape(3.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    tint = colors.accentWarm,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("快门直拍", fontSize = 11.5.sp)
                            }

                            Button(
                                onClick = {
                                    galleryLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.archivePaper,
                                    contentColor = colors.inkPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(colors.borderDivider)),
                                shape = RoundedCornerShape(3.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    tint = colors.accentWarm,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("装裱相纸", fontSize = 11.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ==================== 1. 日常 (life) ====================
            if (noteType == "life") {
                androidx.compose.foundation.text.BasicTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
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
                        if (noteTitle.isEmpty()) {
                            Text(
                                text = "给此刻起个名字…",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.inkMuted.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 220.dp),
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteContent.isEmpty()) {
                            Text(
                                text = "提笔写下当下的心绪、现场气味或片刻偶遇…",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.inkMuted.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                PhotoMountingSection()

                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "发生时刻与时空锚点",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                PaperDateTimeSplitRow(
                    selectedDateTime = noteOccurredAt,
                    onDateTimeSelected = { noteOccurredAt = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "空间驻留地点",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                PaperCityPickerField(
                    selectedCityCode = noteCityCode,
                    selectedCityName = noteCityName,
                    onCitySelected = { code, name ->
                        noteCityCode = code
                        noteCityName = name
                    },
                    placeholder = if (isLocating) "正在定位…" else "选择城市…"
                )

                Spacer(modifier = Modifier.height(8.dp))

                PaperLocationField(
                    value = noteLocation,
                    onValueChange = { noteLocation = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { noteFeatured = !noteFeatured }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (noteFeatured) colors.accentWarm else colors.archiveSubtle)
                            .border(
                                0.8.dp,
                                if (noteFeatured) colors.accentWarm else colors.borderDivider,
                                RoundedCornerShape(3.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (noteFeatured) {
                            Text("✓", fontSize = 10.sp, color = colors.archivePaper, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "标记为精选印记（收录于精选特刊）",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.5.sp,
                        color = if (noteFeatured) colors.accentWarm else colors.inkPrimary
                    )
                }
            }

            // ==================== 2. 游历 (travel) ====================
            else if (noteType == "travel") {
                // 游历专属顶置卡片：到访城市必选高亮 + 关联足迹
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.archiveSubtle.copy(alpha = 0.5f))
                        .border(0.8.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "到访城市与旅程足迹 *",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentWarm
                        )

                        PaperCityPickerField(
                            selectedCityCode = noteCityCode,
                            selectedCityName = noteCityName,
                            onCitySelected = { code, name ->
                                noteCityCode = code
                                noteCityName = name
                            },
                            placeholder = if (isLocating) "正在定位到访城市…" else "选择到访城市 (必选) *"
                        )

                        PaperLocationField(
                            value = noteLocation,
                            onValueChange = { noteLocation = it },
                            placeholder = "具体景点或街巷注记 (选填)"
                        )

                        if (footprints.isNotEmpty()) {
                            val footprintOptions = listOf(null to "-- 自动聚合至同城近期旅程 / 开启新旅程 --") +
                                footprints.map { fp ->
                                    fp.id to (fp.title?.ifBlank { "${fp.cityName}之行" } ?: "${fp.cityName}之行")
                                }
                            PaperSelectField(
                                label = "归属旅程足迹",
                                selectedValue = selectedFootprintId,
                                options = footprintOptions,
                                onSelected = { selectedFootprintId = it },
                                placeholder = "-- 自动聚合至同城近期旅程 --"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteTitle.isEmpty()) {
                            Text(
                                text = "游历见闻标题…",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.inkMuted.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 220.dp),
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteContent.isEmpty()) {
                            Text(
                                text = "写下路上的风景、异乡的气味或旅途偶遇…",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.inkMuted.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                PhotoMountingSection()

                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "到访时刻",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                PaperDateTimeSplitRow(
                    selectedDateTime = noteOccurredAt,
                    onDateTimeSelected = { noteOccurredAt = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { noteFeatured = !noteFeatured }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (noteFeatured) colors.accentWarm else colors.archiveSubtle)
                            .border(
                                0.8.dp,
                                if (noteFeatured) colors.accentWarm else colors.borderDivider,
                                RoundedCornerShape(3.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (noteFeatured) {
                            Text("✓", fontSize = 10.sp, color = colors.archivePaper, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "标记为精选游记",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.5.sp,
                        color = if (noteFeatured) colors.accentWarm else colors.inkPrimary
                    )
                }
            }

            // ==================== 3. 造物 (project) ====================
            else if (noteType == "project") {
                // 子模式切换：撰写已有进展手记 vs 立项新造物
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperFilterPill(
                        selected = projectMode == "entry",
                        onClick = { projectMode = "entry" },
                        label = "撰写已有进展手记"
                    )
                    PaperFilterPill(
                        selected = projectMode == "new",
                        onClick = { projectMode = "new" },
                        label = "＋ 立项新造物"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (projectMode == "entry") {
                    if (projects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.archiveSubtle)
                                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(6.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "暂无活跃造物项目",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkSecondary
                                )
                                Button(
                                    onClick = { projectMode = "new" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentWarm,
                                        contentColor = colors.archivePaper
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("立即立项新造物", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        PaperSelectField(
                            label = "选择关联造物项目 *",
                            selectedValue = selectedProjectId,
                            options = projects.map { it.id to "${it.title} (${it.status ?: "活跃"})" },
                            onSelected = { selectedProjectId = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        androidx.compose.foundation.text.BasicTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.accentWarm),
                            decorationBox = { innerTextField ->
                                if (noteTitle.isEmpty()) {
                                    Text(
                                        text = "手记标题…",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = colors.inkMuted.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        androidx.compose.foundation.text.BasicTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 220.dp),
                            textStyle = TextStyle(
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.accentWarm),
                            decorationBox = { innerTextField ->
                                if (noteContent.isEmpty()) {
                                    Text(
                                        text = "记录今日造物进展、代码提交、设计突破或心得…",
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        color = colors.inkMuted.copy(alpha = 0.45f)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        PhotoMountingSection()

                        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "记录时间",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PaperDateTimeSplitRow(
                            selectedDateTime = noteOccurredAt,
                            onDateTimeSelected = { noteOccurredAt = it }
                        )
                    }
                } else {
                    // 立项新造物表单
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PaperTextField(
                            value = projTitle,
                            onValueChange = { projTitle = it },
                            label = "造物名称 *",
                            placeholder = "例如：Loose-Leaf 散页",
                            singleLine = true
                        )

                        PaperTextField(
                            value = projSubtitle,
                            onValueChange = { projSubtitle = it },
                            label = "副标题",
                            placeholder = "例如：极简纸墨生活自留地",
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaperSelectField(
                                label = "造物分类",
                                selectedValue = projCategory,
                                options = listOf(
                                    "软件" to "软件",
                                    "硬件" to "硬件",
                                    "设计" to "设计",
                                    "手作" to "手作",
                                    "写作" to "写作",
                                    "生活" to "生活"
                                ),
                                onSelected = { projCategory = it },
                                modifier = Modifier.weight(1f)
                            )

                            PaperSelectField(
                                label = "运行状态",
                                selectedValue = projStatus,
                                options = listOf(
                                    "Active" to "活跃",
                                    "Maintained" to "维护",
                                    "WIP" to "构思试验",
                                    "Archived" to "归档"
                                ),
                                onSelected = { projStatus = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        PaperTextField(
                            value = projDescription,
                            onValueChange = { projDescription = it },
                            label = "项目简介 *",
                            placeholder = "一两句话阐述这个项目的核心目的与愿景…"
                        )

                        PaperTextField(
                            value = projStory,
                            onValueChange = { projStory = it },
                            label = "立项初衷",
                            placeholder = "为什么要做这件造物？背后的契机与期许…"
                        )

                        PaperTextField(
                            value = projGithubUrl,
                            onValueChange = { projGithubUrl = it },
                            label = "仓库或作品链接",
                            placeholder = "https://github.com/...",
                            singleLine = true
                        )
                    }
                }
            }

            // ==================== 4. 心愿 (wish) ====================
            else if (noteType == "wish") {
                // 三维形态切换胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("item" to "好物心愿", "travel" to "远方旅程", "milestone" to "阶段里程碑").forEach { (kind, label) ->
                        PaperFilterPill(
                            selected = wishKind == kind,
                            onClick = { wishKind = kind },
                            label = label
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperTextField(
                        value = wishTitle,
                        onValueChange = { wishTitle = it },
                        label = "心愿名称 *",
                        placeholder = when (wishKind) {
                            "item" -> "期许拥有的物件名称…"
                            "travel" -> "想要抵达的目的地或旅程…"
                            else -> "期许实现的里程碑目标…"
                        },
                        singleLine = true
                    )

                    PaperTextField(
                        value = wishBudgetYuan,
                        onValueChange = { wishBudgetYuan = it },
                        label = "预估预算 (¥)",
                        placeholder = "0.00",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (wishKind == "item") {
                        PaperTextField(
                            value = wishSpec,
                            onValueChange = { wishSpec = it },
                            label = "规格型号",
                            placeholder = "如：50mm f/1.4 定焦镜头 / 深空灰 512G",
                            singleLine = true
                        )
                    } else if (wishKind == "travel") {
                        PaperCityPickerField(
                            selectedCityCode = noteCityCode,
                            selectedCityName = noteCityName,
                            onCitySelected = { code, name ->
                                noteCityCode = code
                                noteCityName = name
                            },
                            placeholder = "目的地城市 *"
                        )
                    }

                    PaperDateField(
                        selectedDate = wishTargetDate ?: LocalDate.now(FormatUtils.BeijingZone),
                        onDateSelected = { wishTargetDate = it },
                        label = "期许达成日期"
                    )

                    PaperTextField(
                        value = wishNote,
                        onValueChange = { wishNote = it },
                        label = "心愿寄语",
                        placeholder = "写下对达成那一刻的期许或向往…"
                    )

                    PhotoMountingSection()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

