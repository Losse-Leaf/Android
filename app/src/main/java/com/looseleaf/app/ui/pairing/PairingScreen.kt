package com.looseleaf.app.ui.pairing

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import kotlinx.coroutines.launch

/**
 * 散页免密设备配对与入网界面 (PairingScreen)
 * 严格遵从两阶段入网模型：
 * 阶段一：扫码或粘贴链接自动解析中枢与工单握手连接；
 * 阶段二：连接成功后进入下一页设置设备名称并进入散页。
 */
@Composable
fun PairingScreen(
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    // 0: 连接配对阶段 (扫码/粘贴链接), 1: 配对成功设置设备名称阶段
    var currentStep by remember { mutableStateOf(0) }

    // 方式切换: 0 为扫码, 1 为粘贴配对链接 (严格只保留二维码+链接双通道)
    var selectedMethodTab by remember { mutableStateOf(0) }
    var pastedLinkInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    // 握手成功后的设备信息
    var pairedDeviceId by remember { mutableStateOf("") }
    var pairedHubUrl by remember { mutableStateOf("") }
    var customDeviceName by remember {
        mutableStateOf(Build.MODEL ?: "Android 随行设备")
    }

    fun executeHandshake(rawPayload: String) {
        if (isConnecting) return
        val parsed = PairingPayloadParser.parse(rawPayload)
        if (parsed == null) {
            Toast.makeText(context, "无法识别该配对链接，请确认是否包含中枢地址与工单号", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            isConnecting = true
            try {
                haptics.tapKey()
                apiClient.baseUrl = parsed.hubUrl

                val defaultModel = Build.MODEL ?: "Android"
                val chosenName = customDeviceName.trim().ifBlank { defaultModel }
                val resp = apiClient.confirmPairing(
                    ticket = parsed.ticket,
                    deviceName = chosenName,
                    deviceModel = defaultModel
                )

                // 暂存凭据
                credentialStore.deviceToken = resp.deviceToken
                credentialStore.deviceId = resp.deviceId
                credentialStore.deviceName = resp.deviceName.ifBlank { chosenName }
                pairedDeviceId = resp.deviceId
                pairedHubUrl = resp.hubUrl ?: parsed.hubUrl
                customDeviceName = resp.deviceName.ifBlank { chosenName }

                haptics.stampSave()
                AudioEngine.playPaperFlip()
                // 进入下一页面：设备展示名称定制页
                currentStep = 1
            } catch (e: Exception) {
                haptics.tearDelete()
                Toast.makeText(context, "连接数据中枢失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isConnecting = false
            }
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
            Spacer(modifier = Modifier.height(20.dp))

            if (currentStep == 0) {
                // ==========================================
                // 阶段一：扫码或粘贴链接连接中枢
                // ==========================================

                Text(
                    text = "散页 · 随行入网",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "扫描 Web 展厅管理台的配对二维码，或直接粘贴配对链接",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 两种连接方式切换 Tab (严格只保留扫码与配对链接)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.archiveSubtle)
                        .border(0.5.dp, colors.borderDivider, RoundedCornerShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("扫码连接", "粘贴链接")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedMethodTab == index
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) colors.archivePaper else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    haptics.tapKey()
                                    selectedMethodTab = index
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (index == 0) Icons.Outlined.QrCodeScanner else Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.accentWarm else colors.inkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedMethodTab == 0) {
                    // 方式 1：正方形 1:1 经典取景扫码器
                    SquareQrScannerView(
                        onQrCodeScanned = { scannedText ->
                            executeHandshake(scannedText)
                        }
                    )
                } else {
                    // 方式 2：粘贴配对链接
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "粘贴配对链接",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "在 Web 展厅管理台点击「复制配对链接」，粘贴至下方框内自动解析中枢与工单：",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = colors.inkSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            PaperTextEditor(
                                value = pastedLinkInput,
                                onValueChange = { pastedLinkInput = it },
                                placeholder = "例如 looseleaf://pair?hub=http://...&ticket=pair_tk_...",
                                minHeight = 72.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 从剪贴板粘贴按钮
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (clipText.isNotBlank()) {
                                        pastedLinkInput = clipText.trim()
                                        Toast.makeText(context, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("一键读取剪贴板链接", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TerracottaStampButton(
                                text = if (isConnecting) "正在解析并连接中枢..." else "确认连接中枢 (CONNECT)",
                                enabled = !isConnecting && pastedLinkInput.isNotBlank(),
                                onClick = {
                                    executeHandshake(pastedLinkInput)
                                }
                            )
                        }
                    }
                }

                if (isConnecting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = colors.accentWarm,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "正在握手接入数据中枢，请稍候...",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.accentWarm
                        )
                    }
                }

            } else {
                // ==========================================
                // 阶段二：连接成功，跳转下一页定制展示名称
                // ==========================================

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.accentMoss,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "中枢握手成功！",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        color = colors.inkPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "本设备已获得专属子钥匙授权，现可自定义展示名称",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 已配对节点与设备详情卡片
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "授权凭证信息",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("中枢节点", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                            Text(pairedHubUrl, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkPrimary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("设备编号", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                            Text(pairedDeviceId, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.accentWarm)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 自定义本设备名称卡片
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "本设备展示名称",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "此名称将展示在 Web 展厅的「受信任设备」列表中，方便您识别与管理",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PaperTextField(
                            value = customDeviceName,
                            onValueChange = { customDeviceName = it },
                            placeholder = "例如: 主理人的随行手机、iQOO Neo 9 Pro",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 完成进入按钮 (同时调用后端 PUT /api/v1/auth/devices/:id 同步自定义设备名称)
                TerracottaStampButton(
                    text = "【 完成设置，进入散页 (ENTER) 】",
                    enabled = customDeviceName.isNotBlank(),
                    onClick = {
                        val finalName = customDeviceName.trim()
                        haptics.stampSave()
                        AudioEngine.playPaperFlip()
                        credentialStore.deviceName = finalName
                        scope.launch {
                            if (pairedDeviceId.isNotBlank()) {
                                runCatching { apiClient.updateDeviceName(pairedDeviceId, finalName) }
                            }
                            Toast.makeText(context, "入网就绪，欢迎归来", Toast.LENGTH_SHORT).show()
                            onPairingSuccess()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
