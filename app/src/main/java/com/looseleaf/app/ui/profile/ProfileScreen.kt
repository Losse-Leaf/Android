package com.looseleaf.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.SiteConfig
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.ui.components.PaperDivider
import kotlinx.coroutines.launch

/**
 * 主理人展台与设备中枢设置 (ProfileScreen)
 * 严格遵循 docs/backend.md §一.5 全数据后端驱动与严禁前端写死原则 (Dynamic Data Sovereignty)
 */
@Composable
fun ProfileScreen(
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    var profile by remember { mutableStateOf(CuratorProfile()) }
    var siteConfig by remember { mutableStateOf(SiteConfig()) }
    var isDarkroomMode by remember { mutableStateOf(credentialStore.isDarkroomMode) }
    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var showUnpairDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            profile = apiClient.fetchCuratorProfile()
            siteConfig = apiClient.fetchSiteConfig()
        }
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶栏
            Text(
                text = "主理人展台与设备中枢",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                letterSpacing = 1.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "全数据后端动态驱动 · 单一事实来源",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.sp,
                color = colors.inkMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 主理人肖像与自白卡片
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val avatarUrl = apiClient.resolveMediaUrl(profile.avatarUrl)
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "主理人肖像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.borderDivider, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = profile.name.ifBlank { siteConfig.siteName },
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 19.sp,
                        color = colors.inkPrimary
                    )

                    if (profile.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.title,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }

                    if (profile.motto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "“ ${profile.motto} ”",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp,
                            color = colors.accentWarm
                        )
                    }

                    if (profile.currentCity.isNotBlank() || profile.location.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PinDrop, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = listOf(profile.currentCity, profile.location).filter { it.isNotBlank() }.joinToString(" · "),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    if (profile.manifesto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile.manifesto,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            // EDC 常用随行装备展台
            if (profile.edcGear.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "随行 EDC 装备 (${profile.edcGear.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        for (gear in profile.edcGear) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = gear.name,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    if (gear.spec.isNotBlank()) {
                                        Text(
                                            text = gear.spec,
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 11.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                                Text(
                                    text = gear.category,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 本机入网受信任凭据卡片
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "受信任设备凭据",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileInfoRow(label = "设备编号", value = credentialStore.deviceId ?: "未绑定")
                    ProfileInfoRow(label = "设备名称", value = credentialStore.deviceName)
                    ProfileInfoRow(label = "中枢 Hub", value = apiClient.baseUrl)
                    ProfileInfoRow(
                        label = "授权状态",
                        value = if (credentialStore.isPaired()) "已信任 (Active)" else "未配对"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 移动端个性化开关
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "随行偏好与隐私保护",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("胶片暗房模式 (Darkroom)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 14.sp, color = colors.inkPrimary)
                            Text("碳黑相纸底色与暗房安全灯赤红", fontSize = 11.sp, color = colors.inkMuted)
                        }
                        Switch(
                            checked = isDarkroomMode,
                            onCheckedChange = {
                                haptics.tapKey()
                                isDarkroomMode = it
                                credentialStore.isDarkroomMode = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentWarm, checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("生物识别保护 (Biometrics)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 14.sp, color = colors.inkPrimary)
                            Text("解锁账本与敏感收支柱状图", fontSize = 11.sp, color = colors.inkMuted)
                        }
                        Switch(
                            checked = isBiometricsEnabled,
                            onCheckedChange = {
                                haptics.tapKey()
                                isBiometricsEnabled = it
                                credentialStore.isBiometricsEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.accentWarm, checkedTrackColor = colors.accentWarm.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 吊销此设备按钮
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentWarm),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(colors.accentWarm)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("解除此设备绑定 (UNPAIR)", fontFamily = ArchiveFontFamilies.EditorialSerif)
            }

            if (showUnpairDialog) {
                AlertDialog(
                    onDismissRequest = { showUnpairDialog = false },
                    title = { Text("解除受信任设备？", fontFamily = ArchiveFontFamilies.EditorialSerif) },
                    text = { Text("解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。", fontFamily = ArchiveFontFamilies.BodySans) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics.tearDelete()
                                credentialStore.clearCredentials()
                                showUnpairDialog = false
                                onUnpair()
                            }
                        ) {
                            Text("确认解除", color = colors.accentWarm)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUnpairDialog = false }) {
                            Text("取消", color = colors.inkSecondary)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
        Text(text = value, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkSecondary)
    }
}
