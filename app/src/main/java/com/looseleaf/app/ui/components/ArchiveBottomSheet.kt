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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.borderDivider)
            )
        }
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
                        text = "取消",
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
                    color = colors.inkPrimary
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
