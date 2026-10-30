package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CategoryOption
import com.looseleaf.app.model.CreateCreditChannelRequest
import com.looseleaf.app.model.CreditChannel
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.launch

/**
 * 信贷渠道管理抽屉 (LedgerSheet)
 * - 新增行：渠道名称（占位符"渠道名称"）+ 带标签"还款日"的日期选择（1-31，右侧显示"日"）+ 文字按钮"新增"
 * - 列表分两组：分组标题"系统内置"与"我的渠道"；每项第二行写"每月 N 日还款"
 * - 系统内置项不可删除；自定义项左滑删除并支持撤销
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreditChannelsManagerDialog(
    creditChannels: List<CreditChannel>,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onChannelsChanged: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String, onAction: () -> Unit) -> Unit = { _, _, _ -> }
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()
    var newChannelName by remember { mutableStateOf("") }
    var newChannelDay by remember { mutableStateOf("10") }
    var isSubmitting by remember { mutableStateOf(false) }

    val builtinChannels = remember(creditChannels) {
        creditChannels.filter { it.isBuiltin || it.code in setOf("HUABEI", "BAITIAO", "CUSTOM_INSTALLMENT") }
    }
    val customChannels = remember(creditChannels) {
        creditChannels.filterNot { it.isBuiltin || it.code in setOf("HUABEI", "BAITIAO", "CUSTOM_INSTALLMENT") }
    }

    LedgerSheet(
        onDismissRequest = onDismiss,
        title = "信贷渠道",
        cancelText = "关闭"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 新增渠道行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LedgerField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        placeholder = "渠道名称",
                        singleLine = true
                    )
                }
                Box(modifier = Modifier.width(88.dp)) {
                    LedgerField(
                        value = newChannelDay,
                        onValueChange = { newChannelDay = it.filter { c -> c.isDigit() }.take(2) },
                        label = "还款日",
                        placeholder = "10",
                        trailingText = "日",
                        isNumeric = true,
                        singleLine = true
                    )
                }
                LedgerTextButton(
                    text = "新增",
                    enabled = newChannelName.isNotBlank() && !isSubmitting,
                    onClick = {
                        val day = newChannelDay.toIntOrNull()?.coerceIn(1, 31) ?: 10
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                apiClient.createCreditChannel(
                                    CreateCreditChannelRequest(
                                        name = newChannelName.trim(),
                                        repaymentDay = day
                                    )
                                )
                                newChannelName = ""
                                onChannelsChanged()
                            }.onFailure { err ->
                                ArchiveToastManager.show("新增渠道失败: ${err.message}", isError = true)
                            }
                            isSubmitting = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            LedgerDivider()
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                // 1. 系统内置分组
                item(key = "hdr_builtin") {
                    Text(
                        text = "系统内置",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                items(builtinChannels, key = { "builtin_${it.id}" }) { ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = ch.name,
                                style = LedgerTheme.typography.body,
                                fontFamily = LedgerTheme.typography.serif,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "每月 ${ch.repaymentDay} 日还款",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    LedgerDivider(modifier = Modifier.padding(start = 4.dp))
                }

                // 2. 我的渠道分组
                item(key = "hdr_custom") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "我的渠道",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                if (customChannels.isEmpty()) {
                    item(key = "custom_empty") {
                        Text(
                            text = "暂无自定义信贷渠道",
                            style = LedgerTheme.typography.body,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(customChannels, key = { "custom_${it.id}" }) { ch ->
                        LedgerSwipeRevealRow(
                            onDelete = {
                                scope.launch {
                                    runCatching {
                                        apiClient.deleteCreditChannel(ch.id)
                                        onChannelsChanged()
                                        onShowSnackbar("已删除信贷渠道", "撤销") {
                                            scope.launch {
                                                apiClient.createCreditChannel(
                                                    CreateCreditChannelRequest(
                                                        name = ch.name,
                                                        repaymentDay = ch.repaymentDay
                                                    )
                                                )
                                                onChannelsChanged()
                                            }
                                        }
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("删除渠道失败: ${err.message}", isError = true)
                                    }
                                }
                            },
                            backgroundColor = colors.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = ch.name,
                                            style = LedgerTheme.typography.body,
                                            fontFamily = LedgerTheme.typography.serif,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "每月 ${ch.repaymentDay} 日还款",
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                                LedgerDivider(modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分类管理抽屉 (LedgerSheet)
 */
@Composable
internal fun CategoriesManagerDialog(
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onCategoriesChanged: () -> Unit,
    onShowSnackbar: ((String, String, () -> Unit) -> Unit)? = null
) {
    val colors = LedgerTheme.colors
    val scope = rememberCoroutineScope()
    var selectedScope by remember { mutableStateOf("expense") }
    var categories by remember { mutableStateOf<List<CategoryOption>>(emptyList()) }
    var newCategoryName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pickingColorCategory by remember { mutableStateOf<String?>(null) }

    suspend fun loadCats() {
        isLoading = true
        loadError = null
        runCatching {
            categories = apiClient.fetchCategories(selectedScope)
        }.onFailure { err ->
            loadError = err.message ?: "加载分类失败"
        }
        isLoading = false
    }

    LaunchedEffect(selectedScope) {
        loadCats()
    }

    val palette = remember(colors.isDark) { LedgerCategoryColors.palette(colors.isDark) }
    val nextColorIndex = remember(categories, colors.isDark) {
        val usedIndices = categories.mapNotNull { LedgerCategoryColors.getCategoryColorIndex(it.name) }.toSet()
        (0 until palette.size).firstOrNull { it !in usedIndices } ?: (categories.size % palette.size)
    }
    val nextColor = palette[nextColorIndex]

    LedgerSheet(
        onDismissRequest = onDismiss,
        title = "分类管理",
        cancelText = "关闭"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Scope 切换
            val scopes = listOf(
                "expense" to "支出",
                "income" to "收入",
                "payment" to "支付方式",
                "gear" to "固定资产"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scopes.forEach { (sc, label) ->
                    LedgerChoiceItem(
                        text = label,
                        isSelected = selectedScope == sc,
                        onClick = {
                            if (selectedScope != sc) {
                                selectedScope = sc
                                newCategoryName = ""
                                pickingColorCategory = null
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 新增分类输入行：色点预览（自动取下一个未用色）+ LedgerField + 文字按钮"新增"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(nextColor)
                )

                Box(modifier = Modifier.weight(1f)) {
                    LedgerField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = "新分类名称…",
                        singleLine = true
                    )
                }

                LedgerTextButton(
                    text = "新增",
                    enabled = newCategoryName.isNotBlank(),
                    onClick = {
                        val toAdd = newCategoryName.trim()
                        val assignedIdx = nextColorIndex
                        scope.launch {
                            runCatching {
                                apiClient.addCategoryOption(selectedScope, toAdd)
                                LedgerCategoryColors.setCategoryColorIndex(toAdd, assignedIdx)
                                newCategoryName = ""
                                loadCats()
                                onCategoriesChanged()
                            }.onFailure { err ->
                                ArchiveToastManager.show("新增分类失败: ${err.message}", isError = true)
                            }
                        }
                    }
                )
            }

            // 调色板选择器弹层（点击任一行色点展开）
            if (pickingColorCategory != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择色调：",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                    palette.forEachIndexed { idx, col ->
                        val isSelected = LedgerCategoryColors.getCategoryColorIndex(pickingColorCategory!!) == idx
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(col)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier
                                )
                                .clickable {
                                    LedgerCategoryColors.setCategoryColorIndex(pickingColorCategory!!, idx)
                                    pickingColorCategory = null
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LedgerDivider()
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Text(
                    text = "正在加载分类…",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else if (loadError != null) {
                Text(
                    text = "${loadError}，点击重试",
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.expense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { loadCats() } }
                        .padding(vertical = 8.dp)
                )
            } else {
                val builtinCats = categories.filter { it.isBuiltin }
                val customCats = categories.filter { !it.isBuiltin }

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    // 分组 1：系统内置分类
                    if (builtinCats.isNotEmpty()) {
                        item(key = "section_header_builtin") {
                            SectionHeader(title = "系统分类")
                        }
                        items(builtinCats, key = { "builtin_${it.id}" }) { cat ->
                            val catColor = LedgerCategoryColors.colorForCategory(cat.name, colors.isDark)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                        .clickable { pickingColorCategory = cat.name }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = cat.name,
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    color = colors.textPrimary
                                )
                            }
                            LedgerDivider(modifier = Modifier.padding(start = 24.dp))
                        }
                    }

                    // 分组 2：我的自定义分类
                    if (customCats.isNotEmpty()) {
                        item(key = "section_header_custom") {
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionHeader(title = "我的分类")
                        }
                        items(customCats, key = { "custom_${it.id}" }) { cat ->
                            val catColor = LedgerCategoryColors.colorForCategory(cat.name, colors.isDark)
                            LedgerSwipeRevealRow(
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            apiClient.deleteCategoryOption(cat.id)
                                            loadCats()
                                            onCategoriesChanged()
                                            onShowSnackbar?.invoke("已删除分类", "撤销") {
                                                scope.launch {
                                                    apiClient.addCategoryOption(selectedScope, cat.name)
                                                    loadCats()
                                                    onCategoriesChanged()
                                                }
                                            }
                                        }.onFailure {
                                            ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surface)
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                            .clickable { pickingColorCategory = cat.name }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = cat.name,
                                        style = LedgerTheme.typography.body,
                                        fontFamily = LedgerTheme.typography.serif,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                            LedgerDivider(modifier = Modifier.padding(start = 24.dp))
                        }
                    }
                }
            }
        }
    }
}
