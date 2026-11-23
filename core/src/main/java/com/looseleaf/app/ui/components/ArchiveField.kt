package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 输入槽 (ArchiveField)
 *
 * Folio 的输入框是嵌进纸面的一道「凹槽」：熟宣色底、14dp 圆角，标签在槽外上方。
 * 四种形态各有清晰的视觉语言：
 * - 常态 / 聚焦：无描边 → 1.5dp 朱砂描边，槽底同时提亮到纸页色
 * - 禁用：整体降低透明度，文字与标签为禁用墨色，绝不出现朱砂
 * - 只读选择器（readOnly + onClick）：右侧向下箭头，聚焦时也不画朱砂描边
 * - 错误：1.5dp 胭脂描边 + 槽下方胭脂说明
 * 数字类输入自动启用等宽数字（tnum）。
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
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        autoCorrectEnabled = false,
        capitalization = KeyboardCapitalization.None,
        keyboardType = if (isNumeric) KeyboardType.Decimal else KeyboardType.Text
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Default,
    modifier: Modifier = Modifier,
    reservesErrorSlot: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    // 禁用态优先级最高：点不动的格子不该再喊「你填错了」
    val hasError = enabled && !errorMessage.isNullOrBlank()
    val isPicker = readOnly && onClick != null
    val activeFocus = isFocused && enabled && !readOnly

    val borderColor by animateColorAsState(
        targetValue = when {
            hasError -> colors.alertRed
            activeFocus -> colors.accentWarm
            else -> Color.Transparent
        },
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "fieldBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (hasError || activeFocus) 1.5.dp else 0.dp,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "fieldBorderWidth"
    )
    val containerColor by animateColorAsState(
        targetValue = if (activeFocus) colors.archiveCard else colors.archiveSubtle,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "fieldContainer"
    )
    val labelColor = when {
        !enabled -> colors.inkDisabled
        hasError -> colors.alertRed
        activeFocus -> colors.accentSmallText
        else -> colors.inkSecondary
    }
    val textColor = if (enabled) colors.inkPrimary else colors.inkDisabled
    val placeholderColor = if (enabled) colors.inkMuted else colors.inkDisabled
    val inputFamily = if (isNumeric) ArchiveFontFamilies.Numeric else ArchiveFontFamilies.Text
    val inputStyle = TextStyle(
        fontFamily = inputFamily,
        fontSize = ArchiveTypeScale.bodyLarge,
        lineHeight = 24.sp,
        fontWeight = if (isNumeric) FontWeight.Medium else FontWeight.Normal,
        fontFeatureSettings = if (isNumeric) "tnum, lnum" else null,
        color = textColor
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = ArchiveTextStyles.meta,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clip(ArchiveShapes.field)
                .background(containerColor)
                .border(borderWidth, borderColor, ArchiveShapes.field)
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = inputStyle,
                        color = placeholderColor,
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
                    textStyle = inputStyle,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
                    keyboardActions = keyboardActions,
                    // 只读选择器仍可获得焦点：用透明光标，避免「点开选择」时闪一下光标暗示能打字
                    cursorBrush = if (readOnly) SolidColor(Color.Transparent) else SolidColor(colors.accentWarm)
                )

                if (onClick != null && enabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(role = Role.Button, onClick = onClick)
                    )
                }
            }

            if (!trailingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailingText,
                    style = ArchiveTextStyles.label,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            trailingContent?.let {
                Spacer(modifier = Modifier.width(8.dp))
                it()
            }

            // 只读且可点：向下箭头暗示「点开选择」；不可点的只读字段不画箭头，免得骗人去点
            if (isPicker && enabled) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = FolioGlyphs.ChevronDown,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (reservesErrorSlot) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage.orEmpty(),
                style = ArchiveTextStyles.meta,
                color = colors.alertRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .alpha(if (hasError) 1f else 0f)
            )
        } else if (hasError) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage.orEmpty(),
                style = ArchiveTextStyles.meta,
                color = colors.alertRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 兼容旧调用的 ArchiveTextField，底层指向 ArchiveField
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
