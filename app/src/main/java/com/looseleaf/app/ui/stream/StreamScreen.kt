package com.looseleaf.app.ui.stream

import com.looseleaf.app.BuildConfig
import com.looseleaf.app.data.local.AppDatabase
import com.looseleaf.app.debug.DemoDataSeeder

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.Manifest
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.StreamWindowPage
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.LocalArchiveDecorConfig
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
/**
 * 进程级一次性标记：避免每次进入时间流都请求 GitHub Releases
 * MOB-P1-43：未认证的 GitHub API 只有 60 次/小时/IP 的限额，
 * 每次冷启动都查一次既浪费流量也容易撞限流。
 * 这里在 SharedPreferences 里记下「上次检查时间」，6 小时内不再重复请求。
 */
private var updateCheckedThisProcess = false
private const val UPDATE_CHECK_PREFS = "looseleaf_update_check"
private const val KEY_LAST_CHECK_AT = "last_check_at"
private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

private fun shouldCheckForUpdate(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences(UPDATE_CHECK_PREFS, android.content.Context.MODE_PRIVATE)
    val last = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
    val now = System.currentTimeMillis()
    if (now - last < UPDATE_CHECK_INTERVAL_MS) return false
    prefs.edit().putLong(KEY_LAST_CHECK_AT, now).apply()
    return true
}

/** MOB-P1-32：时间流分页窗口大小（首屏 60 条，滚动到底再取下一批） */
private const val STREAM_PAGE_SIZE = 60

/** 热力图单元格步长（dp） */

/**
 * 生活时间流主展厅 (StreamScreen) - docs/mobile.md §3 & docs/ecosystem.md §4.1
 * 承载生命之流母体记录，支持相纸画廊、那年今日回响、年度生活年鉴、详情查阅与行内修订、岁月留白切线
 */
/** MOB-P2-40：宽屏双栏断点（与 Material3 的 Expanded 断点一致：840dp） */
private const val WIDE_LAYOUT_MIN_WIDTH_DP = 840

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StreamScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val decorConfig = LocalArchiveDecorConfig.current

    // MOB-P1-86：区分「首轮加载中」/「真的没有记录」/「筛选后无匹配」
    val firstSyncCompleted by syncManager.firstSyncCompletedFlow.collectAsStateWithLifecycle()
    val pendingCount by syncManager.pendingEventCountFlow.collectAsStateWithLifecycle()
    val failedCount by syncManager.failedEventCountFlow.collectAsStateWithLifecycle()
    // MOB-P1-23：徽标是否显示用 derivedStateOf 表达 ——
    // 直接读 StateFlow 会让「计数变化」与「是否显示」耦合在同一次重组里，
    // 派生后只有布尔结果变化才通知下游，无关子树免于重组。
    val hasPending by remember { derivedStateOf { pendingCount > 0 } }
    val hasFailed by remember { derivedStateOf { failedCount > 0 } }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var filterCriteria by rememberSaveable(stateSaver = StreamFilterCriteriaSaver) {
        mutableStateOf(StreamFilterCriteria())
    }
    var showFilterSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    // 更新横幅走 JSON 持久化（ReleaseInfo 含非 Saveable 字段）：旋转后不再永久消失
    var cloudUpdateJson by rememberSaveable { mutableStateOf<String?>(null) }
    val cloudUpdate: ReleaseInfo? = remember(cloudUpdateJson) {
        cloudUpdateJson?.let {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ReleaseInfo>(it) }.getOrNull()
        }
    }

    // 那年今日状态
    var remoteOnThisDay by remember { mutableStateOf<List<LifeEntry>>(emptyList()) }
    var isOnThisDayExpanded by rememberSaveable { mutableStateOf(true) }

    // 全局检索与速记随笔状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var showDebugDevSheet by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 钢笔尖 FAB 随列表滚动方向动态展开与收缩（向下滚动收缩淡出，向上滚动或停止恢复）
    var isFabExpanded by rememberSaveable { mutableStateOf(true) }
    var lastFirstVisibleItemIndex by remember { mutableIntStateOf(0) }
    var lastFirstVisibleItemScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.collect { (index, offset, isScrolling) ->
            if (!isScrolling) {
                // 停止滚动后恢复显示
                isFabExpanded = true
            } else {
                if (index > lastFirstVisibleItemIndex || (index == lastFirstVisibleItemIndex && offset > lastFirstVisibleItemScrollOffset + 8)) {
                    // 向下滚动时收缩并淡出隐藏
                    isFabExpanded = false
                } else if (index < lastFirstVisibleItemIndex || (index == lastFirstVisibleItemIndex && offset < lastFirstVisibleItemScrollOffset - 8)) {
                    // 向上滚动恢复显示
                    isFabExpanded = true
                }
            }
            lastFirstVisibleItemIndex = index
            lastFirstVisibleItemScrollOffset = offset
        }
    }

    // 生物识别弹窗标题：必须在 composable 作用域取值（onClick lambda 不是 composable）
    val deleteEntryBiometricTitle = stringResource(R.string.stream_screen_title_02)

    // 失败事件处置抽屉（单条重试 / 全部重试）
    var showFailedEventsSheet by remember { mutableStateOf(false) }


    // 封面区折叠比例 (0f = 完全展开, 1f = 折叠为细栏)
    val coverCollapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
        }
    }

    // 单条档案详情与编辑弹窗状态
    // MOB-P2-40：宽屏（平板 / 折叠屏展开态）走「列表 + 详情」双栏，
    // 窄屏仍是底部详情弹窗 —— 两份状态分开，展开/折叠切换时才不会互相打架。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP
    // 右侧详情栏占 45%（最多 560dp）——「记随笔」浮动印章要据此让位
    // MOB-P1-xx：与右栏实际渲染宽度同源（widthIn(max=560) + fillMaxWidth(0.45) 的较小者），
    // 否则「记随笔」浮动印章的让位宽度会和真实栏宽对不上。
    val paneWidthDp = (screenWidthDp * 0.45f).coerceAtMost(560f)
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }
    var detailDialogEntry by remember { mutableStateOf<LifeEntry?>(null) }

    // 选中入口：宽屏只「选中」（右栏展示），窄屏直接弹详情
    val selectEntry = remember(isWideLayout) { { entry: LifeEntry ->
        selectedEntryForDetail = entry
        if (!isWideLayout) detailDialogEntry = entry
    } }
    var lightboxPhoto by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // MOB-P1-xx：宽屏 / 窄屏切换时收敛。
    //
    // 之前是「两套独立状态」：窄屏点条目会同时写 selectedEntryForDetail 与 detailDialogEntry，
    // 折叠/旋转到宽屏后全屏 Sheet 仍盖在双栏布局上 —— 同一条记录在弹窗和右栏**重复呈现**。
    // 这里在切到宽屏时关掉全屏 Sheet（宽屏由右栏负责展示），切回窄屏时用右栏的选中项补上弹窗。
    LaunchedEffect(isWideLayout) {
        if (isWideLayout) {
            detailDialogEntry = null
        } else if (detailDialogEntry == null && selectedEntryForDetail != null) {
            detailDialogEntry = selectedEntryForDetail
        }
    }

    // 更新检查：整个进程只查一次，失败静默（避免每次切 Tab 都打 GitHub API）
    LaunchedEffect(Unit) {
        if (!updateCheckedThisProcess && cloudUpdate == null && shouldCheckForUpdate(context)) {
            updateCheckedThisProcess = true
            // 检查本身放到后台线程，避免阻塞首帧。
            //
            // MOB-P1-xx：整段必须兜住 —— 「检测到新版本」这条链路一旦抛异常，
            // 会直接把**时间流**连带整个 App 送走（协程里的未捕获异常 = 进程崩溃），
            // 而用户此刻只是想看看自己的记录。更新提示是可选项，崩 App 不是。
            runCatching {
                val result = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdates() }
                when (result) {
                    is UpdateCheckResult.HasUpdate ->
                        cloudUpdateJson = Json { ignoreUnknownKeys = true }.encodeToString(result.release)
                    else -> Unit
                }
            }.onFailure { error ->
                Log.w("StreamScreen", "更新检查失败，已忽略: ${error.message}")
            }
        }
    }

    // 进入时间流时向云端增量拉一次。
    //
    // MOB-P1-37 把「一次拉五类数据」拆成「各页只拉自己渲染的那一类」时，
    // 漏掉了这一处 —— 旧版的 pullRemoteData() 会顺带把时间流也填上，
    // 拆分后账本页只拉账本、造物页只拉造物，**时间流就成了唯一没人拉的那一类**。
    // 后果：本地库为空时，时间流既不显示数据、也不会去云端取，
    // 首屏永远停在「正在翻阅你的时间流…」（firstSyncCompleted 只在成功拉取后才置位）。
    //
    // 与其它页面保持一致：进入即增量拉一次（游标推进后几乎不产生流量）。
    // MOB-P1-xx：拉取失败**必须留痕**。
    //
    // 原来这里写的是 runCatching { pullStreamData() } —— 而 pullStreamData 返回的是
    // Result（失败不抛异常），于是 runCatching 什么也没抓到，失败被**整个吞掉**：
    // 首屏既不显示数据、也不显示原因，只有 forever 转圈（firstSyncCompleted 永不置位）。
    // 用户看到的只有「一直转圈」，完全无从判断是没配对、还是服务器挂了、还是网络断了。
    var streamLoadError by remember { mutableStateOf<String?>(null) }
    var isRetryingStream by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 会话门禁改为**进程级**标志：原先 rememberSaveable 在系统回收进程后恢复时
        // 仍为 true，新会话里时间流不再首拉，用户对着陈旧数据无提示。
        // 失败同样标记（进程内不反复自动重试，重试交给用户手上的按钮）。
        if (!SyncManager.isSessionPullDone("stream")) {
            isRetryingStream = true
            // pullStreamData 本身返回 Result；再套 runCatching 会把
            // Result.failure 包成「外层成功」，失败被吞掉、首屏永远转圈。
            // 页面切走引发的协程取消绝不能当成失败吞掉，必须向上传播。
            val result = try {
                syncManager.pullStreamData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            isRetryingStream = false
            result.onSuccess {
                streamLoadError = null
                SyncManager.markSessionPullDone("stream")
            }
            result.onFailure { error ->
                if (error is CancellationException) return@onFailure
                // 401 单独提示：多半是设备凭据失效（换了密钥 / 重装 / 服务端清了设备表）
                val message = when {
                    error.message?.contains("401") == true ||
                        error.message?.contains("未授权") == true ||
                        error.message?.contains("缺少有效的访问凭证") == true ->
                        "设备凭据已失效，请重新配对中枢"
                    else -> error.message ?: "无法连接中枢"
                }
                streamLoadError = message
                // 同时写 logcat：用户抓一份 logcat 就能定位，不用靠猜
                Log.w("LooseLeaf/Stream", "时间流拉取失败: $message", error)
                ArchiveToastManager.show("时间流拉取失败：$message", isError = true)
            }
        }
    }

    // 那年今日：进入时间流时向服务端对齐一次（同一天内有本地缓存，MOB-P2-35）
    LaunchedEffect(Unit) {
        runCatching { remoteOnThisDay = syncManager.fetchOnThisDayCached() }
    }

    // 合并云端那年今日与本地往年同月同日条目：
    // 本地匹配已在 SyncManager 的 Default 线程管道里完成（AOPT-03c），此处只做选择
    val localOnThisDay by syncManager.localOnThisDayFlow.collectAsStateWithLifecycle()
    val onThisDayEntries = remember(localOnThisDay, remoteOnThisDay) {
        remoteOnThisDay.ifEmpty { localOnThisDay }
    }

    // 年份列表同样来自后台派生流，不在 composition 里遍历全表
    val availableYears by syncManager.availableYearsFlow.collectAsStateWithLifecycle()

    // ============================================================
    // MOB-P1-32 / AND-2：时间流分页窗口 + 增量行模型
    // ============================================================
    // 改造前：把全量档案一次性物化进内存（2 万条 ≈ 20MB 常驻），
    // 再在 Kotlin 侧做分类/年份过滤与行模型装配 —— 每次同步后都要重算一遍。
    // 改造后：
    //   1) 过滤（分类 / 年份 / 月份 / 内容）下沉到 SQL，只取当前窗口；
    //   2) 列表只持有「已加载窗口」的数据，内存与数据量解耦；
    //   3) 行模型由 StreamRowsBuilder 增量维护：翻页只算新行，
    //      key 用稳定业务 id（重复 id 才加出现次序号），顶部插入不会洗掉旧 key。
    val loadedEntries = remember { mutableStateListOf<LifeEntry>() }
    val streamRows = remember { mutableStateListOf<StreamRowItem>() }
    var streamRowsVersion by remember { mutableIntStateOf(0) }
    val rowsBuilder = remember { StreamRowsBuilder() }
    var totalCount by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    // 窗口代号：筛选切换 / 同步刷新会让在途的分页结果作废，防止旧页追加到新窗口。
    var windowGeneration by remember { mutableIntStateOf(0) }
    // 刷新窗口期间禁止分页：否则「刷新先取 N 条、分页后补一页」时，
    // 先完成的分页会被后完成的整窗替换吞掉。
    var isRefreshingWindow by remember { mutableStateOf(false) }

    suspend fun loadWindow(offset: Int, limit: Int): StreamWindowPage =
        withContext(Dispatchers.IO) {
            // 组合筛选条件直接下沉到 Room SQL 查询层
            syncManager.loadStreamWindowFiltered(
                categories = filterCriteria.categories,
                year = filterCriteria.year,
                month = filterCriteria.month,
                contents = filterCriteria.contents,
                offset = offset,
                limit = limit
            )
        }

    // 1) 筛选条件变化 → 重置窗口并加载第一页
    LaunchedEffect(filterCriteria) {
        val generation = ++windowGeneration
        isLoadingMore = false
        isRefreshingWindow = true
        loadedEntries.clear()
        streamRows.clear()
        rowsBuilder.clear()
        hasMore = true
        try {
            val firstPage = loadWindow(0, STREAM_PAGE_SIZE)
            if (generation != windowGeneration) return@LaunchedEffect
            loadedEntries.addAll(firstPage.entries)
            streamRows.addAll(rowsBuilder.replace(firstPage.entries))
            streamRowsVersion++
            totalCount = firstPage.totalCount
            hasMore = firstPage.entries.size < firstPage.totalCount
        } finally {
            if (generation == windowGeneration) isRefreshingWindow = false
        }
    }

    // 2) 数据变更（同步完成 / 本地乐观写入）→ 刷新当前窗口（保持滚动位置）
    //
    // AND-3：entriesRevisionFlow 在 SyncManager 里已经做了 debounce + conflate，
    // 一次分页同步最多只在这里触发一次重载（原来每写一页就整窗重载一次）。
    LaunchedEffect(Unit) {
        syncManager.entriesRevisionFlow.collect {
            val generation = ++windowGeneration
            isLoadingMore = false
            isRefreshingWindow = true
            try {
                val windowSize = maxOf(loadedEntries.size, STREAM_PAGE_SIZE)
                val refreshed = loadWindow(0, windowSize)
                if (generation != windowGeneration) return@collect
                val rows = rowsBuilder.replace(refreshed.entries)
                // SnapshotStateList 原地替换：不产生 `loadedEntries + more` 的全量拷贝
                loadedEntries.clear()
                loadedEntries.addAll(refreshed.entries)
                streamRows.clear()
                streamRows.addAll(rows)
                streamRowsVersion++
                totalCount = refreshed.totalCount
                hasMore = refreshed.entries.size < refreshed.totalCount
            } catch (e: Throwable) {
                Log.e("LooseLeaf/Stream", "Error refreshing window in collect", e)
            } finally {
                if (generation == windowGeneration) isRefreshingWindow = false
            }
        }
    }

    // 3) 滚动接近底部 → 加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                // loadedEntries 为空时首屏加载与这里会同时取第 1 页，导致整页重复。
                if (
                    loadedEntries.isNotEmpty() &&
                    !isRefreshingWindow &&
                    hasMore && !isLoadingMore && lastVisible >= loadedEntries.size - 6
                ) {
                    isLoadingMore = true
                    val generation = windowGeneration
                    val more = loadWindow(loadedEntries.size, STREAM_PAGE_SIZE).entries
                    if (generation != windowGeneration) {
                        isLoadingMore = false
                        return@collect
                    }
                    if (more.isNotEmpty()) {
                        // 增量追加：只算新页的行，旧行对象原样复用
                        loadedEntries.addAll(more)
                        streamRows.addAll(rowsBuilder.append(more))
                        streamRowsVersion++
                    }
                    hasMore = more.isNotEmpty()
                    isLoadingMore = false
                }
            }
    }

    // 4) 行模型装配已并入上面的增量路径（AND-2），不再有「loadedEntries 变化 → 全量 mapIndexed」。

    // 下拉刷新与顶栏按钮共用的同步动作（AOPT-06）
    val refreshArchive: () -> Unit = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                val res = syncManager.pullStreamData()
                runCatching { remoteOnThisDay = apiClient.fetchOnThisDay().entries }
                isRefreshing = false
                if (res.isSuccess) {
                    ArchiveToastManager.show("档案已增量同步")
                } else {
                    ArchiveToastManager.show(res.exceptionOrNull()?.message ?: "同步未完成", isError = true)
                }
            }
        }
    }

    NoiseBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    // MOB-P2-40：宽屏时列表只占 55% 并靠左，把右侧空间让给详情栏
                    // MOB-P1-xx：同上 —— 上限要写在 fillMaxWidth 之前才有效
                    .widthIn(max = 620.dp)
                    .fillMaxWidth(if (isWideLayout) 0.55f else 1f)
                    .fillMaxHeight()
                    .widthIn(max = if (isWideLayout) 620.dp else 680.dp)
                    .align(if (isWideLayout) Alignment.TopStart else Alignment.TopCenter)
            ) {
            // 顶栏：本子封面条 (Collapsible Book Cover Header)
            StreamCoverHeader(
                collapseFraction = coverCollapseFraction,
                activeFilterCount = filterCriteria.activeCount,
                onFilterClick = { showFilterSheet = true },
                onSearchClick = { showSearchDialog = true },
                isSyncing = isRefreshing,
                pendingCount = pendingCount,
                failedCount = failedCount,
                onFailedStampClick = { showFailedEventsSheet = true },
                onPendingStampClick = {
                    scope.launch {
                        ArchiveToastManager.show("发件箱已重新排队，联网后自动上传 ($pendingCount 项待重放)")
                        syncManager.scheduleSync()
                    }
                },
                onTitleLongClick = if (BuildConfig.DEBUG) {
                    { showDebugDevSheet = true }
                } else null
            )

            // 顶栏下沿 1dp 淡线：只在列表滚动、内容上移时出现，平时不显示
            val showTopBarDivider by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (showTopBarDivider) colors.borderDivider else Color.Transparent)
            )

            // 新版本可用提示卡片
            cloudUpdate?.let { update ->
                var downloadProgress by remember { mutableStateOf(0f) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.accentWarm.copy(alpha = 0.08f))
                        .border(0.5.dp, colors.accentWarm, RoundedCornerShape(3.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stream_screen_text_tpl_03, update.tagName, UpdateChecker.currentVersionName),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.accentWarm
                        )
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = colors.accentWarm,
                                trackColor = colors.accentWarm.copy(alpha = 0.2f)
                            )
                            Text(
                                text = stringResource(R.string.stream_screen_text_tpl_04, (downloadProgress * 100).toInt()),
                                fontSize = 11.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.inkMuted
                            )
                        } else if (downloadedFile != null) {
                            Text(
                                text = stringResource(R.string.stream_screen_text_03),
                                fontSize = 11.sp,
                                color = colors.accentMoss
                            )
                        } else if (update.body.isNotBlank()) {
                            Text(
                                text = update.body.lines().firstOrNull() ?: "",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    if (downloadedFile != null) {
                        Button(
                            onClick = {
                                downloadedFile?.let { file ->
                                    if (!UpdateChecker.installApk(context, file)) {
                                        ArchiveToastManager.show(
                                            "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                                            isError = true
                                        )
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                        ) {
                            Text(stringResource(R.string.stream_screen_install_now), fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    } else if (isDownloading) {
                        Text(
                            text = stringResource(R.string.stream_screen_text_tpl_05, (downloadProgress * 100).toInt()),
                            fontSize = 12.sp,
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            color = colors.accentWarm,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isDownloading = true
                                    val res = UpdateChecker.downloadApkStream(
                                        context = context,
                                        apkUrl = update.apkUrl,
                                        versionName = update.versionName,
                                        expectedSha256 = update.apkSha256
                                    ) { p, _, _ ->
                                        downloadProgress = p
                                    }
                                    isDownloading = false
                                    res.onSuccess { f ->
                                        downloadedFile = f
                                        val launched = UpdateChecker.installApk(context, f)
                                        if (!launched) {
                                            ArchiveToastManager.show(
                                                "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                                                isError = true
                                            )
                                        }
                                    }.onFailure { err ->
                                        ArchiveToastManager.show("下载失败: ${err.message}", isError = true)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentWarm, contentColor = colors.archivePaper)
                        ) {
                            Text(stringResource(R.string.stream_screen_in_app_update), fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }
                    }
                }
            }

            // MOB-P1-24：列表项回调提升为**稳定引用**（放在 items{} 之外），
    // 这样 StreamEntryCard 的参数保持稳定，任意一项状态变化都不会牵连整屏重组。
    val onEntryClick = remember(isWideLayout) { { entry: LifeEntry ->
        selectedEntryForDetail = entry
        if (!isWideLayout) detailDialogEntry = entry
    } }
    val onEntryDelete = remember { { entry: LifeEntry -> entryToDelete = entry } }
    val onPhotoClick = remember { { url: String, meta: String? -> lightboxPhoto = url to meta } }

    val showBinderHoles = false
    var binderScrollY by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                binderScrollY += available.y
                return Offset.Zero
            }
        }
    }

            // 切换筛选后滚回顶部
            LaunchedEffect(filterCriteria) {
                listState.scrollToItem(0)
            }

            // 时间流列表：封面区下方直接接列表，间距 16dp 由 LazyColumn contentPadding.top 提供
            val isFiltering = filterCriteria.isActive
            val isEmpty = (loadedEntries.isEmpty() && (!isFiltering && onThisDayEntries.isEmpty())) ||
                    (loadedEntries.isEmpty() && isFiltering)
            if (isEmpty) {
                val loadError = streamLoadError
                if (loadError != null) {
                    StreamErrorCard(
                        errorMessage = loadError,
                        isRetrying = isRetryingStream,
                        onRetry = {
                            if (!isRetryingStream) {
                                scope.launch {
                                    isRetryingStream = true
                                    val result = try {
                                        syncManager.pullStreamData()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                    isRetryingStream = false
                                    result.onSuccess {
                                        streamLoadError = null
                                        SyncManager.markSessionPullDone("stream")
                                    }
                                    result.onFailure { error ->
                                        if (error is CancellationException) return@onFailure
                                        streamLoadError = error.message ?: "无法连接中枢"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                } else if (!firstSyncCompleted) {
                    StreamLoadingSkeleton(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                } else {
                    StreamEmptyState(
                        isFiltering = isFiltering,
                        onWriteClick = { showQuickNoteDialog = true },
                        onResetFilter = {
                            filterCriteria = StreamFilterCriteria()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            } else {
                // 原生下拉刷新手势（AOPT-06）：使用黄铜回形针/书签旋转指示器替代圆形转圈
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refreshArchive,
                    state = pullToRefreshState,
                    indicator = {
                        PaperclipPullIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        // 预分组：按天聚合生成粘性页签 (Sticky Header)
                        val groupedDays = remember(streamRowsVersion) {
                            val list = mutableListOf<Pair<StreamRowItem, List<StreamRowItem>>>()
                            if (streamRows.isEmpty()) return@remember list
                            var currentHeader = streamRows.first()
                            var currentRows = mutableListOf<StreamRowItem>()
                            for (row in streamRows) {
                                if (row.isFirstOfDay && currentRows.isNotEmpty()) {
                                    list.add(currentHeader to currentRows)
                                    currentHeader = row
                                    currentRows = mutableListOf(row)
                                } else {
                                    currentRows.add(row)
                                }
                            }
                            if (currentRows.isNotEmpty()) {
                                list.add(currentHeader to currentRows)
                            }
                            list
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            // 封面区下方直接接列表，间距 16dp；底部留白覆盖底栏与 FAB，彻底防遮挡
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
                        ) {
                            // 4. 生效状态提示：有任意筛选生效时，列表顶部显示一行摘要
                            // "日常 · 游历 · 2026 年 10 月 · 有图"，右侧文字按钮"清除"；点击摘要重新打开面板
                            // 无筛选时这一行完全不存在，不占高度；不使用 chip、边框、底色
                            if (filterCriteria.isActive) {
                                item(key = "stream_active_filter_summary") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = filterCriteria.buildSummaryText(),
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontSize = 12.sp,
                                            color = colors.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    showFilterSheet = true
                                                }
                                                .padding(vertical = 4.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "清除",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontSize = 12.sp,
                                            color = colors.inkMuted,
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    filterCriteria = StreamFilterCriteria()
                                                }
                                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                            }

                            // 那年今日 · 岁月回响 折叠卡片（仅在未启用筛选时呈现）
                            if (!filterCriteria.isActive && onThisDayEntries.isNotEmpty()) {
                                item(key = "on_this_day_banner") {
                                    OnThisDayCard(
                                        entries = onThisDayEntries,
                                        isExpanded = isOnThisDayExpanded,
                                        onToggleExpand = {
                                            isOnThisDayExpanded = !isOnThisDayExpanded
                                        },
                                        onSelectEntry = { entry ->
                                            selectEntry(entry)
                                        },
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }
                            }

                            for ((dayIndex, group) in groupedDays.withIndex()) {
                                val (headerRow, rowsInDay) = group

                                // 岁月留白间隔提示（"历经 N 天…"）：
                                // 放在两组日期之间（上一组最后一条之后、下一组日期头之前），只在间隔 >= 7 天时出现
                                // 联动细节：筛选生效时隐藏间隔提示
                                if (!filterCriteria.isActive && dayIndex > 0 && headerRow.gapDays >= 7) {
                                    item(key = "temporal_gap_${headerRow.year}_${headerRow.monthValue}_${headerRow.dayOfMonth}") {
                                        TemporalGapSeparator(days = headerRow.gapDays)
                                    }
                                }

                        // 月份细线分隔：仅在跨月或首月出现，同月内绝不重复
                        val isMonthChanged = dayIndex == 0 ||
                            groupedDays[dayIndex - 1].first.monthValue != headerRow.monthValue ||
                            groupedDays[dayIndex - 1].first.year != headerRow.year
                        if (isMonthChanged) {
                            item(key = "month_divider_${headerRow.year}_${headerRow.monthValue}") {
                                StreamMonthHeader(
                                    monthDisplay = "${headerRow.year} 年 ${headerRow.monthValue} 月",
                                    isFirst = dayIndex == 0
                                )
                            }
                        }

                        stickyHeader(key = "day_header_${headerRow.year}_${headerRow.monthValue}_${headerRow.dayOfMonth}") {
                            StreamDayHeader(
                                dayOfMonth = headerRow.dayOfMonth,
                                monthValue = headerRow.monthValue,
                                weekday = headerRow.weekdayName,
                                relativeDay = headerRow.relativeDay
                            )
                        }

                        items(
                            items = rowsInDay,
                            key = { row -> row.localKey },
                            contentType = { row ->
                                if (row.entry.attachments.isEmpty()) "text_entry" else "media_entry"
                            }
                        ) { row ->
                            // 散页手账记录条目（直接平铺纸面，内部带 16dp 间距与 1dp 分割线）
                            StreamEntryCard(
                                row = row,
                                apiClient = apiClient,
                                onClick = onEntryClick,
                                onDelete = onEntryDelete,
                                onPhotoClick = onPhotoClick,
                                washiTapeText = null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                }
                }
            }
        }

        // 「写一页」钢笔尖悬浮操作印章 (Fountain Pen FAB)
        FountainPenFab(
            isExpanded = isFabExpanded,
            onClick = { showQuickNoteDialog = true },
            onQuickText = { showQuickNoteDialog = true },
            onQuickPhoto = { showQuickNoteDialog = true },
            onQuickVoice = { showQuickNoteDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // MOB-P2-40：宽屏时右侧有详情栏，浮动印章让到列表列里
                .padding(end = if (isWideLayout) (paneWidthDp + 20).dp else 20.dp, bottom = 20.dp)
        )

        // MOB-P2-40：宽屏右侧详情栏
        // 只读呈现；修订与删除仍复用原有弹窗，避免两套编辑逻辑分叉。
        if (isWideLayout) {
            EntryPreviewPane(
                entry = selectedEntryForDetail,
                onEdit = { detailDialogEntry = selectedEntryForDetail },
                onDelete = { selectedEntryForDetail?.let { entryToDelete = it } },
                onPhotoClick = { url, meta -> lightboxPhoto = url to meta },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // MOB-P1-xx：widthIn 必须写在 fillMaxWidth **之前** ——
                    // fillMaxWidth 会用固定宽约束测量子节点，内侧的 widthIn 再也收不窄，
                    // 于是 560.dp 的上限成了死代码，实际宽度恒为 0.45×屏宽。
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.45f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
            )
        }

        // 单条记录详情与编辑 Sheet/Dialog
        detailDialogEntry?.let { activeEntry ->
            EntryDetailSheetDialog(
                entry = activeEntry,
                apiClient = apiClient,
                onDismiss = {
                    detailDialogEntry = null
                    if (!isWideLayout) selectedEntryForDetail = null
                },
                onSaveEdit = { req, selectedAttachments ->
                    scope.launch {
                        val written = syncManager.updateEntryOptimistic(activeEntry.id, req, selectedAttachments)
                        if (!written) {
                            // 目标行已被并发删除（如编辑期间另一端的删除被同步下来），
                            // 不能假报「已保存」让用户内容凭空消失。
                            ArchiveToastManager.show("该记录已不存在，修订未保存", isError = true)
                            detailDialogEntry = null
                            if (!isWideLayout) selectedEntryForDetail = null
                            return@launch
                        }
                        ArchiveToastManager.show("已修订归册")
                        detailDialogEntry = null
                        // MOB-P1-xx：与 onDismiss 的清理规则保持一致 ——
                        // 宽屏下右栏继续显示这条记录，窄屏下弹窗本身就是详情，清掉选中项。
                        // 原先无条件清空，导致宽屏保存后右栏退回占位符，
                        // 而「取消」却会保留选中项，两个出口行为不一致。
                        if (!isWideLayout) selectedEntryForDetail = null
                    }
                },
                onDelete = {
                    entryToDelete = activeEntry
                },
                onPhotoClick = { url, meta ->
                    lightboxPhoto = url to meta
                }
            )
        }

        // 失败事件处置：展开手账抽屉，支持单条重试、单条放弃与全部重新投递
        if (showFailedEventsSheet) {
            FailedEventsSheet(
                syncManager = syncManager,
                onDismiss = { showFailedEventsSheet = false }
            )
        }

        // 筛选面板（底部弹层，纸色，无投影）
        if (showFilterSheet) {
            StreamFilterSheet(
                criteria = filterCriteria,
                availableYears = availableYears,
                syncManager = syncManager,
                onApply = { newCriteria ->
                    filterCriteria = newCriteria
                },
                onDismiss = { showFilterSheet = false }
            )
        }

        // 撕除确认弹窗
        entryToDelete?.let { entry ->
            ArchiveConfirmDialog(
                title = stringResource(R.string.stream_screen_title_01),
                content = "确认从生活时间流中彻底移除《${entry.title.ifBlank { "无题随笔" }}》？",
                confirmText = stringResource(R.string.stream_screen_confirmText_01),
                isDestructive = true,
                onConfirm = {
                    // docs/mobile.md §3.4：删除重要日记需要生物识别二次确认
                    val performDelete: () -> Unit = {
                        scope.launch {
                            // MOB-P2-47：删除后给一个「撤销」机会
                            val ticket = syncManager.deleteEntry(entry.id)
                            entryToDelete = null
                            if (selectedEntryForDetail?.id == entry.id) {
                                selectedEntryForDetail = null
                            }
                            if (detailDialogEntry?.id == entry.id) {
                                detailDialogEntry = null
                            }
                            if (ticket != null) {
                                ArchiveToastManager.showWithAction("已撕除记录", "撤销") {
                                    scope.launch {
                                        val restored = syncManager.undoDeleteEntry(ticket)
                                        ArchiveToastManager.show(
                                            if (restored) "已恢复该条记录" else "已同步到服务端，无法撤销",
                                            isError = !restored
                                        )
                                    }
                                }
                            } else {
                                ArchiveToastManager.show("已撕除记录")
                            }
                        }
                    }
                    val activity = context as? FragmentActivity
                    if (credentialStore.isBiometricsEnabled && activity != null &&
                        BiometricHelper.canAuthenticate(activity)
                    ) {
                        BiometricHelper.authenticate(
                            activity = activity,
                            title = deleteEntryBiometricTitle,
                            subtitle = "删除私人档案需要二次身份确认",
                            onSuccess = { performDelete() },
                            onError = { msg ->
                                ArchiveToastManager.show(msg, isError = true)
                                entryToDelete = null
                            }
                        )
                    } else {
                        performDelete()
                    }
                },
                onDismiss = { entryToDelete = null }
            )
        }

        // 全屏照片灯箱
        lightboxPhoto?.let { (url, meta) ->
            ArchiveLightboxViewer(
                imageUrl = url,
                metadataText = meta,
                onDismiss = { lightboxPhoto = null }
            )
        }

        // 全局检索对话框
        if (showSearchDialog) {
            GlobalSearchDialog(
                apiClient = apiClient,
                onDismiss = { showSearchDialog = false },
                onSelectEntry = { selectEntry(it) }
            )
        }

        // 随手写散页与图文装裱底抽信笺
        if (showQuickNoteDialog) {
            QuickNoteSheet(
                onDismiss = { showQuickNoteDialog = false },
                syncManager = syncManager,
                apiClient = apiClient,
                onSuccess = { showQuickNoteDialog = false }
            )
        }

        // 开发者调试底抽 (Debug 专用，长按顶栏标题触发)
        if (BuildConfig.DEBUG && showDebugDevSheet) {
            ArchiveBottomSheet(
                onDismissRequest = { showDebugDevSheet = false },
                title = "开发者调试菜单 (Debug 专用)",
                actionButtonText = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "演示数据灌入与清理",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "灌入覆盖多天、跨月、跨年、多比例图片及发件箱异常状态的手账演示数据，或一键彻底清除",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val count = DemoDataSeeder.seedDemoData(context, AppDatabase.getInstance(context))
                                                ArchiveToastManager.show("成功灌入 $count 条演示手账记录")
                                            } catch (e: Exception) {
                                                ArchiveToastManager.show("灌入失败: ${e.message}", isError = true)
                                            }
                                            showDebugDevSheet = false
                                        }
                                    },
                                    shape = RoundedCornerShape(2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentWarm,
                                        contentColor = colors.archivePaper
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "灌入演示数据",
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 12.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val count = DemoDataSeeder.clearDemoData(context, AppDatabase.getInstance(context))
                                                ArchiveToastManager.show("已彻底清除 $count 条演示记录与资源")
                                            } catch (e: Exception) {
                                                ArchiveToastManager.show("清除失败: ${e.message}", isError = true)
                                            }
                                            showDebugDevSheet = false
                                        }
                                    },
                                    shape = RoundedCornerShape(2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentWarm),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        brush = SolidColor(colors.accentWarm)
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "清除演示数据",
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 12.sp
                                    )
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
