package com.looseleaf.app.ui.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 生活时间流主展厅 (StreamScreen) - docs/mobile.md §3 & docs/ecosystem.md §4.1
 * 承载生命之流母体记录，支持相纸画廊、那年今日回响、年度生活年鉴、详情查阅与行内修订、岁月留白切线
 */
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

    val entries by syncManager.entriesFlow.collectAsState(initial = emptyList())
    val pendingCount by syncManager.pendingEventCountFlow.collectAsState(initial = 0)

    var selectedFilter by remember { mutableStateOf("all") }
    var isRefreshing by remember { mutableStateOf(false) }
    var cloudUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }

    // 那年今日状态
    var remoteOnThisDay by remember { mutableStateOf<List<LifeEntry>>(emptyList()) }
    var isOnThisDayExpanded by remember { mutableStateOf(true) }

    // 年度年鉴弹窗状态
    var showAlmanacDialog by remember { mutableStateOf(false) }

    // 全局检索与速记随笔状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 单条档案详情与编辑弹窗状态
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }

    LaunchedEffect(Unit) {
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

    val filteredEntries = remember(entries, selectedFilter) {
        if (selectedFilter == "all") entries
        else entries.filter { it.type == selectedFilter }
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
                        text = "散页 · 时光手账",
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pendingCount > 0) {
                        PaperBadge(
                            text = "$pendingCount 待同步",
                            isPendingSync = true,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    // 全局检索按钮
                    IconButton(
                        onClick = {
                            haptics.tapKey()
                            showSearchDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "检索",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // + 记随笔入口
                    TextButton(
                        onClick = {
                            haptics.tapKey()
                            showQuickNoteDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "+ 记随笔",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            color = colors.accentWarm
                        )
                    }

                    // 年度年鉴入口按钮
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archiveSubtle)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            .clickable {
                                haptics.tapKey()
                                showAlmanacDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = "年度年鉴",
                                tint = colors.accentWarm,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "年度年鉴",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

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
                                    Toast.makeText(context, "档案已增量同步", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "同步提示: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
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
                            text = "🎉 发现新版本 ${update.tagName}（当前 v${UpdateChecker.currentVersionName}）",
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
                                        Toast.makeText(context, "下载失败: ${err.message}", Toast.LENGTH_SHORT).show()
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
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
                        Toast.makeText(context, "已修订归册", Toast.LENGTH_SHORT).show()
                        selectedEntryForDetail = null
                    }
                },
                onDelete = {
                    entryToDelete = activeEntry
                }
            )
        }

        // 撕除确认弹窗
        entryToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                title = { Text("撕除手记？", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                text = { Text("确认从生活时间流中彻底移除《${entry.title.ifBlank { "无题随笔" }}》？", fontFamily = ArchiveFontFamilies.BodySans) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                haptics.tearDelete()
                                syncManager.deleteEntry(entry.id)
                                Toast.makeText(context, "已撕除记录", Toast.LENGTH_SHORT).show()
                                entryToDelete = null
                                if (selectedEntryForDetail?.id == entry.id) {
                                    selectedEntryForDetail = null
                                }
                            }
                        }
                    ) {
                        Text("确认撕除", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { entryToDelete = null }) {
                        Text("取消", color = colors.inkSecondary)
                    }
                }
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

        // 速记随笔对话框
        if (showQuickNoteDialog) {
            QuickNoteDialog(
                onDismiss = { showQuickNoteDialog = false },
                onSave = { type, title, content, loc ->
                    scope.launch {
                        haptics.stampSave()
                        AudioEngine.playShutter()
                        val code = CityDictionary.codeForLocation(loc)
                        val cityName = code?.let { CityDictionary.cityNameForCode(it) }
                        syncManager.createEntryOptimistic(
                            type = type,
                            title = title.ifBlank { "无题随笔" },
                            content = content,
                            occurredAt = FormatUtils.beijingNowIsoString(),
                            location = loc.takeIf { it.isNotBlank() },
                            cityCode = code,
                            cityName = cityName
                        )
                        Toast.makeText(context, "随笔已归册", Toast.LENGTH_SHORT).show()
                        showQuickNoteDialog = false
                    }
                }
            )
        }

        // 年度生活年鉴五幕弹窗
        if (showAlmanacDialog) {
            AlmanacDialog(
                apiClient = apiClient,
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
    onDelete: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var isEditing by remember { mutableStateOf(false) }
    var editType by remember(entry) { mutableStateOf(entry.type) }
    var editTitle by remember(entry) { mutableStateOf(entry.title) }
    var editContent by remember(entry) { mutableStateOf(entry.content) }
    var editLocation by remember(entry) { mutableStateOf(entry.location ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
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

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("正文手记") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it },
                        label = { Text("所处地点") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TerracottaStampButton(
                        text = "保存修订 (UPDATE)",
                        enabled = editTitle.isNotBlank() || editContent.isNotBlank(),
                        onClick = {
                            val code = CityDictionary.codeForLocation(editLocation) ?: entry.cityCode
                            val cityName = code?.let { CityDictionary.cityNameForCode(it) } ?: entry.cityName
                            val req = CreateEntryRequest(
                                type = editType,
                                title = editTitle.ifBlank { "无题随笔" },
                                content = editContent.ifBlank { "无文字记录" },
                                occurredAt = entry.occurredAt,
                                location = editLocation.takeIf { it.isNotBlank() },
                                cityCode = code,
                                cityName = cityName,
                                projectId = entry.projectId,
                                wishId = entry.wishId,
                                footprintId = entry.footprintId,
                                featured = entry.featured ?: false,
                                attachmentIds = entry.attachments.mapNotNull { it.id.takeIf { id -> id > 0 } }
                            )
                            onSaveEdit(req)
                        }
                    )
                } else {
                    // 阅览模式
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val typeLabel = when (entry.type) {
                            "travel" -> "山川游历"
                            "project" -> "造物日志"
                            "wish" -> "心愿印记"
                            else -> "日常随笔"
                        }
                        PaperBadge(text = typeLabel)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
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

                    if (!entry.camera.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "器材底款: ${entry.camera}",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }

                    if (entry.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        for (att in entry.attachments) {
                            val fullUrl = apiClient.resolveMediaUrl(att.url)
                            val meta = entry.camera ?: att.originalFilename
                            FilmFrame(
                                imageUrl = fullUrl,
                                metadataText = meta,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlmanacDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val currentYear = remember { LocalDate.now(FormatUtils.BeijingZone).year }
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

                Spacer(modifier = Modifier.height(8.dp))

                // 年份切换胶囊
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (yr in listOf(currentYear, currentYear - 1, currentYear - 2)) {
                        val isSelected = yr == selectedYear
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.inkPrimary else colors.archivePaper)
                                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
                                .clickable { selectedYear = yr }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
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
            PaperBadge(text = chapterNumber)
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
        "life" to "日常随笔",
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

@Composable
private fun StreamEntryCard(
    entry: LifeEntry,
    apiClient: APIClient,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    PaperCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // 头部：类型标签、时间戳、地点与同步标记
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typeLabel = when (entry.type) {
                        "travel" -> "游历"
                        "project" -> "造物"
                        "wish" -> "心愿"
                        else -> "日常"
                    }
                    PaperBadge(text = typeLabel)

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )

                    val placeString = listOfNotNull(entry.cityName, entry.location).distinct().filter { it.isNotBlank() }.joinToString(" · ")
                    if (placeString.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = placeString,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.syncStatus != "SYNCED") {
                        PaperBadge(text = "待同步", isPendingSync = true)
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

            // 关联胶片相纸附件渲染
            if (entry.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                for (att in entry.attachments) {
                    val fullUrl = apiClient.resolveMediaUrl(att.url)
                    val meta = entry.camera ?: att.originalFilename
                    FilmFrame(
                        imageUrl = fullUrl,
                        metadataText = meta,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = onClick
                    )
                }
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
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PaperCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        if (q.trim().length >= 2) {
                            scope.launch {
                                isSearching = true
                                runCatching {
                                    searchResult = apiClient.searchGlobalArchive(q)
                                }
                                isSearching = false
                            }
                        } else {
                            searchResult = null
                        }
                    },
                    placeholder = { Text("搜索生活印记、山川足迹、心愿或造物…", fontSize = 12.sp, color = colors.inkMuted) },
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
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
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
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
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
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
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

@Composable
private fun QuickNoteDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, title: String, content: String, location: String) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var noteType by remember { mutableStateOf("life") }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var noteLocation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("速记随笔 · 生活印记", fontFamily = ArchiveFontFamilies.EditorialSerif) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("life" to "日常", "travel" to "游历", "project" to "造物", "wish" to "心愿").forEach { (k, label) ->
                        FilterChip(
                            selected = noteType == k,
                            onClick = { noteType = k },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("记录命名 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text("当下心绪手记 *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteLocation,
                    onValueChange = { noteLocation = it },
                    label = { Text("地点/城市 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = noteTitle.isNotBlank() || noteContent.isNotBlank(),
                onClick = {
                    onSave(noteType, noteTitle.trim(), noteContent.trim(), noteLocation.trim())
                }
            ) {
                Text("盖印归册", color = colors.accentWarm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.inkSecondary)
            }
        }
    )
}
