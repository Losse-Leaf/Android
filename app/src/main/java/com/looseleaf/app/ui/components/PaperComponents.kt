package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 兼容旧入口：Paper* 组件已统一收敛并委托至 :core 的 Archive* 设计系统，
 * 消除双套组件、散乱圆角与描边输入框混用问题。
 */

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveSubtle
    val border = borderColor ?: colors.borderDivider
    val shape = remember(cornerRadius) {
        if (cornerRadius == 12.dp) ArchiveShapes.medium else RoundedCornerShape(cornerRadius)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, shape)
            .border(ArchiveLines.hairline, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun TerracottaStampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp
) {
    val colors = LooseLeafTheme.colors
    val haptics = LocalHapticFeedback.current

    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = ArchiveShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentWarm,
            contentColor = colors.archivePaper,
            disabledContainerColor = colors.archiveSubtle,
            disabledContentColor = colors.inkMuted.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
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
            fontWeight = FontWeight.SemiBold,
            fontSize = ArchiveTypeScale.bodyLarge,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun PaperBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    textColor: Color? = null
) {
    val colors = LooseLeafTheme.colors
    val bg = containerColor ?: colors.archiveSubtle
    val fg = textColor ?: colors.inkSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ArchiveShapes.small)
            .background(bg, ArchiveShapes.small)
            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = ArchiveTypeScale.micro,
            letterSpacing = 0.5.sp,
            color = fg
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun PaperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    isUnderline: Boolean = true,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) = ArchiveField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = placeholder,
    singleLine = singleLine,
    enabled = enabled,
    trailingContent = trailingIcon,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    modifier = modifier
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun PaperTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    minHeight: Dp = 96.dp,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) = ArchiveField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = placeholder,
    singleLine = false,
    minLines = 3,
    enabled = enabled,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    modifier = modifier
)

@Composable
fun PaperCityPickerField(
    selectedCityCode: String?,
    selectedCityName: String?,
    onCitySelected: (code: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "选择城市…"
) = ArchiveCityPickerField(
    selectedCityCode = selectedCityCode,
    selectedCityName = selectedCityName,
    onCitySelected = onCitySelected,
    label = label,
    placeholder = placeholder,
    modifier = modifier
)

@Composable
fun PaperDateTimeSplitRow(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) = ArchiveDateTimeSplitRow(
    selectedDateTime = selectedDateTime,
    onDateTimeSelected = onDateTimeSelected,
    modifier = modifier
)

@Composable
fun PaperLocationField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "具体地点",
    placeholder: String = "街巷或地标注记（选填）"
) = ArchiveField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = placeholder,
    singleLine = true,
    modifier = modifier
)

@Composable
fun <T> PaperSelectField(
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "请选择…"
) = ArchiveSelectField(
    selectedValue = selectedValue,
    options = options,
    onValueSelected = onSelected,
    label = label,
    placeholder = placeholder,
    modifier = modifier
)
