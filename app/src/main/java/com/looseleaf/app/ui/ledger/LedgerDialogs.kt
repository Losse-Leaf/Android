package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import kotlinx.coroutines.launch
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import com.looseleaf.app.ui.components.ArchiveBottomSheet
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
@Composable
internal fun CreditChannelsManagerDialog(
    creditChannels: List<CreditChannel>,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onChannelsChanged: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var newChannelName by remember { mutableStateOf("") }
    var newChannelDay by remember { mutableStateOf("10") }
    var editingChannel by remember { mutableStateOf<CreditChannel?>(null) }
    // MOB-P0-14：待确认删除的信贷渠道
    var channelToDelete by remember { mutableStateOf<CreditChannel?>(null) }
    var editChannelName by remember { mutableStateOf("") }
    var editChannelDay by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // MOB-P0-14：删除信贷渠道二次确认
    channelToDelete?.let { ch ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.ledger_dialogs_title_01),
            content = "确认删除「${ch.name}」？已记录的该渠道分期账单将失去渠道归属。",
            confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    runCatching {
                        apiClient.deleteCreditChannel(ch.id)
                        onChannelsChanged()
                        ArchiveToastManager.show("已删除信贷渠道「${ch.name}」")
                    }.onFailure {
                        ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                    }
                }
                channelToDelete = null
            },
            onDismiss = { channelToDelete = null }
        )
    }

    // MOB-P2-43：原先用的是 Material3 原生 AlertDialog —— 圆角、字体、按钮排布与全站
    // 纸墨弹窗家族完全不同，深色模式下还会带上 M3 默认紫调。
    // 这里统一迁移到自研 ArchiveBottomSheet（自带软键盘推升 + 底部安全区 + 折痕拖拽条）。
    editingChannel?.let { channel ->
        ArchiveBottomSheet(
            onDismissRequest = { editingChannel = null },
            title = stringResource(R.string.ledger_dialogs_title_tpl_01, channel.name),
            actionButtonText = "保存",
            onActionClick = {
                val day = editChannelDay.toIntOrNull()?.coerceIn(1, 31) ?: channel.repaymentDay
                scope.launch {
                    runCatching {
                        apiClient.updateCreditChannel(
                            id = channel.id,
                            req = CreateCreditChannelRequest(
                                name = editChannelName.trim().ifBlank { channel.name },
                                repaymentDay = day
                            )
                        )
                        editingChannel = null
                        onChannelsChanged()
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperTextField(
                    value = editChannelName,
                    onValueChange = { editChannelName = it },
                    label = stringResource(R.string.ledger_dialogs_label_01),
                    singleLine = true
                )
                PaperTextField(
                    value = editChannelDay,
                    onValueChange = { editChannelDay = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.ledger_dialogs_label_02),
                    singleLine = true
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.archivePaper)
                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ledger_dialogs_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paper_components_contentDescription_02), tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 新增渠道行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaperTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        placeholder = stringResource(R.string.ledger_dialogs_label_01),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    PaperTextField(
                        value = newChannelDay,
                        onValueChange = { newChannelDay = it.filter { ch -> ch.isDigit() } },
                        placeholder = stringResource(R.string.ledger_dialogs_placeholder_01),
                        singleLine = true,
                        modifier = Modifier.width(60.dp)
                    )
                    Button(
                        onClick = {
                            if (newChannelName.isNotBlank() && !isSubmitting) {
                                isSubmitting = true
                                scope.launch {
                                    runCatching {
                                        val day = newChannelDay.toIntOrNull()?.coerceIn(1, 31) ?: 10
                                        apiClient.createCreditChannel(
                                            CreateCreditChannelRequest(
                                                name = newChannelName.trim(),
                                                repaymentDay = day
                                            )
                                        )
                                        newChannelName = ""
                                        onChannelsChanged()
                                    }
                                    isSubmitting = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                    ) {
                        Text("添加", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(creditChannels, key = { it.id }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = ch.name,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = stringResource(R.string.ledger_dialogs_text_tpl_01, ch.repaymentDay, if (ch.isBuiltin) " · 系统内置" else ""),
                                    fontSize = 10.5.sp,
                                    color = colors.inkMuted
                                )
                            }
                            if (!ch.isBuiltin) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingChannel = ch
                                            editChannelName = ch.name
                                            editChannelDay = ch.repaymentDay.toString()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.ledger_dialogs_contentDescription_01), tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            // MOB-P0-14：破坏性删除先确认
                                            channelToDelete = ch
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.ledger_dialogs_contentDescription_02), tint = colors.accentWarm, modifier = Modifier.size(14.dp))
                                    }
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
internal fun CategoriesManagerDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onCategoriesChanged: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()
    var selectedScope by remember { mutableStateOf("expense") }
    var categories by remember { mutableStateOf<List<CategoryOption>>(emptyList()) }
    // MOB-P0-14：待确认删除的分类
    var categoryToDelete by remember { mutableStateOf<CategoryOption?>(null) }
    var newCategoryName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }


    suspend fun loadCats() {
        isLoading = true
        runCatching {
            categories = apiClient.fetchCategories(selectedScope)
        }
        isLoading = false
    }

    LaunchedEffect(selectedScope) {
        loadCats()
    }

    // MOB-P0-14：删除分类二次确认
    categoryToDelete?.let { cat ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.ledger_dialogs_title_02),
            content = "确认删除分类「${cat.name}」？该分类下的历史记账会保留，但不再能在新建时选用。",
            confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    runCatching {
                        apiClient.deleteCategoryOption(cat.id)
                        loadCats()
                        onCategoriesChanged()
                        ArchiveToastManager.show("已删除分类「${cat.name}」")
                    }.onFailure {
                        ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                    }
                }
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }


    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.archivePaper)
                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ledger_dialogs_text_02),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // RC-142 已废除「重置预设」能力，全端不再提供「恢复默认分类」入口
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paper_components_contentDescription_02), tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scope 切换
                val scopes = listOf(
                    "expense" to "支出",
                    "income" to "收入",
                    "payment" to "支付方式",
                    "gear" to "固定资产",
                    "project" to "造物"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    scopes.forEach { (sc, label) ->
                        PaperFilterPill(
                            selected = selectedScope == sc,
                            onClick = { selectedScope = sc },
                            label = label
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 新增输入行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaperTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = stringResource(R.string.ledger_dialogs_placeholder_02),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                scope.launch {
                                    runCatching {
                                        apiClient.addCategoryOption(selectedScope, newCategoryName.trim())
                                        newCategoryName = ""
                                        loadCats()
                                        onCategoriesChanged()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                    ) {
                        Text("新增", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider)
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Text("加载中…", fontSize = 12.sp, color = colors.inkMuted)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name + if (cat.isBuiltin) " (系统)" else "",
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                                if (!cat.isBuiltin) {
                                    IconButton(
                                        onClick = {
                                            // MOB-P0-14：破坏性删除先确认
                                            categoryToDelete = cat
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.ledger_dialogs_contentDescription_03), tint = colors.accentWarm, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
