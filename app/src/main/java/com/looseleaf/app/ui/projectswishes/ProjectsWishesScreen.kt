package com.looseleaf.app.ui.projectswishes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
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
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
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

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 2)) { 3 }
    val selectedTab = pagerState.currentPage

    val projectsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val wishesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val footprintsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var snackbarData by remember { mutableStateOf<ArchiveSnackbarData?>(null) }

    val currentListState = when (pagerState.currentPage) {
        0 -> projectsListState
        1 -> wishesListState
        else -> footprintsListState
    }

    var isScrollingUp by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentListState) {
        snapshotFlow { currentListState.firstVisibleItemIndex to currentListState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                if (idx > previousIndex || (idx == previousIndex && off > previousOffset + 12)) {
                    isScrollingUp = false
                } else if (idx < previousIndex || (idx == previousIndex && off < previousOffset - 12)) {
                    isScrollingUp = true
                }
                previousIndex = idx
                previousOffset = off
            }
    }

    val isFabVisible by remember {
        derivedStateOf {
            isScrollingUp || currentListState.firstVisibleItemIndex == 0
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onTabChanged?.invoke(pagerState.currentPage)
    }

    LaunchedEffect(initialTab) {
        if (pagerState.currentPage != initialTab) {
            pagerState.scrollToPage(initialTab.coerceIn(0, 2))
        }
    }

    val projects by syncManager.projectsFlow.collectAsStateWithLifecycle()
    val wishes by syncManager.wishesFlow.collectAsStateWithLifecycle()
    val footprints by syncManager.footprintsFlow.collectAsStateWithLifecycle()

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
    // MOB-P0-14：破坏性删除必须二次确认（造物 / 足迹此前是「一点即删」）
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var footprintToDelete by remember { mutableStateOf<Pair<Footprint, Int>?>(null) }

    suspend fun reloadAll(forceLoading: Boolean = false) {
        if (forceLoading || (projects.isEmpty() && wishes.isEmpty() && footprints.isEmpty())) {
            isLoading = true
        }
        loadError = null
        val result = try {
            syncManager.pullProjectsWishesData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onFailure {
            if (it !is CancellationException) {
                loadError = it.message ?: "同步失败"
            }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        // 会话门禁改为**进程级**标志：原先 rememberSaveable 在系统回收进程后恢复时
        // 仍为 true，新会话里造物/心愿/足迹不再首拉，用户对着陈旧数据无提示。
        if (!com.looseleaf.app.data.sync.SyncManager.isSessionPullDone("projects_wishes")) {
            reloadAll(forceLoading = false)
            com.looseleaf.app.data.sync.SyncManager.markSessionPullDone("projects_wishes")
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
                // 顶栏：标题"工坊"与账本、时间流同一规格，右上角不再放"+ 新建…"文字
                ArchiveTopAppBar(
                    title = stringResource(R.string.projects_wishes_screen_text_01),
                    subtitle = null,
                    actions = {}
                )

                // Tab 切换器：纯文字标签 造物 · 心愿 · 山川，选中加粗 + 2dp 赤陶下划线，无框无底色，后跟等宽数字
                ArchiveSegmentedTabs(
                    tabs = listOf("造物", "心愿", "山川"),
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    counts = listOf(projects.size, wishes.size, footprints.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 同步失败重试入口
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
                            painter = painterResource(R.drawable.ic_error_outline),
                            contentDescription = null,
                            tint = colors.accentWarm,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.projects_wishes_screen_text_tpl_01, message),
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
                    // 下拉刷新 + 支持左右滑动的 HorizontalPager
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { scope.launch { reloadAll(forceLoading = true) } },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> ProjectsList(
                                    projects = projects,
                                    onProjectClick = { editingProject = it },
                                    onDeleteClick = { proj ->
                                        scope.launch {
                                            runCatching {
                                                apiClient.deleteProject(proj.id)
                                                reloadAll()
                                                snackbarData = ArchiveSnackbarData(
                                                    message = "已删除造物《${proj.title}》",
                                                    actionLabel = "撤销",
                                                    onActionClick = {
                                                        scope.launch {
                                                            runCatching {
                                                                apiClient.createProject(
                                                                    CreateProjectRequest(
                                                                        title = proj.title,
                                                                        subtitle = proj.subtitle,
                                                                        description = proj.description,
                                                                        story = proj.story,
                                                                        category = proj.category,
                                                                        status = proj.status ?: "进行中",
                                                                        githubUrl = proj.githubUrl,
                                                                        order = proj.order ?: 0
                                                                    )
                                                                )
                                                                reloadAll()
                                                            }
                                                        }
                                                    }
                                                )
                                            }.onFailure {
                                                ArchiveToastManager.show("删除造物失败: ${it.message}", isError = true)
                                            }
                                        }
                                    },
                                    onNewProjectClick = { showCreateDialog = true },
                                    listState = projectsListState,
                                    modifier = Modifier.fillMaxSize()
                                )
                                1 -> WishesList(
                                    wishes = wishes,
                                    onResolveClick = { resolvingWish = it },
                                    onEditClick = { editingWish = it },
                                    onDeleteClick = { wish ->
                                        scope.launch {
                                            runCatching {
                                                apiClient.deleteWish(wish.id)
                                                reloadAll()
                                                snackbarData = ArchiveSnackbarData(
                                                    message = "已移除心愿《${wish.title}》",
                                                    actionLabel = "撤销",
                                                    onActionClick = {
                                                        scope.launch {
                                                            runCatching {
                                                                apiClient.createWish(
                                                                    CreateWishRequest(
                                                                        title = wish.title,
                                                                        kind = wish.kind ?: "item",
                                                                        budget = wish.budget,
                                                                        spec = wish.spec,
                                                                        cityCode = wish.cityCode,
                                                                        cityName = wish.cityName,
                                                                        note = wish.note,
                                                                        targetDate = wish.targetDate
                                                                    )
                                                                )
                                                                reloadAll()
                                                            }
                                                        }
                                                    }
                                                )
                                            }.onFailure {
                                                ArchiveToastManager.show("移除心愿失败: ${it.message}", isError = true)
                                            }
                                        }
                                    },
                                    onNewWishClick = { showCreateDialog = true },
                                    listState = wishesListState,
                                    modifier = Modifier.fillMaxSize()
                                )
                                2 -> FootprintsList(
                                    footprints = footprints,
                                    onFootprintClick = { selectedDrawerFootprint = it },
                                    onNewFootprintClick = { showCreateDialog = true },
                                    listState = footprintsListState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // 新增入口：56dp 赤陶圆形 FAB"＋"，与账本一致，向下滚动收缩淡出
            val fabOffset by animateDpAsState(
                targetValue = if (snackbarData != null) (-56).dp else 0.dp,
                label = "fab_offset"
            )

            AnimatedVisibility(
                visible = isFabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .offset(y = fabOffset)
            ) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = colors.accentWarm,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = when (pagerState.currentPage) {
                            0 -> "新建造物"
                            1 -> "许下心愿"
                            else -> "点亮足迹"
                        },
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 撤销删除 Snackbar
            snackbarData?.let { data ->
                ArchiveSnackbar(
                    data = data,
                    onDismiss = { snackbarData = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
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

            val isSaveEnabled = if (selectedTab == 2) fpCityName.isNotBlank() else inputTitle.isNotBlank()

            ArchiveSheet(
                onDismissRequest = { showCreateDialog = false },
                title = when (selectedTab) {
                    0 -> "新建造物"
                    1 -> "许下心愿"
                    else -> "点亮足迹"
                },
                cancelText = "取消",
                actionText = "保存",
                isActionEnabled = isSaveEnabled,
                onAction = {
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
                                else -> apiClient.createFootprint(
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
                            showCreateDialog = false
                            reloadAll()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ArchiveToastManager.show("创建失败: ${e.message}", isError = true)
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (selectedTab == 0) {
                        ArchiveField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = "造物名称",
                            placeholder = "输入造物名称…",
                            singleLine = true,
                            errorMessage = if (inputTitle.isBlank()) "请输入造物名称" else null
                        )
                        ArchiveField(
                            value = inputSubtitle,
                            onValueChange = { inputSubtitle = it },
                            label = "副标题",
                            placeholder = "一句话描述（选填）",
                            singleLine = true
                        )
                        ArchiveField(
                            value = inputNote,
                            onValueChange = { inputNote = it },
                            label = "手记与背景",
                            placeholder = "书写关于造物的构想或记录…",
                            singleLine = false,
                            minLines = 4
                        )
                    } else if (selectedTab == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                                ArchiveChoiceItem(
                                    text = label,
                                    isSelected = wishKind == k,
                                    onClick = { wishKind = k }
                                )
                            }
                        }
                        ArchiveField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = "心愿标题",
                            placeholder = "输入心愿标题…",
                            singleLine = true,
                            errorMessage = if (inputTitle.isBlank()) "请输入心愿标题" else null
                        )
                        if (wishKind == "travel") {
                            ArchiveCityPickerField(
                                selectedCityCode = wishCityCode,
                                selectedCityName = wishCityName,
                                onCitySelected = { c, n -> wishCityCode = c; wishCityName = n },
                                label = "目的地城市",
                                placeholder = "选择城市"
                            )
                        }
                        ArchiveField(
                            value = inputThird,
                            onValueChange = { inputThird = it },
                            label = "预算金额 (元)",
                            placeholder = "0.00",
                            isNumeric = true,
                            singleLine = true
                        )
                        ArchiveField(
                            value = inputNote,
                            onValueChange = { inputNote = it },
                            label = "心愿手记",
                            placeholder = "写下向往的缘由…",
                            singleLine = false,
                            minLines = 4
                        )
                    } else {
                        ArchiveCityPickerField(
                            selectedCityCode = fpCityCode,
                            selectedCityName = fpCityName,
                            onCitySelected = { c, n -> fpCityCode = c; fpCityName = n },
                            label = "到访城市",
                            placeholder = "选择城市",
                            errorMessage = if (fpCityName.isBlank()) "请选择城市" else null
                        )
                        ArchiveDateTimeSplitRow(
                            selectedDateTime = fpVisitedAt,
                            onDateTimeSelected = { fpVisitedAt = it },
                            label = "到访时刻"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArchiveChoiceItem(
                                text = "单日",
                                isSelected = !fpIsMultiDay,
                                onClick = { fpIsMultiDay = false }
                            )
                            ArchiveChoiceItem(
                                text = "多日",
                                isSelected = fpIsMultiDay,
                                onClick = { fpIsMultiDay = true }
                            )
                        }
                        if (fpIsMultiDay) {
                            ArchiveDateTimeSplitRow(
                                selectedDateTime = fpDepartedAt,
                                onDateTimeSelected = { fpDepartedAt = it },
                                label = "返程时刻"
                            )
                        }
                        ArchiveField(
                            value = inputThird,
                            onValueChange = { inputThird = it },
                            label = "标题（选填）",
                            placeholder = "如 秋日漫步",
                            singleLine = true
                        )
                        ArchiveField(
                            value = inputNote,
                            onValueChange = { inputNote = it },
                            label = "行旅手记",
                            placeholder = "记录旅途所见所感…",
                            singleLine = false,
                            minLines = 5
                        )
                    }
                }
            }
        }

        resolvingWish?.let { wish ->
            var costInput by remember(wish.id) {
                mutableStateOf(FormatUtils.centsToYuanString(wish.budget, includeSymbol = false))
            }
            var resType by remember(wish.id) { mutableStateOf("exact") }
            var actualTitleInput by remember(wish.id) { mutableStateOf("") }
            var entryNote by remember(wish.id) { mutableStateOf("") }
            var fulfilledAtDt by remember(wish.id) {
                mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone))
            }
            var createTx by remember(wish.id) { mutableStateOf(wish.budget > 0) }
            var wishPayChoice by remember(wish.id) { mutableStateOf("full") }
            var wishPeriodPreset by remember(wish.id) { mutableStateOf("12") }
            var wishChannelCode by remember(wish.id) { mutableStateOf("huabei") }
            var wishChannels by remember(wish.id) {
                mutableStateOf(
                    listOf(
                        "huabei" to "花呗",
                        "baitiao" to "京东白条",
                        "credit_card" to "信用卡"
                    )
                )
            }
            LaunchedEffect(wish.id) {
                runCatching {
                    val channels = apiClient.fetchCreditChannels()
                    if (channels.isNotEmpty()) {
                        wishChannels = channels.map { it.code to it.name }
                        if (wishChannelCode !in wishChannels.map { it.first }) {
                            wishChannelCode = channels.first().code
                        }
                    }
                }
            }
            var createGear by remember(wish.id) { mutableStateOf(wish.kind == "item") }
            var createEntry by remember(wish.id) { mutableStateOf(true) }
            var createFp by remember(wish.id) { mutableStateOf(wish.kind == "travel") }
            val costInvalid = !resType.equals("relinquished") &&
                costInput.isNotBlank() && costInput.trim().toDoubleOrNull() == null

            ArchiveSheet(
                onDismissRequest = { resolvingWish = null },
                title = "达成归册 · ${wish.title}",
                actionText = "确认归册",
                isActionEnabled = !costInvalid,
                onAction = {
                    if (costInvalid) {
                        ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                        return@ArchiveSheet
                    }
                    scope.launch {
                        try {
                            val isRel = resType == "relinquished"
                            val cents = if (isRel) 0L else FormatUtils.yuanStringToCents(costInput)
                            val isInst = !isRel && createTx && wishPayChoice == "installment"
                            apiClient.resolveWish(
                                id = wish.id,
                                req = ResolveWishRequest(
                                    fulfilledAt = FormatUtils.toBeijingIsoString(fulfilledAtDt),
                                    actualCost = cents,
                                    resolutionType = resType,
                                    actualTitle = actualTitleInput.trim().takeIf { it.isNotBlank() },
                                    createTransaction = !isRel && createTx && cents > 0,
                                    createGearAsset = !isRel && createGear && wish.kind == "item",
                                    createLifeEntry = createEntry,
                                    createFootprint = !isRel && createFp && wish.kind == "travel" &&
                                        !wish.cityCode.isNullOrBlank(),
                                    entryContent = entryNote.takeIf { it.isNotBlank() },
                                    isInstallment = if (isInst) true else null,
                                    installmentChannelCode = if (isInst) wishChannelCode else null,
                                    installmentMonths = if (isInst) (wishPeriodPreset.toIntOrNull() ?: 12) else null,
                                    repaymentDay = if (isInst) 10 else null
                                )
                            )
                            resolvingWish = null
                            reloadAll()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("exact" to "如愿拿下", "altered" to "替代方案", "relinquished" to "理性拔草").forEach { (k, label) ->
                            ArchiveChoiceItem(
                                text = label,
                                isSelected = resType == k,
                                onClick = { resType = k }
                            )
                        }
                    }

                    ArchiveDateTimeSplitRow(
                        selectedDateTime = fulfilledAtDt,
                        onDateTimeSelected = { fulfilledAtDt = it },
                        label = stringResource(R.string.projects_wishes_screen_label_03)
                    )

                    if (resType != "relinquished") {
                        ArchiveField(
                            value = actualTitleInput,
                            onValueChange = { actualTitleInput = it },
                            label = stringResource(R.string.projects_wishes_screen_label_04),
                            placeholder = wish.title,
                            singleLine = true
                        )
                        ArchiveField(
                            value = costInput,
                            onValueChange = { costInput = it },
                            label = stringResource(R.string.projects_wishes_screen_label_05),
                            placeholder = "0.00",
                            isNumeric = true,
                            singleLine = true
                        )
                        if (costInvalid) {
                            Text(
                                text = "金额格式不正确，请输入有效数字",
                                fontSize = 11.5.sp,
                                color = colors.alertRed
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArchiveChoiceItem(
                                text = if (createTx) "✓ 记账" else "不记账",
                                isSelected = createTx,
                                onClick = { createTx = !createTx }
                            )
                            if (wish.kind == "item") {
                                ArchiveChoiceItem(
                                    text = if (createGear) "✓ 建固资" else "不建固资",
                                    isSelected = createGear,
                                    onClick = { createGear = !createGear }
                                )
                            }
                            if (wish.kind == "travel") {
                                if (wish.cityCode.isNullOrBlank()) {
                                    Text(
                                        text = "未设目的地，无法亮足迹",
                                        fontSize = 12.sp,
                                        color = colors.inkMuted,
                                        modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)
                                    )
                                } else {
                                    ArchiveChoiceItem(
                                        text = if (createFp) "✓ 亮足迹" else "不建足迹",
                                        isSelected = createFp,
                                        onClick = { createFp = !createFp }
                                    )
                                }
                            }
                            ArchiveChoiceItem(
                                text = if (createEntry) "✓ 伴生手记" else "不写手记",
                                isSelected = createEntry,
                                onClick = { createEntry = !createEntry }
                            )
                        }

                        if (createTx) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ArchiveChoiceItem(
                                    text = "一次付清",
                                    isSelected = wishPayChoice == "full",
                                    onClick = { wishPayChoice = "full" }
                                )
                                ArchiveChoiceItem(
                                    text = "分期付款",
                                    isSelected = wishPayChoice == "installment",
                                    onClick = { wishPayChoice = "installment" }
                                )
                            }

                            if (wishPayChoice == "installment") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("3", "6", "12", "24").forEach { p ->
                                        ArchiveChoiceItem(
                                            text = "${p}期",
                                            isSelected = wishPeriodPreset == p,
                                            onClick = { wishPeriodPreset = p }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    wishChannels.forEach { (code, name) ->
                                        ArchiveChoiceItem(
                                            text = name,
                                            isSelected = wishChannelCode == code,
                                            onClick = { wishChannelCode = code }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ArchiveField(
                        value = entryNote,
                        onValueChange = { entryNote = it },
                        label = stringResource(R.string.projects_wishes_screen_label_06),
                        placeholder = "记录心愿达成时的感悟与细节…",
                        singleLine = false,
                        minLines = 3
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
            ArchiveSheet(
                onDismissRequest = { selectedDrawerFootprint = null },
                title = stringResource(R.string.projects_wishes_screen_title_tpl_02, foot.cityName),
                cancelText = "关闭"
            ) {
                val d = drawerData
                if (d == null) {
                    if (drawerError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.projects_wishes_screen_text_tpl_02, drawerError.orEmpty()),
                                fontSize = 12.sp,
                                color = colors.alertRed,
                                modifier = Modifier.clickable(role = Role.Button) {
                                    drawerError = null
                                    scope.launch {
                                        runCatching {
                                            drawerData = apiClient.fetchCityMemoryDrawer(foot.cityCode)
                                        }.onFailure { drawerError = it.message ?: "网络异常" }
                                    }
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.projects_wishes_screen_text_07),
                                fontSize = 13.sp,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                color = colors.inkMuted
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.projects_wishes_screen_text_tpl_03, d.visitStats.totalVisits, d.entries.size),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                            if (d.ledgerSummary.totalSpent > 0) {
                                Text(
                                    text = stringResource(R.string.projects_wishes_screen_text_tpl_04, FormatUtils.centsToYuanString(d.ledgerSummary.totalSpent)),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.accentMoss
                                )
                            }
                        }

                        if (d.entries.isNotEmpty()) {
                            ArchiveSectionHeader(title = "属地散页手记")
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                d.entries.forEachIndexed { index, entry ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ArchiveToastManager.show("已定位手记《${entry.title.ifBlank { "散页手记" }}》")
                                            }
                                            .padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = entry.title.ifBlank { "散页手记" },
                                            fontSize = 14.sp,
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.inkPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = FormatUtils.formatDisplayDate(entry.occurredAt),
                                            fontSize = 11.sp,
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            color = colors.inkSecondary
                                        )
                                    }
                                    if (index < d.entries.size - 1) {
                                        ArchiveDivider()
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        ArchiveDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArchiveTextButton(
                                text = stringResource(R.string.projects_wishes_province_drawer),
                                textColor = colors.accentWarm,
                                onClick = {
                                    val prefix = if (foot.cityCode.length >= 2) foot.cityCode.substring(0, 2) else ""
                                    val prov = CityDictionary.provincePrefixMap[prefix] ?: "属地省份"
                                    val code = foot.cityCode
                                    selectedDrawerFootprint = null
                                    selectedProvinceDrawerAdcode = code to prov
                                }
                            )

                            ArchiveTextButton(
                                text = stringResource(R.string.projects_wishes_edit_ticket),
                                textColor = colors.accentWarm,
                                onClick = {
                                    val f = foot
                                    selectedDrawerFootprint = null
                                    editingFootprint = f
                                }
                            )

                            ArchiveTextButton(
                                text = stringResource(R.string.projects_wishes_remove),
                                isDestructive = true,
                                onClick = {
                                    val f = foot
                                    val count = d.entries.size
                                    selectedDrawerFootprint = null
                                    footprintToDelete = f to count
                                }
                            )
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

            ArchiveSheet(
                onDismissRequest = { editingProject = null },
                title = stringResource(R.string.projects_wishes_screen_title_tpl_03, proj.title),
                actionText = "保存更新",
                isActionEnabled = editTitle.isNotBlank(),
                onAction = {
                    scope.launch {
                        runCatching {
                            apiClient.updateProject(
                                id = proj.id,
                                req = CreateProjectRequest(
                                    title = editTitle.trim(),
                                    subtitle = editSubtitle.trim(),
                                    description = editDesc.trim(),
                                    story = proj.story,
                                    category = proj.category,
                                    status = editStatus,
                                    githubUrl = editGithub.trim(),
                                    order = proj.order ?: 0
                                )
                            )
                            ArchiveToastManager.show("造物项目已更新")
                        }.onFailure { err ->
                            ArchiveToastManager.show("更新造物失败: ${err.message}", isError = true)
                            return@launch
                        }
                        editingProject = null
                        reloadAll()
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "进行中" to colors.tagTravel,
                            "已完成" to androidx.compose.ui.graphics.Color(0xFF6B7280),
                            "维护中" to colors.tagProject,
                            "已搁置" to colors.inkMuted
                        ).forEach { (s, dotColor) ->
                            ArchiveChoiceItem(
                                text = s,
                                isSelected = editStatus == s,
                                colorDot = dotColor,
                                onClick = { editStatus = s }
                            )
                        }
                    }

                    ArchiveField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = stringResource(R.string.projects_wishes_screen_label_07),
                        placeholder = "输入造物名称…",
                        singleLine = true,
                        errorMessage = if (editTitle.isBlank()) "请输入造物名称" else null
                    )
                    ArchiveField(
                        value = editSubtitle,
                        onValueChange = { editSubtitle = it },
                        label = stringResource(R.string.projects_wishes_screen_label_08),
                        placeholder = "一句话简介（选填）",
                        singleLine = true
                    )
                    ArchiveField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = stringResource(R.string.projects_wishes_screen_label_09),
                        placeholder = "详细说明与记录…",
                        singleLine = false,
                        minLines = 3
                    )
                    ArchiveField(
                        value = editGithub,
                        onValueChange = { editGithub = it },
                        label = stringResource(R.string.projects_wishes_screen_label_10),
                        placeholder = "github.com/...",
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    ArchiveDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ArchiveTextButton(
                            text = stringResource(R.string.projects_wishes_delete_project),
                            isDestructive = true,
                            onClick = {
                                val p = proj
                                editingProject = null
                                projectToDelete = p
                            }
                        )
                    }
                }
            }
        }

        // 删除心愿确认对话框
        wishToDelete?.let { wish ->
            ArchiveConfirmDialog(
                title = stringResource(R.string.projects_wishes_screen_title_01),
                content = "确认从心愿清单中移除《${wish.title}》？此操作不可逆。",
                confirmText = stringResource(R.string.projects_wishes_screen_confirmText_01),
                isDestructive = true,
                onConfirm = {
                    scope.launch {
                        runCatching {
                            apiClient.deleteWish(wish.id)
                            ArchiveToastManager.show("心愿已移除")
                        }.onFailure { err ->
                            ArchiveToastManager.show("移除心愿失败: ${err.message}", isError = true)
                            return@launch
                        }
                        wishToDelete = null
                        reloadAll()
                    }
                },
                onDismiss = { wishToDelete = null }
            )
        }

        // MOB-P0-14：删除造物项目确认对话框
        projectToDelete?.let { proj ->
            ArchiveConfirmDialog(
                title = stringResource(R.string.projects_wishes_screen_title_02),
                content = "确认删除《${proj.title}》？此操作不可逆，相关记录将一并失去关联。",
                confirmText = stringResource(R.string.ledger_dialogs_confirmText_01),
                isDestructive = true,
                onConfirm = {
                    scope.launch {
                        runCatching {
                            apiClient.deleteProject(proj.id)
                            ArchiveToastManager.show("造物项目已删除")
                        }.onFailure { err ->
                            ArchiveToastManager.show("删除造物失败: ${err.message}", isError = true)
                            return@launch
                        }
                        projectToDelete = null
                        editingProject = null
                        reloadAll()
                    }
                },
                onDismiss = { projectToDelete = null }
            )
        }

        // 移除足迹确认对话框（文案写明手记去向：保留在时间流中，仅解绑）
        footprintToDelete?.let { (foot, entriesCount) ->
            val entriesText = if (entriesCount > 0) {
                "该城市下的 $entriesCount 篇散页手记将完整保留在时间流中（仅解除旅程关联），不会被删除。"
            } else {
                "该城市下的散页手记将完整保留在时间流中（仅解除旅程关联），不会被删除。"
            }
            ArchiveConfirmDialog(
                title = "移除行迹票根",
                content = "确认移除在《${foot.cityName}》的这枚行迹票根吗？$entriesText",
                confirmText = "确认移除",
                isDestructive = true,
                onConfirm = {
                    scope.launch {
                        runCatching {
                            apiClient.deleteFootprint(foot.id)
                            ArchiveToastManager.show("票根已移除")
                        }.onFailure { err ->
                            ArchiveToastManager.show("移除票根失败: ${err.message}", isError = true)
                            return@launch
                        }
                        footprintToDelete = null
                        selectedDrawerFootprint = null
                        reloadAll()
                    }
                },
                onDismiss = { footprintToDelete = null }
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
