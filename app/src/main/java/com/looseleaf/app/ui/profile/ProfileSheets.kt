package com.looseleaf.app.ui.profile

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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.profile_sheets_title_01),
        actionButtonText = "保存档案",
        isActionEnabled = name.isNotBlank(),
        onActionClick = {
            // 契约 D：可清空文本字段「出现且空串 = 清空；缺省/null = 保持」。
            // 原先 takeIf { isNotBlank } 会把用户删空的字段整个省略，
            // 服务端按「保持原值」处理——「删空格言/头衔」从未真正生效。
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
        // 1. 称谓输入
        Text(stringResource(R.string.profile_sheets_title), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_sheets_text_01),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 18.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 2. 头衔与身份
        Text(stringResource(R.string.profile_sheets_identity), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 15.sp,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (title.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_sheets_text_02),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 3. 常居城市（严格使用统一行政区划选择器，定位到地级市！）
        Text("常居城市 (点选定位至地级市)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(6.dp))
        PaperCityPickerField(
            selectedCityCode = cityCode,
            selectedCityName = cityName,
            onCitySelected = { c, n ->
                cityCode = c
                cityName = n
            },
            placeholder = stringResource(R.string.profile_sheets_placeholder_01)
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 4. 生活信条与格言
        Text(stringResource(R.string.profile_sheets_motto), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = motto,
            onValueChange = { motto = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 14.5.sp,
                color = colors.accentWarm
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (motto.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_sheets_text_03),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 14.5.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 5. 自白手记正文
        Text(stringResource(R.string.profile_sheets_confession), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = manifesto,
            onValueChange = { manifesto = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 240.dp),
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (manifesto.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_sheets_text_04),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = colors.inkMuted.copy(alpha = 0.45f)
                    )
                }
                innerTextField()
            }
        )
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

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "受信任跨端设备 (${otherDevices.size})",
        actionButtonText = null
    ) {
        Text(
            text = "这些跨端设备已通过散页端到端中枢建立受信任配对，共享同步手稿、账本与山川印记。",
            fontFamily = ArchiveFontFamilies.BodySans,
            fontSize = 12.sp,
            color = colors.inkSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (otherDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无其他已配对设备",
                    fontFamily = ArchiveFontFamilies.BodySans,
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

        Spacer(modifier = Modifier.height(24.dp))
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
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "偏好设置与系统运维",
        actionButtonText = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 一、移动端个性化与安全
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "偏好与安全",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "外观主题",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 12.5.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.accentWarm else colors.archiveSubtle)
                                    .border(
                                        0.5.dp,
                                        if (selected) colors.accentWarm else colors.borderDivider,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onAppearanceModeChanged(mode) }
                                    .padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 12.sp,
                                    color = if (selected) colors.archivePaper else colors.inkSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "生物识别门禁",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = "应用切入后台或冷启动时需验证面容/指纹",
                                fontSize = 11.sp,
                                color = colors.inkMuted
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
                }
            }

            // 二、受信任设备组
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "受信任设备组 (${otherDevices.size + 1})",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                        TextButton(
                            onClick = onIssuePairTicket,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            enabled = !isIssuingPairTicket
                        ) {
                            Text(
                                text = if (isIssuingPairTicket) "签发中…" else "+ 签发配对工单",
                                fontSize = 11.5.sp,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                color = colors.accentWarm
                            )
                        }
                    }

                    issuedPairTicket?.let { ticket ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.archiveSubtle, RoundedCornerShape(4.dp))
                                .border(0.5.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
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
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkPrimary
                                    )
                                    TextButton(
                                        onClick = onClosePairTicket,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("收起", fontSize = 11.sp, color = colors.inkMuted)
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
                                Button(
                                    onClick = { onCopyPairTicket(ticket.qrPayload) },
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.inkPrimary,
                                        contentColor = colors.archivePaper
                                    ),
                                    shape = RoundedCornerShape(3.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("复制配对链接", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.archiveSubtle.copy(alpha = 0.5f))
                                .border(0.5.dp, colors.borderDivider.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
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
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "Web 伴侣、Mac 桌面端等 · 点击查看与管理",
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
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
            }

            // 三、存储配置与快照容灾
            PaperCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStorageBackup() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SQLite WAL 快照全量灾备与对象存储热配置",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.sp,
                                color = colors.inkMuted
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

            // 四、版本信息与检测更新
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "关于散页随行手账",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileInfoRow(label = "当前版本", value = "v${UpdateChecker.currentVersionName}")
                    ProfileInfoRow(label = "发布渠道", value = "GitHub Releases")

                    Spacer(modifier = Modifier.height(8.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isCheckingUpdate) "正在连接 GitHub 检索更新..." else "检测云端最新发布",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )

                        Button(
                            onClick = onCheckUpdate,
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentWarm,
                                contentColor = colors.archivePaper
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isCheckingUpdate) "检测中…" else "检测新版本",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 五、危险区域
            OutlinedButton(
                onClick = onUnpair,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentWarm),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = SolidColor(colors.accentWarm)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link_off),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "解除本机配对并清空本地缓存",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 13.sp
                )
            }
        }
    }
}
