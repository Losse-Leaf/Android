package com.looseleaf.app.ui.projectswishes

import androidx.compose.animation.core.animateDpAsState
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
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.ProvinceMemoryDrawerResponse
import com.looseleaf.app.model.ResolveWishRequest
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.ui.stream.EntryDetailSheetDialog
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
/**
 * 造物、心愿与足迹视界 (ProjectsWishesScreen)
 * 严格遵从 docs/README.md §三.8 记录为一切的母体原则
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsWishesScreen(
    modifier: Modifier = Modifier,
    initialTab: Int? = null,
    /**
     * 内部 tab 切换回传（0 造物 / 1 心愿 / 2 足迹）：
     * 底栏「足迹」「造物」共用本页，必须同步 currentRoute 才不会出现
     * 「内容已切换、底栏还高亮在旧项」的错位。
     */
    onTabChanged: ((Int) -> Unit)? = null,
    /** 从全局搜索命中一条记录时带过来的目标（RG-31），消费后回调清空 */
    initialTarget: com.looseleaf.app.WorkshopTarget? = null,
    onTargetConsumed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { com.looseleaf.app.data.sync.SyncManager.getInstance(context) }
    val uiPrefs = remember(context) {
        context.getSharedPreferences("looseleaf_ui_prefs", android.content.Context.MODE_PRIVATE)
    }

    val projects by syncManager.projectsFlow.collectAsStateWithLifecycle()
    val wishes by syncManager.wishesFlow.collectAsStateWithLifecycle()
    val footprints by syncManager.footprintsFlow.collectAsStateWithLifecycle()

    fun resolvePreferredTab(): Int {
        if (initialTab != null) return initialTab.coerceIn(0, 2)
        val savedTab = uiPrefs.getInt("workshop_last_sub_tab", -1)
        val firstNonEmpty = when {
            projects.isNotEmpty() -> 0
            wishes.isNotEmpty() -> 1
            footprints.isNotEmpty() -> 2
            else -> -1
        }
        return if (savedTab in 0..2) {
            val savedHasItems = when (savedTab) {
                0 -> projects.isNotEmpty()
                1 -> wishes.isNotEmpty()
                else -> footprints.isNotEmpty()
            }
            if (savedHasItems || firstNonEmpty == -1) savedTab else firstNonEmpty
        } else if (firstNonEmpty != -1) {
            firstNonEmpty
        } else {
            0
        }
    }

    var hasUserInteractedWithTab by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = resolvePreferredTab()) { 3 }
    val selectedTab = pagerState.currentPage

    val projectsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val wishesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val footprintsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var snackbarData by remember { mutableStateOf<ArchiveSnackbarData?>(null) }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            val target = initialTab.coerceIn(0, 2)
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    LaunchedEffect(projects.isNotEmpty(), wishes.isNotEmpty(), footprints.isNotEmpty()) {
        if (initialTab == null && !hasUserInteractedWithTab) {
            val preferred = resolvePreferredTab()
            if (pagerState.currentPage != preferred) {
                pagerState.scrollToPage(preferred)
            }
            if (projects.isNotEmpty() || wishes.isNotEmpty() || footprints.isNotEmpty()) {
                onTabChanged?.invoke(preferred)
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (hasUserInteractedWithTab || initialTab != null) {
            uiPrefs.edit().putInt("workshop_last_sub_tab", pagerState.settledPage).apply()
            onTabChanged?.invoke(pagerState.settledPage)
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            hasUserInteractedWithTab = true
        }
    }

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
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 全局搜索命中（RG-31）：先切到对应分段，再等列表数据到位后打开详情。
    // 列表可能还在首拉，直接取会扑空；这里靠 (projects/wishes/footprints) 变化重试。
    var pendingOpenTarget by remember(initialTarget) { mutableStateOf(initialTarget) }

    LaunchedEffect(initialTarget) {
        val target = initialTarget ?: return@LaunchedEffect
        val page = when (target.kind) {
            "project" -> 0
            "wish" -> 1
            else -> 2
        }
        hasUserInteractedWithTab = true
        uiPrefs.edit().putInt("workshop_last_sub_tab", page).apply()
        if (pagerState.currentPage != page) pagerState.scrollToPage(page)
    }

    LaunchedEffect(pendingOpenTarget, projects, wishes, footprints) {
        val target = pendingOpenTarget ?: return@LaunchedEffect
        val opened: Any? = when (target.kind) {
            "project" -> projects.firstOrNull { it.id == target.id }?.also { editingProject = it }
            "wish" -> wishes.firstOrNull { it.id == target.id }?.also { editingWish = it }
            else -> footprints.firstOrNull { it.id == target.id }?.also { selectedDrawerFootprint = it }
        }
        if (opened != null) {
            pendingOpenTarget = null
            onTargetConsumed?.invoke()
        }
    }

    // 目标已被删除 / 数据一直没到：3 秒后放手，只保留分段切换，避免意图永久挂着
    LaunchedEffect(pendingOpenTarget) {
        if (pendingOpenTarget == null) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000)
        if (pendingOpenTarget != null) {
            ArchiveToastManager.show("没找到这条记录，可能已被删除", isError = true)
            pendingOpenTarget = null
            onTargetConsumed?.invoke()
        }
    }

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
                // 顶栏：标题"工坊"，右侧放随当前 Tab 切换的轻量新建动作，取代右下角悬浮 FAB
                ArchivePageHeader(
                    title = stringResource(R.string.projects_wishes_screen_text_01),
                    subtitle = null,
                    actions = {
                        ArchiveTextButton(
                            text = when (selectedTab) {
                                0 -> "+ 立项"
                                1 -> "+ 许愿"
                                else -> "+ 足迹"
                            },
                            textColor = colors.accentWarm,
                            onClick = { showCreateDialog = true }
                        )
                    }
                )

                // Tab 切换器：纯文字标签 造物 · 心愿 · 足迹，选中加粗 + 2dp 赤陶下划线，无框无底色，后跟等宽数字
                ArchiveSegmentedTabs(
                    tabs = listOf("造物", "心愿", "足迹"),
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        hasUserInteractedWithTab = true
                        uiPrefs.edit().putInt("workshop_last_sub_tab", index).apply()
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
                            // 同步失败重试入口：整组走"错误"语义色（砖红），
                            // 不用赤陶 —— 赤陶在本产品里专指主操作/选中。
                            .background(colors.alertRed.copy(alpha = 0.10f))
                            .clickable(role = Role.Button) {
                                scope.launch { reloadAll(forceLoading = true) }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            painter = painterResource(com.looseleaf.core.R.drawable.ic_error_outline),
                            contentDescription = null,
                            tint = colors.alertRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.projects_wishes_screen_text_tpl_01, message),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 11.sp,
                            color = colors.alertRed,
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
                    // 下拉刷新（黄铜回形针指示器，未下拉时不显示任何异常底圈） + 支持左右滑动的 HorizontalPager
                    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { scope.launch { reloadAll(forceLoading = true) } },
                        state = pullToRefreshState,
                        indicator = {
                            PaperclipPullIndicator(
                                state = pullToRefreshState,
                                isRefreshing = isLoading
                            )
                        },
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
                                    onDeleteClick = { proj -> projectToDelete = proj },
                                    onNewProjectClick = { showCreateDialog = true },
                                    listState = projectsListState,
                                    modifier = Modifier.fillMaxSize()
                                )
                                1 -> WishesList(
                                    wishes = wishes,
                                    onResolveClick = { resolvingWish = it },
                                    onEditClick = { editingWish = it },
                                    onDeleteClick = { wish -> wishToDelete = wish },
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
            var projCategory by remember { mutableStateOf("软件") }
            var projStatus by remember { mutableStateOf("进行中") }
            var projStory by remember { mutableStateOf("") }
            var projGithubUrl by remember { mutableStateOf("") }
            var wishKind by remember { mutableStateOf("item") }
            var wishSpec by remember { mutableStateOf("") }
            var wishTargetDate by remember { mutableStateOf<LocalDate?>(null) }
            var fpCityCode by remember { mutableStateOf("") }
            var fpCityName by remember { mutableStateOf("") }
            var wishCityCode by remember { mutableStateOf("") }
            var wishCityName by remember { mutableStateOf("") }
            var fpVisitedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone)) }
            var fpIsMultiDay by remember { mutableStateOf(false) }
            var fpDepartedAt by remember { mutableStateOf(LocalDateTime.now(FormatUtils.BeijingZone).plusDays(2)) }
            var isSubmittingCreate by remember { mutableStateOf(false) }

            val isSaveEnabled = (if (selectedTab == 2) fpCityName.isNotBlank() else inputTitle.isNotBlank()) && !isSubmittingCreate

            ArchiveSheet(
                onDismissRequest = { if (!isSubmittingCreate) showCreateDialog = false },
                title = when (selectedTab) {
                    0 -> "新建造物"
                    1 -> "许下心愿"
                    else -> "点亮足迹"
                },
                cancelText = "取消",
                actionText = if (isSubmittingCreate) "保存中…" else "保存",
                isActionEnabled = isSaveEnabled,
                onAction = onAction@{
                    if (isSubmittingCreate) return@onAction
                    isSubmittingCreate = true
                    scope.launch {
                        try {
                            when (selectedTab) {
                                0 -> {
                                    val queued = syncManager.createProjectOrQueue(
                                        CreateProjectRequest(
                                            title = inputTitle.trim(),
                                            subtitle = inputSubtitle.trim().takeIf { it.isNotBlank() },
                                            description = inputNote.trim(),
                                            story = projStory.trim().takeIf { it.isNotBlank() },
                                            category = projCategory,
                                            status = projStatus,
                                            githubUrl = projGithubUrl.trim().takeIf { it.isNotBlank() }
                                        )
                                    )
                                    if (queued) ArchiveToastManager.show("已离线排队创建造物")
                                    else ArchiveToastManager.show("造物已创建")
                                }
                                1 -> {
                                    val queued = syncManager.createWishOrQueue(
                                        CreateWishRequest(
                                            title = inputTitle.trim(),
                                            kind = wishKind,
                                            spec = if (wishKind == "item") wishSpec.trim().takeIf { it.isNotBlank() } else null,
                                            note = inputNote.trim().takeIf { it.isNotBlank() },
                                            budget = FormatUtils.yuanStringToCents(inputThird),
                                            cityCode = if (wishKind == "travel") wishCityCode.takeIf { it.isNotBlank() } else null,
                                            cityName = if (wishKind == "travel") wishCityName.takeIf { it.isNotBlank() } else null,
                                            targetDate = wishTargetDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        )
                                    )
                                    if (queued) ArchiveToastManager.show("已离线排队许下心愿")
                                    else ArchiveToastManager.show("心愿已记录")
                                }
                                else -> {
                                    val queued = syncManager.createFootprintOrQueue(
                                        CreateFootprintRequest(
                                            cityCode = fpCityCode,
                                            cityName = fpCityName,
                                            title = inputThird.trim().takeIf { it.isNotBlank() },
                                            visitedAt = FormatUtils.toBeijingIsoString(fpVisitedAt),
                                            departedAt = if (fpIsMultiDay) FormatUtils.toBeijingIsoString(fpDepartedAt) else null,
                                            note = inputNote.trim().takeIf { it.isNotBlank() }
                                        )
                                    )
                                    if (queued) ArchiveToastManager.show("已离线排队足迹")
                                    else ArchiveToastManager.show("足迹已记录")
                                }
                            }
                            showCreateDialog = false
                            reloadAll()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ArchiveToastManager.show("创建失败: ${e.message}", isError = true)
                        } finally {
                            isSubmittingCreate = false
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
                            singleLine = true
                        )
                        ArchiveField(
                            value = inputSubtitle,
                            onValueChange = { inputSubtitle = it },
                            label = "副标题",
                            placeholder = "一句话描述（选填）",
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArchiveSelectField(
                                label = "造物分类",
                                selectedValue = projCategory,
                                options = listOf(
                                    "软件" to "软件",
                                    "硬件" to "硬件",
                                    "设计" to "设计",
                                    "手作" to "手作",
                                    "写作" to "写作",
                                    "生活" to "生活"
                                ),
                                onValueSelected = { projCategory = it },
                                modifier = Modifier.weight(1f)
                            )
                            ArchiveSelectField(
                                label = "当前状态",
                                selectedValue = projStatus,
                                options = listOf(
                                    "进行中" to "进行中",
                                    "已完成" to "已完成",
                                    "维护中" to "维护中",
                                    "已搁置" to "已搁置"
                                ),
                                onValueSelected = { projStatus = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ArchiveField(
                            value = inputNote,
                            onValueChange = { inputNote = it },
                            label = "手记与简介",
                            placeholder = "书写关于造物的构想或记录…",
                            singleLine = false,
                            minLines = 3
                        )
                        ArchiveField(
                            value = projStory,
                            onValueChange = { projStory = it },
                            label = "缘起故事（选填）",
                            placeholder = "为何着手做这件造物…",
                            singleLine = false,
                            minLines = 2
                        )
                        ArchiveField(
                            value = projGithubUrl,
                            onValueChange = { projGithubUrl = it },
                            label = "源码仓库 URL（选填）",
                            placeholder = "github.com/...",
                            singleLine = true
                        )
                    } else if (selectedTab == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("item" to "好物", "travel" to "旅程", "milestone" to "里程碑").forEach { (k, label) ->
                                ArchiveSelectableCapsule(
                                    text = label,
                                    selected = wishKind == k,
                                    onClick = { wishKind = k }
                                )
                            }
                        }
                        ArchiveField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = "心愿标题",
                            placeholder = "输入心愿标题…",
                            singleLine = true
                        )
                        if (wishKind == "item") {
                            ArchiveField(
                                value = wishSpec,
                                onValueChange = { wishSpec = it },
                                label = "规格 / 型号（选填）",
                                placeholder = "如 银色 256G",
                                singleLine = true
                            )
                        } else if (wishKind == "travel") {
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
                        ArchiveDatePickerField(
                            selectedDate = wishTargetDate,
                            onDateSelected = { wishTargetDate = it },
                            label = "期许达成日期",
                            placeholder = "未设置",
                            onClear = { wishTargetDate = null }
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
                            placeholder = "选择城市"
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
                            ArchiveSelectableCapsule(
                                text = "单日",
                                selected = !fpIsMultiDay,
                                onClick = { fpIsMultiDay = false }
                            )
                            ArchiveSelectableCapsule(
                                text = "多日",
                                selected = fpIsMultiDay,
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
            var wishChannelCode by remember(wish.id) { mutableStateOf("") }
            var wishChannels by remember(wish.id) {
                mutableStateOf<List<Pair<String, String>>>(emptyList())
            }
            LaunchedEffect(wish.id) {
                runCatching {
                    val channels = apiClient.fetchCreditChannels().filter { !it.isBuiltin }
                    wishChannels = channels.map { it.code to it.name }
                    if (channels.isNotEmpty() && wishChannelCode !in wishChannels.map { it.first }) {
                        wishChannelCode = channels.first().code
                    }
                }
            }
            var createGear by remember(wish.id) { mutableStateOf(wish.kind == "item") }
            var createEntry by remember(wish.id) { mutableStateOf(true) }
            var createFp by remember(wish.id) { mutableStateOf(wish.kind == "travel") }
            var isResolvingWish by remember { mutableStateOf(false) }
            val costInvalid = !resType.equals("relinquished") &&
                costInput.isNotBlank() && costInput.trim().toDoubleOrNull() == null
            val installmentReady = resType == "relinquished" || !createTx ||
                wishPayChoice != "installment" || wishChannelCode.isNotBlank()

            ArchiveSheet(
                onDismissRequest = { if (!isResolvingWish) resolvingWish = null },
                title = "达成归册 · ${wish.title}",
                actionText = if (isResolvingWish) "归册中…" else "归册",
                isActionEnabled = !costInvalid && installmentReady && !isResolvingWish,
                onAction = onAction@{
                    if (isResolvingWish) return@onAction
                    if (costInvalid) {
                        ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                        return@onAction
                    }
                    isResolvingWish = true
                    scope.launch {
                        try {
                            val isRel = resType == "relinquished"
                            val cents = if (isRel) 0L else FormatUtils.yuanStringToCents(costInput)
                            val isInst = !isRel && createTx && wishPayChoice == "installment"
                            val queued = syncManager.resolveWishOrQueue(
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
                            if (queued) ArchiveToastManager.show("已离线排队心愿达成")
                            else ArchiveToastManager.show("心愿已如愿达成")
                            resolvingWish = null
                            reloadAll()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                        } finally {
                            isResolvingWish = false
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
                            ArchiveSelectableCapsule(
                                text = label,
                                selected = resType == k,
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
                            trailingText = if (costInvalid) "请输入有效数字" else "元",
                            isNumeric = true,
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArchiveSelectableCapsule(
                                text = if (createTx) "✓ 记账" else "不记账",
                                selected = createTx,
                                onClick = { createTx = !createTx }
                            )
                            if (wish.kind == "item") {
                                ArchiveSelectableCapsule(
                                    text = if (createGear) "✓ 建固资" else "不建固资",
                                    selected = createGear,
                                    onClick = { createGear = !createGear }
                                )
                            }
                            if (wish.kind == "travel") {
                                if (wish.cityCode.isNullOrBlank()) {
                                    Text(
                                        text = "未设目的地，无法亮足迹",
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.inkMuted,
                                        modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)
                                    )
                                } else {
                                    ArchiveSelectableCapsule(
                                        text = if (createFp) "✓ 亮足迹" else "不建足迹",
                                        selected = createFp,
                                        onClick = { createFp = !createFp }
                                    )
                                }
                            }
                            ArchiveSelectableCapsule(
                                text = if (createEntry) "✓ 伴生手记" else "不写手记",
                                selected = createEntry,
                                onClick = { createEntry = !createEntry }
                            )
                        }

                        if (createTx) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ArchiveSelectableCapsule(
                                    text = "一次付清",
                                    selected = wishPayChoice == "full",
                                    onClick = { wishPayChoice = "full" }
                                )
                                ArchiveSelectableCapsule(
                                    text = "分期付款",
                                    selected = wishPayChoice == "installment",
                                    onClick = { wishPayChoice = "installment" }
                                )
                            }

                            if (wishPayChoice == "installment") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("3", "6", "12", "24").forEach { p ->
                                        ArchiveSelectableCapsule(
                                            text = "${p}期",
                                            selected = wishPeriodPreset == p,
                                            isMonospace = true,
                                            onClick = { wishPeriodPreset = p }
                                        )
                                    }
                                }

                                if (wishChannels.isEmpty()) {
                                    Text(
                                        text = "暂无自定义还款渠道，请先在账本「渠道」中添加",
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.inkMuted
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        wishChannels.forEach { (code, name) ->
                                            ArchiveSelectableCapsule(
                                                text = name,
                                                selected = wishChannelCode == code,
                                                onClick = { wishChannelCode = code }
                                            )
                                        }
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
                                                selectedEntryForDetail = entry
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
            var isUpdatingProject by remember { mutableStateOf(false) }

            ArchiveSheet(
                onDismissRequest = { if (!isUpdatingProject) editingProject = null },
                title = stringResource(R.string.projects_wishes_screen_title_tpl_03, proj.title),
                actionText = if (isUpdatingProject) "保存中…" else "保存",
                isActionEnabled = editTitle.isNotBlank() && !isUpdatingProject,
                onAction = onAction@{
                    if (isUpdatingProject) return@onAction
                    isUpdatingProject = true
                    scope.launch {
                        try {
                            val queued = syncManager.updateProjectOrQueue(
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
                            if (queued) {
                                ArchiveToastManager.show("已离线排队更新造物")
                            } else {
                                ArchiveToastManager.show("造物项目已更新")
                            }
                            editingProject = null
                            reloadAll()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ArchiveToastManager.show("更新造物失败: ${e.message}", isError = true)
                        } finally {
                            isUpdatingProject = false
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "进行中" to colors.tagTravel,
                            "已完成" to colors.inkSecondary,
                            "维护中" to colors.tagProject,
                            "已搁置" to colors.inkMuted
                        ).forEach { (s, dotColor) ->
                            ArchiveSelectableCapsule(
                                text = s,
                                selected = editStatus == s,
                                leadingDotColor = dotColor,
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
                    val targetWishId = wish.id
                    wishToDelete = null
                    scope.launch {
                        runCatching {
                            val queued = syncManager.deleteWishOrQueue(targetWishId)
                            if (queued) {
                                ArchiveToastManager.show("已离线排队移除心愿")
                            } else {
                                ArchiveToastManager.show("心愿已移除")
                            }
                        }.onFailure { err ->
                            ArchiveToastManager.show("移除心愿失败: ${err.message}", isError = true)
                            return@launch
                        }
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
                    val targetProjId = proj.id
                    projectToDelete = null
                    editingProject = null
                    scope.launch {
                        runCatching {
                            val queued = syncManager.deleteProjectOrQueue(targetProjId)
                            if (queued) {
                                ArchiveToastManager.show("已离线排队删除造物")
                            } else {
                                ArchiveToastManager.show("造物项目已删除")
                            }
                        }.onFailure { err ->
                            ArchiveToastManager.show("删除造物失败: ${err.message}", isError = true)
                            return@launch
                        }
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
                    val targetFootId = foot.id
                    footprintToDelete = null
                    selectedDrawerFootprint = null
                    scope.launch {
                        runCatching {
                            val queued = syncManager.deleteFootprintOrQueue(targetFootId)
                            if (queued) ArchiveToastManager.show("当前离线，已排队移除票根")
                            else ArchiveToastManager.show("票根已移除")
                        }.onFailure { err ->
                            ArchiveToastManager.show("移除票根失败: ${err.message}", isError = true)
                            return@launch
                        }
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
                onDismiss = { selectedProvinceDrawerAdcode = null },
                onSelectEntry = { entry -> selectedEntryForDetail = entry }
            )
        }

        // 属地散页手记详情与编辑抽屉（打通城市/省份抽屉点击断头路）
        selectedEntryForDetail?.let { activeEntry ->
            EntryDetailSheetDialog(
                entry = activeEntry,
                apiClient = apiClient,
                onDismiss = { selectedEntryForDetail = null },
                onSaveEdit = { req, attachments ->
                    scope.launch {
                        runCatching {
                            val written = syncManager.updateEntryOptimistic(activeEntry.id, req, attachments)
                            if (written) ArchiveToastManager.show("手记已修订")
                            else ArchiveToastManager.show("未能定位手记", isError = true)
                            selectedEntryForDetail = null
                            reloadAll()
                        }.onFailure { err ->
                            ArchiveToastManager.show("修订失败: ${err.message}", isError = true)
                        }
                    }
                },
                onDelete = {
                    entryToDelete = activeEntry
                },
                onPhotoClick = { _, _ -> }
            )
        }

        entryToDelete?.let { deleting ->
            ArchiveConfirmDialog(
                title = "删除散页手记",
                content = "确认删除《${deleting.title.ifBlank { "无题随笔" }}》？此操作不可逆。",
                confirmText = "确认删除",
                isDestructive = true,
                onConfirm = {
                    val id = deleting.id
                    entryToDelete = null
                    selectedEntryForDetail = null
                    scope.launch {
                        runCatching {
                            syncManager.deleteEntry(id)
                            ArchiveToastManager.show("手记已删除")
                            reloadAll()
                        }.onFailure { err ->
                            ArchiveToastManager.show("删除失败: ${err.message}", isError = true)
                        }
                    }
                },
                onDismiss = { entryToDelete = null }
            )
        }
    }
}
}
