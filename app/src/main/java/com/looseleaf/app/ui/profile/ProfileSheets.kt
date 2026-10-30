package com.looseleaf.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.BuildConfig
import com.looseleaf.app.R
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.PairTicketResponse
import com.looseleaf.app.model.TrustedDevice
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.updater.UpdateChecker
import com.looseleaf.app.utils.CityDictionary
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileSheet(
    profile: CuratorProfile,
    onDismiss: () -> Unit,
    onSave: (UpdateProfileRequest) -> Unit
) {
    val colors = LooseLeafTheme.colors
    // MOB 表单审计 #19：remember 的 key 用稳定 id 而不是 profile 对象——
    // 同步刷新会用同一条档案的新实例替换旧实例（内容相同、引用不同），
    // 以对象为 key 会让正在输入的字段被整体重置回服务器旧值
    //（与 StreamDialogs.kt 已修复的「打字打到一半被清空」同类问题）。
    val profileKey = profile.id ?: 0L
    var name by remember(profileKey) { mutableStateOf(profile.name) }
    var title by remember(profileKey) { mutableStateOf(profile.title ?: "") }
    var cityCode by remember(profileKey) {
        val initialLoc = profile.currentCity.takeIf { !it.isNullOrBlank() } ?: profile.location ?: ""
        mutableStateOf(CityDictionary.codeForLocation(initialLoc) ?: "")
    }
    var cityName by remember(profileKey) {
        mutableStateOf(profile.currentCity.takeIf { !it.isNullOrBlank() } ?: profile.location ?: "")
    }
    var motto by remember(profileKey) { mutableStateOf(profile.motto ?: "") }
    var manifesto by remember(profileKey) { mutableStateOf(profile.manifesto ?: "") }

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "编辑资料",
        cancelText = "取消",
        actionText = "保存",
        isActionEnabled = name.isNotBlank(),
        onAction = {
            val finalCity = cityName.trim()
            onSave(
                UpdateProfileRequest(
                    name = name.trim(),
                    title = title.trim(),
                    currentCity = finalCity,
                    location = finalCity,
                    motto = motto.trim(),
                    manifesto = manifesto.trim()
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // 1. 称谓输入
            ArchiveField(
                value = name,
                onValueChange = { name = it },
                label = "称谓",
                placeholder = "输入主理人称谓",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 头衔与身份
            ArchiveField(
                value = title,
                onValueChange = { title = it },
                label = "头衔与身份",
                placeholder = "如：生活记录者 · 独立思想家",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 常居城市
            ArchiveCityPickerField(
                selectedCityCode = cityCode,
                selectedCityName = cityName,
                onCitySelected = { c, n ->
                    cityCode = c
                    cityName = n
                },
                label = "常居城市",
                placeholder = "选择常居地级市"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 生活信条与格言
            ArchiveField(
                value = motto,
                onValueChange = { motto = it },
                label = "生活信条与格言",
                placeholder = "在散落的日常里，拾取有光芒的瞬间",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 自白手记正文
            ArchiveField(
                value = manifesto,
                onValueChange = { manifesto = it },
                label = "自白手记正文",
                placeholder = "写下一段主理人自白...",
                singleLine = false,
                minLines = 4,
                maxLines = 8
            )
        }
    }
}

/**
 * 其他受信任设备组二级管理底抽
 * 解决多端配对设备在个人展台主页平铺导致的版面过长问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OtherDevicesSheet(
    otherDevices: List<TrustedDevice>,
    onDismiss: () -> Unit,
    onRename: (TrustedDevice) -> Unit,
    onRevoke: (TrustedDevice) -> Unit
) {
    val colors = LooseLeafTheme.colors

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "受信任跨端设备 (${otherDevices.size})",
        actionText = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "这些跨端设备已通过散页端到端中枢建立受信任配对，共享同步手稿、账本与山川印记。",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 13.sp,
                color = colors.inkSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (otherDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无其他已配对设备",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkMuted
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    otherDevices.forEach { dev ->
                        TrustedDeviceRow(
                            deviceName = dev.name.ifBlank { dev.deviceId },
                            deviceId = dev.deviceId,
                            platform = dev.platform,
                            isLocal = false,
                            lastActiveAt = dev.lastActiveAt,
                            onRename = { onRename(dev) },
                            onRevokeOrUnpair = { onRevoke(dev) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 偏好设置与系统运维管理抽屉 (SettingsSheet)
 * 将原本平铺在展台主页的设备管理、存储灾备、外观偏好、生物识别、版本更新与解绑危险区
 * 统一收敛至标准化的独立设置中心。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    isBiometricsEnabled: Boolean,
    onBiometricsChanged: (Boolean) -> Unit,
    showBinderHoles: Boolean = true,
    onShowBinderHolesChanged: ((Boolean) -> Unit)? = null,
    enableDecorations: Boolean = true,
    onEnableDecorationsChanged: ((Boolean) -> Unit)? = null,
    isInstallmentReminderEnabled: Boolean = true,
    onInstallmentReminderChanged: ((Boolean) -> Unit)? = null,
    localDeviceName: String,
    localDeviceId: String,
    onRenameLocalDevice: () -> Unit,
    otherDevices: List<TrustedDevice>,
    onOpenOtherDevices: () -> Unit,
    isIssuingPairTicket: Boolean,
    onIssuePairTicket: () -> Unit,
    issuedPairTicket: PairTicketResponse?,
    onClosePairTicket: () -> Unit,
    onCopyPairTicket: (String) -> Unit,
    onOpenStorageBackup: () -> Unit,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onUnpair: () -> Unit,
    onSeedDemoData: (() -> Unit)? = null,
    onClearDemoData: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "偏好与设置",
        actionText = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
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
                        val selected = appearanceMode == mode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveCard)
                                .border(
                                    1.dp,
                                    if (selected) colors.accentWarm else colors.borderSubtle,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onAppearanceModeChanged(mode) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (selected) colors.accentWarm else colors.inkPrimary
                            )
                        }
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
                            fontSize = 13.5.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "应用切入后台或冷启动时需验证面容/指纹",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.5.sp,
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

                if (onShowBinderHolesChanged != null) {
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
                                text = "活页打孔装订线",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.5.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "在时间流左边缘呈现真实活页冲孔装订纹理",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 11.5.sp,
                                color = colors.inkSecondary
                            )
                        }
                        Switch(
                            checked = showBinderHoles,
                            onCheckedChange = onShowBinderHolesChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentWarm,
                                checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f)
                            )
                        )
                    }
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
                                fontSize = 13.5.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "开启微旋转、倾角、和纸胶带、按压下沉与纸张错位",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 11.5.sp,
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

                    val context = LocalContext.current
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        onInstallmentReminderChanged(isGranted)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "分期还款到期提醒",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.5.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "到期前一天与当天提醒还款计划，不漏交手续费",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 11.5.sp,
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
                }
            }

            ArchiveDivider()

            // 二、受信任设备组
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "受信任设备组 (${otherDevices.size + 1})",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    ArchiveTextButton(
                        text = if (isIssuingPairTicket) "签发中…" else "+ 签发配对工单",
                        onClick = onIssuePairTicket,
                        enabled = !isIssuingPairTicket
                    )
                }

                issuedPairTicket?.let { ticket ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archiveCard)
                            .border(1.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "新设备 5 分钟配对凭据",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkPrimary
                                )
                                TextButton(
                                    onClick = onClosePairTicket,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("收起", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 11.5.sp, color = colors.inkMuted)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ticket.qrPayload,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 10.sp,
                                color = colors.inkSecondary,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.accentWarm)
                                    .clickable { onCopyPairTicket(ticket.qrPayload) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("复制配对链接", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 12.sp, color = colors.archivePaper)
                            }
                        }
                    }
                }

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

                if (otherDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archiveCard)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                            .clickable { onOpenOtherDevices() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_devices),
                                contentDescription = null,
                                tint = colors.accentWarm,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "其他受信任设备 (${otherDevices.size} 台)",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.5.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "Web 伴侣、Mac 桌面端等 · 点击查看与管理",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            ArchiveDivider()

            // 三、存储配置与快照容灾
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.archiveCard)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                    .clickable { onOpenStorageBackup() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_storage),
                        contentDescription = null,
                        tint = colors.accentWarm,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "存储配置与快照灾备",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SQLite WAL 快照全量灾备与对象存储热配置",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = colors.inkSecondary
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            ArchiveDivider()

            // 四、版本信息与检测更新
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
                        fontSize = 12.5.sp,
                        color = colors.inkSecondary
                    )

                    ArchiveTextButton(
                        text = if (isCheckingUpdate) "检测中…" else "检测新版本",
                        onClick = onCheckUpdate,
                        enabled = !isCheckingUpdate
                    )
                }
            }

            // 开发者选项 (Debug 专用)
            if (BuildConfig.DEBUG && (onSeedDemoData != null || onClearDemoData != null)) {
                ArchiveDivider()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "开发者选项 (Debug 专用)",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "灌入覆盖多天、跨月、跨年、多比例图片的手账演示数据，或一键清除",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 11.5.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onSeedDemoData != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.accentWarm)
                                    .clickable(onClick = onSeedDemoData)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "灌入演示数据",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.archivePaper
                                )
                            }
                        }
                        if (onClearDemoData != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveCard)
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                                    .clickable(onClick = onClearDemoData)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "清除演示数据",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
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
                        fontSize = 13.5.sp,
                        color = colors.alertRed
                    )
                }
            }
        }
    }
}
