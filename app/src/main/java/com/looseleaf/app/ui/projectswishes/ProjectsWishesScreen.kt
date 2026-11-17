package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.ui.stream.EntryDetailSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
            WorkshopCreateSheet(
                selectedTab = selectedTab,
                syncManager = syncManager,
                onDismiss = { showCreateDialog = false },
                onCreated = { reloadAll() }
            )
        }

        resolvingWish?.let { wish ->
            ResolveWishSheet(
                wish = wish,
                apiClient = apiClient,
                syncManager = syncManager,
                onDismiss = { resolvingWish = null },
                onResolved = { reloadAll() }
            )
        }

        selectedDrawerFootprint?.let { foot ->
            CityMemoryDrawerBottomSheet(
                footprint = foot,
                apiClient = apiClient,
                onDismiss = { selectedDrawerFootprint = null },
                onSelectEntry = { selectedEntryForDetail = it },
                onOpenProvinceDrawer = { code, prov -> selectedProvinceDrawerAdcode = code to prov },
                onEditFootprint = { editingFootprint = it },
                onRequestDeleteFootprint = { f, count -> footprintToDelete = f to count }
            )
        }

        editingProject?.let { proj ->
            EditProjectSheet(
                project = proj,
                syncManager = syncManager,
                onDismiss = { editingProject = null },
                onSaved = { reloadAll() },
                onRequestDelete = { projectToDelete = it }
            )
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
                confirmText = stringResource(R.string.projects_wishes_screen_confirm_delete),
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
