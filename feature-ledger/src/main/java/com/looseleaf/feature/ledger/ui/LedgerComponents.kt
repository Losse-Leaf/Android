package com.looseleaf.feature.ledger.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveColors
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.core.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.looseleaf.app.ui.components.*
import com.looseleaf.feature.ledger.theme.LedgerTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = LedgerTheme.typography.title,
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            titleContentColor = colors.textPrimary,
            actionIconContentColor = colors.textPrimary
        ),
        modifier = modifier
    )
}

/**
 * 视图切换（账单明细 / 资产大盘）：纯文字标签，选中项加粗 + 2dp 赤陶下划线，无框无底色
 */
@Composable
fun LedgerSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) = ArchiveSegmentedTabs(
    tabs = tabs,
    selectedIndex = selectedIndex,
    onTabSelected = onTabSelected,
    modifier = modifier
)

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) = ArchiveCard(
    modifier = modifier,
    cornerRadius = cornerRadius,
    contentPadding = contentPadding,
    content = content
)

@Composable
fun LedgerDivider(
    modifier: Modifier = Modifier,
    color: Color? = null,
    thickness: Dp = ArchiveLines.hairline
) = ArchiveDivider(
    modifier = modifier,
    color = color,
    thickness = thickness
)

@Composable
fun LedgerEmptyState(
    title: String = "这个月还没有账目",
    subtitle: String? = null,
    icon: Painter? = null,
    imageVector: ImageVector? = null,
    actionText: String? = "记一笔",
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = ArchiveEmptyState(
    title = title,
    subtitle = subtitle,
    icon = icon,
    imageVector = imageVector,
    actionText = actionText,
    onActionClick = onActionClick,
    modifier = modifier
)

/**
 * 纸色月份选择弹层
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerMonthPickerSheet(
    selectedYearMonth: YearMonth,
    onYearMonthSelected: (YearMonth) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LedgerTheme.colors
    var currentYear by remember(selectedYearMonth) { mutableStateOf(selectedYearMonth.year) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { currentYear-- }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "上一年",
                        tint = colors.textPrimary
                    )
                }
                Text(
                    text = "${currentYear} 年",
                    style = LedgerTheme.typography.title,
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = { currentYear++ }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = "下一年",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val months = (1..12).toList()
            for (row in months.chunked(4)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (m in row) {
                        val isSelected = currentYear == selectedYearMonth.year && m == selectedYearMonth.monthValue
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onYearMonthSelected(YearMonth.of(currentYear, m))
                                    onDismissRequest()
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${m}月",
                                    fontFamily = if (isSelected) LedgerTheme.typography.serif else FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (isSelected) colors.accent else colors.textPrimary
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(2.dp)
                                            .background(colors.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) = ArchiveTextButton(
    text = text,
    onClick = onClick,
    enabled = enabled,
    isDestructive = isDestructive,
    modifier = modifier
)

@Composable
fun LedgerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) = ArchivePrimaryButton(
    text = text,
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
)

@Composable
fun LedgerChoiceItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colorDot: Color? = null,
    modifier: Modifier = Modifier
) = ArchiveChoiceItem(
    text = text,
    isSelected = isSelected,
    onClick = onClick,
    colorDot = colorDot,
    modifier = modifier
)

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = ArchiveSectionHeader(
    title = title,
    actionText = actionText,
    onActionClick = onActionClick,
    modifier = modifier
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerSheet(
    onDismissRequest: () -> Unit,
    title: String,
    onCancel: () -> Unit = onDismissRequest,
    cancelText: String = "取消",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isActionEnabled: Boolean = true,
    isActionDestructive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = ArchiveSheet(
    onDismissRequest = onDismissRequest,
    title = title,
    onCancel = onCancel,
    cancelText = cancelText,
    actionText = actionText,
    onAction = onAction,
    isActionEnabled = isActionEnabled,
    isActionDestructive = isActionDestructive,
    modifier = modifier,
    content = content
)

typealias LedgerSnackbarData = ArchiveSnackbarData

@Composable
fun LedgerSnackbar(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = ArchiveSnackbar(
    message = message,
    actionLabel = actionLabel,
    onActionClick = onActionClick,
    modifier = modifier
)

@Composable
fun LedgerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String = "",
    isNumeric: Boolean = false,
    singleLine: Boolean = true,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    modifier: Modifier = Modifier
) = ArchiveField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = placeholder,
    isNumeric = isNumeric,
    singleLine = singleLine,
    trailingText = trailingText,
    trailingContent = trailingContent,
    readOnly = readOnly,
    enabled = enabled,
    onClick = onClick,
    keyboardOptions = keyboardOptions,
    modifier = modifier
)

@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    isNumeric: Boolean = false,
    modifier: Modifier = Modifier
) = ArchiveTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    label = label,
    singleLine = singleLine,
    isNumeric = isNumeric,
    modifier = modifier
)

@Composable
fun LedgerCityPickerField(
    selectedCityCode: String?,
    selectedCityName: String?,
    onCitySelected: (code: String, name: String) -> Unit,
    placeholder: String = "选择城市…",
    modifier: Modifier = Modifier
) = ArchiveCityPickerField(
    selectedCityCode = selectedCityCode,
    selectedCityName = selectedCityName,
    onCitySelected = onCitySelected,
    placeholder = placeholder,
    modifier = modifier
)

@Composable
fun LedgerDateTimeSplitRow(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) = ArchiveDateTimeSplitRow(
    selectedDateTime = selectedDateTime,
    onDateTimeSelected = onDateTimeSelected,
    modifier = modifier
)

@Composable
fun LedgerSkeleton(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) = ArchiveSkeleton(
    modifier = modifier,
    shape = shape
)

@Composable
fun TypeToggleButton(
    text: String,
    isSelected: Boolean,
    isExpense: Boolean,
    onClick: () -> Unit
) = ArchiveTypeToggleButton(
    text = text,
    isSelected = isSelected,
    onClick = onClick
)

@Composable
fun LedgerExtraPill(
    text: String,
    onClick: () -> Unit,
    icon: Painter? = null,
    imageVector: ImageVector? = null,
    highlighted: Boolean = false
) {
    val colors = LedgerTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) colors.accent.copy(alpha = 0.12f) else colors.surfaceElevated)
            .border(
                width = ArchiveLines.hairline,
                color = if (highlighted) colors.accent else colors.borderSubtle,
                shape = RoundedCornerShape(50)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (highlighted) colors.accent else colors.textSecondary
                )
            } else if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (highlighted) colors.accent else colors.textSecondary
                )
            }
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = if (highlighted) colors.accent else colors.textSecondary
            )
        }
    }
}

/**
 * 散页统一的左滑露出操作行 (LedgerSwipeRevealRow)
 *
 * 规范与交互：
 * - 向左滑动条目露出右侧"删除"按钮（砖红低饱和背景与文字，4dp圆角），松手后停留在露出状态。
 * - 用户必须主动点击露出的"删除"按钮才触发实际删除，杜绝滑动误删。
 * - 处于露出状态时，点击前景任意区域或向右滑动会自动回弹收起。
 * - 未滑出状态下，点击条目正常响应 onClick 逻辑。
 */
@Composable
fun LedgerSwipeRevealRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 76.dp,
    deleteLabel: String = "删除",
    backgroundColor: Color = LedgerTheme.colors.background,
    content: @Composable () -> Unit
) = ArchiveSwipeRevealRow(
    onDelete = onDelete,
    modifier = modifier,
    actionWidth = actionWidth,
    deleteLabel = deleteLabel,
    backgroundColor = backgroundColor,
    content = content
)
