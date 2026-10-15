package com.looseleaf.app.ui.projectswishes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
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
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.CityMemoryDrawerResponse
import com.looseleaf.app.model.CreateFootprintRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.ResolveWishRequest
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperBadge
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

/**
 * 造物、心愿与山川足迹视界 (ProjectsWishesScreen)
 * 严格遵从 docs/README.md §三.8 记录为一切的母体原则
 */
@Composable
fun ProjectsWishesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    var selectedTab by remember { mutableStateOf(0) } // 0: 造物, 1: 心愿, 2: 足迹
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var wishes by remember { mutableStateOf<List<Wish>>(emptyList()) }
    var footprints by remember { mutableStateOf<List<Footprint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var resolvingWish by remember { mutableStateOf<Wish?>(null) }
    var selectedDrawerFootprint by remember { mutableStateOf<Footprint?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var wishToDelete by remember { mutableStateOf<Wish?>(null) }

    suspend fun reloadAll() {
        isLoading = true
        loadError = null
        try {
            projects = apiClient.fetchProjects()
            wishes = apiClient.fetchWishes()
            footprints = apiClient.fetchFootprints()
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "造物 · 心愿 · 山川",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "生命之流的二级投影 · 真实心迹与行迹",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }
                TextButton(
                    onClick = {
                        haptics.tapKey()
                        showCreateDialog = true
                    }
                ) {
                    Text(
                        text = when (selectedTab) {
                            0 -> "+ 新建造物"
                            1 -> "+ 许下心愿"
                            else -> "+ 点亮足迹"
                        },
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                }
            }

            // Tab 切换器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.archiveSubtle)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(16.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabs = listOf("造物 (${projects.size})", "心愿 (${wishes.size})", "山川 (${footprints.size})")
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.archivePaper else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                haptics.tapKey()
                                selectedTab = index
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                }
            } else {
                when (selectedTab) {
                    0 -> ProjectsList(
                        projects = projects,
                        onProjectClick = { editingProject = it },
                        modifier = Modifier.weight(1f)
                    )
                    1 -> WishesList(
                        wishes = wishes,
                        onResolveClick = { resolvingWish = it },
                        onDeleteClick = { wishToDelete = it },
                        modifier = Modifier.weight(1f)
                    )
                    2 -> FootprintsList(
                        footprints = footprints,
                        onFootprintClick = { selectedDrawerFootprint = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showCreateDialog) {
            var inputTitle by remember { mutableStateOf("") }
            var inputNote by remember { mutableStateOf("") }
            var inputThird by remember { mutableStateOf("") }
            var wishKind by remember { mutableStateOf("item") }

            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "新建造物项目"
                            1 -> "许下新心愿"
                            else -> "点亮山川足迹"
                        },
                        fontFamily = ArchiveFontFamilies.EditorialSerif
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedTab == 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                                    FilterChip(
                                        selected = wishKind == k,
                                        onClick = { wishKind = k },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = {
                                Text(
                                    when (selectedTab) {
                                        0 -> "造物名称"
                                        1 -> "心愿标题"
                                        else -> "城市名称 (如: 杭州市、大理市)"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedTab == 1) {
                            OutlinedTextField(
                                value = inputThird,
                                onValueChange = { inputThird = it },
                                label = { Text("预算金额 (元，可选)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (selectedTab == 2) {
                            OutlinedTextField(
                                value = inputThird,
                                onValueChange = { inputThird = it },
                                label = { Text("旅程标题 (可选)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = inputNote,
                            onValueChange = { inputNote = it },
                            label = { Text("备注或随笔手记", fontSize = 12.sp) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = inputTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    when (selectedTab) {
                                        0 -> apiClient.createProject(
                                            CreateProjectRequest(
                                                title = inputTitle.trim(),
                                                description = inputNote.trim()
                                            )
                                        )
                                        1 -> apiClient.createWish(
                                            CreateWishRequest(
                                                title = inputTitle.trim(),
                                                kind = wishKind,
                                                note = inputNote.trim().takeIf { it.isNotBlank() },
                                                budget = FormatUtils.yuanStringToCents(inputThird)
                                            )
                                        )
                                        else -> {
                                            val city = inputTitle.trim()
                                            val code = CityDictionary.codeForLocation(city) ?: "330100"
                                            apiClient.createFootprint(
                                                CreateFootprintRequest(
                                                    cityCode = code,
                                                    cityName = city,
                                                    title = inputThird.trim().takeIf { it.isNotBlank() },
                                                    visitedAt = FormatUtils.beijingNowIsoString(),
                                                    note = inputNote.trim().takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                    haptics.stampSave()
                                    showCreateDialog = false
                                    reloadAll()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("保存归册", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("取消", color = colors.inkSecondary)
                    }
                }
            )
        }

        resolvingWish?.let { wish ->
            var costInput by remember(wish.id) {
                mutableStateOf(FormatUtils.centsToYuanString(wish.budget, includeSymbol = false))
            }
            var resType by remember(wish.id) { mutableStateOf("exact") }
            var entryNote by remember(wish.id) { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { resolvingWish = null },
                title = { Text("圆满心愿 · ${wish.title}", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("exact" to "如愿拿下", "altered" to "替代方案", "relinquished" to "理性拔草").forEach { (k, label) ->
                                FilterChip(
                                    selected = resType == k,
                                    onClick = { resType = k },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (resType != "relinquished") {
                            OutlinedTextField(
                                value = costInput,
                                onValueChange = { costInput = it },
                                label = { Text("实付金额 (元)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = entryNote,
                            onValueChange = { entryNote = it },
                            label = { Text("圆梦感言手记 (同步写入时间流)", fontSize = 12.sp) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val isRel = resType == "relinquished"
                                    val cents = if (isRel) 0L else FormatUtils.yuanStringToCents(costInput)
                                    apiClient.resolveWish(
                                        id = wish.id,
                                        req = ResolveWishRequest(
                                            fulfilledAt = FormatUtils.beijingNowIsoString(),
                                            actualCost = cents,
                                            resolutionType = resType,
                                            actualTitle = wish.title,
                                            createTransaction = !isRel && cents > 0,
                                            createGearAsset = !isRel && wish.kind == "item",
                                            createLifeEntry = true,
                                            createFootprint = !isRel && wish.kind == "travel",
                                            entryContent = entryNote.takeIf { it.isNotBlank() }
                                        )
                                    )
                                    haptics.stampSave()
                                    resolvingWish = null
                                    reloadAll()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "归册失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("确认归册", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resolvingWish = null }) {
                        Text("取消", color = colors.inkSecondary)
                    }
                }
            )
        }

        selectedDrawerFootprint?.let { foot ->
            var drawerData by remember(foot.cityCode) { mutableStateOf<CityMemoryDrawerResponse?>(null) }
            LaunchedEffect(foot.cityCode) {
                runCatching {
                    drawerData = apiClient.fetchCityMemoryDrawer(foot.cityCode)
                }
            }
            AlertDialog(
                onDismissRequest = { selectedDrawerFootprint = null },
                title = { Text("${foot.cityName} · 城市回忆抽屉", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                text = {
                    val d = drawerData
                    if (d == null) {
                        Text("正在检索属地散页与旅途消费…", fontSize = 12.sp, color = colors.inkMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "累计到访 ${d.visitStats.totalVisits} 次 · 散页 ${d.entries.size} 篇",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                            if (d.ledgerSummary.totalSpent > 0) {
                                Text(
                                    text = "属地累计花销: ${FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.accentMoss
                                )
                            }
                            d.entries.take(4).forEach { entry ->
                                Text(
                                    text = "• ${entry.title.ifBlank { "散页手记" }} (${FormatUtils.formatDisplayDate(entry.occurredAt)})",
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedDrawerFootprint = null }) {
                        Text("合上抽屉", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    apiClient.deleteFootprint(foot.id)
                                    Toast.makeText(context, "足迹已移除", Toast.LENGTH_SHORT).show()
                                }
                                selectedDrawerFootprint = null
                                reloadAll()
                            }
                        }
                    ) {
                        Text("移除足迹", color = colors.accentWarm)
                    }
                }
            )
        }

        // 编辑/删除造物项目对话框
        editingProject?.let { proj ->
            var editTitle by remember(proj.id) { mutableStateOf(proj.title) }
            var editDesc by remember(proj.id) { mutableStateOf(proj.description ?: "") }
            var editStatus by remember(proj.id) { mutableStateOf(proj.status ?: "进行中") }
            var editGithub by remember(proj.id) { mutableStateOf(proj.githubUrl ?: "") }

            AlertDialog(
                onDismissRequest = { editingProject = null },
                title = { Text("修订造物 · ${proj.title}", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("进行中", "已完成", "维护中", "已搁置").forEach { s ->
                                FilterChip(
                                    selected = editStatus == s,
                                    onClick = { editStatus = s },
                                    label = { Text(s, fontSize = 11.sp) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("造物名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text("项目概述与故事") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editGithub,
                            onValueChange = { editGithub = it },
                            label = { Text("仓库/发布链接 (可选)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = editTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    apiClient.updateProject(
                                        id = proj.id,
                                        req = CreateProjectRequest(
                                            title = editTitle.trim(),
                                            description = editDesc.trim(),
                                            status = editStatus,
                                            githubUrl = editGithub.trim().takeIf { it.isNotBlank() }
                                        )
                                    )
                                    Toast.makeText(context, "造物项目已更新", Toast.LENGTH_SHORT).show()
                                }
                                editingProject = null
                                reloadAll()
                            }
                        }
                    ) {
                        Text("保存更新", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        apiClient.deleteProject(proj.id)
                                        Toast.makeText(context, "造物项目已删除", Toast.LENGTH_SHORT).show()
                                    }
                                    editingProject = null
                                    reloadAll()
                                }
                            }
                        ) {
                            Text("删除项目", color = colors.accentWarm)
                        }
                        TextButton(onClick = { editingProject = null }) {
                            Text("取消", color = colors.inkSecondary)
                        }
                    }
                }
            )
        }

        // 删除心愿确认对话框
        wishToDelete?.let { wish ->
            AlertDialog(
                onDismissRequest = { wishToDelete = null },
                title = { Text("移除心愿？", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                text = { Text("确认从心愿清单中移除《${wish.title}》？此操作不可逆。", fontFamily = ArchiveFontFamilies.BodySans) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    apiClient.deleteWish(wish.id)
                                    Toast.makeText(context, "心愿已移除", Toast.LENGTH_SHORT).show()
                                }
                                wishToDelete = null
                                reloadAll()
                            }
                        }
                    ) {
                        Text("确认移除", color = colors.accentWarm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { wishToDelete = null }) {
                        Text("取消", color = colors.inkSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun ProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (projects.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text("暂无造物记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(projects, key = { it.id }) { proj ->
            PaperCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProjectClick(proj) }
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proj.title,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = colors.inkPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PaperBadge(text = proj.status ?: "进行中")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "编辑",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!proj.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = proj.description,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }

                    if (!proj.githubUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = proj.githubUrl,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WishesList(
    wishes: List<Wish>,
    onResolveClick: (Wish) -> Unit,
    onDeleteClick: (Wish) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var statusFilter by remember { mutableStateOf("all") }

    val filteredWishes = remember(wishes, statusFilter) {
        if (statusFilter == "all") wishes
        else wishes.filter { it.status == statusFilter }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 状态筛选条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusFilters = listOf(
                "all" to "全部 (${wishes.size})",
                "wanting" to "向往中 (${wishes.count { it.status == "wanting" }})",
                "fulfilled" to "已圆满 (${wishes.count { it.status == "fulfilled" }})",
                "relinquished" to "已拔草 (${wishes.count { it.status == "relinquished" }})"
            )
            statusFilters.forEach { (k, label) ->
                val isSelected = statusFilter == k
                FilterChip(
                    selected = isSelected,
                    onClick = { statusFilter = k },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        if (filteredWishes.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().weight(1f)) {
                Text("暂无匹配的心愿记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredWishes, key = { it.id }) { wish ->
                val isFulfilled = wish.status == "fulfilled" || wish.status == "relinquished"
                PaperCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isFulfilled) Icons.Outlined.CheckCircle else Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = if (isFulfilled) colors.accentMoss else colors.accentWarm,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = wish.title,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = colors.inkPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isFulfilled) {
                                    TextButton(onClick = { onResolveClick(wish) }) {
                                        Text("达成归册", fontSize = 12.sp, color = colors.accentMoss)
                                    }
                                } else {
                                    PaperBadge(text = if (wish.status == "relinquished") "已拔草" else "已圆满")
                                }
                                IconButton(
                                    onClick = { onDeleteClick(wish) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "删除心愿",
                                        tint = colors.inkMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!wish.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = wish.note,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp,
                                color = colors.inkSecondary
                            )
                        }

                        if (wish.budget > 0 || wish.actualCost > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "预算: ${FormatUtils.centsToYuanString(wish.budget)} · 实际: ${FormatUtils.centsToYuanString(wish.actualCost)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FootprintsList(
    footprints: List<Footprint>,
    onFootprintClick: (Footprint) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (footprints.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text("暂无山川足迹记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(footprints, key = { it.id }) { foot ->
            PaperCard(
                modifier = Modifier.clickable { onFootprintClick(foot) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左联：副券存根 (目的地中文名 + 到访日期)
                    Column(
                        modifier = Modifier
                            .width(108.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = foot.cityName,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = foot.visitedAt?.takeIf { it.isNotBlank() }?.let { FormatUtils.formatDisplayDate(it) } ?: "行迹存根",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 10.sp,
                            color = colors.inkMuted
                        )
                    }

                    // 齿孔撕票分割线
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .padding(vertical = 8.dp)
                            .background(colors.borderDivider)
                    )

                    // 右联：正券手记 + 邮戳印记
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = foot.title?.takeIf { it.isNotBlank() } ?: "${foot.cityName} · 行旅手记",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = colors.inkPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "NO.${foot.cityCode}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 10.sp,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = foot.note?.takeIf { it.isNotBlank() } ?: "行至此处，留下一纸驻足印记。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
