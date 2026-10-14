package com.looseleaf.app.ui.pairing

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.ui.components.PaperDivider
import com.looseleaf.app.ui.components.TerracottaStampButton
import kotlinx.coroutines.launch

/**
 * 免密设备配对与入网界面 (PairingScreen) - docs/mobile.md §3.4
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

    var hubUrl by remember { mutableStateOf(apiClient.baseUrl) }
    var ticketText by remember { mutableStateOf("") }
    var masterPasscode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(credentialStore.deviceName) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isDirectPasscodeMode by remember { mutableStateOf(false) }

    // 条码/二维码扫描启动器
    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            // 解析二维码内容：可能包含完整 URL 或工单号
            val parsedTicket = if (contents.contains("ticket=")) {
                contents.substringAfter("ticket=").substringBefore("&")
            } else {
                contents.trim()
            }
            ticketText = parsedTicket
            Toast.makeText(context, "已识别配对工单: $parsedTicket", Toast.LENGTH_SHORT).show()
        }
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // 标题
            Text(
                text = "散页 · 设备入网",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                letterSpacing = 2.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "第一现场随行暗房与口袋触觉账本",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 1. 中枢网络地址配置卡片
            PaperCard {
                Column {
                    Text(
                        text = "数据中枢地址 (Hub URL)",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hubUrl,
                        onValueChange = {
                            hubUrl = it
                            apiClient.baseUrl = it
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentWarm,
                            unfocusedBorderColor = colors.borderDivider,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本地调试: 模拟器用 10.0.2.2:8088，真机局域网填写电脑 IP",
                        fontSize = 11.sp,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        color = colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 配对模式切换
            if (!isDirectPasscodeMode) {
                // 扫码/工单配对模式
                PaperCard {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = colors.accentWarm
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "扫码握手配对",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "在 Web 展厅管理后台点击「信任新设备」，扫描屏幕展示的动态配对二维码，即可免密接入。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val options = ScanOptions().apply {
                                    setPrompt("请对准 Web 展厅端展示的配对二维码")
                                    setBeepEnabled(true)
                                    setOrientationLocked(false)
                                }
                                barcodeLauncher.launch(options)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.archivePaper,
                                contentColor = colors.inkPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.dp,
                                brush = androidx.compose.ui.graphics.SolidColor(colors.borderDivider)
                            ),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开启相机扫描二维码", fontFamily = ArchiveFontFamilies.EditorialSerif)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "或手动输入 6 位工单号 / 配对码",
                            fontSize = 13.sp,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            color = colors.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ticketText,
                            onValueChange = { ticketText = it },
                            placeholder = { Text("例如: llpt_7a9c82...", color = colors.inkMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentWarm,
                                unfocusedBorderColor = colors.borderDivider,
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            )
                        )
                    }
                }
            } else {
                // 管理员母密钥直接登入
                PaperCard {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = colors.accentWarm
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "管理员母密钥登入",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "首次装帧设定的主人密钥，验证通过后将为本机签发专属子钥匙 (Device Token)。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = masterPasscode,
                            onValueChange = { masterPasscode = it },
                            placeholder = { Text("输入母密钥", color = colors.inkMuted) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentWarm,
                                unfocusedBorderColor = colors.borderDivider,
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 设备名称配置
            PaperCard {
                Column {
                    Text(
                        text = "本设备在管理台展示的名称",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = {
                            deviceName = it
                            credentialStore.deviceName = it
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentWarm,
                            unfocusedBorderColor = colors.borderDivider,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 提交按钮
            TerracottaStampButton(
                text = if (isLoading) "正在握手配对..." else "确认接入中枢 (CONNECT)",
                enabled = !isLoading && (ticketText.isNotBlank() || masterPasscode.isNotBlank()),
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = null
                        try {
                            if (!isDirectPasscodeMode) {
                                val resp = apiClient.confirmPairing(
                                    ticket = ticketText.trim(),
                                    deviceName = deviceName.trim(),
                                    deviceModel = Build.MODEL ?: "Android"
                                )
                                credentialStore.deviceToken = resp.deviceToken
                                credentialStore.deviceId = resp.deviceId
                                credentialStore.deviceName = resp.deviceName
                                if (!resp.hubUrl.isNullOrBlank()) {
                                    apiClient.baseUrl = resp.hubUrl
                                }
                                statusMessage = "配对成功！设备编号: ${resp.deviceId}"
                                onPairingSuccess()
                            } else {
                                val resp = apiClient.verifyMasterPasscode(
                                    passcode = masterPasscode.trim(),
                                    deviceName = deviceName.trim()
                                )
                                credentialStore.deviceToken = resp.token
                                credentialStore.deviceId = resp.deviceId
                                credentialStore.deviceName = resp.deviceName
                                statusMessage = "验证成功！设备编号: ${resp.deviceId}"
                                onPairingSuccess()
                            }
                        } catch (e: Exception) {
                            statusMessage = "连接失败: ${e.message}"
                            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 切换按钮
            TextButton(
                onClick = { isDirectPasscodeMode = !isDirectPasscodeMode }
            ) {
                Text(
                    text = if (isDirectPasscodeMode) "切换为 扫码工单配对" else "切换为 母密钥直接登入",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.sp,
                    color = colors.inkMuted
                )
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage!!,
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 12.sp,
                    color = if (statusMessage!!.contains("成功")) colors.accentMoss else colors.accentWarm
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
