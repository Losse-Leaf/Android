package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateChecker
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * 散页自研新版发布笺纸对话框 (ArchiveReleaseDialog)
 * 替代系统 AlertDialog 展示版本更新日志与安装进度
 */
@Composable
fun ArchiveReleaseDialog(
    release: ReleaseInfo,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onDismiss: () -> Unit,
    onDownloadAndInstall: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val cardShape = com.looseleaf.app.theme.ArchiveShapes.lg
    val buttonShape = com.looseleaf.app.theme.ArchiveShapes.full

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .shadow(
                        elevation = com.looseleaf.app.theme.ArchiveShadows.dialogElevation,
                        shape = cardShape,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(cardShape)
                    .background(colors.surfaceRaised)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                            .background(colors.accentWarm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "新",
                            fontFamily = ArchiveFontFamilies.Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.onAccent
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.archive_release_dialog_text_01),
                            style = com.looseleaf.app.theme.ArchiveTextStyles.sheetTitle,
                            color = colors.inkPrimary
                        )
                        Text(
                            // 安装前把「版本 / 体积 / 来源」说清楚（签名校验已在 UpdateChecker 内完成）
                            text = buildString {
                                append(
                                    if (release.versionName.isNotBlank()) "版本 ${release.versionName}" else release.tagName
                                )
                                val sizeMb = release.apkSizeBytes?.let { it / 1024.0 / 1024.0 }
                                if (sizeMb != null && sizeMb > 0) {
                                    append(String.format(java.util.Locale.US, " · %.1f MB", sizeMb))
                                }
                                append(" · GitHub Releases")
                            },
                            style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                            color = colors.inkMuted
                        )
                    }
                }

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "更新说明",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(com.looseleaf.app.theme.ArchiveShapes.md)
                            .background(colors.archiveSubtle)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Text(
                            text = release.body,
                            style = com.looseleaf.app.theme.ArchiveTextStyles.body,
                            color = colors.inkSecondary
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "正在下载新版本",
                            style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                            color = colors.inkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (downloadProgress != null) {
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = com.looseleaf.app.theme.ArchiveTextStyles.meta,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accentSmallText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(com.looseleaf.app.theme.ArchiveShapes.full),
                            color = colors.accentWarm,
                            trackColor = colors.archiveSubtle
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(com.looseleaf.app.theme.ArchiveShapes.full),
                            color = colors.accentWarm,
                            trackColor = colors.archiveSubtle
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchiveTextButton(
                        text = stringResource(R.string.archive_release_dialog_remind_later),
                        onClick = onDismiss,
                        enabled = !isDownloading,
                        textColor = if (isDownloading) colors.inkDisabled else colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    ArchiveSheetActionButton(
                        text = stringResource(R.string.archive_release_dialog_update_now),
                        onClick = onDownloadAndInstall,
                        enabled = !isDownloading
                    )
                }
            }
        }
    }
}

class AppUpdateDownloadState(
    val isDownloading: Boolean,
    val downloadProgress: Float,
    val downloadedFile: File?,
    val startDownloadAndInstall: () -> Unit,
    val installDownloaded: () -> Unit
)

@Composable
fun rememberAppUpdateController(release: ReleaseInfo): AppUpdateDownloadState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadProgress by remember(release.tagName) { mutableFloatStateOf(0f) }
    var isDownloading by remember(release.tagName) { mutableStateOf(false) }
    var downloadedFile by remember(release.tagName) { mutableStateOf<File?>(null) }

    val installApkWithFeedback: (File) -> Unit = { file ->
        if (!UpdateChecker.installApk(context, file)) {
            ArchiveToastManager.show(
                "安装包签名与已装版本不一致，需先做服务端备份再卸载重装",
                isError = true
            )
        }
    }

    return AppUpdateDownloadState(
        isDownloading = isDownloading,
        downloadProgress = downloadProgress,
        downloadedFile = downloadedFile,
        startDownloadAndInstall = {
            if (!isDownloading) {
                scope.launch {
                    isDownloading = true
                    val res = UpdateChecker.downloadApkStream(
                        context = context,
                        apkUrl = release.apkUrl,
                        versionName = release.versionName,
                        expectedSha256 = release.apkSha256
                    ) { p, _, _ ->
                        downloadProgress = p
                    }
                    isDownloading = false
                    res.onSuccess { f ->
                        downloadedFile = f
                        installApkWithFeedback(f)
                    }.onFailure { err ->
                        ArchiveToastManager.show("下载失败: ${err.message}", isError = true)
                    }
                }
            }
        },
        installDownloaded = {
            downloadedFile?.let(installApkWithFeedback)
        }
    )
}

@Composable
fun AppUpdateDialogHost(
    release: ReleaseInfo?,
    onDismiss: () -> Unit
) {
    val activeRelease = release ?: return
    val controller = rememberAppUpdateController(activeRelease)
    ArchiveReleaseDialog(
        release = activeRelease,
        isDownloading = controller.isDownloading,
        downloadProgress = if (controller.isDownloading) controller.downloadProgress else null,
        onDismiss = {
            if (!controller.isDownloading) onDismiss()
        },
        onDownloadAndInstall = controller.startDownloadAndInstall
    )
}
