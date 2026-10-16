package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 手账质感纸张卡片容器
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveSubtle
    val border = borderColor ?: colors.borderDivider
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 陶土赭石色高对比印章按钮 (Terracotta Stamp Button)
 * 专为「盖章记下」与核心主干提交操作打造，模拟物理木印章的厚重沉稳感
 */
@Composable
fun TerracottaStampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(2.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentWarm,
            contentColor = colors.archivePaper,
            disabledContainerColor = colors.archiveSubtle,
            disabledContentColor = colors.inkMuted.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        ),
        border = if (!enabled) ButtonDefaults.outlinedButtonBorder.copy(
            brush = SolidColor(colors.borderDivider)
        ) else null,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            letterSpacing = 2.sp
        )
    }
}

/**
 * 纸质分类与状态标签 (Paper Badge)
 */
@Composable
fun PaperBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    textColor: Color? = null,
    isPendingSync: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    val bg = containerColor ?: if (isPendingSync) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveSubtle
    val fg = textColor ?: if (isPendingSync) colors.accentWarm else colors.inkSecondary
    val shape = RoundedCornerShape(2.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, colors.borderDivider, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (isPendingSync) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accentWarm)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = fg
        )
    }
}

/**
 * 典雅极细折痕线
 */
@Composable
fun PaperDivider(
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val dividerColor = color ?: LooseLeafTheme.colors.borderDivider
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = dividerColor
    )
}

/**
 * 散页独家自研纸本输入框 (PaperTextField)
 * 彻底替换 Material 粗糙的 OutlinedTextField，还原宣纸微凹、极细墨边与沉稳光标
 */
@Composable
fun PaperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(4.dp)
    val borderColor = if (isFocused) colors.accentWarm else colors.borderDivider
    val borderWidth = if (isFocused) 1.dp else 0.5.dp
    val effectiveTextStyle = (textStyle ?: TextStyle(
        fontFamily = ArchiveFontFamilies.EditorialSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = colors.inkPrimary
    ))

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.archiveSubtle.copy(alpha = 0.7f), shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = effectiveTextStyle.copy(color = colors.inkMuted)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                        minLines = minLines,
                        enabled = enabled,
                        textStyle = effectiveTextStyle,
                        cursorBrush = SolidColor(colors.accentWarm),
                        interactionSource = interactionSource,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * 散页独家自研多行文稿纸本编辑器 (PaperTextEditor)
 * 专为长篇手记、随笔与感言打造，温润信笺质感，优美行高与微凹边框
 */
@Composable
fun PaperTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    minHeight: Dp = 100.dp,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(4.dp)
    val borderColor = if (isFocused) colors.accentWarm else colors.borderDivider
    val borderWidth = if (isFocused) 1.dp else 0.5.dp
    val effectiveTextStyle = (textStyle ?: TextStyle(
        fontFamily = ArchiveFontFamilies.BodySans,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        color = colors.inkPrimary
    ))

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.archiveSubtle.copy(alpha = 0.7f), shape)
                .border(borderWidth, borderColor, shape)
                .padding(12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = effectiveTextStyle.copy(color = colors.inkMuted)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                enabled = enabled,
                textStyle = effectiveTextStyle,
                cursorBrush = SolidColor(colors.accentWarm),
                interactionSource = interactionSource,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions
            )
        }
    }
}

/**
 * 散页纸本筛选与形态选择胶囊 (PaperFilterPill)
 * 彻底替换 Material3 默认 FilterChip 的违和薰衣草紫色，呈现沉稳的朱砂与棉纸大地质感
 */
@Composable
fun PaperFilterPill(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle
    val border = if (selected) colors.accentWarm else colors.borderDivider
    val textColor = if (selected) colors.accentWarm else colors.inkSecondary

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(if (selected) 1.dp else 0.5.dp, border, shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}
