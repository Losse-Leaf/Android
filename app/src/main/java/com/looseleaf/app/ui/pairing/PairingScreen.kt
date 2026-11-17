package com.looseleaf.app.ui.pairing

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.HubUrlPolicy
import com.looseleaf.app.data.api.buildOidcLoginUrl
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource

/**
 * 校验目标中枢地址并在系统默认浏览器拉起 Vesper Auth OIDC + PKCE 登录页。
 * 成功拉起时返回规范化后的中枢地址，失败时通过纸墨 Toast 提示原因并返回 null。
 */
internal fun launchVesperAuthBrowser(context: Context, rawHubUrl: String): String? {
    val validatedHub = when (val verdict = HubUrlPolicy.validate(rawHubUrl)) {
        is HubUrlPolicy.Verdict.Allowed -> verdict.url
        is HubUrlPolicy.Verdict.Rejected -> {
            ArchiveToastManager.show(verdict.reason, isError = true)
            return null
        }
    }
    val loginUrl = buildOidcLoginUrl(
        baseUrl = validatedHub,
        platform = "android",
        deviceModel = Build.MODEL ?: "Android"
    )
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
        context.startActivity(intent)
        validatedHub
    } catch (_: Exception) {
        ArchiveToastManager.show("未找到可用的系统浏览器，请改用下方扫码或手动深链配对", isError = true)
        null
    }
}

/**
 * 散页免密设备配对与统一通行证入网界面 (PairingScreen)
 * 阶段一：顶部主通道通过 Vesper Auth 通行证一键接入，次级兜底保留二维码扫描与配对深链粘贴；
 * 阶段二：首次握手连接成功后进入设备命名确认页（会话失效重连则直接恢复同步返回时间流）。
 */
@Composable
fun PairingScreen(
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: String? = null,
    initialSsoHubUrl: String? = null,
    isSessionReconnect: Boolean = false,
    expandFallbackInitially: Boolean = false,
    onPayloadConsumed: () -> Unit = {},
    onSsoHubInitiated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }

    // 0: 连接配对阶段, 1: 配对成功设置设备名称阶段
    var currentStep by rememberSaveable { mutableStateOf(0) }

    // Vesper Auth 主通道状态
    var ssoHubInput by rememberSaveable {
        mutableStateOf(
            initialSsoHubUrl?.takeIf { it.isNotBlank() }
                ?: apiClient.baseUrl.ifBlank { APIClient.DEFAULT_BASE_URL }
        )
    }
    var activeSsoHubUrl by rememberSaveable(initialSsoHubUrl) {
        mutableStateOf(initialSsoHubUrl.orEmpty())
    }
    var hasLaunchedSsoBrowser by rememberSaveable(initialSsoHubUrl) {
        mutableStateOf(!initialSsoHubUrl.isNullOrBlank())
    }

    // 次级兜底通道（默认折叠，避免用户走 Vesper Auth Passkey 时无故弹出相机权限申请）
    var showFallbackMethods by rememberSaveable(expandFallbackInitially) {
        mutableStateOf(expandFallbackInitially)
    }
    // 兜底子标签: 0 为扫码, 1 为粘贴配对链接
    var selectedMethodTab by rememberSaveable { mutableStateOf(0) }
    var pastedLinkInput by rememberSaveable { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    val redeemedTickets = remember { mutableSetOf<String>() }

    // 来自外部非预期深链的待确认配对请求
    var pendingLinkPayload by remember { mutableStateOf<String?>(null) }
    var pendingLinkHub by remember { mutableStateOf("") }

    // 握手成功后的设备信息
    var pairedDeviceId by rememberSaveable { mutableStateOf("") }
    var pairedHubUrl by rememberSaveable { mutableStateOf("") }
    var customDeviceName by rememberSaveable {
        mutableStateOf(
            credentialStore.deviceName?.takeIf { it.isNotBlank() }
                ?: (Build.MODEL ?: "Android 随行设备")
        )
    }

    fun executeHandshake(rawPayload: String, fromTrustedSso: Boolean = false) {
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

        val cleanTicket = parsed.ticket.trim()
        if (cleanTicket in redeemedTickets) {
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
                    ticket = cleanTicket,
                    deviceName = chosenName,
                    deviceModel = defaultModel
                )
                redeemedTickets.add(cleanTicket)

                credentialStore.deviceToken = resp.deviceToken
                credentialStore.deviceId = resp.deviceId
                credentialStore.deviceName = resp.deviceName.ifBlank { chosenName }
                val canonical = resp.hubUrl?.takeIf { it.isNotBlank() } ?: parsed.hubUrl
                if (previousBaseUrl.isNotBlank() &&
                    HubUrlPolicy.normalize(previousBaseUrl) != HubUrlPolicy.normalize(canonical)
                ) {
                    com.looseleaf.app.data.sync.LocalDataResetCoordinator.resetAll(
                        context = context,
                        syncManager = syncManager,
                        reason = com.looseleaf.app.data.sync.ResetReason.SWITCH_HUB
                    )
                    ArchiveToastManager.show("已切换到新的数据中枢，旧中枢的本地数据与待同步记录已清除")
                }
                SyncManager.resetSessionPulls()
                pairedDeviceId = resp.deviceId
                pairedHubUrl = resp.hubUrl ?: parsed.hubUrl
                customDeviceName = resp.deviceName.ifBlank { chosenName }

                val canonicalHub = resp.hubUrl?.takeIf { it.isNotBlank() }
                if (!canonicalHub.isNullOrBlank() && canonicalHub != parsed.hubUrl) {
                    runCatching { apiClient.baseUrl = canonicalHub }
                }

                if (isSessionReconnect && fromTrustedSso) {
                    ArchiveToastManager.show("通行证验证通过，设备会话已恢复")
                    onPairingSuccess()
                } else {
                    currentStep = 1
                }
            } catch (e: CancellationException) {
                runCatching { apiClient.baseUrl = previousBaseUrl }
                throw e
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
            onPayloadConsumed()
            val parsed = PairingPayloadParser.parse(raw)
            if (parsed == null) {
                ArchiveToastManager.show("无法识别该配对链接", isError = true)
            } else if (
                activeSsoHubUrl.isNotBlank() &&
                HubUrlPolicy.normalize(activeSsoHubUrl) == HubUrlPolicy.normalize(parsed.hubUrl)
            ) {
                // 主理人刚刚从本机发起同一中枢的 Vesper Auth 登录回跳：直接核销一次性工单完成激活
                executeHandshake(raw, fromTrustedSso = true)
            } else {
                // 非本机主动发起的外部深链一律先二次确认，防止静默改绑中枢
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
                // 阶段一：Vesper Auth 通行证主通道 + 扫码/深链次级兜底
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

                Spacer(modifier = Modifier.height(22.dp))

                // 1. 顶部主通道：Vesper Auth 统一单点登录一键接入
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(R.string.pairing_screen_sso_title),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.pairing_screen_sso_desc),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.pairing_screen_sso_hub_label),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        ArchiveField(
                            value = ssoHubInput,
                            onValueChange = { ssoHubInput = it },
                            placeholder = stringResource(R.string.pairing_screen_sso_hub_placeholder),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ArchivePrimaryButton(
                            text = stringResource(R.string.pairing_screen_sso_button),
                            enabled = !isConnecting && ssoHubInput.isNotBlank(),
                            onClick = {
                                val launchedHub = launchVesperAuthBrowser(context, ssoHubInput)
                                if (launchedHub != null) {
                                    ssoHubInput = launchedHub
                                    activeSsoHubUrl = launchedHub
                                    hasLaunchedSsoBrowser = true
                                    onSsoHubInitiated(launchedHub)
                                }
                            }
                        )

                        if (hasLaunchedSsoBrowser) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.archiveSubtle)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.pairing_screen_sso_launched_hint),
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 次级兜底通道：扫码连接 / 手动粘贴深链
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.archiveSubtle)
                        .border(0.5.dp, colors.borderDivider, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            showFallbackMethods = !showFallbackMethods
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pairing_screen_fallback_toggle),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkSecondary
                    )
                    Icon(
                        painter = painterResource(
                            if (showFallbackMethods) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                        ),
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showFallbackMethods) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // 两种兜底连接方式切换 Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.archiveSubtle)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
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
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.archivePaper else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(role = Role.Tab) {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedMethodTab == 0) {
                        // 兜底方式 1：正方形 1:1 经典取景扫码器
                        SquareQrScannerView(
                            onQrCodeScanned = { scannedText ->
                                executeHandshake(scannedText)
                            }
                        )
                    } else {
                        // 兜底方式 2：粘贴配对链接
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

                                ArchiveField(
                                    value = pastedLinkInput,
                                    onValueChange = { pastedLinkInput = it },
                                    placeholder = stringResource(R.string.pairing_screen_placeholder_01),
                                    singleLine = false,
                                    minLines = 3,
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
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(painter = painterResource(R.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.pairing_screen_clipboard), fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                ArchivePrimaryButton(
                                    text = if (isConnecting) "正在解析并连接中枢..." else "确认连接中枢 (CONNECT)",
                                    enabled = !isConnecting && pastedLinkInput.isNotBlank(),
                                    onClick = {
                                        executeHandshake(pastedLinkInput)
                                    }
                                )
                            }
                        }
                    }
                }

                if (isConnecting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            // 与同一行的说明文字同色：图标属非文本（3:1 即可），
                            // 但同组一深一浅会比"两个都略低"更难看
                            color = colors.accentSmallText,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.pairing_screen_text_05),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.accentSmallText
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
                            Text(pairedDeviceId, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.accentSmallText)
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

                        ArchiveField(
                            value = customDeviceName,
                            onValueChange = { customDeviceName = it },
                            placeholder = stringResource(R.string.pairing_screen_placeholder_02),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 完成进入按钮 (同时调用后端 PUT /api/v1/auth/devices/:id 同步自定义设备名称)
                ArchivePrimaryButton(
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
