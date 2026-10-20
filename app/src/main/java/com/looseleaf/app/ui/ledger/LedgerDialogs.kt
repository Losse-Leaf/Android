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
    var editChannelName by remember { mutableStateOf("") }
    var editChannelDay by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    if (editingChannel != null) {
        AlertDialog(
            onDismissRequest = { editingChannel = null },
            containerColor = colors.archivePaper,
            title = {
                Text(
                    text = "修订渠道 · ${editingChannel?.name}",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperTextField(
                        value = editChannelName,
                        onValueChange = { editChannelName = it },
                        label = "渠道名称",
                        singleLine = true
                    )
                    PaperTextField(
                        value = editChannelDay,
                        onValueChange = { editChannelDay = it.filter { c -> c.isDigit() } },
                        label = "每月还款日 (1~31)",
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ch = editingChannel ?: return@TextButton
                        val day = editChannelDay.toIntOrNull()?.coerceIn(1, 31) ?: ch.repaymentDay
                        scope.launch {
                            runCatching {
                                apiClient.updateCreditChannel(
                                    id = ch.id,
                                    req = CreateCreditChannelRequest(
                                        name = editChannelName.trim().ifBlank { ch.name },
                                        repaymentDay = day
                                    )
                                )
                                editingChannel = null
                                onChannelsChanged()
                            }
                        }
                    }
                ) {
                    Text("保存", color = colors.accentWarm)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingChannel = null }) {
                    Text("取消", color = colors.inkSecondary)
                }
            }
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
                        text = "信贷分期渠道管理",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
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
                        placeholder = "渠道名称",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    PaperTextField(
                        value = newChannelDay,
                        onValueChange = { newChannelDay = it.filter { ch -> ch.isDigit() } },
                        placeholder = "还款日",
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
                                    text = "每月 ${ch.repaymentDay} 日还款${if (ch.isBuiltin) " · 系统内置" else ""}",
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
                                        Icon(Icons.Outlined.Edit, contentDescription = "编辑渠道", tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    apiClient.deleteCreditChannel(ch.id)
                                                    onChannelsChanged()
                                                    ArchiveToastManager.show("已删除信贷渠道「${ch.name}」")
                                                }.onFailure {
                                                    ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "删除渠道", tint = colors.accentWarm, modifier = Modifier.size(14.dp))
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
                        text = "分类字典管理",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // RC-142 已废除「重置预设」能力，全端不再提供「恢复默认分类」入口
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
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
                        placeholder = "新分类名称…",
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
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "删除分类", tint = colors.accentWarm, modifier = Modifier.size(15.dp))
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
