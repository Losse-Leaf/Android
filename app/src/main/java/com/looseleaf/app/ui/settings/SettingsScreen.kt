package com.looseleaf.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveHeaderIconButton
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.updater.UpdateChecker

/**
 * 设置 (SettingsScreen)
 *
 * 一页安静的目录：
 * - 页头：圆形返回 + 衬线大标题「设置」
 * - 账号：Vesper Auth 登录身份与服务器地址
 * - 外观：三枚带纸样预览的选择卡（跟随系统 / 宣 / 墨）
 * - 偏好与安全：生物识别门禁、分期提醒两条开关
 * - 关于：版本、渠道、检测更新
 * - 末尾：胭脂描边的「退出登录」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    accountDisplayName: String,
    accountSubtitle: String,
    serverHost: String,
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    isBiometricsEnabled: Boolean,
    onBiometricsChanged: (Boolean) -> Unit,
    isInstallmentReminderEnabled: Boolean = true,
    onInstallmentReminderChanged: ((Boolean) -> Unit)? = null,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LooseLeafTheme.colors

    // 全屏设置页不在路由栈里，必须自己拦返回键
    BackHandler(onBack = onBack)

    // 通知权限 launcher 放在无条件作用域，避免随回调空 / 非空变化反复注册
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
        // 外层已统一消费状态栏 inset；这里清零，避免内容区再吃一条导航栏高度
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = ArchiveSpacing.gutter, top = 6.dp)
            ) {
                ArchiveHeaderIconButton(
                    onClick = onBack,
                    contentDescription = "返回",
                    imageVector = FolioGlyphs.ChevronLeft,
                    iconSize = 24.dp
                )
                Text(
                    text = "设置",
                    style = ArchiveTextStyles.pageTitle,
                    color = colors.inkPrimary,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp)
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // 一、账号
            SettingsGroup(title = "账号") {
                AccountIdentityRow(
                    displayName = accountDisplayName,
                    subtitle = accountSubtitle
                )
                SettingsRowDivider()
                ProfileInfoRow(label = "登录方式", value = "Vesper Auth")
                SettingsRowDivider()
                ProfileInfoRow(label = "同步服务器", value = serverHost)
            }

            // 二、外观
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "外观",
                    style = ArchiveTextStyles.overline,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppearanceOption(
                        label = "跟随系统",
                        selected = appearanceMode == AppearanceMode.SYSTEM,
                        onClick = { onAppearanceModeChanged(AppearanceMode.SYSTEM) },
                        split = true,
                        modifier = Modifier.weight(1f)
                    )
                    AppearanceOption(
                        label = "宣 · 浅色",
                        selected = appearanceMode == AppearanceMode.PAPER,
                        onClick = { onAppearanceModeChanged(AppearanceMode.PAPER) },
                        dark = false,
                        modifier = Modifier.weight(1f)
                    )
                    AppearanceOption(
                        label = "墨 · 深色",
                        selected = appearanceMode == AppearanceMode.DARKROOM,
                        onClick = { onAppearanceModeChanged(AppearanceMode.DARKROOM) },
                        dark = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 二、偏好与安全
            SettingsGroup(title = "偏好与安全") {
                SettingsSwitchRow(
                    title = "生物识别门禁",
                    subtitle = "应用切入后台或冷启动时需验证面容 / 指纹",
                    checked = isBiometricsEnabled,
                    onCheckedChange = onBiometricsChanged
                )
                if (onInstallmentReminderChanged != null) {
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = "分期还款到期提醒",
                        subtitle = "到期前一天与当天提醒，不漏交手续费",
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
                        }
                    )
                    if (notifPermanentlyDenied) {
                        SettingsRowDivider()
                        SettingsLinkRow(
                            title = "前往系统设置开启通知",
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

            // 四、关于
            SettingsGroup(title = "关于散页") {
                ProfileInfoRow(label = "当前版本", value = "v${UpdateChecker.currentVersionName}")
                SettingsRowDivider()
                ProfileInfoRow(label = "发布渠道", value = "GitHub Releases")
                SettingsRowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate, role = Role.Button, onClick = onCheckUpdate)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCheckingUpdate) "正在检索更新" else "检测新版本",
                        style = ArchiveTextStyles.body,
                        fontWeight = FontWeight.Medium,
                        color = if (isCheckingUpdate) colors.inkMuted else colors.accentSmallText,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentWarm,
                            trackColor = colors.borderSubtle
                        )
                    } else {
                        Icon(
                            imageVector = FolioGlyphs.ChevronRight,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 五、退出登录
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.full)
                    .border(ArchiveLines.hairline, colors.alertRed.copy(alpha = 0.45f), ArchiveShapes.full)
                    .clickable(role = Role.Button, onClick = onLogout)
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FolioGlyphs.Logout,
                    contentDescription = null,
                    tint = colors.alertRed,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "退出登录",
                    style = ArchiveTextStyles.label,
                    color = colors.alertRed
                )
            }

            Text(
                text = "散页 · Loose Leaf",
                style = ArchiveTextStyles.overline,
                color = colors.inkMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * 外观选择卡：上方一块小小的「纸样」预览（画布 + 纸页 + 两行字迹 + 朱砂点），下方标签。
 * split = true 时预览为左浅右深的对半纸样，表示跟随系统。
 */
@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    split: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    val border by animateColorAsState(
        targetValue = if (selected) colors.accentWarm else colors.borderCard,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "appearanceBorder"
    )
    Column(
        modifier = modifier
            .semantics { this.selected = selected }
            .clip(ArchiveShapes.lg)
            .background(colors.archiveCard)
            .border(if (selected) 1.5.dp else ArchiveLines.hairline, border, ArchiveShapes.lg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(ArchiveShapes.md)
        ) {
            if (split) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PaperSwatch(dark = false, modifier = Modifier.weight(1f))
                    PaperSwatch(dark = true, modifier = Modifier.weight(1f))
                }
            } else {
                PaperSwatch(dark = dark, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    imageVector = FolioGlyphs.Check,
                    contentDescription = null,
                    tint = colors.accentSmallText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = ArchiveTextStyles.meta,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.inkPrimary else colors.inkSecondary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PaperSwatch(dark: Boolean, modifier: Modifier = Modifier) {
    val palette = if (dark) DarkArchiveColors else LightArchiveColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.archivePaper)
            .padding(start = 10.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(ArchiveShapes.sm)
                .background(palette.archiveCard)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 40.dp)
                    .fillMaxWidth(0.7f)
                    .height(4.dp)
                    .clip(ArchiveShapes.full)
                    .background(palette.inkPrimary.copy(alpha = 0.7f))
            )
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 28.dp)
                    .fillMaxWidth(0.5f)
                    .height(4.dp)
                    .clip(ArchiveShapes.full)
                    .background(palette.inkMuted.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(ArchiveShapes.xs)
                    .background(palette.accentWarm)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = ArchiveTextStyles.rowTitle,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = ArchiveTextStyles.meta,
                color = colors.inkMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentWarm,
                checkedBorderColor = colors.accentWarm,
                uncheckedThumbColor = colors.inkMuted,
                uncheckedTrackColor = colors.archiveSubtle,
                uncheckedBorderColor = colors.borderDivider
            )
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, onClick: () -> Unit) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ArchiveTextStyles.body,
            fontWeight = FontWeight.Medium,
            color = colors.accentSmallText,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = FolioGlyphs.ChevronRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

