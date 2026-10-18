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
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

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
        runCatching {
            storageStatus = apiClient.fetchStorageStatus()
            backups = apiClient.fetchBackupList()
            storageStatus?.let { st ->
                bucketName = st.bucket ?: ""
                endpoint = st.endpoint ?: ""
                accessKeyId = st.accessKeyId ?: ""
                publicDomain = st.publicDomain ?: ""
            }
        }.onFailure { err ->
            ArchiveToastManager.show("加载灾备信息失败: ${err.message}", isError = true)
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    // 回滚二次确认弹窗
    backupToRestore?.let { b ->
        ArchiveConfirmDialog(
            title = "确认回滚恢复快照？",
            content = "恢复操作将从备份「${b.filename}」中整库还原数据库，未备份的最新改动将被覆盖，恢复后将自动重载应用。",
            confirmText = "确认回滚恢复",
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
            title = "删除此快照备份？",
            content = "确认永久删除备份压缩包「${b.filename}」？删除后无法找回。",
            confirmText = "确认删除",
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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "《存储与灾备中枢》",
        actionButtonText = if (selectedTab == 0) "立即备份" else null,
        isActionEnabled = !isOperating && !isLoading,
        onActionClick = {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tab 切换器 (0: 全量快照备份, 1: 对象存储配置)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.archiveSubtle)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(0 to "快照备份 (${backups.size})", 1 to "对象存储配置").forEach { (idx, title) ->
                    val isSelected = selectedTab == idx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.archivePaper else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // ==================== TAB 0: 全量快照备份 ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "服务端全量 ZIP 快照由 SQLite WAL 数据库及本地元数据打包生成，最多自动轮转保留 7 份：",
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
                            Text("正在拉取备份清单…", fontSize = 12.sp, color = colors.inkMuted)
                        }
                    } else if (backups.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Text("暂无任何快照备份，点击右上角「立即备份」开始初次备份", fontSize = 12.sp, color = colors.inkMuted)
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
                                                    fontSize = 9.sp,
                                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                                    color = if (b.storageType == "r2") colors.accentWarm else colors.inkSecondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        val mbSize = String.format("%.2f MB", b.size.toDouble() / (1024 * 1024))
                                        Text(
                                            text = "$mbSize · ${b.createdAt.take(19).replace("T", " ")}",
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 10.sp,
                                            color = colors.inkMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "回滚",
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
                                            text = "删除",
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
                            imageVector = if (isConfigured) Icons.Outlined.CloudDone else Icons.Outlined.Storage,
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

                    PaperTextField(
                        value = bucketName,
                        onValueChange = { bucketName = it },
                        label = "Bucket Name (存储桶名称)",
                        placeholder = "例如: loose-leaf-media",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = "S3 Endpoint 或 Cloudflare Account ID",
                        placeholder = "https://<account_id>.r2.cloudflarestorage.com",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = accessKeyId,
                        onValueChange = { accessKeyId = it },
                        label = "Access Key ID",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = secretAccessKey,
                        onValueChange = { secretAccessKey = it },
                        label = if (storageStatus?.hasSecretKey == true) "Secret Access Key (留空表示沿用已保存密钥)" else "Secret Access Key",
                        placeholder = if (storageStatus?.hasSecretKey == true) "••••••••••••••••••••••••••••••••" else "输入存储密钥",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PaperTextField(
                        value = publicDomain,
                        onValueChange = { publicDomain = it },
                        label = "公开访问域名 (选填，加速相纸加载)",
                        placeholder = "https://media.yourdomain.com",
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
                            Text("测试连通性", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.BodySans)
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
                            Text("保存并生效", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.BodySans)
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
                            Text("一键将本地媒体同步至 R2", fontSize = 11.sp, color = if (isConfigured) colors.accentWarm else colors.inkMuted)
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
                            Text("恢复本地存储", fontSize = 11.sp, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}
