package com.looseleaf.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.looseleaf.app.ui.components.ArchiveToastHost
import com.looseleaf.app.ui.components.ArchiveToastManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.feature.ledger.LedgerEntry
import com.looseleaf.feature.ledger.LedgerModule
import com.looseleaf.app.ui.pairing.PairingScreen
import com.looseleaf.app.ui.components.SpineBottomBar
import com.looseleaf.app.ui.wishes.CreateWishSheet
import com.looseleaf.app.ui.wishes.EditWishSheetDialog
import com.looseleaf.app.ui.wishes.ResolveWishSheet
import com.looseleaf.app.ui.wishes.WishesList
import com.looseleaf.app.ui.settings.SettingsHost
import com.looseleaf.app.ui.stream.StreamScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.looseleaf.app.utils.CrashReporter
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.painterResource

class MainActivity : FragmentActivity() {

    companion object {
        /** 进程内解锁态：旋转屏幕重建 Activity 时无需再次验证，应用整体切后台则立即失效 */
        @Volatile
        private var sessionUnlocked = false

        /** MOB-P0-11：已知的一级路由白名单（桌面快捷方式 / 深链据此校验） */
        private val KNOWN_ROUTES = setOf("stream", "ledger", "wishes")

        /** MOB-P1-60：系统大字体缩放上限（超过会撑破固定高度的键盘与顶栏布局） */
        private const val MAX_FONT_SCALE = 1.5f
    }

    // AND-7：不再在 onCreate 同步构建这两个单例。
    // by lazy 保证首次真正使用才初始化；常见冷启动路径（首帧渲染）
    // 不会因为 KeyStore / Room / WorkManager 而阻塞主线程。
    private val credentialStore by lazy { CredentialStore.getInstance(this) }
    private val syncManager by lazy { SyncManager.getInstance(this) }
    private var requestedRouteState = mutableStateOf<String?>(null)
    private var pendingDeepLinkPayloadState = mutableStateOf<String?>(null)
    private var ssoPendingHubState = mutableStateOf<String?>(null)
    private var repairReconnectModeState = mutableStateOf(false)
    private var expandPairingFallbackState = mutableStateOf(false)

    // MOB-P0-09：外观模式提升为 Activity 字段级状态，
    // 这样回调可以做成「稳定引用」，MainContent 才有机会跳过重组。
    private val appearanceModeState = mutableStateOf(AppearanceMode.SYSTEM)
    private val isPairedState = mutableStateOf(false)

    /**
     * 生物识别门禁状态必须放在 Activity 字段并用 SnapshotState 暴露给 Compose：
     * 之前只用 `remember { mutableStateOf(...) }` + 静态 `sessionUnlocked`，
     * 切后台时只改了静态变量，Compose 里的 `isUnlocked` 仍是 true ⇒ 回前台不重锁。
     */
    private val isUnlockedState = mutableStateOf(false)
    private val biometricsEnabledState = mutableStateOf(false)

    /** MOB-P1-34：进程级生命周期观察者（持有引用，需在 onDestroy 移除） */
    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (credentialStore.isBiometricsEnabled) {
                    sessionUnlocked = false
                    isUnlockedState.value = false
                }
            }
            Lifecycle.Event.ON_START -> {
                // 设置页可能刚开关过生物识别，回前台时重新读取，而不是沿用冷启动时的值。
                val enabled = credentialStore.isBiometricsEnabled
                biometricsEnabledState.value = enabled
                isUnlockedState.value = !enabled || sessionUnlocked
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        com.looseleaf.app.reminder.InstallmentReminderWorker.checkAndNotify(this@MainActivity)
                    }
                }
            }
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MOB-P1-20：必须早于 super.onCreate()，由系统接管冷启动的第一帧
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // MOB-P0-12：WindowInsets.ime 仅 API 30+ 存在；API 26~29 一旦进入
        // edge-to-edge，窗口不再随软键盘 resize，而 Compose 的 imePadding()
        // 又恒为 0 ⇒ 软键盘会直接盖住全部输入框。
        // 因此低版本保留系统默认的 adjustResize 行为。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        // 只在「首次创建」时消费 Intent：否则旋转屏幕会把用户拽回快捷方式指定的 Tab
        if (savedInstanceState == null) {
            requestedRouteState.value = intent?.getStringExtra("shortcut_route")
            intent?.data?.toString()?.takeIf { it.startsWith("looseleaf://") }?.let {
                pendingDeepLinkPayloadState.value = it
            }
        }

        // 「应用整体切后台才重锁」必须监听进程级生命周期：
        // Activity 的 ON_STOP 在拉起系统相机 / 相册选择器时同样会触发，
        // 按它判「离开应用」会让用户拍一张照回来就被锁，暗房流程被反复打断。
        // ProcessLifecycleOwner 天然不受旋转重建影响，也无需 isChangingConfigurations 特判。
        // MOB-P1-34：进程级 Lifecycle 的观察者**必须**在 onDestroy 里移除 ——
        // 否则每次旋转 / 配置变更都会再叠加一个观察者，
        // 泄漏的 Activity 实例线性增长，且 ON_STOP 被重复触发（重复置锁）。
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        biometricsEnabledState.value = credentialStore.isBiometricsEnabled
        isUnlockedState.value = sessionUnlocked || !biometricsEnabledState.value

        // 外观偏好必须在首帧前读到：否则暗房用户冷启动会先闪一下纸色界面。
        // 这里只读普通 SharedPreferences（非加密存储），开销远小于一次主题闪变。
        appearanceModeState.value = credentialStore.appearanceMode
        isPairedState.value = credentialStore.isPaired()

        setContent {
            // MOB-P1-85：待同步与失败计数（供全局离线横幅使用）
            var pendingEventCount by remember { mutableIntStateOf(0) }
            var failedEventCount by remember { mutableIntStateOf(0) }
            var showFailedEventsSheet by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                runCatching {
                    // AND-7：先显式启动后台基础设施，再订阅计数（幂等）
                    syncManager.ensureStarted()
                    launch {
                        syncManager.pendingEventCountFlow.collect { count ->
                            pendingEventCount = count
                        }
                    }
                    launch {
                        syncManager.failedEventCountFlow.collect { count ->
                            failedEventCount = count
                        }
                    }
                }
            }

            // 外观三态（AOPT-08c）：跟随系统时由 isSystemInDarkTheme 实时决定
            val appearanceMode by appearanceModeState
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> systemInDark
                AppearanceMode.PAPER -> false
                AppearanceMode.DARKROOM -> true
            }

            // MOB-P0-09：稳定引用的回调 —— 避免每次重组都产生新 lambda 实例，
            // 否则 MainContent / Scaffold / 整个屏幕树都会被判定为「参数已变」而全量重组。
            val onAppearanceModeChanged: (AppearanceMode) -> Unit = remember {
                { mode: AppearanceMode ->
                    credentialStore.appearanceMode = mode
                    appearanceModeState.value = mode
                }
            }

            LooseLeafTheme(darkTheme = isDarkTheme) {
                // MOB-P1-60：钳制系统大字体缩放。
                // 全部字号都用了 sp（会随 fontScale 放大），但布局没有为放大做准备：
                // 4×3 大键盘（26sp）放大后高度超过固定区 ⇒「记入账本」按钮被挤出屏幕；
                // 顶栏 22sp 标题会与右侧 4 个图标按钮重叠；底栏 11sp 标签会换行。
                // 这里把 fontScale 限制在 1.5x —— 仍保留大部分无障碍放大能力，
                // 同时避免布局被彻底撑破。
                val baseDensity = LocalDensity.current
                val clampedDensity = remember(baseDensity) {
                    Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale.coerceAtMost(MAX_FONT_SCALE)
                    )
                }
                CompositionLocalProvider(
                    LocalDensity provides clampedDensity
                ) {
                    // Toast 宿主挂在最外层（各底层抽屉已自带独立 Window 级 ModalBottomSheet 遮罩）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LooseLeafTheme.colors.archivePaper)
                    ) {
                        // 状态栏内边距在最外层统一消费一次：横幅、配对页、锁屏、设置页与主 Scaffold
                        // 都不再各自让位，避免「设置页压住状态栏 / 横幅钻进状态栏」这类错位。
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            OfflineBanner(
                                pendingCount = pendingEventCount,
                                failedCount = failedEventCount,
                                onOpenFailedSheet = { showFailedEventsSheet = true }
                            )
                            MainContent(
                                modifier = Modifier.weight(1f),
                                isDarkTheme = isDarkTheme,
                                onAppearanceModeChanged = onAppearanceModeChanged
                            )
                        }

                        if (showFailedEventsSheet) {
                            com.looseleaf.app.ui.stream.FailedEventsSheet(
                                syncManager = syncManager,
                                onDismiss = { showFailedEventsSheet = false }
                            )
                        }

                        // 顶层全局自研纸墨 Toast 通知覆盖层
                        ArchiveToastHost()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainContent(
        modifier: Modifier = Modifier,
        isDarkTheme: Boolean,
        onAppearanceModeChanged: (AppearanceMode) -> Unit
    ) {
        var isPaired by isPairedState
        val currentRouteState = rememberSaveable {
            mutableStateOf(requestedRouteState.value ?: "stream")
        }
        val currentRoute by currentRouteState

        // MOB-P2-73：返回栈 —— 记录离开时的路由，返回时逐级回退。
        // 用 rememberSaveable 保证旋转 / 配置变更后返回栈不丢失。
        val routeStack = rememberSaveable(saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )) { mutableStateListOf<String>() }

        fun sectionOf(route: String): String = when (route) {
            "wishes" -> "ledger"
            else -> route
        }

        var showSettings by rememberSaveable { mutableStateOf(false) }
        var requestedLedgerTab by rememberSaveable { mutableStateOf<Int?>(null) }

        // MOB-P0-09：稳定的路由切换回调（键为状态对象本身，引用恒定）
        val onRouteSelected: (String) -> Unit = remember(currentRouteState) {
            { route: String ->
                val normalized = if (route == "wishes") {
                    requestedLedgerTab = 1
                    "ledger"
                } else {
                    route
                }
                val current = currentRouteState.value
                if (normalized != current) {
                    val fromSection = sectionOf(current)
                    val toSection = sectionOf(normalized)
                    if (fromSection != toSection) {
                        routeStack.removeAll { it == toSection || it == fromSection }
                        routeStack.add(fromSection)
                    }
                    currentRouteState.value = normalized
                }
            }
        }

        // 跨页跳转意图：一次性消费
        var pendingContentFilter by remember { mutableStateOf<Set<String>?>(null) }

        val onOpenWishTarget: (Long) -> Unit = remember(onRouteSelected) {
            { _: Long ->
                requestedLedgerTab = 1
                onRouteSelected("ledger")
            }
        }

        val pendingShortcut by requestedRouteState
        LaunchedEffect(pendingShortcut) {
            val target = pendingShortcut
            if (!target.isNullOrBlank()) {
                when (target) {
                    "wishes" -> {
                        requestedLedgerTab = 1
                        currentRouteState.value = "ledger"
                    }
                    in KNOWN_ROUTES -> currentRouteState.value = target
                    else -> currentRouteState.value = "stream"
                }
                requestedRouteState.value = null
            }
        }

        // AND-7：先读应用内开关（普通 Prefs，代价极低）。
        // 关闭生物识别时**完全不碰** BiometricManager / canAuthenticate；
        // 开启时才把它放到 IO 线程，首帧先用 null 表示「检测中」。
        val biometricsEnabled by biometricsEnabledState
        var canAuth by remember {
            mutableStateOf<Boolean?>(if (biometricsEnabled) null else true)
        }
        // 直接委托到 Activity 字段：ON_STOP 改它，Compose 会立即重锁。
        var isUnlocked by isUnlockedState
        var isAuthenticating by remember { mutableStateOf(false) }

        LaunchedEffect(biometricsEnabled) {
            if (biometricsEnabled) {
                val allowed = withContext(Dispatchers.IO) {
                    BiometricHelper.canAuthenticate(this@MainActivity)
                }
                canAuth = allowed
                // 设备没有任何可用生物识别 / 锁屏口令：与旧行为一致，直接放行
                if (!allowed) isUnlocked = true
            }
        }

        val biometricUnlockTitle = stringResource(R.string.main_activity_title_01)

        fun requestBiometricUnlock() {
            if (isAuthenticating) return
            if (!biometricsEnabled || canAuth == false) {
                isUnlocked = true
                return
            }
            // 后台检测尚未完成：等 LaunchedEffect(canAuth) 再决定，避免主线程同步探测
            if (canAuth == null) return

            isAuthenticating = true
            BiometricHelper.authenticate(
                activity = this@MainActivity,
                title = biometricUnlockTitle,
                subtitle = "验证面容或指纹以解锁散页随行手账",
                onSuccess = {
                    sessionUnlocked = true
                    isUnlocked = true
                    isAuthenticating = false
                },
                onError = {
                    isAuthenticating = false
                }
            )
        }

        // 后台检测完成后补一次解锁（首次进入时 ON_START 可能早于检测结果）
        LaunchedEffect(canAuth) {
            if (isPaired && !isUnlocked && canAuth == true) {
                requestBiometricUnlock()
            }
        }

        // 冷启动 / 切回前台都要重新验证（docs/mobile.md §3.4）。
        // 重锁的「切后台」判定在 onCreate 里由 ProcessLifecycleOwner 负责。
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (isPaired && !isUnlocked) {
                requestBiometricUnlock()
            }
        }

        // 401 全局引导（AOPT-09b, P1-7）：使用 rememberSaveable 保护弹窗展示状态，防止屏幕旋转丢失
        var showRepairDialog by rememberSaveable { mutableStateOf(false) }

        // MOB-P1-63：上次是非正常退出（崩溃）时，给出一次友好提示并保留诊断信息入口
        var showCrashNotice by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            showCrashNotice = CrashReporter.lastCrashReport(this@MainActivity) != null
        }
        val apiClient = remember { com.looseleaf.app.data.api.APIClient.getInstance(this@MainActivity) }
        // MOB-P1-07：使用 rememberSaveable 并在重配对/弹窗关闭后 resetReplayCache，避免旋转屏幕时重复提示 401
        var repairPromptShown by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            apiClient.unauthorizedEvents.collect {
                if (isPaired && !repairPromptShown) {
                    repairPromptShown = true
                    showRepairDialog = true
                }
            }
        }

        // MOB-P1-63：崩溃提示（纸页写坏了）
        if (showCrashNotice) {
            ArchiveConfirmDialog(
                title = stringResource(R.string.main_activity_title_02),
                content = "上次打开散页时发生了意外中断，记录与账目都已安全保存在本机。" +
                    "你可以继续使用；若反复出现，可在展台页反馈这个问题。",
                confirmText = stringResource(R.string.main_activity_confirmText_01),
                dismissText = null,
                onConfirm = {
                    showCrashNotice = false
                    CrashReporter.clearLastCrash(this@MainActivity)
                },
                onDismiss = {
                    showCrashNotice = false
                    CrashReporter.clearLastCrash(this@MainActivity)
                }
            )
        }

        if (showRepairDialog) {
            val dialogColors = LooseLeafTheme.colors
            com.looseleaf.app.ui.components.ArchiveDialog(
                onDismissRequest = {
                    showRepairDialog = false
                    apiClient.unauthorizedEvents.resetReplayCache()
                },
                title = stringResource(R.string.main_activity_title_03)
            ) {
                Text(
                    text = "这台设备的访问凭据已失效或已被移出受信任设备组。本机尚未同步的记录均已妥善保留，重新接入后会自动续传。",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = dialogColors.inkSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                com.looseleaf.app.ui.components.ArchivePrimaryButton(
                    text = stringResource(R.string.main_activity_sso_reconnect),
                    onClick = {
                        val hubCandidate = apiClient.baseUrl.ifBlank {
                            com.looseleaf.app.data.api.APIClient.DEFAULT_BASE_URL
                        }
                        val launchedHub = com.looseleaf.app.ui.pairing.launchVesperAuthBrowser(
                            this@MainActivity,
                            hubCandidate
                        )
                        if (launchedHub != null) {
                            ssoPendingHubState.value = launchedHub
                            repairReconnectModeState.value = true
                            expandPairingFallbackState.value = false
                            showRepairDialog = false
                            repairPromptShown = false
                            apiClient.unauthorizedEvents.resetReplayCache()
                            credentialStore.deviceToken = null
                            syncManager.cancelScheduledSync()
                            isPaired = false
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.looseleaf.app.ui.components.ArchiveTextButton(
                        text = stringResource(R.string.main_activity_manual_reconnect),
                        onClick = {
                            ssoPendingHubState.value = null
                            repairReconnectModeState.value = true
                            expandPairingFallbackState.value = true
                            showRepairDialog = false
                            repairPromptShown = false
                            apiClient.unauthorizedEvents.resetReplayCache()
                            credentialStore.deviceToken = null
                            syncManager.cancelScheduledSync()
                            isPaired = false
                        }
                    )
                    com.looseleaf.app.ui.components.ArchiveTextButton(
                        text = stringResource(R.string.main_activity_dismissText_01),
                        textColor = dialogColors.inkMuted,
                        onClick = {
                            showRepairDialog = false
                            apiClient.unauthorizedEvents.resetReplayCache()
                        }
                    )
                }
            }
        }

        if (!isPaired) {
            val deepLinkPayload by pendingDeepLinkPayloadState
            val ssoPendingHub by ssoPendingHubState
            val isSessionReconnect by repairReconnectModeState
            val expandFallbackInitially by expandPairingFallbackState
            PairingScreen(
                initialPayload = deepLinkPayload,
                initialSsoHubUrl = ssoPendingHub,
                isSessionReconnect = isSessionReconnect,
                expandFallbackInitially = expandFallbackInitially,
                onPayloadConsumed = {
                    pendingDeepLinkPayloadState.value = null
                },
                onSsoHubInitiated = { initiatedHub ->
                    ssoPendingHubState.value = initiatedHub
                },
                onPairingSuccess = {
                    sessionUnlocked = true
                    isPaired = true
                    isUnlocked = true
                    repairPromptShown = false
                    ssoPendingHubState.value = null
                    repairReconnectModeState.value = false
                    expandPairingFallbackState.value = false
                    apiClient.unauthorizedEvents.resetReplayCache()
                    pendingDeepLinkPayloadState.value = null
                    currentRouteState.value = "stream"
                    // 重新配对后必须重启队列：401 暂停期间挂起的发件箱事件
                    // 若无新写入，要等进程重启才会重试
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            syncManager.cancelScheduledSync()
                            syncManager.scheduleSync()
                        }
                    }
                }
            )
        } else if (!isUnlocked) {
            BiometricLockOverlay(onRequestUnlock = { requestBiometricUnlock() })
        } else {
            var lastBackPressTime by remember { mutableLongStateOf(0L) }

            // MOB-P2-72：改用 PredictiveBackHandler ——
            // 系统声明了 enableOnBackInvokedCallback="true"，但旧式 BackHandler
            // 没有跟手动画，用户看到的是「松手后瞬间跳转」。
            // PredictiveBackHandler 在 API < 33 上会自动退化为传统 BackHandler，行为一致
            //
            // MOB-P2-72 的后续修正（CI 的 lint 门禁把它揪了出来）：activity-compose 1.9
            // 的回调收的是 **Flow<BackEventCompat>**，而且**必须 collect** ——
            // 不 collect 的话 AndroidX 会直接抛 "You must collect the progress flow"，
            // 更糟的是整段逻辑会在**手势刚开始**时就跑一遍（松手前就跳走了），跟手动画也没了。
            //
            // 官方推荐的结构：collect 正常结束 = 手势**完成**；抛 CancellationException = 手势**取消**。
            PredictiveBackHandler { progress ->
                try {
                    progress.collect { /* 手势进度事件：目前不驱动额外动画，只消费掉 */ }
                    when {
                        showSettings -> {
                            showSettings = false
                        }
                        routeStack.isNotEmpty() -> {
                            currentRouteState.value = routeStack.removeAt(routeStack.lastIndex)
                        }
                        currentRoute != "stream" -> {
                            currentRouteState.value = "stream"
                        }
                        else -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPressTime < 2000L) {
                                finish()
                            } else {
                                lastBackPressTime = now
                                ArchiveToastManager.show("再按一次退出散页")
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            }

            val saveableStateHolder = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()
            val wishes by syncManager.wishesFlow.collectAsStateWithLifecycle()
            val wantingWishCount = remember(wishes) { wishes.count { it.status == "wanting" } }

            var showCreateWishSheet by rememberSaveable { mutableStateOf(false) }
            var editingWish by remember { mutableStateOf<Wish?>(null) }
            var resolvingWish by remember { mutableStateOf<Wish?>(null) }
            var deletingWish by remember { mutableStateOf<Wish?>(null) }

            LaunchedEffect(currentRoute) {
                if (currentRoute == "ledger" && !SyncManager.isSessionPullDone("wishes")) {
                    SyncManager.markSessionPullDone("wishes")
                    syncManager.pullWishesData(forceFull = false)
                }
            }

            if (showCreateWishSheet) {
                CreateWishSheet(
                    syncManager = syncManager,
                    onDismiss = { showCreateWishSheet = false },
                    onCreated = {
                        syncManager.pullWishesData(forceFull = false)
                        syncManager.pullStreamData(forceFull = false)
                    }
                )
            }

            editingWish?.let { wish ->
                EditWishSheetDialog(
                    wish = wish,
                    apiClient = apiClient,
                    onDismiss = { editingWish = null },
                    onSaved = {
                        scope.launch {
                            syncManager.pullWishesData(forceFull = false)
                            syncManager.pullStreamData(forceFull = false)
                        }
                    }
                )
            }

            resolvingWish?.let { wish ->
                ResolveWishSheet(
                    wish = wish,
                    apiClient = apiClient,
                    syncManager = syncManager,
                    onDismiss = { resolvingWish = null },
                    onResolved = {
                        syncManager.pullWishesData(forceFull = false)
                        syncManager.pullStreamData(forceFull = false)
                        runCatching { LedgerModule.syncTransactions(force = true) }
                    }
                )
            }

            deletingWish?.let { wish ->
                ArchiveConfirmDialog(
                    title = "删除心愿",
                    content = "确定要删除「${wish.title}」吗？",
                    confirmText = "删除",
                    isDestructive = true,
                    onConfirm = {
                        deletingWish = null
                        scope.launch {
                            runCatching {
                                val queued = syncManager.deleteWishOrQueue(wish.id)
                                if (queued) ArchiveToastManager.show("已离线排队删除心愿")
                                else ArchiveToastManager.show("心愿已删除")
                                syncManager.pullWishesData(forceFull = false)
                            }.onFailure {
                                ArchiveToastManager.show("删除失败: ${it.message}", isError = true)
                            }
                        }
                    },
                    onDismiss = { deletingWish = null }
                )
            }

            Box(modifier = modifier.fillMaxSize()) {
                if (showSettings) {
                    SettingsHost(
                        appearanceMode = appearanceModeState.value,
                        onAppearanceModeChanged = onAppearanceModeChanged,
                        onUnpair = {
                            showSettings = false
                            isPaired = false
                            credentialStore.deviceToken = null
                            syncManager.cancelScheduledSync()
                        },
                        onBack = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            LooseLeafBottomBar(
                                currentRoute = currentRoute,
                                onRouteSelected = onRouteSelected
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            val stateKey = sectionOf(currentRoute)
                            saveableStateHolder.SaveableStateProvider(stateKey) {
                                when (currentRoute) {
                                    "stream" -> StreamScreen(
                                        onOpenWishTarget = onOpenWishTarget,
                                        initialContentFilter = pendingContentFilter,
                                        onContentFilterConsumed = { pendingContentFilter = null },
                                        onOpenSettings = { showSettings = true }
                                    )
                                    "ledger", "wishes" -> LedgerEntry(
                                        isDarkTheme = isDarkTheme,
                                        requestedTab = requestedLedgerTab,
                                        onRequestedTabConsumed = { requestedLedgerTab = null },
                                        wishlistTabContent = { tabMod ->
                                            WishesList(
                                                wishes = wishes,
                                                onResolveClick = { resolvingWish = it },
                                                onEditClick = { editingWish = it },
                                                onDeleteClick = { deletingWish = it },
                                                onNewWishClick = { showCreateWishSheet = true },
                                                modifier = tabMod
                                            )
                                        },
                                        wishlistCount = wantingWishCount,
                                        onRefreshWishlist = {
                                            syncManager.pullWishesData(forceFull = false)
                                        },
                                        onAddWishClick = { showCreateWishSheet = true },
                                        onOpenSettings = { showSettings = true }
                                    )
                                    else -> {
                                        LaunchedEffect(currentRoute) {
                                            ArchiveToastManager.show("该入口已下线，已返回散页", isError = true)
                                            currentRouteState.value = "stream"
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

    override fun onDestroy() {
        // MOB-P1-34：移除进程级观察者，避免泄漏 Activity 实例
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("shortcut_route")?.takeIf { it.isNotBlank() }?.let {
            requestedRouteState.value = it
        }
        intent.data?.toString()?.takeIf { it.startsWith("looseleaf://") }?.let {
            pendingDeepLinkPayloadState.value = it
        }
    }
}

/**
 * MOB-P1-85：全局离线与同步异常横幅。
 * 断网时明确告知「当前离线 · N 项待同步」；发生同步失败时明确提示异常并支持点击查看重试。
 */
@Composable
private fun OfflineBanner(
    pendingCount: Int,
    failedCount: Int = 0,
    onOpenFailedSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LooseLeafTheme.colors
    var isOnline by remember { mutableStateOf(isNetworkAvailable(context)) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = isNetworkAvailable(context) }
            override fun onUnavailable() { isOnline = false }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = !isOnline || failedCount > 0,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        val hasFailed = failedCount > 0
        val tint = if (hasFailed) colors.alertRed else colors.inkSecondary
        val container = if (hasFailed) colors.negativeTint else colors.archiveSubtle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(com.looseleaf.app.theme.ArchiveShapes.full)
                    .background(container)
                    .then(if (hasFailed) Modifier.clickable(onClick = onOpenFailedSheet) else Modifier)
                    .padding(start = 12.dp, end = if (hasFailed) 8.dp else 14.dp, top = 7.dp, bottom = 7.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_off),
                    contentDescription = stringResource(R.string.main_activity_contentDescription_02),
                    tint = tint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        hasFailed && pendingCount > 0 -> "$failedCount 项同步失败 · $pendingCount 项待同步"
                        hasFailed -> "$failedCount 项记录同步失败"
                        pendingCount > 0 -> "当前离线 · $pendingCount 项联网后自动上传"
                        else -> "当前离线 · 记录会先存在本机"
                    },
                    style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                    color = tint,
                    maxLines = 1
                )
                if (hasFailed) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = com.looseleaf.app.ui.components.FolioGlyphs.ChevronRight,
                        contentDescription = "查看失败项",
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** 读取当前是否有可用网络（不触发任何网络请求） */
private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    return runCatching {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}

@Composable
fun LooseLeafBottomBar(
    currentRoute: String,
    onRouteSelected: (String) -> Unit
) {
    SpineBottomBar(
        currentRoute = currentRoute,
        onRouteSelected = onRouteSelected
    )
}
