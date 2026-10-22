package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.updater.ReleaseInfo
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R

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
    val cardShape = RoundedCornerShape(4.dp)

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
                        elevation = 8.dp,
                        shape = cardShape,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(cardShape)
                    .background(colors.archivePaper)
                    .border(0.5.dp, colors.borderDivider, cardShape)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.archive_release_dialog_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )

                    PaperBadge(
                        text = release.tagName,
                        containerColor = colors.accentWarm.copy(alpha = 0.12f),
                        textColor = colors.accentWarm
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    // MOB-P2-50：安装前把「版本 / 体积 / 来源」说清楚，
                    // 让用户确认自己装的是官方包（签名校验已在 UpdateChecker 内完成）。
                    text = buildString {
                        append(
                            if (release.versionName.isNotBlank()) {
                                "版本 ${release.versionName}"
                            } else {
                                "版本更新与体验修缮"
                            }
                        )
                        val sizeMb = release.apkSizeBytes?.let { it / 1024.0 / 1024.0 }
                        if (sizeMb != null && sizeMb > 0) {
                            append(String.format(java.util.Locale.US, " · %.1f MB", sizeMb))
                        }
                        append(" · 来源 GitHub Releases")
                    },
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 13.5.sp,
                    color = colors.inkSecondary
                )

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.archiveSubtle)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(2.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = release.body,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.inkPrimary
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (downloadProgress != null) "正在装载新版底片... ${(downloadProgress * 100).toInt()}%" else "正在装载新版底片...",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.accentWarm
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = colors.accentWarm,
                            trackColor = colors.borderDivider
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = colors.accentWarm,
                            trackColor = colors.borderDivider
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(stringResource(R.string.archive_release_dialog_remind_later), fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp, color = colors.inkMuted)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onDownloadAndInstall,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper
                        )
                    ) {
                        Text(stringResource(R.string.archive_release_dialog_update_now), fontFamily = ArchiveFontFamilies.EditorialSerif, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
