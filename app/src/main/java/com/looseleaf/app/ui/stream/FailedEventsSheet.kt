package com.looseleaf.app.ui.stream

import android.content.Context
import androidx.compose.foundation.BorderStroke
import com.looseleaf.app.ui.components.ArchiveTextButton
import com.looseleaf.app.ui.components.ArchiveSecondaryButton
import com.looseleaf.app.ui.components.ArchivePrimaryButton
import com.looseleaf.app.ui.components.ArchiveEmptyState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.data.local.OutboxEventEntity
import com.looseleaf.app.data.sync.OfflineMediaEntryPayload
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveSheet
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.feature.ledger.LedgerModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

internal data class DisplayFailedEvent(
    val eventId: String,
    val isLedger: Boolean,
    val entityType: String,
    val action: String,
    val retryCount: Int,
    val lastError: String?,
    val localTargetId: Long
)

private val failedEventJson = Json { ignoreUnknownKeys = true }

/**
 * 统一放弃单条发件箱事件并清理本地孤儿行与暂存媒体文件（供单条放弃与全部放弃共用）。
 */
internal suspend fun discardOutboxEvent(
    context: Context,
    database: AppDatabase,
    evt: OutboxEventEntity
) {
    val stagedFilesToDelete = mutableListOf<File>()
    val outboxMediaDir = File(context.filesDir, "outbox_media")

    database.withTransaction {
        database.outboxDao().deleteById(evt.eventId)
        if (evt.action == "CREATE") {
            when (evt.entityType) {
                "entry" -> database.entryDao().deleteByLocalId(evt.localTargetId)
                "media_entry" -> {
                    val entry = database.entryDao().getEntryByLocalId(evt.localTargetId)
                    if (entry == null || entry.serverId == null || entry.syncStatus == SyncManager.STATUS_PENDING_CREATE) {
                        database.entryDao().deleteByLocalId(evt.localTargetId)
                        database.outboxDao().deleteByLocalTarget("entry", evt.localTargetId)
                    }
                    val paths = runCatching {
                        val payload = failedEventJson.decodeFromString<OfflineMediaEntryPayload>(evt.payload)
                        (payload.files.map { it.filePath } + payload.localImageFilePath)
                            .filter { it.isNotBlank() }
                            .distinct()
                    }.getOrDefault(emptyList())
                    for (path in paths) {
                        val f = File(path)
                        if (f.parentFile?.absolutePath == outboxMediaDir.absolutePath) {
                            stagedFilesToDelete.add(f)
                        }
                    }
                }
                "wish" -> database.wishDao().deleteById(evt.localTargetId)
            }
        } else {
            when (evt.entityType) {
                "entry", "media_entry" -> {
                    val entry = database.entryDao().getEntryByLocalId(evt.localTargetId)
                    if (entry != null) {
                        if (entry.serverId == null) {
                            database.entryDao().deleteByLocalId(evt.localTargetId)
                        } else {
                            database.entryDao().updateSyncStatus(evt.localTargetId, SyncManager.STATUS_SYNCED)
                        }
                    }
                }
            }
        }
    }

    stagedFilesToDelete.forEach { file ->
        runCatching { if (file.exists()) file.delete() }
    }
}

/**
 * 失败事件处置抽屉 (Failed Events BottomSheet)
 *
 * 统一展示主库（散页/心愿/相纸）与独立账本库中阻滞的死信事件：
 * - 支持单条重试（置空 failed_at 与重置 retry_count）、单条放弃（联动清理未上云的本地孤儿行与暂存相纸）；
 * - 底部支持一键全部重新排队与全部放弃。
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

    var failedList by remember { mutableStateOf<List<DisplayFailedEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            isLoading = true
            val items = withContext(Dispatchers.IO) {
                val mainItems = database.outboxDao().getFailedEvents().map {
                    DisplayFailedEvent(
                        eventId = it.eventId,
                        isLedger = false,
                        entityType = it.entityType,
                        action = it.action,
                        retryCount = it.retryCount,
                        lastError = it.lastError,
                        localTargetId = it.localTargetId
                    )
                }
                val ledgerItems = LedgerModule.getFailedEvents(context).map {
                    DisplayFailedEvent(
                        eventId = it.eventId,
                        isLedger = true,
                        entityType = "ledger",
                        action = it.action,
                        retryCount = it.retryCount,
                        lastError = it.lastError,
                        localTargetId = it.localTargetId
                    )
                }
                mainItems + ledgerItems
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

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = if (failedList.isEmpty()) "同步异常" else "同步异常 · ${failedList.size}"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                    .background(colors.archiveSubtle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = com.looseleaf.app.ui.components.FolioGlyphs.Lock,
                    contentDescription = null,
                    tint = colors.accentMoss,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "这些记录与中枢同步受阻，但都已安全保存在本机。确认后可重新投递或放弃。",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta.copy(lineHeight = 18.sp),
                    color = colors.inkSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accentWarm,
                        trackColor = colors.borderSubtle
                    )
                }
            } else if (failedList.isEmpty()) {
                ArchiveEmptyState(
                    title = "没有待处理的记录",
                    subtitle = "所有内容都已与中枢对齐",
                    imageVector = com.looseleaf.app.ui.components.FolioGlyphs.Check,
                    modifier = Modifier.heightIn(min = 200.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(failedList, key = { "${if (it.isLedger) "l" else "m"}_${it.eventId}" }) { event ->
                        FailedEventItemRow(
                            event = event,
                            onRetry = {
                                scope.launch {
                                    if (event.isLedger) {
                                        LedgerModule.retryFailedEvents(context, event.eventId)
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            database.outboxDao().resetFailedById(event.eventId)
                                        }
                                        syncManager.scheduleSync()
                                    }
                                    ArchiveToastManager.show("已重新排队")
                                    reload()
                                }
                            },
                            onDiscard = {
                                scope.launch {
                                    if (event.isLedger) {
                                        LedgerModule.discardFailedEvent(context, event.eventId)
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            val evt = database.outboxDao().findEventById(event.eventId)
                                            if (evt != null) {
                                                discardOutboxEvent(context, database, evt)
                                            }
                                        }
                                        syncManager.notifyLocalWrite()
                                    }
                                    ArchiveToastManager.show("已放弃该条发件箱记录")
                                    reload()
                                }
                            }
                        )
                    }
                }
            }

            if (!isLoading && failedList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArchiveSecondaryButton(
                        text = "全部放弃",
                        isDestructive = true,
                        onClick = {
                            scope.launch {
                                LedgerModule.discardAllFailed(context)
                                withContext(Dispatchers.IO) {
                                    val mainFailed = database.outboxDao().getFailedEvents()
                                    for (evt in mainFailed) {
                                        discardOutboxEvent(context, database, evt)
                                    }
                                }
                                syncManager.notifyLocalWrite()
                                ArchiveToastManager.show("已放弃所有失败事件")
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ArchivePrimaryButton(
                        text = "全部重新投递",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.outboxDao().resetAllFailed()
                                }
                                syncManager.scheduleSync()
                                LedgerModule.retryFailedEvents(context)
                                ArchiveToastManager.show("正在重试所有失败记录")
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedEventItemRow(
    event: DisplayFailedEvent,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val entityLabel = when (event.entityType) {
        "media_entry" -> "图文"
        "wish" -> "心愿"
        "ledger" -> "账本"
        else -> "日常"
    }
    val badge = entityLabel.take(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.looseleaf.app.theme.ArchiveShapes.leaf)
            .background(colors.archiveCard)
            .border(1.dp, colors.borderCard, com.looseleaf.app.theme.ArchiveShapes.leaf)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(com.looseleaf.app.theme.ArchiveShapes.sm)
                    .background(colors.negativeTint),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontFamily = ArchiveFontFamilies.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.alertRed
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$entityLabel · ${event.action}",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.lastError ?: "连接中枢受阻",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    color = colors.inkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "已试 ${event.retryCount} 次",
                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                color = colors.alertRed
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArchiveTextButton(
                text = "放弃",
                onClick = onDiscard,
                textColor = colors.inkSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                    .background(colors.inverseSurface)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "重试",
                    style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inverseInk
                )
            }
        }
    }
}
