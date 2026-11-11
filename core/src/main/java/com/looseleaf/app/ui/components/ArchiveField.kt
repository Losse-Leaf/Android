package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一底线式输入框 (ArchiveField)
 * - 标签在上方（小字、次要色）
 * - 仅底部 1dp 淡线，聚焦时变为 2dp 赤陶下划线
 * - 数字类输入自动采用等宽字体 (ArchiveFontFamilies.MonoScale)
 * - 占位符单行省略
 * - 支持 trailingText 与 trailingContent 插槽
 */
@Composable
fun ArchiveField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String = "",
    isNumeric: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else minLines,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = ArchiveTypeScale.caption,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = if (isFocused) colors.accentWarm else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = if (isNumeric) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.bodyLarge,
                        color = colors.inkMuted,
                        maxLines = if (singleLine) 1 else maxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontFamily = if (isNumeric) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.bodyLarge,
                        color = colors.inkPrimary
                    ),
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(colors.accentWarm)
                )

                if (onClick != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(role = Role.Button, onClick = onClick)
                    )
                }
            }

            if (!trailingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = trailingText,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = ArchiveTypeScale.body,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            trailingContent?.let {
                Spacer(modifier = Modifier.width(6.dp))
                it()
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFocused) 2.dp else 1.dp)
                    .background(if (isFocused) colors.accentWarm else colors.borderSubtle)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage ?: "",
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = ArchiveTypeScale.caption,
            color = colors.alertRed,
            maxLines = 1,
            modifier = Modifier.alpha(if (errorMessage.isNullOrBlank()) 0f else 1f)
        )
    }
}

/**
 * 兼容旧调用的 ArchiveTextField，底层指向底线式输入 ArchiveField
 */
@Composable
fun ArchiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    isNumeric: Boolean = false,
    modifier: Modifier = Modifier
) {
    ArchiveField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        isNumeric = isNumeric,
        singleLine = singleLine,
        modifier = modifier
    )
}
