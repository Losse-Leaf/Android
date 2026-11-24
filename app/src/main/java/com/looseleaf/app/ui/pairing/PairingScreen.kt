package com.looseleaf.app.ui.pairing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.HubUrlPolicy
import com.looseleaf.app.data.api.buildOidcLoginUrl
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.LocalDataResetCoordinator
import com.looseleaf.app.data.sync.ResetReason
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import com.looseleaf.app.ui.components.ArchivePrimaryButton
import com.looseleaf.app.ui.components.ArchiveTextButton
import com.looseleaf.app.ui.components.ArchiveTextField
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.ui.components.FolioGlyphs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 校验目标服务器地址并在系统默认浏览器拉起 Vesper Auth OAuth2/OIDC + PKCE 登录页。
 * 成功拉起时返回规范化后的服务器地址，失败时通过提示条反馈原因并返回 null。
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
        ArchiveToastManager.show("未找到可用的系统浏览器，请先安装或启用浏览器后重试", isError = true)
        null
    }
}

/**
 * 散页账号登录界面 (OAuth2 / OIDC · Vesper Auth)
 *
 * 简洁单步登录体验：
 * 1. 默认指向官方服务器，点击「使用 Vesper Auth 登录」在浏览器完成账号认证；
 * 2. 认证后自动回跳应用、静默换取会话并直接进入时间流；
 * 3. 服务器地址折叠在卡片下方，仅在自托管 / 切换地址时按需展开。
 */
@Composable
fun PairingScreen(
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: String? = null,
    initialSsoHubUrl: String? = null,
    onPayloadConsumed: () -> Unit = {},
    onSsoHubInitiated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }

    var serverUrlInput by rememberSaveable {
        mutableStateOf(
            initialSsoHubUrl?.takeIf { it.isNotBlank() }
                ?: apiClient.baseUrl.ifBlank { APIClient.DEFAULT_BASE_URL }
        )
    }
    var activeAuthHubUrl by rememberSaveable(initialSsoHubUrl) {
        mutableStateOf(initialSsoHubUrl.orEmpty())
    }
    var hasLaunchedBrowser by rememberSaveable(initialSsoHubUrl) {
        mutableStateOf(!initialSsoHubUrl.isNullOrBlank())
    }
    var isEditingServer by rememberSaveable { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    val redeemedCodes = remember { mutableSetOf<String>() }

    // 若收到指向非预期服务器的外部回调链接，需经用户二次确认防止钓鱼跳转
    var pendingExternalPayload by remember { mutableStateOf<String?>(null) }
    var pendingExternalHub by remember { mutableStateOf("") }

    fun completeOAuthSignIn(rawPayload: String) {
        if (isSigningIn) return

        if (!credentialStore.isSecureStorageAvailable) {
            ArchiveToastManager.show(
                "本机安全存储不可用，无法保存登录凭证；请检查系统锁屏安全设置后重试",
                isError = true
            )
            return
        }

        val parsed = PairingPayloadParser.parse(rawPayload)
        if (parsed == null) {
            ArchiveToastManager.show("无法识别登录回调链接，请重新点击登录", isError = true)
            return
        }

        val cleanCode = parsed.code.trim()
        if (cleanCode in redeemedCodes) {
            return
        }

        when (val verdict = HubUrlPolicy.validate(parsed.hubUrl)) {
            is HubUrlPolicy.Verdict.Rejected -> {
                ArchiveToastManager.show(verdict.reason, isError = true)
                return
            }
            is HubUrlPolicy.Verdict.Allowed -> Unit
        }

        isSigningIn = true
        scope.launch {
            val previousHub = apiClient.baseUrl
            try {
                apiClient.baseUrl = parsed.hubUrl

                val defaultDeviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
                val resp = apiClient.exchangeOidcCode(
                    code = cleanCode,
                    deviceName = defaultDeviceName,
                    deviceModel = Build.MODEL ?: "Android"
                )

                val token = resp.resolvedToken
                if (token.isBlank()) {
                    throw IllegalStateException("服务器未返回有效的登录凭证")
                }

                redeemedCodes.add(cleanCode)

                val finalHub = resp.hubUrl?.takeIf { it.isNotBlank() } ?: parsed.hubUrl
                if (previousHub.isNotBlank() &&
                    HubUrlPolicy.normalize(previousHub) != HubUrlPolicy.normalize(finalHub)
                ) {
                    LocalDataResetCoordinator.resetAll(
                        context = context,
                        syncManager = syncManager,
                        reason = ResetReason.SWITCH_HUB
                    )
                }

                apiClient.baseUrl = finalHub
                credentialStore.deviceToken = token
                credentialStore.deviceId = resp.resolvedSessionId
                credentialStore.deviceName = resp.deviceName.ifBlank { defaultDeviceName }
                credentialStore.accountSub = resp.account?.sub
                credentialStore.accountName = resp.account?.name
                credentialStore.accountEmail = resp.account?.email

                SyncManager.resetSessionPulls()
                val welcomeLabel = resp.account?.name?.takeIf { it.isNotBlank() }
                    ?: resp.account?.email?.takeIf { it.isNotBlank() }
                ArchiveToastManager.show(
                    if (welcomeLabel != null) "欢迎回来，$welcomeLabel" else "登录成功"
                )
                onPairingSuccess()
            } catch (e: CancellationException) {
                if (previousHub.isNotBlank()) runCatching { apiClient.baseUrl = previousHub }
                throw e
            } catch (e: Exception) {
                if (previousHub.isNotBlank()) runCatching { apiClient.baseUrl = previousHub }
                ArchiveToastManager.show("登录失败: ${e.message}", isError = true)
            } finally {
                isSigningIn = false
            }
        }
    }

    // 监听来自浏览器 OAuth2 认证完成后的回调深链
    LaunchedEffect(initialPayload) {
        val raw = initialPayload?.trim()
        if (!raw.isNullOrEmpty()) {
            val parsed = PairingPayloadParser.parse(raw)
            if (parsed != null) {
                val normalizedParsed = parsed.hubUrl.trimEnd('/')
                val expectedHub = activeAuthHubUrl.trimEnd('/')
                val currentInputHub = serverUrlInput.trim().trimEnd('/')
                if ((expectedHub.isNotEmpty() && normalizedParsed.equals(expectedHub, ignoreCase = true)) ||
                    (currentInputHub.isNotEmpty() && normalizedParsed.equals(currentInputHub, ignoreCase = true))
                ) {
                    completeOAuthSignIn(raw)
                } else {
                    pendingExternalHub = parsed.hubUrl
                    pendingExternalPayload = raw
                }
            }
            onPayloadConsumed()
        }
    }

    val displayHost = remember(serverUrlInput) {
        serverUrlInput.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .ifBlank { APIClient.DEFAULT_BASE_URL.removePrefix("https://") }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.archivePaper)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 品牌刊头
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "LOOSE LEAF",
                    style = ArchiveTextStyles.overline,
                    color = colors.accentSmallText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_screen_title),
                    style = ArchiveTextStyles.pageTitle,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_screen_subtitle),
                    style = ArchiveTextStyles.body,
                    color = colors.inkSecondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 账号登录主卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.leaf)
                    .background(colors.archiveCard)
                    .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(ArchiveShapes.md)
                            .background(colors.positiveTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FolioGlyphs.Person,
                            contentDescription = null,
                            tint = colors.accentMoss,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.login_screen_card_title),
                            style = ArchiveTextStyles.rowTitle,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.login_screen_card_desc),
                            style = ArchiveTextStyles.meta,
                            color = colors.inkMuted
                        )
                    }
                }

                ArchivePrimaryButton(
                    text = if (isSigningIn) {
                        stringResource(R.string.login_screen_signing_in)
                    } else {
                        stringResource(R.string.login_screen_oauth_button)
                    },
                    enabled = !isSigningIn,
                    onClick = {
                        val launchedHub = launchVesperAuthBrowser(context, serverUrlInput)
                        if (launchedHub != null) {
                            serverUrlInput = launchedHub
                            activeAuthHubUrl = launchedHub
                            hasLaunchedBrowser = true
                            isEditingServer = false
                            onSsoHubInitiated(launchedHub)
                        }
                    }
                )

                if (isSigningIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentWarm,
                            trackColor = colors.borderSubtle
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.login_screen_exchanging_hint),
                            style = ArchiveTextStyles.meta,
                            color = colors.inkSecondary
                        )
                    }
                } else if (hasLaunchedBrowser) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.md)
                            .background(colors.archiveSubtle)
                            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.md)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_screen_browser_waiting_hint),
                            style = ArchiveTextStyles.meta,
                            color = colors.inkSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 折叠式服务器地址设置（默认折叠，保持纯账号登录观感）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.leaf)
                    .background(colors.archiveCard)
                    .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { isEditingServer = !isEditingServer },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.login_screen_server_label),
                        style = ArchiveTextStyles.meta,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayHost,
                        style = ArchiveTextStyles.meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isEditingServer) FolioGlyphs.ChevronUp else FolioGlyphs.ChevronDown,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isEditingServer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ArchiveTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        placeholder = stringResource(R.string.login_screen_server_placeholder),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ArchiveTextButton(
                            text = stringResource(R.string.login_screen_server_reset),
                            textColor = colors.inkMuted,
                            onClick = { serverUrlInput = APIClient.DEFAULT_BASE_URL }
                        )
                    }
                }
            }
        }
    }

    if (pendingExternalPayload != null) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.login_screen_confirm_server_title),
            content = "即将登录到服务器：\n$pendingExternalHub\n\n请确认该地址可信后再继续。",
            confirmText = stringResource(R.string.login_screen_confirm_server_action),
            onConfirm = {
                val payload = pendingExternalPayload
                pendingExternalPayload = null
                if (payload != null) {
                    completeOAuthSignIn(payload)
                }
            },
            onDismiss = {
                pendingExternalPayload = null
            }
        )
    }
}
