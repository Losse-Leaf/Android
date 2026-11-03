package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页自研纸墨输入提示弹窗 (ArchivePromptDialog)
 * 用于重命名随行设备、单行文本录入等微交互场景，复用统一 ArchiveDialog 容器
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
    val haptic = LocalHapticFeedback.current
    val buttonShape = ArchiveShapes.small
    var textValue by remember { mutableStateOf(initialValue) }

    ArchiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Bold,
            fontSize = ArchiveTypeScale.title,
            color = colors.inkPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        ArchiveField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = placeholder,
            label = label,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm(textValue.trim())
                },
                enabled = textValue.isNotBlank(),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentWarm,
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
