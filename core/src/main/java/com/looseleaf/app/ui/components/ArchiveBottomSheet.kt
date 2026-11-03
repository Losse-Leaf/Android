package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.core.R

@Composable
fun ArchiveDragHandle(
    modifier: Modifier = Modifier,
    color: Color = LooseLeafTheme.colors.inkMuted.copy(alpha = 0.35f)
) {
    Box(
        modifier = modifier
            .padding(vertical = 10.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(ArchiveShapes.full)
            .background(color)
    )
}

/**
 * 兼容旧入口：内部完全委托至统一的 ArchiveSheet，保证全站底部抽屉顶栏、圆角与按钮完全一致
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    isActionEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ArchiveSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        title = title,
        cancelText = stringResource(R.string.archive_bottom_sheet_text_01),
        actionText = actionButtonText,
        onAction = onActionClick,
        isActionEnabled = isActionEnabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            content()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
