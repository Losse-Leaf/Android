package com.looseleaf.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import java.io.File
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource

/**
 * 散页存储配置与全量快照灾备中枢 (Storage & Backup Management Sheet)
 * 严格对齐 Web 端 StorageBackupModal.vue 契约：
 * 1. 云端/本地 SQLite WAL 全量快照备份、列表、回滚与清理
 * 2. Cloudflare R2 / S3 兼容对象存储在线热配置、握手连通性测速与双向媒体迁移
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBackupSheetDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: 快照备份, 1: 对象存储配置

    var storageStatus by remember { mutableStateOf<StorageStatus?>(null) }
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }
    var backupToRestore by remember { mutableStateOf<BackupItem?>(null) }
    var backupToDelete by remember { mutableStateOf<BackupItem?>(null) }

    // 对象存储配置表单字段
    var bucketName by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var publicDomain by remember { mutableStateOf("") }

    suspend fun reloadData() {
        isLoading = true
        val result = try {
            storageStatus = apiClient.fetchStorageStatus()
            backups = apiClient.fetchBackupList()
            storageStatus?.let { st ->
                bucketName = st.bucket ?: ""
                endpoint = st.endpoint ?: ""
                accessKeyId = st.accessKeyId ?: ""
                publicDomain = st.publicDomain ?: ""
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onFailure { err ->
            if (err !is CancellationException) {
                ArchiveToastManager.show("加载灾备信息失败: ${err.message}", isError = true)
            }
        }
        isLoading = false
    }

    val context = LocalContext.current

    fun downloadBackupAction(b: BackupItem) {
        scope.launch {
            isOperating = true
            ArchiveToastManager.show("正在下载「${b.filename}」…")
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Q+ 走 MediaStore Downloads：无需任何存储权限，IS_PENDING 防半截文件
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, b.filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        // 先隐藏，写完再转为可见；失败时不会留下半截文件
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw IllegalStateException("无法写入系统下载目录")
                    try {
                        resolver.openOutputStream(uri)?.use { os ->
                            apiClient.downloadBackupTo(b.filename, os)
                        } ?: throw IllegalStateException("无法写入系统下载目录")
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null
                        )
                    } catch (e: Exception) {
                        runCatching { resolver.delete(uri, null, null) }
                        throw e
                    }
                    ArchiveToastManager.show("已保存至系统下载目录：${b.filename}")
                } else {
                    // pre-Q 若写 MediaStore.Files 需要 WRITE_EXTERNAL_STORAGE 运行时授权
                    // （insert 抛 SecurityException 而非返回 null，原兜底根本不会触发），
                    // 为不引入额外权限，直接写应用专属下载目录（免权限、用户可经文件管理器访问）
                    val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    File(downloadDir, b.filename).outputStream().use { os ->
                        apiClient.downloadBackupTo(b.filename, os)
                    }
                    ArchiveToastManager.show("已保存至应用下载目录：Android/data/${context.packageName}/files/Download")
                }
            }.onFailure { err ->
                ArchiveToastManager.show("下载快照失败: ${err.message}", isError = true)
            }
            isOperating = false
        }
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    // 回滚二次确认弹窗
    backupToRestore?.let { b ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.storage_backup_sheet_dialog_title_01),
            content = "恢复操作将从备份「${b.filename}」中整库还原数据库，未备份的最新改动将被覆盖，恢复后将自动重载应用。",
            confirmText = stringResource(R.string.storage_backup_sheet_dialog_confirmText_01),
            isDestructive = true,
            onConfirm = {
                val target = b.filename
                backupToRestore = null
                scope.launch {
                    isOperating = true
                    runCatching {
                        apiClient.restoreBackup(target)
                        ArchiveToastManager.show("快照已成功恢复，正在重载…")
                        onRestored()
                        onDismiss()
                    }.onFailure { err ->
                        ArchiveToastManager.show("快照恢复失败: ${err.message}", isError = true)
                    }
                    isOperating = false
                }
            },
            onDismiss = { backupToRestore = null }
        )
    }

    // 删除备份二次确认
    backupToDelete?.let { b ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.storage_backup_sheet_dialog_title_02),
            content = "确认永久删除备份压缩包「${b.filename}」？删除后无法找回。",
            confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
            isDestructive = true,
            onConfirm = {
                val target = b.filename
                backupToDelete = null
                scope.launch {
                    isOperating = true
                    runCatching {
                        backups = apiClient.deleteBackup(target)
                        ArchiveToastManager.show("快照已删除")
                    }.onFailure { err ->
                        ArchiveToastManager.show("删除失败: ${err.message}", isError = true)
                    }
                    isOperating = false
                }
            },
            onDismiss = { backupToDelete = null }
        )
    }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.storage_backup_sheet_dialog_title_03),
        cancelText = "关闭",
        actionText = if (selectedTab == 0) "立即备份" else null,
        isActionEnabled = !isOperating && !isLoading,
        onAction = {
            if (selectedTab == 0) {
                scope.launch {
                    isOperating = true
                    runCatching {
                        val newItem = apiClient.createBackupNow()
                        backups = listOf(newItem) + backups.filter { it.filename != newItem.filename }
                        ArchiveToastManager.show("全量快照备份创建成功")
                    }.onFailure { err ->
                        ArchiveToastManager.show("创建备份失败: ${err.message}", isError = true)
                    }
                    isOperating = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab 切换器 (0: 全量快照备份, 1: 对象存储配置)
            ArchiveSegmentedTabs(
                tabs = listOf("快照备份", "对象存储配置"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                counts = listOf(backups.size, 0)
            )

            if (selectedTab == 0) {
                // ==================== TAB 0: 全量快照备份 ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.storage_backup_sheet_dialog_text_01),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = colors.inkSecondary
                    )

                    if (isLoading) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Text(stringResource(R.string.storage_backup_fetching), fontSize = 12.sp, color = colors.inkMuted)
                        }
                    } else if (backups.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Text(stringResource(R.string.storage_backup_empty), fontSize = 12.sp, color = colors.inkMuted)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            backups.forEach { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.archiveSubtle.copy(alpha = 0.55f))
                                        .border(0.5.dp, colors.borderDivider.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = b.filename,
                                                fontFamily = ArchiveFontFamilies.MonoScale,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.inkPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (b.storageType == "r2") colors.accentWarm.copy(alpha = 0.12f) else colors.borderDivider.copy(alpha = 0.4f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = if (b.storageType == "r2") "R2 云端" else "本地",
                                                    fontSize = 11.sp,
                                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                                    color = if (b.storageType == "r2") colors.accentWarm else colors.inkSecondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        val mbSize = String.format(java.util.Locale.US, "%.2f MB", b.size.toDouble() / (1024 * 1024))
                                        Text(
                                            text = stringResource(R.string.storage_backup_sheet_dialog_text_tpl_01, mbSize, b.createdAt.take(19).replace("T", " ")),
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 11.sp,
                                            color = colors.inkMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.storage_backup_sheet_dialog_text_02),
                                            fontSize = 11.5.sp,
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            color = colors.inkPrimary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .clickable { downloadBackupAction(b) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.storage_backup_sheet_dialog_text_03),
                                            fontSize = 11.5.sp,
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            color = colors.accentWarm,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .clickable { backupToRestore = b }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.ledger_transactions_tab_contentDescription_03),
                                            fontSize = 11.5.sp,
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            color = colors.inkMuted,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .clickable { backupToDelete = b }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== TAB 1: 对象存储在线配置 ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 当前状态卡片
                    val isConfigured = storageStatus?.configured == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isConfigured) colors.accentMoss.copy(alpha = 0.08f) else colors.archiveSubtle)
                            .border(0.5.dp, if (isConfigured) colors.accentMoss.copy(alpha = 0.3f) else colors.borderDivider, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = if (isConfigured) painterResource(R.drawable.ic_cloud_done) else painterResource(R.drawable.ic_storage),
                            contentDescription = null,
                            tint = if (isConfigured) colors.accentMoss else colors.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isConfigured) "对象存储：Cloudflare R2 / S3 兼容模式 (已就绪)" else "当前模式：本地磁盘媒体存储",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isConfigured) colors.accentMoss else colors.inkPrimary
                            )
                            val fileStats = "本地媒体: ${storageStatus?.localFileCount ?: 0} 个文件"
                            Text(text = fileStats, fontSize = 10.5.sp, color = colors.inkMuted)
                        }
                    }

                    ArchiveField(
                        value = bucketName,
                        onValueChange = { bucketName = it },
                        label = stringResource(R.string.storage_backup_sheet_dialog_label_01),
                        placeholder = stringResource(R.string.storage_backup_sheet_dialog_placeholder_01),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ArchiveField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = stringResource(R.string.storage_backup_sheet_dialog_label_02),
                        placeholder = stringResource(R.string.storage_backup_sheet_dialog_placeholder_02),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ArchiveField(
                        value = accessKeyId,
                        onValueChange = { accessKeyId = it },
                        label = stringResource(R.string.storage_backup_sheet_dialog_label_03),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ArchiveField(
                        value = secretAccessKey,
                        onValueChange = { secretAccessKey = it },
                        label = if (storageStatus?.hasSecretKey == true) "Secret Access Key (留空表示沿用已保存密钥)" else "Secret Access Key",
                        placeholder = if (storageStatus?.hasSecretKey == true) "••••••••••••••••••••••••••••••••" else "输入存储密钥",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ArchiveField(
                        value = publicDomain,
                        onValueChange = { publicDomain = it },
                        label = stringResource(R.string.storage_backup_sheet_dialog_label_04),
                        placeholder = stringResource(R.string.storage_backup_sheet_dialog_placeholder_03),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 动作按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    runCatching {
                                        val req = StorageConfigRequest(
                                            bucketName = bucketName.trim(),
                                            endpoint = endpoint.trim(),
                                            accessKeyId = accessKeyId.trim(),
                                            secretAccessKey = secretAccessKey.trim(),
                                            publicDomain = publicDomain.trim()
                                        )
                                        val res = apiClient.testStorageConfig(req)
                                        ArchiveToastManager.show("握手成功！往返延迟: ${res.latencyMs}ms")
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("测试失败: ${err.message}", isError = true)
                                    }
                                    isOperating = false
                                }
                            },
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.archivePaper),
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(stringResource(R.string.storage_backup_test), fontSize = 12.sp, fontFamily = ArchiveFontFamilies.BodySans)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    runCatching {
                                        val req = StorageConfigRequest(
                                            bucketName = bucketName.trim(),
                                            endpoint = endpoint.trim(),
                                            accessKeyId = accessKeyId.trim(),
                                            secretAccessKey = secretAccessKey.trim(),
                                            publicDomain = publicDomain.trim()
                                        )
                                        storageStatus = apiClient.updateStorageConfig(req)
                                        secretAccessKey = ""
                                        ArchiveToastManager.show("存储配置已保存并热重载")
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("保存配置失败: ${err.message}", isError = true)
                                    }
                                    isOperating = false
                                }
                            },
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper),
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(stringResource(R.string.storage_backup_save), fontSize = 12.sp, fontFamily = ArchiveFontFamilies.BodySans)
                        }
                    }

                    // 迁移与回退按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    runCatching {
                                        val res = apiClient.migrateStorageMedia()
                                        ArchiveToastManager.show(res.message)
                                        reloadData()
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("迁移失败: ${err.message}", isError = true)
                                    }
                                    isOperating = false
                                }
                            },
                            enabled = isConfigured && !isOperating
                        ) {
                            Text(stringResource(R.string.storage_backup_sync_r2), fontSize = 11.sp, color = if (isConfigured) colors.accentWarm else colors.inkMuted)
                        }

                        TextButton(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    runCatching {
                                        storageStatus = apiClient.resetStorageConfig()
                                        secretAccessKey = ""
                                        reloadData()
                                        ArchiveToastManager.show("已切回本地存储模式")
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("重置失败: ${err.message}", isError = true)
                                    }
                                    isOperating = false
                                }
                            },
                            enabled = isConfigured && !isOperating
                        ) {
                            Text(stringResource(R.string.storage_backup_restore), fontSize = 11.sp, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}
