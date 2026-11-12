package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一纸色底部抽屉 (ArchiveSheet)
 * - 纯纸色、顶部圆角 12dp (ArchiveShapes.sheetTop)、无投影 (tonalElevation = 0.dp)
 * - 统一标题栏（左"取消"、中单行标题、右实心主操作按钮）
 * - 避让状态栏与软键盘，独立 Window 级全屏遮罩原生覆盖底栏与状态栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSheet(
    onDismissRequest: () -> Unit,
    title: String,
    /** 提交进行中传 { !isSubmitting }：下滑手势 / 遮罩 / 取消按钮统一走这道闸。 */
    dismissEnabled: () -> Boolean = { true },
    onCancel: () -> Unit = onDismissRequest,
    cancelText: String = "取消",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isActionEnabled: Boolean = true,
    isActionDestructive: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ArchiveSheetContent(
        onDismissRequest = { if (dismissEnabled()) onDismissRequest() },
        title = title,
        onCancel = { if (dismissEnabled()) onCancel() },
        cancelText = cancelText,
        actionText = actionText,
        onAction = onAction,
        isActionEnabled = isActionEnabled,
        isActionDestructive = isActionDestructive,
        sheetState = sheetState,
        modifier = modifier,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveSheetContent(
    onDismissRequest: () -> Unit,
    title: String,
    onCancel: () -> Unit = onDismissRequest,
    cancelText: String = "取消",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isActionEnabled: Boolean = true,
    isActionDestructive: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = (screenHeight - statusBarTop - 16.dp).coerceAtLeast(200.dp)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        shape = ArchiveShapes.sheetTop,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp.coerceAtMost(maxSheetHeight), max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ArchiveTextButton(
                    text = cancelText,
                    onClick = onCancel,
                    textColor = colors.inkSecondary
                )
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = ArchiveTypeScale.bodyLarge,
                    color = colors.inkPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                if (!actionText.isNullOrBlank() && onAction != null) {
                    ArchiveSheetActionButton(
                        text = actionText,
                        onClick = onAction,
                        enabled = isActionEnabled,
                        isDestructive = isActionDestructive
                    )
                } else {
                    Spacer(modifier = Modifier.width(56.dp))
                }
            }

            ArchiveDivider()

            content()
        }
    }
}
