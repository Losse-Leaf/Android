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

/**
 * 散页自研纸墨确认对话框 (ArchiveConfirmDialog)
 * 彻底替换 Android 系统原生及粗糙的 AlertDialog，呈现手工宣纸、极细沉墨边与金石朱砂印章质感
 */
@Composable
fun ArchiveConfirmDialog(
    title: String,
    content: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val cardShape = RoundedCornerShape(4.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
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
                    .widthIn(max = 380.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = cardShape,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(cardShape)
                    .background(colors.archivePaper)
                    .border(0.5.dp, colors.borderDivider, cardShape)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                // 1. 报章宋体标题
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp,
                    color = colors.inkPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 书卷正文内容
                if (content.isNotBlank()) {
                    Text(
                        text = content,
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = colors.inkSecondary
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 3. 底部按钮操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = dismissText,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.5.sp,
                            color = colors.inkMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) colors.accentWarm else colors.inkPrimary,
                            contentColor = colors.archivePaper
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)
                    ) {
                        Text(
                            text = confirmText,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}
