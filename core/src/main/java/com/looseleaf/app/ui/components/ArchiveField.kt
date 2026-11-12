package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.core.R

/**
 * 散页统一底线式输入框 (ArchiveField)
 * - 标签在上方（小字、次要色）
 * - 仅底部一条线：聚焦时 2dp 赤陶，错误时 2dp 砖红，其余 1dp 淡线
 * - 数字类输入自动采用等宽字体 (ArchiveFontFamilies.MonoScale)
 * - 占位符单行省略
 * - 支持 trailingText 与 trailingContent 插槽
 *
 * 【第 1 批补全输入三态】此前 `enabled = false` 只是透传给 BasicTextField，
 * 文字颜色和下划线完全不变，用户会反复去点一个点不动的格子；`readOnly`
 * 与普通输入框长得一模一样，用户以为可以打字；`errorMessage` 只有文字没有视觉分支。
 * 现在四种形态各有明确视觉语言：
 * - 普通 / 聚焦：1dp 淡线 → 2dp 赤陶
 * - 禁用：字 inkDisabled、线 lineEdge 60%、标签 inkDisabled，**绝不出现赤陶**
 * - 只读（日期/类别等选择器）：不画线，右侧 12dp chevron，暗示"点开选择"
 * - 错误：2dp 砖红下划线 + 下方 12sp 砖红说明
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
        autoCorrect = false,
        capitalization = KeyboardCapitalization.None,
        keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Default,
    modifier: Modifier = Modifier,
    reservesErrorSlot: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    // 禁用态优先级最高：一个点不动的格子不该再喊"你填错了"，
    // 也不该画红色下划线（会让人以为改一改就能提交）。故 hasError 在禁用时直接为 false。
    val hasError = enabled && !errorMessage.isNullOrBlank()
    // 只读输入框是"选择器"，不是文本框：不画下划线，靠右侧箭头表达可点
    val underlineColor = when {
        !enabled -> colors.lineEdge.copy(alpha = 0.6f)
        hasError -> colors.alertRed
        readOnly -> Color.Transparent
        isFocused -> colors.accentWarm
        else -> colors.borderSubtle
    }
    val underlineHeight = if (hasError || (isFocused && enabled && !readOnly)) 2.dp else 1.dp
    val labelColor = when {
        !enabled -> colors.inkDisabled
        hasError -> colors.alertRed
        // 只读选择器被点开时不该把标签染成赤陶：赤陶＝"可输入/当前焦点"，
        // 而这里根本没有可输入的东西。
        isFocused && !readOnly -> colors.accentWarm
        else -> colors.inkSecondary
    }
    val textColor = if (enabled) colors.inkPrimary else colors.inkDisabled
    val placeholderColor = if (enabled) colors.inkMuted else colors.inkDisabled

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
                color = labelColor,
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
                    textStyle = TextStyle(
                        fontFamily = if (isNumeric) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.bodyLarge,
                        color = textColor
                    ),
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = keyboardOptions.copy(imeAction = imeAction),
                    keyboardActions = keyboardActions,
                    // 只读选择器仍可获得焦点：不换透明光标就会在"点开选择"时闪一下赤陶竖线，
                    // 暗示这里能打字。
                    cursorBrush = if (readOnly) {
                        SolidColor(Color.Transparent)
                    } else {
                        SolidColor(colors.accentWarm)
                    }
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

            // 只读选择器：右侧 12dp 右尖角，暗示"点开选择"而不是"这里能打字"。
            // 必须同时有 onClick 才画 —— 「只读且不可点」的字段画个箭头等于骗用户去点。
            if (readOnly && enabled && onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(12.dp)
                )
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
                    .height(underlineHeight)
                    .background(underlineColor)
            )
        }

        if (reservesErrorSlot) {
            // 常驻错误槽：占位不跳动，空文案时以 alpha 隐藏
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage.orEmpty(),
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = ArchiveTypeScale.caption,
                color = colors.alertRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (hasError) 1f else 0f)
            )
        } else if (hasError) {
            // 按需渲染：无错误时不占空间
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage.orEmpty(),
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = ArchiveTypeScale.caption,
                color = colors.alertRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
