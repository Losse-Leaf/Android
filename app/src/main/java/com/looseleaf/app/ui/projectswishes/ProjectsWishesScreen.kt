package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.CityMemoryDrawerResponse
import com.looseleaf.app.model.CreateFootprintRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.ProvinceMemoryDrawerResponse
import com.looseleaf.app.model.ResolveWishRequest
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDateTime
import kotlinx.coroutines.launch
/**
 * 造物、心愿与山川足迹视界 (ProjectsWishesScreen)
 * 严格遵从 docs/README.md §三.8 记录为一切的母体原则
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsWishesScreen(
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    /**
     * 内部 tab 切换回传（0 造物 / 1 心愿 / 2 山川）：
     * 底栏「足迹」「造物」共用本页，必须同步 currentRoute 才不会出现
     * 「内容已切换、底栏还高亮在旧项」的错位。
     */
    onTabChanged: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { com.looseleaf.app.data.sync.SyncManager.getInstance(context) }

    // 不带 initialTab 作 key：从「足迹」切到「造物」时 route 会变化并触发重组，
    // 若 key 参与 rememberSaveable 会把内部 tab 重置回 initialTab，用户刚点的 tab 被冲掉
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) } // 0: 造物, 1: 心愿, 2: 足迹
    val projects by syncManager.projectsFlow.collectAsStateWithLifecycle()
    val wishes by syncManager.wishesFlow.collectAsStateWithLifecycle()
    val footprints by syncManager.footprintsFlow.collectAsStateWithLifecycle()

    var hasInitializedProjectsWishes by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var resolvingWish by remember { mutableStateOf<Wish?>(null) }
    var selectedDrawerFootprint by remember { mutableStateOf<Footprint?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var editingWish by remember { mutableStateOf<Wish?>(null) }
    var editingFootprint by remember { mutableStateOf<Footprint?>(null) }
    var selectedProvinceDrawerAdcode by remember { mutableStateOf<Pair<String, String>?>(null) }
    var wishToDelete by remember { mutableStateOf<Wish?>(null) }

    suspend fun reloadAll(forceLoading: Boolean = false) {
        if (forceLoading || (projects.isEmpty() && wishes.isEmpty() && footprints.isEmpty())) {
            isLoading = true
        }
        loadError = null
        // pullRemoteData 自身返回 Result，不会抛异常；必须读 Result 才能拿到失败原因
        syncManager.pullRemoteData()
            .onFailure { loadError = it.message ?: "同步失败" }
        isLoading = false
    }

    LaunchedEffect(hasInitializedProjectsWishes) {
        if (!hasInitializedProjectsWishes) {
            reloadAll(forceLoading = false)
            hasInitializedProjectsWishes = true
        }
    }

    NoiseBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
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
                                selectedTab = index
                                onTabChanged?.invoke(index)
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

            // 同步失败不再只写进一个没人读的 state：给出可见的重试入口
            loadError?.let { message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accentWarm.copy(alpha = 0.10f))
                        .clickable(role = Role.Button) {
                            scope.launch { reloadAll(forceLoading = true) }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "同步未完成：$message · 点此重试",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 11.sp,
                        color = colors.accentWarm,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoading && projects.isEmpty() && wishes.isEmpty() && footprints.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                }
            } else {
                // 下拉刷新（AOPT-06）：三个列表共用同一次全量同步
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { scope.launch { reloadAll(forceLoading = true) } },
                    modifier = Modifier.weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> ProjectsList(
                            projects = projects,
                            onProjectClick = { editingProject = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> WishesList(
                            wishes = wishes,
                            onResolveClick = { resolvingWish = it },
                            onEditClick = { editingWish = it },
                            onDeleteClick = { wishToDelete = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> FootprintsList(
                            footprints = footprints,
                            onFootprintClick = { selectedDrawerFootprint = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            var inputTitle by remember { mutableStateOf("") }
            var inputSubtitle by remember { mutableStateOf("") }
            var inputNote by remember { mutableStateOf("") }
            var inputThird by remember { mutableStateOf("") }
            var wishKind by remember { mutableStateOf("item") }
            var fpCityCode by remember { mutableStateOf("") }
            var fpCityName by remember { mutableStateOf("") }
            var wishCityCode by remember { mutableStateOf("") }
            var wishCityName by remember { mutableStateOf("") }
            var fpVisitedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone)) }
            var fpIsMultiDay by remember { mutableStateOf(false) }
            var fpDepartedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone).plusDays(2)) }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            val isSaveEnabled = if (selectedTab == 2) fpCityName.isNotBlank() else inputTitle.isNotBlank()

            ModalBottomSheet(
                onDismissRequest = { showCreateDialog = false },
                sheetState = sheetState,
                containerColor = colors.archivePaper,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                // navigationBarsPadding（AOPT-02b/08b）：软键盘收起时内容不再被
                // 系统手势横条压住；ArchiveBottomSheet 一直是这样做的，这里补齐
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // 顶栏：取消 / 标题 / 保存
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showCreateDialog = false },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("取消", color = colors.inkSecondary, fontSize = 14.sp)
                        }

                        Text(
                            text = when (selectedTab) {
                                0 -> "《新建造物》"
                                1 -> "《许下心愿》"
                                else -> "《点亮足迹》"
                            },
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )

                        TextButton(
                            enabled = isSaveEnabled,
                            onClick = {
                                scope.launch {
                                    try {
                                        when (selectedTab) {
                                            0 -> apiClient.createProject(
                                                CreateProjectRequest(
                                                    title = inputTitle.trim(),
                                                    subtitle = inputSubtitle.trim().takeIf { it.isNotBlank() },
                                                    description = inputNote.trim()
                                                )
                                            )
                                            1 -> apiClient.createWish(
                                                CreateWishRequest(
                                                    title = inputTitle.trim(),
                                                    kind = wishKind,
                                                    note = inputNote.trim().takeIf { it.isNotBlank() },
                                                    budget = FormatUtils.yuanStringToCents(inputThird),
                                                    cityCode = wishCityCode.takeIf { it.isNotBlank() },
                                                    cityName = wishCityName.takeIf { it.isNotBlank() }
                                                )
                                            )
                                            else -> {
                                                // isSaveEnabled 已保证足迹页 cityName 非空（L270），
                                                // 不需要「杭州」类魔法兜底；cityCode 与 cityName 同源由城市选择器写入
                                                apiClient.createFootprint(
                                                    CreateFootprintRequest(
                                                        cityCode = fpCityCode,
                                                        cityName = fpCityName,
                                                        title = inputThird.trim().takeIf { it.isNotBlank() },
                                                        visitedAt = FormatUtils.toBeijingIsoString(fpVisitedAt),
                                                        departedAt = if (fpIsMultiDay) FormatUtils.toBeijingIsoString(fpDepartedAt) else null,
                                                        note = inputNote.trim().takeIf { it.isNotBlank() }
                                                    )
                                                )
                                            }
                                        }
                                        showCreateDialog = false
                                        reloadAll()
                                    } catch (e: Exception) {
                                        ArchiveToastManager.show("创建失败: ${e.message}", isError = true)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "确认保存",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isSaveEnabled) colors.accentWarm else colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedTab == 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                                PaperFilterPill(
                                    selected = wishKind == k,
                                    onClick = { wishKind = k },
                                    label = label
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (selectedTab == 2) {
                        // 足迹：标准行政区划城市选择器 + 到访时刻补记
                        PaperCityPickerField(
                            selectedCityCode = fpCityCode,
                            selectedCityName = fpCityName,
                            onCitySelected = { c, n -> fpCityCode = c; fpCityName = n },
                            placeholder = "选择行政区划城市…"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PaperDateTimePickerField(
                            selectedDateTime = fpVisitedAt,
                            onDateTimeSelected = { fpVisitedAt = it },
                            label = "启程/到访时刻"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = fpIsMultiDay,
                                onCheckedChange = { fpIsMultiDay = it },
                                colors = CheckboxDefaults.colors(checkedColor = colors.accentWarm)
                            )
                            Text(
                                text = "多日游历（选填返程日期）",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkPrimary
                            )
                        }
                        if (fpIsMultiDay) {
                            Spacer(modifier = Modifier.height(6.dp))
                            PaperDateTimePickerField(
                                selectedDateTime = fpDepartedAt,
                                onDateTimeSelected = { fpDepartedAt = it },
                                label = "返程/离程时刻"
                            )
                        }
                    } else {
                        // 大号标题输入
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.accentWarm),
                            decorationBox = { innerTextField ->
                                if (inputTitle.isEmpty()) {
                                    Text(
                                        text = when (selectedTab) {
                                            0 -> "造物名称…"
                                            else -> "心愿标题…"
                                        },
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 20.sp,
                                        color = colors.inkMuted.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (selectedTab == 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputSubtitle,
                                onValueChange = { inputSubtitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 14.sp,
                                    color = colors.inkSecondary
                                ),
                                cursorBrush = SolidColor(colors.accentWarm),
                                decorationBox = { innerTextField ->
                                    if (inputSubtitle.isEmpty()) {
                                        Text(
                                            text = "副标题或设计注记 (选填)…",
                                            fontSize = 14.sp,
                                            color = colors.inkMuted.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        if (selectedTab == 1 && wishKind == "travel") {
                            Spacer(modifier = Modifier.height(10.dp))
                            PaperCityPickerField(
                                selectedCityCode = wishCityCode,
                                selectedCityName = wishCityName,
                                onCitySelected = { c, n -> wishCityCode = c; wishCityName = n },
                                placeholder = "点选目的地城市…"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedTab == 1 || selectedTab == 2) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputThird,
                            onValueChange = { inputThird = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.accentWarm),
                            decorationBox = { innerTextField ->
                                if (inputThird.isEmpty()) {
                                    Text(
                                        text = if (selectedTab == 1) "预算金额 (元)" else "旅程标题",
                                        fontSize = 15.sp,
                                        color = colors.inkMuted.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 开阔手记书写区域
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputNote,
                        onValueChange = { inputNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 120.dp, max = 240.dp),
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 14.5.sp,
                            lineHeight = 24.sp,
                            color = colors.inkPrimary
                        ),
                        cursorBrush = SolidColor(colors.accentWarm),
                        decorationBox = { innerTextField ->
                            if (inputNote.isEmpty()) {
                                Text(
                                    text = "写下背后的故事、随笔手记或愿景备忘…",
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 14.5.sp,
                                    lineHeight = 24.sp,
                                    color = colors.inkMuted.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        resolvingWish?.let { wish ->
            var costInput by remember(wish.id) {
                mutableStateOf(FormatUtils.centsToYuanString(wish.budget, includeSymbol = false))
            }
            var resType by remember(wish.id) { mutableStateOf("exact") }
            var actualTitleInput by remember(wish.id) { mutableStateOf(wish.title) }
            var entryNote by remember(wish.id) { mutableStateOf("") }
            var fulfilledAtDt by remember(wish.id) {
                mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone))
            }
            var createTx by remember(wish.id) { mutableStateOf(wish.budget > 0) }
            var createGear by remember(wish.id) { mutableStateOf(wish.kind == "item") }
            var createEntry by remember(wish.id) { mutableStateOf(true) }
            var createFp by remember(wish.id) { mutableStateOf(wish.kind == "travel") }

            ArchiveBottomSheet(
                onDismissRequest = { resolvingWish = null },
                title = "圆满心愿 · ${wish.title}",
                actionButtonText = "确认归册",
                onActionClick = {
                    scope.launch {
                        try {
                            val isRel = resType == "relinquished"
                            val cents = if (isRel) 0L else FormatUtils.yuanStringToCents(costInput)
                            apiClient.resolveWish(
                                id = wish.id,
                                req = ResolveWishRequest(
                                    fulfilledAt = FormatUtils.toBeijingIsoString(fulfilledAtDt),
                                    actualCost = cents,
                                    resolutionType = resType,
                                    actualTitle = actualTitleInput.ifBlank { wish.title },
                                    createTransaction = !isRel && createTx && cents > 0,
                                    createGearAsset = !isRel && createGear && wish.kind == "item",
                                    createLifeEntry = createEntry,
                                    createFootprint = !isRel && createFp && wish.kind == "travel",
                                    entryContent = entryNote.takeIf { it.isNotBlank() }
                                )
                            )
                            resolvingWish = null
                            reloadAll()
                        } catch (e: Exception) {
                            ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                        }
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("exact" to "如愿拿下", "altered" to "替代方案", "relinquished" to "理性拔草").forEach { (k, label) ->
                            PaperFilterPill(
                                selected = resType == k,
                                onClick = { resType = k },
                                label = label
                            )
                        }
                    }
                    PaperDateTimePickerField(
                        selectedDateTime = fulfilledAtDt,
                        onDateTimeSelected = { fulfilledAtDt = it },
                        label = "圆满时刻"
                    )
                    if (resType != "relinquished") {
                        PaperTextField(
                            value = actualTitleInput,
                            onValueChange = { actualTitleInput = it },
                            label = "实获好物/方案名称",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PaperTextField(
                            value = costInput,
                            onValueChange = { costInput = it },
                            label = "实付金额 (元)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaperFilterPill(
                                selected = createTx,
                                onClick = { createTx = !createTx },
                                label = if (createTx) "✓ 记账" else "不记账"
                            )
                            if (wish.kind == "item") {
                                PaperFilterPill(
                                    selected = createGear,
                                    onClick = { createGear = !createGear },
                                    label = if (createGear) "✓ 建固资" else "不建固资"
                                )
                            }
                            if (wish.kind == "travel") {
                                PaperFilterPill(
                                    selected = createFp,
                                    onClick = { createFp = !createFp },
                                    label = if (createFp) "✓ 亮足迹" else "不建足迹"
                                )
                            }
                            PaperFilterPill(
                                selected = createEntry,
                                onClick = { createEntry = !createEntry },
                                label = if (createEntry) "✓ 伴生手记" else "不写手记"
                            )
                        }
                    }
                    PaperTextEditor(
                        value = entryNote,
                        onValueChange = { entryNote = it },
                        label = "圆梦感言手记 (同步写入时间流)",
                        minHeight = 80.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        selectedDrawerFootprint?.let { foot ->
            var drawerData by remember(foot.cityCode) { mutableStateOf<CityMemoryDrawerResponse?>(null) }
            var drawerError by remember(foot.cityCode) { mutableStateOf<String?>(null) }
            LaunchedEffect(foot.cityCode) {
                drawerError = null
                runCatching {
                    drawerData = apiClient.fetchCityMemoryDrawer(foot.cityCode)
                }.onFailure { drawerError = it.message ?: "网络异常" }
            }
            ArchiveBottomSheet(
                onDismissRequest = { selectedDrawerFootprint = null },
                title = "${foot.cityName} · 城市回忆抽屉",
                actionButtonText = "合上",
                onActionClick = { selectedDrawerFootprint = null }
            ) {
                val d = drawerData
                if (d == null) {
                    // 区分 loading / error：断网时不能永远停在「正在检索」
                    if (drawerError != null) {
                        Text(
                            text = "属地检索失败：$drawerError · 点此重试",
                            fontSize = 12.sp,
                            color = colors.accentWarm,
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .clickable(role = Role.Button) {
                                    drawerError = null
                                    scope.launch {
                                        runCatching {
                                            drawerData = apiClient.fetchCityMemoryDrawer(foot.cityCode)
                                        }.onFailure { drawerError = it.message ?: "网络异常" }
                                    }
                                }
                        )
                    } else {
                        Text(
                            text = "正在检索属地散页与旅途消费…",
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "累计到访 ${d.visitStats.totalVisits} 次 · 散页 ${d.entries.size} 篇",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentWarm
                            )
                            if (d.ledgerSummary.totalSpent > 0) {
                                Text(
                                    text = "属地花销: ${FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.accentMoss
                                )
                            }
                        }

                        if (d.entries.isNotEmpty()) {
                            Text(
                                text = "属地散页手记",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = colors.inkSecondary
                            )
                            d.entries.take(5).forEach { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.archiveSubtle)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = entry.title.ifBlank { "散页手记" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = FormatUtils.formatDisplayDate(entry.occurredAt),
                                        fontSize = 11.sp,
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val prefix = if (foot.cityCode.length >= 2) foot.cityCode.substring(0, 2) else ""
                                    val prov = CityDictionary.provincePrefixMap[prefix] ?: "属地省份"
                                    selectedProvinceDrawerAdcode = foot.cityCode to prov
                                }
                            ) {
                                Text("省级回忆抽屉", fontSize = 12.sp, color = colors.accentWarm)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        editingFootprint = foot
                                    }
                                ) {
                                    Text("编辑票根", fontSize = 12.sp, color = colors.inkPrimary)
                                }

                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                apiClient.deleteFootprint(foot.id)
                                                ArchiveToastManager.show("足迹已移除")
                                            }
                                            selectedDrawerFootprint = null
                                            reloadAll()
                                        }
                                    }
                                ) {
                                    Text("移除", fontSize = 12.sp, color = colors.accentWarm)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 编辑/删除造物项目抽屉
        editingProject?.let { proj ->
            var editTitle by remember(proj.id) { mutableStateOf(proj.title) }
            var editSubtitle by remember(proj.id) { mutableStateOf(proj.subtitle ?: "") }
            var editDesc by remember(proj.id) { mutableStateOf(proj.description ?: "") }
            var editStatus by remember(proj.id) { mutableStateOf(proj.status ?: "进行中") }
            var editGithub by remember(proj.id) { mutableStateOf(proj.githubUrl ?: "") }

            ArchiveBottomSheet(
                onDismissRequest = { editingProject = null },
                title = "修订造物 · ${proj.title}",
                actionButtonText = "保存更新",
                isActionEnabled = editTitle.isNotBlank(),
                onActionClick = {
                    scope.launch {
                        runCatching {
                            apiClient.updateProject(
                                id = proj.id,
                                req = CreateProjectRequest(
                                    title = editTitle.trim(),
                                    subtitle = editSubtitle.trim().takeIf { it.isNotBlank() },
                                    description = editDesc.trim(),
                                    story = proj.story,
                                    category = proj.category,
                                    status = editStatus,
                                    githubUrl = editGithub.trim().takeIf { it.isNotBlank() },
                                    featured = proj.featured ?: false,
                                    order = proj.order ?: 0
                                )
                            )
                            ArchiveToastManager.show("造物项目已更新")
                        }
                        editingProject = null
                        reloadAll()
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("进行中", "已完成", "维护中", "已搁置").forEach { s ->
                            PaperFilterPill(
                                selected = editStatus == s,
                                onClick = { editStatus = s },
                                label = s
                            )
                        }
                    }
                    PaperTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = "造物名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = editSubtitle,
                        onValueChange = { editSubtitle = it },
                        label = "副标题或设计注记",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextEditor(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = "项目概述与故事",
                        minHeight = 88.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PaperTextField(
                        value = editGithub,
                        onValueChange = { editGithub = it },
                        label = "仓库/发布链接",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    apiClient.deleteProject(proj.id)
                                    ArchiveToastManager.show("造物项目已删除")
                                }
                                editingProject = null
                                reloadAll()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("删除此造物项目", fontSize = 12.sp, color = colors.accentWarm)
                    }
                }
            }
        }

        // 删除心愿确认对话框
        wishToDelete?.let { wish ->
            ArchiveConfirmDialog(
                title = "移除心愿？",
                content = "确认从心愿清单中移除《${wish.title}》？此操作不可逆。",
                confirmText = "确认移除",
                isDestructive = true,
                onConfirm = {
                    scope.launch {
                        runCatching {
                            apiClient.deleteWish(wish.id)
                            ArchiveToastManager.show("心愿已移除")
                        }
                        wishToDelete = null
                        reloadAll()
                    }
                },
                onDismiss = { wishToDelete = null }
            )
        }

        // 编辑心愿抽屉
        editingWish?.let { wish ->
            EditWishSheetDialog(
                wish = wish,
                apiClient = apiClient,
                onDismiss = { editingWish = null },
                onSaved = { scope.launch { reloadAll() } }
            )
        }

        // 编辑足迹票根抽屉
        editingFootprint?.let { foot ->
            EditFootprintSheetDialog(
                footprint = foot,
                apiClient = apiClient,
                onDismiss = { editingFootprint = null },
                onSaved = { scope.launch { reloadAll() } }
            )
        }

        // 省级回忆抽屉
        selectedProvinceDrawerAdcode?.let { (adcode, provName) ->
            ProvinceMemoryDrawerBottomSheet(
                adcode = adcode,
                provinceName = provName,
                apiClient = apiClient,
                onDismiss = { selectedProvinceDrawerAdcode = null }
            )
        }
    }
}
}
