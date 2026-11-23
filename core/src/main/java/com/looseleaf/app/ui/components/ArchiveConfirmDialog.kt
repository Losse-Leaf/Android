package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 统一弹窗容器 (ArchiveDialog)
 * - 一张浮起的纸页：纸页色、24dp 圆角、无描边、柔和远投影
 * - 标题用书页声部（衬线），正文与按钮用仪表声部
 * - 自适应软键盘与导航栏
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
    val cardShape = ArchiveShapes.lg

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
                    .widthIn(max = 400.dp)
                    .shadow(
                        elevation = ArchiveShadows.dialogElevation,
                        shape = cardShape,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(cardShape)
                    .background(colors.surfaceRaised)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = ArchiveTextStyles.sheetTitle,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                content()
                if (dismissButton != null || confirmButton != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            if (confirmButton != null) {
                                Spacer(modifier = Modifier.width(4.dp))
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
 * 确认对话框 (ArchiveConfirmDialog)
 * - 破坏性操作在标题前加一枚胭脂警示图形，确认按钮转为胭脂胶囊
 * - 单动作模式（dismissText = null）只保留确认按钮
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

    ArchiveDialog(onDismissRequest = onDismiss) {
        if (isDestructive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.negativeTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FolioGlyphs.Alert,
                    contentDescription = null,
                    tint = colors.alertRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = title,
            style = ArchiveTextStyles.sheetTitle,
            color = colors.inkPrimary
        )

        if (content.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = ArchiveTextStyles.body,
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
                ArchiveTextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    textColor = colors.inkSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            ArchiveSheetActionButton(
                text = confirmText,
                isDestructive = isDestructive,
                onClick = {
                    haptic.performHapticFeedback(
                        if (isDestructive) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                    )
                    onConfirm()
                }
            )
        }
    }
}
