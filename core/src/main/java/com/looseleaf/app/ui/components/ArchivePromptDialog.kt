package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 单行输入弹窗 (ArchivePromptDialog)
 * 用于重命名设备等单行文本录入，复用 ArchiveDialog 与 ArchiveField。
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
    var textValue by remember { mutableStateOf(initialValue) }

    ArchiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = ArchiveTextStyles.sheetTitle,
            color = colors.inkPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        ArchiveField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = placeholder,
            label = label,
            singleLine = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = { if (textValue.isNotBlank()) onConfirm(textValue.trim()) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArchiveTextButton(
                text = dismissText,
                onClick = onDismiss,
                textColor = colors.inkSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            ArchiveSheetActionButton(
                text = confirmText,
                enabled = textValue.isNotBlank(),
                onClick = { onConfirm(textValue.trim()) }
            )
        }
    }
}
