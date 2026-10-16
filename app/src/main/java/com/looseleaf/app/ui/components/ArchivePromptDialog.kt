package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * 散页自研纸墨输入提示弹窗 (ArchivePromptDialog)
 * 用于重命名随行设备、单行文本录入等微交互场景，提供典雅稿纸下划线与聚焦金石光标
 */
@Composable
fun ArchivePromptDialog(
    title: String,
    initialValue: String = "",
    placeholder: String = "",
    label: String? = null,
    confirmText: String = "保存",
    dismissText: String = "取消",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val cardShape = RoundedCornerShape(4.dp)
    var textValue by remember { mutableStateOf(initialValue) }

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
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = colors.inkPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                PaperTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = placeholder,
                    label = label,
                    isUnderline = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                        onClick = { onConfirm(textValue.trim()) },
                        enabled = textValue.isNotBlank(),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
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
