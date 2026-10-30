package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

/**
 * 散页自研纸本文稿底部抽屉 (ArchiveBottomSheet)
 * 彻底替换 Android 原生 AlertDialog 用于表单输入场景，提供自适应软键盘推升、稿纸折痕与大拇指黄金热区交互
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
    val colors = LooseLeafTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { ArchiveDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // 顶栏：取消 / 标题 / 提交操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.archive_bottom_sheet_text_01),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.5.sp,
                        color = colors.inkMuted
                    )
                }

                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp,
                    color = colors.inkPrimary,
                    // MOB-P1-82：标题是动态拼接的（"修订造物 · xxx"），
                    // 不限宽会把右侧「保存」按钮挤成 0 宽 —— 主操作直接不可达。
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                if (actionButtonText != null && onActionClick != null) {
                    TextButton(
                        onClick = onActionClick,
                        enabled = isActionEnabled,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = actionButtonText,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isActionEnabled) colors.accentWarm else colors.inkMuted.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 滚动可推升正文
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
