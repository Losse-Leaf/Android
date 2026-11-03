package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一弹窗容器 (ArchiveDialog)
 * 唯一标准居中弹窗入口：12dp 中圆角纸片、1dp 发丝沉墨边、自适应软键盘避让
 */
@Composable
fun ArchiveDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    val cardShape = ArchiveShapes.medium

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = cardShape,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(cardShape)
                    .background(colors.archivePaper)
                    .border(ArchiveLines.hairline, colors.borderDivider, cardShape)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = ArchiveTypeScale.title,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
                content()
                if (dismissButton != null || confirmButton != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            if (confirmButton != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        if (confirmButton != null) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 散页自研纸墨确认对话框 (ArchiveConfirmDialog)
 * 彻底替换 Android 系统原生及粗糙的 AlertDialog，呈现手工宣纸、极细沉墨边与金石朱砂印章质感
 */
@Composable
fun ArchiveConfirmDialog(
    title: String,
    content: String,
    confirmText: String = "确认",
    /** 取消按钮文案；传 null 表示「只有一个动作」（用于纯展示型弹窗） */
    dismissText: String? = "取消",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val buttonShape = ArchiveShapes.small

    ArchiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Bold,
            fontSize = ArchiveTypeScale.title,
            letterSpacing = 0.5.sp,
            color = colors.inkPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (content.isNotBlank()) {
            Text(
                text = content,
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = ArchiveTypeScale.body,
                lineHeight = 21.sp,
                color = colors.inkSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissText != null) {
                TextButton(
                    onClick = onDismiss,
                    shape = buttonShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = dismissText,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.body,
                        color = colors.inkMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(
                        if (isDestructive) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                    )
                    onConfirm()
                },
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colors.alertRed else colors.accentWarm,
                    contentColor = colors.archivePaper
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Text(
                    text = confirmText,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = ArchiveTypeScale.body
                )
            }
        }
    }
}
