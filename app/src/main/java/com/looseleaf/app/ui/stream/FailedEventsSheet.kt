package com.looseleaf.app.ui.stream

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
                "project" -> database.projectDao().deleteById(evt.localTargetId)
                "wish" -> database.wishDao().deleteById(evt.localTargetId)
                "footprint" -> database.footprintDao().deleteById(evt.localTargetId)
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
 * 统一展示主库（手稿/造物/心愿/足迹/相纸）与独立账本库中阻滞的死信事件：
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
        title = "发件箱待重验 (${failedList.size})"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "下列记录在与中枢同步时受阻，已妥善保存在本地离线库中，请确认后重发或放弃：",
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 14.dp)
            )

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
                        color = colors.accentWarm
                    )
                }
            } else if (failedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
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
                            withContext(Dispatchers.IO) {
                                database.outboxDao().resetAllFailed()
                            }
                            syncManager.scheduleSync()
                            LedgerModule.retryFailedEvents(context)
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
                        fontSize = 14.sp
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
        "travel" -> "游历"
        "media_entry" -> "图文"
        "project" -> "造物"
        "wish" -> "心愿"
        "footprint" -> "足迹"
        "ledger" -> "账本"
        else -> "日常"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.archiveCard,
        border = BorderStroke(0.6.dp, colors.borderDivider),
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
                        fontSize = 11.sp,
                        color = colors.alertRed
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = event.lastError ?: "连接中枢受阻",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 12.sp,
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
