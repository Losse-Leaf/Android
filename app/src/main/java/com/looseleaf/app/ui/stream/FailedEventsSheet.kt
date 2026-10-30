package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveBottomSheet
import com.looseleaf.app.ui.components.ArchiveToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 失败事件处置抽屉 (Failed Events BottomSheet)
 *
 * 点击封面区赤陶色邮戳展开：
 * - 列出所有在发件箱中被阻滞的条目（带类型、操作、错误摘要）
 * - 支持单条重试、单条放弃，以及底部一键全部重试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FailedEventsSheet(
    syncManager: SyncManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }

    var failedList by remember { mutableStateOf<List<OutboxEventEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            isLoading = true
            val items = withContext(Dispatchers.IO) {
                database.outboxDao().getFailedEvents()
            }
            failedList = items
            isLoading = false
            if (items.isEmpty()) {
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "发件箱待重验 (${failedList.size})"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "下列手稿在与中枢同步时受阻，已妥善保存在本地离线库中，请确认后重发或放弃：",
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accentWarm
                    )
                }
            } else if (failedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "所有待办已清空",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        color = colors.inkMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(failedList, key = { it.eventId }) { event ->
                        FailedEventItemRow(
                            event = event,
                            onRetry = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        // 重置单条重试计数
                                        database.outboxDao().markFailed(event.eventId, null.toString(), null)
                                    }
                                    syncManager.scheduleSync()
                                    ArchiveToastManager.show("已重新排队")
                                    reload()
                                }
                            },
                            onDiscard = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.outboxDao().deleteById(event.eventId)
                                    }
                                    ArchiveToastManager.show("已放弃该条发件箱记录")
                                    reload()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部操作栏：放弃全部 / 全部重新排队
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            syncManager.discardFailedEvents()
                            ArchiveToastManager.show("已放弃所有失败事件")
                            onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = "全部放弃",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkMuted
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            syncManager.retryFailedEvents()
                            ArchiveToastManager.show("正在重试所有失败记录…")
                            onDismiss()
                        }
                    },
                    shape = ArchiveShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.archivePaper
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "全部重新投递",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedEventItemRow(
    event: OutboxEventEntity,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val entityLabel = when (event.entityType) {
        "travel" -> "游历"
        "project" -> "造物"
        "ledger" -> "账本"
        else -> "日常"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.archiveCard,
        border = androidx.compose.foundation.BorderStroke(0.6.dp, colors.borderDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "[$entityLabel] ${event.action}",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "重试 ${event.retryCount} 次",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 10.5.sp,
                        color = colors.accentWarm
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = event.lastError ?: "连接中枢受阻",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDiscard,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "放弃",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm.copy(alpha = 0.15f),
                        contentColor = colors.accentWarm
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "重试",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
