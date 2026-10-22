package com.looseleaf.app.ui.pairing

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.HubUrlPolicy
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource

/**
 * 散页免密设备配对与入网界面 (PairingScreen)
 * 严格遵从两阶段入网模型：
 * 阶段一：扫码或粘贴链接自动解析中枢与工单握手连接；
 * 阶段二：连接成功后进入下一页设置设备名称并进入散页。
 */
@Composable
fun PairingScreen(
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }

    // 0: 连接配对阶段 (扫码/粘贴链接), 1: 配对成功设置设备名称阶段
    var currentStep by remember { mutableStateOf(0) }

    // 方式切换: 0 为扫码, 1 为粘贴配对链接 (严格只保留二维码+链接双通道)
    var selectedMethodTab by remember { mutableStateOf(0) }
    var pastedLinkInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    // 来自外部深链 / 二维码的待确认配对请求
    var pendingLinkPayload by remember { mutableStateOf<String?>(null) }
    var pendingLinkHub by remember { mutableStateOf("") }

    // 握手成功后的设备信息
    var pairedDeviceId by remember { mutableStateOf("") }
    var pairedHubUrl by remember { mutableStateOf("") }
    var customDeviceName by remember {
        mutableStateOf(Build.MODEL ?: "Android 随行设备")
    }

    fun executeHandshake(rawPayload: String) {
        if (isConnecting) return

        if (!credentialStore.isSecureStorageAvailable) {
            ArchiveToastManager.show(
                "本机密钥库不可用，无法安全保存设备凭据；请检查系统安全设置后重试",
                isError = true
            )
            return
        }

        val parsed = PairingPayloadParser.parse(rawPayload)
        if (parsed == null) {
            ArchiveToastManager.show("无法识别该配对链接，请确认是否包含中枢地址与工单号", isError = true)
            return
        }

        when (val verdict = HubUrlPolicy.validate(parsed.hubUrl)) {
            is HubUrlPolicy.Verdict.Rejected -> {
                ArchiveToastManager.show(verdict.reason, isError = true)
                return
            }
            is HubUrlPolicy.Verdict.Allowed -> Unit
        }

        scope.launch {
            isConnecting = true
            val previousBaseUrl = apiClient.baseUrl
            try {
                // 只在握手期间临时指向目标中枢；失败则回滚，避免把 App 永久指到一个连不上的地址
                apiClient.baseUrl = parsed.hubUrl

                val defaultModel = Build.MODEL ?: "Android"
                val chosenName = customDeviceName.trim().ifBlank { defaultModel }
                val resp = apiClient.confirmPairing(
                    ticket = parsed.ticket,
                    deviceName = chosenName,
                    deviceModel = defaultModel
                )

                credentialStore.deviceToken = resp.deviceToken
                credentialStore.deviceId = resp.deviceId
                credentialStore.deviceName = resp.deviceName.ifBlank { chosenName }
                pairedDeviceId = resp.deviceId
                pairedHubUrl = resp.hubUrl ?: parsed.hubUrl
                customDeviceName = resp.deviceName.ifBlank { chosenName }

                // 服务端若返回了规范化地址，按同一套策略校验后再落库
                val canonicalHub = resp.hubUrl?.takeIf { it.isNotBlank() }
                if (!canonicalHub.isNullOrBlank() && canonicalHub != parsed.hubUrl) {
                    runCatching { apiClient.baseUrl = canonicalHub }
                }

                currentStep = 1
            } catch (e: Exception) {
                runCatching { apiClient.baseUrl = previousBaseUrl }
                ArchiveToastManager.show("连接数据中枢失败: ${e.message}", isError = true)
            } finally {
                isConnecting = false
            }
        }
    }

    LaunchedEffect(initialPayload) {
        val raw = initialPayload
        if (!raw.isNullOrBlank()) {
            // 外部深链一律先确认：不允许「点开链接就静默改绑中枢」
            val parsed = PairingPayloadParser.parse(raw)
            if (parsed == null) {
                ArchiveToastManager.show("无法识别该配对链接", isError = true)
            } else {
                pendingLinkPayload = raw
                pendingLinkHub = parsed.hubUrl
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
                    text = stringResource(R.string.pairing_screen_text_01),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.pairing_screen_text_02),
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
                                    selectedMethodTab = index
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = if (index == 0) painterResource(R.drawable.ic_qr_code_scanner) else painterResource(R.drawable.ic_link),
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
                                text = stringResource(R.string.pairing_screen_text_03),
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.pairing_screen_text_04),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = colors.inkSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            PaperTextEditor(
                                value = pastedLinkInput,
                                onValueChange = { pastedLinkInput = it },
                                placeholder = stringResource(R.string.pairing_screen_placeholder_01),
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
                                        ArchiveToastManager.show("已粘贴剪贴板内容")
                                    } else {
                                        ArchiveToastManager.show("剪贴板为空")
                                    }
                                },
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.pairing_screen_clipboard), fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
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
                            text = stringResource(R.string.pairing_screen_text_05),
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
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = colors.accentMoss,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.pairing_screen_text_06),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        color = colors.inkPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.pairing_screen_text_07),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 已配对节点与设备详情卡片
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.pairing_screen_text_08),
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
                            Text(stringResource(R.string.pairing_screen_hub), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                            Text(pairedHubUrl, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkPrimary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.pairing_screen_device_id), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
                            Text(pairedDeviceId, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.accentWarm)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 自定义本设备名称卡片
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.pairing_screen_text_09),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.pairing_screen_text_10),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PaperTextField(
                            value = customDeviceName,
                            onValueChange = { customDeviceName = it },
                            placeholder = stringResource(R.string.pairing_screen_placeholder_02),
                            isUnderline = true,
                            textStyle = TextStyle(
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkPrimary
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 完成进入按钮 (同时调用后端 PUT /api/v1/auth/devices/:id 同步自定义设备名称)
                TerracottaStampButton(
                    text = stringResource(R.string.pairing_screen_text_11),
                    enabled = customDeviceName.isNotBlank(),
                    onClick = {
                        val finalName = customDeviceName.trim()
                        credentialStore.deviceName = finalName
                        scope.launch {
                            if (pairedDeviceId.isNotBlank()) {
                                runCatching { apiClient.updateDeviceName(pairedDeviceId, finalName) }
                            }
                            ArchiveToastManager.show("入网就绪，欢迎归来")
                            onPairingSuccess()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 外部深链的二次确认：只有明确点击「确认配对」才交换设备凭据
    pendingLinkPayload?.let { raw ->
        ArchiveConfirmDialog(
            title = stringResource(R.string.pairing_screen_title_01),
            content = "外部链接请求将本机绑定到：\n$pendingLinkHub\n\n确认后才会交换设备凭据。若这不是你的中枢地址，请直接取消。",
            confirmText = stringResource(R.string.pairing_screen_confirmText_01),
            onConfirm = {
                pendingLinkPayload = null
                executeHandshake(raw)
            },
            onDismiss = { pendingLinkPayload = null }
        )
    }
}
