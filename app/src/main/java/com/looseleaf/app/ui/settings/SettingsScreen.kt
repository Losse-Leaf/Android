package com.looseleaf.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.looseleaf.app.BuildConfig
import com.looseleaf.app.R
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.ui.profile.ProfileInfoRow
import com.looseleaf.app.ui.profile.TrustedDeviceRow
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.CityDictionary


/**
 * 设置中心 (SettingsScreen)
 *
 * 从展台里独立出来（RG-32）：展台负责「看」（档案 / 统计 / 热力图 / 装备），
 * 设置负责「改」（外观 / 安全 / 通知 / 设备 / 更新 / 解绑）。
 * 原先两者挤在一页，同一件事有三个入口：⋮ 菜单、设置抽屉、页面内联。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    isBiometricsEnabled: Boolean,
    onBiometricsChanged: (Boolean) -> Unit,
    enableDecorations: Boolean = true,
    onEnableDecorationsChanged: ((Boolean) -> Unit)? = null,
    isInstallmentReminderEnabled: Boolean = true,
    onInstallmentReminderChanged: ((Boolean) -> Unit)? = null,
    localDeviceName: String,
    localDeviceId: String,
    onRenameLocalDevice: () -> Unit,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LooseLeafTheme.colors

    // 全屏设置页是展台内部的覆盖层，不在路由栈里，必须自己拦返回键，
    // 否则按返回会直接跳到时间流而不是回展台。
    BackHandler(onBack = onBack)

    // EV-08：通知权限 launcher 提到无条件作用域，避免随 onInstallmentReminderChanged
    // 空/非空变化反复注册注销；永久拒绝时给 Toast 并展示「去设置」入口。
    val context = LocalContext.current
    var notifPermanentlyDenied by remember { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            notifPermanentlyDenied = false
            onInstallmentReminderChanged?.invoke(true)
        } else {
            val activity = context as? android.app.Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, android.Manifest.permission.POST_NOTIFICATIONS
                )
            if (permanentlyDenied) {
                notifPermanentlyDenied = true
                ArchiveToastManager.show("通知权限已被永久拒绝，请到系统设置开启", isError = true)
            }
            onInstallmentReminderChanged?.invoke(false)
        }
    }

    Scaffold(
        // 本页挂在外层 Scaffold 的 innerPadding 之内，外层已经让过状态栏与导航栏。
        // 内层 Scaffold 若不把 contentWindowInsets 清零，内容区会再吃一条导航栏高度。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                // 本页挂在外层 Scaffold 的 innerPadding 之内（MainActivity 已让过状态栏），
                // 这里的 padding 不会被"消费"窗口 inset，再加 statusBarsPadding() 等于
                // 让两次位 —— 顶部会多出一条状态栏高的空白。故此处不再加。
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.inkPrimary
                    )
                }
                Text(
                    text = "设置",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            }
        },
        containerColor = colors.archivePaper
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 一、偏好与安全
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "偏好与安全",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "外观主题",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        AppearanceMode.SYSTEM to "跟随系统",
                        AppearanceMode.PAPER to "暖色棉纸",
                        AppearanceMode.DARKROOM to "暗房"
                    )
                    options.forEach { (mode, label) ->
                        ArchiveSelectableCapsule(
                            text = label,
                            selected = appearanceMode == mode,
                            onClick = { onAppearanceModeChanged(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                ArchiveDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "生物识别门禁",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "应用切入后台或冷启动时需验证面容/指纹",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }
                    Switch(
                        checked = isBiometricsEnabled,
                        onCheckedChange = onBiometricsChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accentWarm,
                            checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f)
                        )
                    )
                }

                if (onEnableDecorationsChanged != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ArchiveDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "手账质感装饰",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "开启微旋转、倾角、和纸胶带、按压下沉与纸张错位",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                        Switch(
                            checked = enableDecorations,
                            onCheckedChange = onEnableDecorationsChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentWarm,
                                checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                if (onInstallmentReminderChanged != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ArchiveDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "分期还款到期提醒",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "到期前一天与当天提醒还款计划，不漏交手续费",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                        Switch(
                            checked = isInstallmentReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onInstallmentReminderChanged(true)
                                    }
                                } else {
                                    onInstallmentReminderChanged(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentWarm,
                                checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (notifPermanentlyDenied) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ArchiveTextButton(
                            text = "去设置",
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        )
                    }
                }
            }

            ArchiveDivider()

            // 二、受信任设备组
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "本机设备",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 本机设备
                TrustedDeviceRow(
                    deviceName = localDeviceName.ifBlank { "Android 随行设备" },
                    deviceId = localDeviceId.ifBlank { "未初始化" },
                    platform = "android",
                    isLocal = true,
                    lastActiveAt = null,
                    onRename = onRenameLocalDevice,
                    onRevokeOrUnpair = onUnpair
                )
            }

            ArchiveDivider()

            // 三、版本信息与检测更新
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "关于散页随行手账",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProfileInfoRow(label = "当前版本", value = "v${UpdateChecker.currentVersionName}")
                ProfileInfoRow(label = "发布渠道", value = "GitHub Releases")

                Spacer(modifier = Modifier.height(8.dp))
                ArchiveDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isCheckingUpdate) "正在检索更新..." else "检测云端最新发布",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )

                    ArchiveTextButton(
                        text = if (isCheckingUpdate) "检测中…" else "检测新版本",
                        onClick = onCheckUpdate,
                        enabled = !isCheckingUpdate
                    )
                }
            }

            ArchiveDivider()

            // 五、危险区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.archiveCard)
                    .border(1.dp, colors.alertRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onUnpair)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_link_off),
                        contentDescription = null,
                        tint = colors.alertRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "解除本机配对并清空本地缓存",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.alertRed
                    )
                }
            }
        }
    }
}
