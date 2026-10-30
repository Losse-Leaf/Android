package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一纸色底部抽屉 (ArchiveSheet)
 * - 纯纸色、顶部圆角 4dp、无投影 (tonalElevation = 0.dp)
 * - 统一标题栏（左"取消"、中标题、右主操作文字）
 * - 避让状态栏与软键盘，最大高度 90%
 * - 支持全局 Root 宿主挂载，遮罩完整覆盖底栏 (LooseLeafBottomBar) 与状态栏，半高与全高均不露底栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSheet(
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
) {
    // 散页底层直接使用 ModalBottomSheet 自带的独立 Window 级全屏遮罩与弹层，
    // 原生覆盖 Scaffold、底栏与状态栏；避免外部宿主状态异步竞态与孤儿 Window 挂死。
    ArchiveSheetContent(
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
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = colors.archivePaper,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    fontSize = 16.sp,
                    color = colors.inkPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (!actionText.isNullOrBlank() && onAction != null) {
                    ArchiveTextButton(
                        text = actionText,
                        onClick = onAction,
                        enabled = isActionEnabled,
                        isDestructive = isActionDestructive
                    )
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            ArchiveDivider()

            content()
        }
    }
}
