package com.looseleaf.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.looseleaf.app.theme.ArchiveTypeScale
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "这些跨端设备已通过散页端到端中枢建立受信任配对，共享同步手稿、账本与足迹印记。",
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