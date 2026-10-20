package com.looseleaf.app.ui.stream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedFilter by rememberSaveable { mutableStateOf("all") }
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

    // 年度年鉴弹窗状态
    var showAlmanacDialog by remember { mutableStateOf(false) }

    // 全局检索与速记随笔状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<LifeEntry?>(null) }

    // 生物识别弹窗标题：必须在 composable 作用域取值（onClick lambda 不是 composable）
    val deleteEntryBiometricTitle = stringResource(R.string.stream_screen_title_02)

    // 失败事件处置弹窗（重试 / 放弃）
    var showFailedEventsDialog by remember { mutableStateOf(false) }

    // 单条档案详情与编辑弹窗状态
    // MOB-P2-40：宽屏（平板 / 折叠屏展开态）走「列表 + 详情」双栏，
    // 窄屏仍是底部详情弹窗 —— 两份状态分开，展开/折叠切换时才不会互相打架。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP
    // 右侧详情栏占 45%（最多 560dp）——「记随笔」浮动印章要据此让位
    val paneWidthDp = (screenWidthDp * 0.45f).coerceAtMost(560f)
    var selectedEntryForDetail by remember { mutableStateOf<LifeEntry?>(null) }
    var detailDialogEntry by remember { mutableStateOf<LifeEntry?>(null) }

    // 选中入口：宽屏只「选中」（右栏展示），窄屏直接弹详情
    val selectEntry = remember(isWideLayout) { { entry: LifeEntry ->
        selectedEntryForDetail = entry
        if (!isWideLayout) detailDialogEntry = entry
    } }
    var lightboxPhoto by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // 更新检查：整个进程只查一次，失败静默（避免每次切 Tab 都打 GitHub API）
    LaunchedEffect(Unit) {
        if (!updateCheckedThisProcess && cloudUpdate == null && shouldCheckForUpdate(context)) {
            updateCheckedThisProcess = true
            // 检查本身放到后台线程，避免阻塞首帧
            val result = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdates() }
            when (result) {
                is UpdateCheckResult.HasUpdate ->
                    cloudUpdateJson = Json { ignoreUnknownKeys = true }.encodeToString(result.release)
                else -> Unit
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

    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

    // 年份列表同样来自后台派生流，不在 composition 里遍历全表
    val availableYears by syncManager.availableYearsFlow.collectAsStateWithLifecycle()

    // ============================================================
    // MOB-P1-32：时间流改为**分页窗口**
    // ============================================================
    // 改造前：把全量档案一次性物化进内存（2 万条 ≈ 20MB 常驻），
    // 再在 Kotlin 侧做分类/年份过滤与行模型装配 —— 每次同步后都要重算一遍。
    // 改造后：
    //   1) 过滤（分类 / 年份）下沉到 SQL，只取当前窗口；
    //   2) 列表只持有「已加载窗口」的数据，内存与数据量解耦；
    //   3) 行模型装配仍在后台线程（间隔天数 / 时间戳文本 / 属地）。
    var loadedEntries by remember { mutableStateOf<List<LifeEntry>>(emptyList()) }
    var streamRows by remember { mutableStateOf<List<StreamRowItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // 热力图计数改为 SQL GROUP BY（不再全表物化）
    val dayCounts by syncManager.dayCountsFlow.collectAsStateWithLifecycle()

    suspend fun loadWindow(offset: Int, limit: Int): List<LifeEntry> =
        withContext(Dispatchers.IO) {
            syncManager.loadStreamPage(selectedFilter, selectedYear, offset, limit)
        }

    // 1) 筛选 / 年份变化 → 重置窗口并加载第一页
    LaunchedEffect(selectedFilter, selectedYear) {
        loadedEntries = emptyList()
        streamRows = emptyList()
        hasMore = true
        val first = loadWindow(0, STREAM_PAGE_SIZE)
        loadedEntries = first
        totalCount = withContext(Dispatchers.IO) {
            syncManager.countStreamEntries(selectedFilter, selectedYear)
        }
        hasMore = first.size < totalCount
    }

    // 2) 数据变更（同步完成 / 本地乐观写入）→ 刷新当前窗口（保持滚动位置）
    LaunchedEffect(Unit) {
        syncManager.entriesRevisionFlow.collect {
            val window = maxOf(loadedEntries.size, STREAM_PAGE_SIZE)
            val refreshed = loadWindow(0, window)
            loadedEntries = refreshed
            totalCount = withContext(Dispatchers.IO) {
                syncManager.countStreamEntries(selectedFilter, selectedYear)
            }
            hasMore = refreshed.size < totalCount
        }
    }

    // 3) 滚动接近底部 → 加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (hasMore && !isLoadingMore && lastVisible >= loadedEntries.size - 6) {
                    isLoadingMore = true
                    val more = loadWindow(loadedEntries.size, STREAM_PAGE_SIZE)
                    if (more.isNotEmpty()) {
                        loadedEntries = loadedEntries + more
                    }
                    hasMore = more.isNotEmpty()
                    isLoadingMore = false
                }
            }
    }

    // 4) 行模型装配（间隔天数 / 时间戳文本 / 属地 / 年份 / 类型小写）在后台线程完成
    //    —— MOB-P0-05 / MOB-P0-06 / MOB-P0-07
    LaunchedEffect(loadedEntries) {
        streamRows = withContext(Dispatchers.Default) {
            loadedEntries.mapIndexed { index, entry ->
                StreamRowItem(
                    entry = entry,
                    gapDays = if (index == 0) 0L else FormatUtils.calculateDaysBetween(
                        entry.occurredAt,
                        loadedEntries[index - 1].occurredAt
                    ),
                    displayTime = FormatUtils.formatDisplayDateTime(entry.occurredAt),
                    displayPlace = listOfNotNull(entry.cityName, entry.location)
                        .distinct()
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                )
            }
        }
    }

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
                    .fillMaxWidth(if (isWideLayout) 0.55f else 1f)
                    .fillMaxHeight()
                    .widthIn(max = if (isWideLayout) 620.dp else 680.dp)
                    .align(if (isWideLayout) Alignment.TopStart else Alignment.TopCenter)
            ) {
            // 顶栏：标尺、年度年鉴与同步指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stream_screen_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.stream_screen_text_02),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasPending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentWarm.copy(alpha = 0.12f))
                                .clickable(role = Role.Button) {
                                    scope.launch {
                                        // 徽标文案是「待同步」：点击应立即推进发件箱（排队 Worker），
                                        // 而不是拉一整轮远端数据
                                        ArchiveToastManager.show("发件箱已重新排队，联网后自动上传 ($pendingCount 项待重放)")
                                        syncManager.scheduleSync()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentWarm)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.stream_screen_text_tpl_01, pendingCount),
                                fontSize = 11.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (hasFailed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentWarm.copy(alpha = 0.18f))
                                .clickable(role = Role.Button) {
                                    // 弹二选一而不是直接重试：永久 4xx / 重试耗尽的事件
                                    // 只会再失败一轮，用户必须有一条「放弃」的出路
                                    showFailedEventsDialog = true
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_error_outline),
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.stream_screen_text_tpl_02, failedCount),
                                fontSize = 11.sp,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // 全局检索按钮
                    IconButton(
                        onClick = {
                            showSearchDialog = true
                        },
                        // 40dp 而非 32dp：右上角本就是单手最难触达的盲区，热区不能再缩
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.stream_screen_contentDescription_01),
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 年度年鉴入口按钮
                    IconButton(
                        onClick = {
                            showAlmanacDialog = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_auto_stories),
                            contentDescription = stringResource(R.string.stream_screen_contentDescription_02),
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 同步刷新按钮
                    IconButton(
                        onClick = refreshArchive,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.stream_screen_contentDescription_03),
                            tint = if (isRefreshing) colors.accentWarm else colors.inkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

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
                            Text("立即安装", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
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
                            Text("应用内更新", fontSize = 12.sp, fontFamily = ArchiveFontFamilies.EditorialSerif)
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

    // MOB-P1-84：切换分类 / 年份后必须把列表滚回顶部 ——
            // 否则数据集变小而 LazyListState 仍停在旧偏移，屏幕看起来「一片空白」。
            LaunchedEffect(selectedFilter, selectedYear) {
                listState.scrollToItem(0)
            }

            // 分类筛选横滑栏
            StreamFilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = {
                    selectedFilter = it
                }
            )

            // 年份归档切换栏
            YearFilterBar(
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = {
                    selectedYear = it
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 时间流列表
            // MOB-P1-86：此前只有「空态」一个分支，导致首轮同步未完成时
            // 就显示「岁月静好，暂无此分类记录」，随后记录才陆续出现 ——
            // 用户会以为数据丢了。现在拆成「加载中 / 真的没有 / 筛选无匹配」三态。
            val isFiltering = selectedFilter != "all" || selectedYear != null
            if (loadedEntries.isEmpty() && onThisDayEntries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!firstSyncCompleted) {
                            // 加载中：给出明确反馈，而不是「空列表」
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.stream_screen_text_04),
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 15.sp,
                                color = colors.inkMuted
                            )
                        } else {
                            Text(
                                text = if (isFiltering) {
                                    "这段时间里没有符合条件的记录"
                                } else {
                                    "岁月静好，暂无此分类记录"
                                },
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 15.sp,
                                color = colors.inkMuted
                            )
                            if (isFiltering) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.stream_screen_text_05),
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            } else {
                // 原生下拉刷新手势（AOPT-06）：列表滚到顶继续下拉即可同步，
                // 不再依赖右上角那颗 40dp 图标
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refreshArchive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    // 外层 Scaffold 已按 LooseLeafBottomBar 扣除 innerPadding；
                    // 这里的底部留白专门留给右下角「记随笔」浮动印章（56dp FAB + 16dp 边距 + 16dp 呼吸），
                    // 否则滚到底时最后一张卡片会被 FAB 压住
                    contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
                ) {
                    // 52周时光生活刻度热力图
                    item(key = "life_activity_heatmap") {
                        LifeActivityHeatmap(
                            dayCounts = dayCounts,
                            totalEntryCount = totalCount,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // 那年今日 · 岁月回响 折叠卡片
                    if (onThisDayEntries.isNotEmpty()) {
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

                    // 滚动期的日期解析全部预计算：原来在 itemsIndexed 渲染块里
                    // 每条都要解析两次 ISO 字符串，快速滑动时主线程出现帧毛刺（AOPT-03b）
                    items(
                        items = streamRows,
                        key = { row -> row.entry.id },
                        contentType = { row ->
                            if (row.entry.attachments.isEmpty()) "text_entry" else "media_entry"
                        }
                    ) { row ->
                        // 岁月留白刻度：若与上一条时间跨度 >= 14 天，自动插入切线
                        if (row.gapDays >= 14) {
                            TemporalGapSeparator(days = row.gapDays)
                        }

                        StreamEntryCard(
                            row = row,
                            apiClient = apiClient,
                            // MOB-P1-24：稳定引用（见上方定义）
                            onClick = onEntryClick,
                            onDelete = onEntryDelete,
                            onPhotoClick = onPhotoClick,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                }
            }
        }

        // 「记随笔」纸上浮动印章：右下角单手拇指热区，替代原顶栏右上角 32dp 图标
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // MOB-P2-40：宽屏时右侧有详情栏，浮动印章让到列表列里
                .padding(end = if (isWideLayout) (paneWidthDp + 20).dp else 20.dp, bottom = 20.dp)
                .size(56.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    spotColor = colors.frameShadowSpot,
                    ambientColor = colors.frameShadowAmbient
                )
                .clip(CircleShape)
                .background(colors.accentWarm)
                .clickable(role = Role.Button) { showQuickNoteDialog = true }
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.stream_screen_contentDescription_04),
                tint = colors.archivePaper,
                modifier = Modifier.size(24.dp)
            )
        }

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
                    .fillMaxWidth(0.45f)
                    .widthIn(max = 560.dp)
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
                onSaveEdit = { req ->
                    scope.launch {
                        syncManager.updateEntryOptimistic(activeEntry.id, req)
                        ArchiveToastManager.show("已修订归册")
                        detailDialogEntry = null
                        selectedEntryForDetail = null
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

        // 失败事件处置：重试或放弃（放弃仅移除发件箱事件，本地记录保留）
        // 统一为纸墨 Dialog（AOPT-08b）：原 Material3 AlertDialog 的圆角/排版与全站不一致
        if (showFailedEventsDialog) {
            Dialog(
                onDismissRequest = { showFailedEventsDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 380.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(4.dp),
                                spotColor = colors.frameShadowSpot,
                                ambientColor = colors.frameShadowAmbient
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archivePaper)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 22.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_screen_text_tpl_06, failedCount),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.stream_screen_text_06) +
                                "（本地记录与「待同步」标记保留，便于排查）。点卡片外部可稍后处理。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                showFailedEventsDialog = false
                                scope.launch {
                                    syncManager.discardFailedEvents()
                                    ArchiveToastManager.show("已放弃失败事件；本地记录保留")
                                }
                            }) {
                                Text("放弃", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp, color = colors.inkMuted)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    showFailedEventsDialog = false
                                    scope.launch {
                                        ArchiveToastManager.show("正在重试 $failedCount 条失败记录…")
                                        syncManager.retryFailedEvents()
                                    }
                                },
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentWarm,
                                    contentColor = colors.archivePaper
                                )
                            ) {
                                Text("全部重试", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
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

        // 年度生活年鉴五幕弹窗
        if (showAlmanacDialog) {
            AlmanacDialog(
                apiClient = apiClient,
                earliestEntryYear = availableYears.lastOrNull(),
                onDismiss = { showAlmanacDialog = false }
            )
        }
    }
}
}
