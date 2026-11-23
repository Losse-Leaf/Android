package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 底部抽屉 (ArchiveSheet)
 * - 画布色纸面、30dp 顶部圆角、顶部一枚 36×4 的拉手
 * - 统一标题栏：左「取消」（次要色文字按钮）、中衬线标题、右朱砂胶囊主动作
 * - 避让状态栏与软键盘；独立 Window 级遮罩覆盖底栏与状态栏
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
    val maxSheetHeight = (screenHeight - statusBarTop - 12.dp).coerceAtLeast(200.dp)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        contentColor = colors.inkPrimary,
        scrimColor = colors.scrim,
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
            ArchiveSheetGrip()
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
                    style = ArchiveTextStyles.sheetTitle,
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
                    Spacer(modifier = Modifier.width(64.dp))
                }
            }

            ArchiveDivider()

            content()
        }
    }
}

/**
 * 抽屉拉手：36×4 的墨色短条，透明度 18%。
 */
@Composable
fun ArchiveSheetGrip(modifier: Modifier = Modifier) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(ArchiveShapes.full)
                .background(colors.inkPrimary.copy(alpha = 0.18f))
        )
    }
}
