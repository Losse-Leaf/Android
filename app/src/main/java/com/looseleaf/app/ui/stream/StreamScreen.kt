package com.looseleaf.app.ui.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

/**
 * 生活时间流主展厅 (StreamScreen) - docs/mobile.md §3 & docs/ecosystem.md §4.1
 * 承载生命之流母体记录，支持相纸画廊、岁月留白切线与离线乐观呈现
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

    LaunchedEffect(Unit) {
        when (val res = UpdateChecker.checkForUpdates()) {
            is UpdateCheckResult.HasUpdate -> cloudUpdate = res.release
            else -> {}
        }
    }

    val filteredEntries = remember(entries, selectedFilter) {
        if (selectedFilter == "all") entries
        else entries.filter { it.type == selectedFilter }
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏：标尺与同步指示
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
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                val res = syncManager.pullRemoteData()
                                isRefreshing = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "档案已更新", Toast.LENGTH_SHORT).show()
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
                        if (update.body.isNotBlank()) {
                            Text(
                                text = update.body.lines().firstOrNull() ?: "",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = colors.inkSecondary
                            )
                        }
                    }
                    TextButton(
                        onClick = { UpdateChecker.openDownloadPage(context, update.apkUrl) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("立即升级", color = colors.accentWarm, fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
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
            if (filteredEntries.isEmpty()) {
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
                                brush = androidx.compose.ui.graphics.SolidColor(colors.borderDivider)
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
                            onDelete = {
                                scope.launch {
                                    haptics.tearDelete()
                                    syncManager.deleteEntry(entry.id)
                                    Toast.makeText(context, "已撕除记录", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    PaperCard(modifier = modifier.fillMaxWidth()) {
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

                    if (!entry.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = entry.location,
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
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
