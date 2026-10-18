package com.looseleaf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.looseleaf.app.ui.components.ArchiveToastHost
import com.looseleaf.app.ui.components.ArchiveToastManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.security.BiometricHelper
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.darkroom.DarkroomScreen
import com.looseleaf.app.ui.ledger.LedgerScreen
import com.looseleaf.app.ui.pairing.PairingScreen
import com.looseleaf.app.ui.profile.ProfileScreen
import com.looseleaf.app.ui.projectswishes.ProjectsWishesScreen
import com.looseleaf.app.ui.stream.StreamScreen

class MainActivity : FragmentActivity() {

    private lateinit var credentialStore: CredentialStore
    private lateinit var haptics: HapticsEngine
    private var requestedRouteState = mutableStateOf<String?>(null)
    private var pendingDeepLinkPayloadState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        credentialStore = CredentialStore.getInstance(this)
        haptics = HapticsEngine.getInstance(this)

        requestedRouteState.value = intent?.getStringExtra("shortcut_route")
        intent?.data?.toString()?.takeIf { it.startsWith("looseleaf://") }?.let {
            pendingDeepLinkPayloadState.value = it
        }

        setContent {
            var isDarkTheme by remember { mutableStateOf(credentialStore.isDarkroomMode) }

            LooseLeafTheme(darkTheme = isDarkTheme) {
                var isPaired by remember { mutableStateOf(credentialStore.isPaired()) }
                var currentRoute by remember {
                    mutableStateOf(requestedRouteState.value ?: "stream")
                }

                val pendingShortcut by requestedRouteState
                LaunchedEffect(pendingShortcut) {
                    val target = pendingShortcut
                    if (!target.isNullOrBlank()) {
                        currentRoute = target
                        requestedRouteState.value = null
                    }
                }

                // 生物识别保护触发
                val canAuth = remember { BiometricHelper.canAuthenticate(this@MainActivity) }
                var isUnlocked by remember {
                    mutableStateOf(!credentialStore.isBiometricsEnabled || !canAuth)
                }

                fun requestBiometricUnlock() {
                    if (credentialStore.isBiometricsEnabled && BiometricHelper.canAuthenticate(this@MainActivity)) {
                        BiometricHelper.authenticate(
                            activity = this@MainActivity,
                            title = "散页身份验证",
                            subtitle = "验证面容或指纹以解锁散页随行手账",
                            onSuccess = { isUnlocked = true },
                            onError = { /* 保持锁屏门禁态 */ }
                        )
                    } else {
                        isUnlocked = true
                    }
                }

                LaunchedEffect(isPaired) {
                    if (isPaired && !isUnlocked) {
                        requestBiometricUnlock()
                    }
                }

                if (!isPaired) {
                    val deepLinkPayload by pendingDeepLinkPayloadState
                    PairingScreen(
                        initialPayload = deepLinkPayload,
                        onPairingSuccess = {
                            isPaired = true
                            isUnlocked = true
                            pendingDeepLinkPayloadState.value = null
                            currentRoute = "stream"
                        }
                    )
                } else if (!isUnlocked) {
                    val colors = LooseLeafTheme.colors
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.archivePaper)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "已加锁",
                                tint = colors.accentWarm,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "散页 · 隐私守护中",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 20.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请通过生物识别验证以翻阅私人档案与账本",
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

                    BackHandler {
                        if (currentRoute != "stream") {
                            haptics.tapKey()
                            AudioEngine.playPaperFlip()
                            currentRoute = "stream"
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPressTime < 2000L) {
                                finish()
                            } else {
                                lastBackPressTime = now
                                ArchiveToastManager.show("再按一次退出散页")
                            }
                        }
                    }

                    val saveableStateHolder = rememberSaveableStateHolder()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            bottomBar = {
                                LooseLeafBottomBar(
                                    currentRoute = currentRoute,
                                    onRouteSelected = { route ->
                                        haptics.tapKey()
                                        AudioEngine.playPaperFlip()
                                        currentRoute = route
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                saveableStateHolder.SaveableStateProvider(currentRoute) {
                                    when (currentRoute) {
                                        "stream" -> StreamScreen(
                                            onNavigateToDarkroom = {
                                                currentRoute = "darkroom"
                                            }
                                        )
                                        "ledger" -> LedgerScreen()
                                        "footprints" -> ProjectsWishesScreen(initialTab = 2)
                                        "projects" -> ProjectsWishesScreen(initialTab = 0)
                                        "profile" -> ProfileScreen(
                                            onUnpair = {
                                                isPaired = false
                                            },
                                            onDarkroomModeChanged = { enabled ->
                                                isDarkTheme = enabled
                                            }
                                        )
                                        "darkroom" -> DarkroomScreen(
                                            onEntrySaved = {
                                                currentRoute = "stream"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 顶层全局自研纸墨 Toast 通知覆盖层
                        ArchiveToastHost()
                    }
                }
            }
        }
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

data class NavigationTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun LooseLeafBottomBar(
    currentRoute: String,
    onRouteSelected: (String) -> Unit
) {
    val colors = LooseLeafTheme.colors
    val tabs = listOf(
        NavigationTabItem("stream", "时间流", Icons.Outlined.AutoStories),
        NavigationTabItem("ledger", "账本", Icons.Outlined.Calculate),
        NavigationTabItem("footprints", "足迹", Icons.Outlined.Explore),
        NavigationTabItem("projects", "造物", Icons.Outlined.AllInclusive),
        NavigationTabItem("profile", "展台", Icons.Outlined.AccountCircle)
    )

    Surface(
        color = colors.archivePaper,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.borderDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                        .clickable { onRouteSelected(tab.route) }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
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
