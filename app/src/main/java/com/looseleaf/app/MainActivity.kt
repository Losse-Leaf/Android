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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Immutable
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.ledger.LedgerScreen
import com.looseleaf.app.ui.pairing.PairingScreen
import com.looseleaf.app.ui.profile.ProfileScreen
import com.looseleaf.app.ui.projectswishes.ProjectsWishesScreen
import com.looseleaf.app.ui.stream.StreamScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.painter.Painter

class MainActivity : FragmentActivity() {

    companion object {
        /** 进程内解锁态：旋转屏幕重建 Activity 时无需再次验证，应用整体切后台则立即失效 */
        @Volatile
        private var sessionUnlocked = false

        /** MOB-P0-11：已知的一级路由白名单（桌面快捷方式 / 深链据此校验） */
        private val KNOWN_ROUTES = setOf("stream", "ledger", "footprints", "projects", "profile")

        /** MOB-P1-60：系统大字体缩放上限（超过会撑破固定高度的键盘与顶栏布局） */
        private const val MAX_FONT_SCALE = 1.5f
    }

    private lateinit var credentialStore: CredentialStore
    private lateinit var syncManager: SyncManager
    private var requestedRouteState = mutableStateOf<String?>(null)
    private var pendingDeepLinkPayloadState = mutableStateOf<String?>(null)

    // MOB-P0-09：外观模式提升为 Activity 字段级状态，
    // 这样回调可以做成「稳定引用」，MainContent 才有机会跳过重组。
    private val appearanceModeState = mutableStateOf(AppearanceMode.SYSTEM)

    /** MOB-P2-45：高对比度模式（应用内开关） */
    private val highContrastState = mutableStateOf(false)

    /** MOB-P1-34：进程级生命周期观察者（持有引用，需在 onDestroy 移除） */
    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && credentialStore.isBiometricsEnabled) {
            sessionUnlocked = false
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

        credentialStore = CredentialStore.getInstance(this)
        syncManager = SyncManager.getInstance(this)

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

        appearanceModeState.value = credentialStore.appearanceMode
        // MOB-P2-45：高对比度开关
        highContrastState.value = credentialStore.highContrast

        setContent {
            // MOB-P1-85：待同步计数（供全局离线横幅使用）
            var pendingEventCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                runCatching {
                    SyncManager.getInstance(this@MainActivity).pendingEventCountFlow.collect { count ->
                        pendingEventCount = count
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

            LooseLeafTheme(darkTheme = isDarkTheme, highContrast = highContrastState.value) {
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
                CompositionLocalProvider(LocalDensity provides clampedDensity) {
                // Toast 宿主挂在最外层：配对页（!isPaired）与锁屏页（!isUnlocked）也有
                // 错误提示——挂在主界面分支里的话，配对阶段的「密钥库不可用」「连接失败」
                // 会因为没有 Collector 被直接丢弃，新用户入网第一路径零反馈。
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // MOB-P1-85：全局离线横幅 ——
                        // 此前「离线」这件事只有时间流页的一枚小徽标在表达，
                        // 账本 / 造物 / 展台页完全没有任何提示，
                        // 用户在其它页面断网保存后根本不知道数据还没同步。
                        OfflineBanner(pendingCount = pendingEventCount)
                        MainContent(
                            modifier = Modifier.weight(1f),
                            isDarkTheme = isDarkTheme,
                            appearanceMode = appearanceMode,
                            onAppearanceModeChanged = onAppearanceModeChanged
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
        appearanceMode: AppearanceMode,
        onAppearanceModeChanged: (AppearanceMode) -> Unit
    ) {
        var isPaired by remember { mutableStateOf(credentialStore.isPaired()) }
        val currentRouteState = rememberSaveable {
            mutableStateOf(requestedRouteState.value ?: "stream")
        }
        val currentRoute by currentRouteState

        // MOB-P2-73：返回栈 —— 此前从「造物」按返回会直接跳到「时间流」，
        // 用户在多个分段之间来回切换后无法回到原处。
        // 这里记录「离开时的路由」，返回时逐级回退。
        // MOB-P1-xx：用 rememberSaveable —— 旋转 / 配置变更后 currentRouteState 会被恢复，
        // 但普通 remember 的返回栈会一并丢失 ⇒ 返回键行为突变（直接回时间流）。
        // mutableStateListOf 需要 Saver 才能进 rememberSaveable，这里用数组中转。
        val routeStack = rememberSaveable(saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )) { mutableStateListOf<String>() }

        fun sectionOf(route: String): String = when (route) {
            // 造物 / 心愿 / 足迹同属一个分段，内部切换不单独入栈
            "footprints", "projects", "wishes" -> "projects"
            else -> route
        }

        // MOB-P0-09：稳定的路由切换回调（键为状态对象本身，引用恒定）
        val onRouteSelected: (String) -> Unit = remember(currentRouteState) {
            { route: String ->
                if (route != currentRouteState.value) {
                    val fromSection = sectionOf(currentRouteState.value)
                    val toSection = sectionOf(route)
                    if (fromSection != toSection) {
                        // MOB-P1-xx：不压入**重复**的段 ——
                        // 原先栈只增不减且允许重复：时间流→账本→时间流→账本 会压成
                        // ["stream","ledger","stream"]，此后返回键就在两个分段之间来回横跳，
                        // 必须按多次才能退出到首页。
                        if (routeStack.isEmpty() || routeStack.last() != fromSection) {
                            routeStack.add(fromSection)
                        }
                    }
                    currentRouteState.value = route
                }
            }
        }
        // MOB-P1-72：二级 Tab「心愿」此前被映射成 "projects"，
        // 于是用户在看心愿单时底栏却高亮「造物」—— 导航在说谎。
        // 现在心愿拥有独立路由（底栏不高亮任何一项，由二级分段控件表达选中）。
        val onSubTabChanged: (Int) -> Unit = remember(currentRouteState) {
            { tab: Int ->
                val target = when (tab) {
                    2 -> "footprints"
                    1 -> "wishes"
                    else -> "projects"
                }
                if (target != currentRouteState.value) {
                    if (sectionOf(target) != sectionOf(currentRouteState.value)) {
                        val fromSection = sectionOf(currentRouteState.value)
                        if (routeStack.isEmpty() || routeStack.last() != fromSection) {
                            routeStack.add(fromSection)
                        }
                    }
                    currentRouteState.value = target
                }
            }
        }

        val pendingShortcut by requestedRouteState
        LaunchedEffect(pendingShortcut) {
            val target = pendingShortcut
            if (!target.isNullOrBlank()) {
                currentRouteState.value = if (target in KNOWN_ROUTES) target else "stream"
                requestedRouteState.value = null
            }
        }

        // 生物识别保护触发
        val canAuth = remember { BiometricHelper.canAuthenticate(this@MainActivity) }
        var isUnlocked by remember {
            mutableStateOf(
                sessionUnlocked || !credentialStore.isBiometricsEnabled || !canAuth
            )
        }

        val biometricUnlockTitle = stringResource(R.string.main_activity_title_01)

        fun requestBiometricUnlock() {
            if (credentialStore.isBiometricsEnabled && BiometricHelper.canAuthenticate(this@MainActivity)) {
                BiometricHelper.authenticate(
                    activity = this@MainActivity,
                    title = biometricUnlockTitle,
                    subtitle = "验证面容或指纹以解锁散页随行手账",
                    onSuccess = {
                        sessionUnlocked = true
                        isUnlocked = true
                    },
                    onError = { /* 保持锁屏门禁态 */ }
                )
            } else {
                isUnlocked = true
            }
        }

        // 冷启动 / 切回前台都要重新验证（docs/mobile.md §3.4）。
        // 重锁的「切后台」判定在 onCreate 里由 ProcessLifecycleOwner 负责。
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (isPaired && !isUnlocked) {
                requestBiometricUnlock()
            }
        }

        // 401 全局引导（AOPT-09b）：设备在 Web/Mac 端被吊销时主动提示重新配对，
        // 而不是让用户对着「同步失败」干瞪眼。Room 中的 outbox 不清空，重配对后自动续传。
        var showRepairDialog by remember { mutableStateOf(false) }

        // MOB-P1-63：上次是非正常退出（崩溃）时，给出一次友好提示并保留诊断信息入口
        var showCrashNotice by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            showCrashNotice = CrashReporter.lastCrashReport(this@MainActivity) != null
        }
        val apiClient = remember { com.looseleaf.app.data.api.APIClient.getInstance(this@MainActivity) }
        LaunchedEffect(Unit) {
            apiClient.unauthorizedEvents.collect {
                if (isPaired) showRepairDialog = true
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
            com.looseleaf.app.ui.components.ArchiveConfirmDialog(
                title = stringResource(R.string.main_activity_title_03),
                content = "这台设备已被移出受信任设备组（可能在网页端或 Mac 端被撤销）。" +
                    "需要重新扫码配对；本机尚未同步的记录会保留，配对成功后自动续传。",
                confirmText = stringResource(R.string.main_activity_confirmText_02),
                dismissText = stringResource(R.string.main_activity_dismissText_01),
                onConfirm = {
                    showRepairDialog = false
                    isPaired = false
                },
                onDismiss = { showRepairDialog = false }
            )
        }

        if (!isPaired) {
            val deepLinkPayload by pendingDeepLinkPayloadState
            PairingScreen(
                initialPayload = deepLinkPayload,
                onPairingSuccess = {
                    sessionUnlocked = true
                    isPaired = true
                    isUnlocked = true
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
            val colors = LooseLeafTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // edge-to-edge 下必须避让状态栏与手势条，否则「隐私守护中」文案
                    // 会顶到状态栏下方、解锁按钮贴住手势横条
                    .systemBarsPadding()
                    .background(colors.archivePaper)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = stringResource(R.string.main_activity_contentDescription_01),
                        tint = colors.accentWarm,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.main_activity_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 20.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.main_activity_text_02),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { requestBiometricUnlock() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("验证指纹 / 面容解锁", fontFamily = ArchiveFontFamilies.EditorialSerif)
                    }
                }
            }
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
                    // 手势完成 —— MOB-P2-73：优先逐级回退到上一个分段；栈空后才回时间流 / 退出
                    when {
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
                    // 手势被取消（滑回原处）—— 什么都不做
                    throw e
                }
            }

            val saveableStateHolder = rememberSaveableStateHolder()

            Box(modifier = modifier.fillMaxSize()) {
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
                        // 「足迹」「造物」共用 ProjectsWishesScreen：两者共享同一个
                        // SaveableState 桶，否则内部 tab 回传导致 route 变化时
                        // 页面会被重建、内部 tab 被打回 initialTab
                        // 造物 / 心愿 / 足迹共用同一个 SaveableState 桶，
                        // 否则内部 Tab 回传导致 route 变化时页面会被重建、Tab 被打回初始值
                        val stateKey = if (currentRoute in setOf("footprints", "projects", "wishes")) {
                            "projects"
                        } else {
                            currentRoute
                        }
                        // 内部 tab（0 造物 / 1 心愿 / 2 山川）回传，同步底栏高亮
                        // 内部 tab（0 造物 / 1 心愿 / 2 山川）回传，同步底栏高亮（回调已提升为稳定引用）
                        saveableStateHolder.SaveableStateProvider(stateKey) {
                            when (currentRoute) {
                                "stream" -> StreamScreen()
                                "ledger" -> LedgerScreen()
                                "footprints" -> ProjectsWishesScreen(
                                    initialTab = 2,
                                    onTabChanged = onSubTabChanged
                                )
                                "projects" -> ProjectsWishesScreen(
                                    initialTab = 0,
                                    onTabChanged = onSubTabChanged
                                )
                                "wishes" -> ProjectsWishesScreen(
                                    initialTab = 1,
                                    onTabChanged = onSubTabChanged
                                )
                                "profile" -> ProfileScreen(
                                    onUnpair = {
                                        isPaired = false
                                    },
                                    onAppearanceModeChanged = onAppearanceModeChanged,
                                    highContrast = highContrastState.value,
                                    onHighContrastChanged = { enabled ->
                                        credentialStore.highContrast = enabled
                                        highContrastState.value = enabled
                                    }
                                )
                                // MOB-P0-11：未知路由兜底 —— 任何脏数据 / 过期快捷方式
                                // 都不再渲染成空白页，而是回到时间流并给出提示。
                                else -> {
                                    LaunchedEffect(currentRoute) {
                                        ArchiveToastManager.show("该入口已下线，已返回时间流", isError = true)
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

@Immutable
data class NavigationTabItem(
    val route: String,
    val label: String,
    // MOB-P2-54：图标改用项目内本地矢量（Painter），不再依赖 material-icons-extended
    val icon: Painter
)

/**
 * 底栏页签列表。
 *
 * MOB-P1-28：原先每次重组都新建 List，导致底栏永远无法跳过重组 —— 提升为顶层常量。
 * MOB-P2-54：图标改成本地矢量后，`painterResource` 只能在组合上下文调用，
 * 因此列表改为 **remember 缓存**（painter 本身是稳定的，键不会变），
 * 既保住了「只创建一次」的初衷，又不必把 extended 图标依赖留在包里。
 */
@Composable
private fun rememberBottomTabs(): List<NavigationTabItem> {
    val streamIcon = painterResource(R.drawable.ic_auto_stories)
    val ledgerIcon = painterResource(R.drawable.ic_calculate)
    val footprintsIcon = painterResource(R.drawable.ic_explore)
    val projectsIcon = painterResource(R.drawable.ic_all_inclusive)
    val profileIcon = painterResource(R.drawable.ic_account_circle)
    return remember(streamIcon, ledgerIcon, footprintsIcon, projectsIcon, profileIcon) {
        listOf(
            NavigationTabItem("stream", "时间流", streamIcon),
            NavigationTabItem("ledger", "账本", ledgerIcon),
            NavigationTabItem("footprints", "足迹", footprintsIcon),
            NavigationTabItem("projects", "造物", projectsIcon),
            NavigationTabItem("profile", "展台", profileIcon)
        )
    }
}

/**
 * MOB-P1-85：全局离线横幅。
 * 断网时明确告知「当前离线 · N 项待同步」，让离线优先不再是「静默失败」。
 */
@Composable
private fun OfflineBanner(
    pendingCount: Int,
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

    if (!isOnline) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .background(colors.accentWarm)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_off),
                contentDescription = stringResource(R.string.main_activity_contentDescription_02),
                tint = colors.archivePaper,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (pendingCount > 0) {
                    "当前离线 · $pendingCount 项待同步（联网后自动上传）"
                } else {
                    "当前离线 · 记录会先存到本机"
                },
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 12.sp,
                color = colors.archivePaper
            )
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
    val colors = LooseLeafTheme.colors
    val tabs = rememberBottomTabs()

    Surface(
        color = colors.archivePaper,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.borderDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        // MOB-P2-40：宽屏（平板 / 折叠屏展开态）时底栏 5 项不再被拉得过散 ——
        // 页签行限宽 680dp 并居中，与正文栏的排版宽度保持一致。
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in tabs) {
                val isSelected = tab.route == currentRoute
                val contentColor = if (isSelected) colors.accentWarm else colors.inkMuted

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onRouteSelected(tab.route) }
                        )
                        // MOB-P1-55：底栏是全 App 使用频率最高的控件，
                        // 原先整块可点区域只有 ~38dp 高，低于 48dp 系统基线。
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painter = tab.icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
        }
    }
}
