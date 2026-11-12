package com.looseleaf.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一页头 (ArchivePageHeader)
 *
 * 全站一级页面唯一的 56dp 页头：时间流以外的四页共用，标题左、actions 右。
 * 第 2 批把原先重复的 `ArchiveTopAppBar`(core) 与 `LedgerTopAppBar`(feature-ledger)
 * 合并到这里 —— 两者此前只有副标题色和可点副标题的差别，却各维护了一份 56dp 布局。
 *
 * 契约：
 * - 标题：EditorialSerif / Bold / h2(22sp) / 字距 1sp / `inkPrimary`，单行省略
 * - 副标题：EditorialSerif / caption(12sp) / `inkMuted`，单行省略
 * - [onSubtitleClick] 非空时副标题变成可点控件（账本月份选择器），带 2dp 内边距扩大热区，
 *   语义为 `Role.Button`；为空则退化为纯文本
 * - actions 之间固定 4dp 间距；赤陶由各页自己控制，一屏最多出现 3 次
 *
 * 【刻意不提供 windowInsets】两个旧组件都有这个参数却从未在 body 里使用过，
 * 属于零效果的冗余字段。系统栏内边距由宿主 Scaffold 的 `contentWindowInsets` 统一负责。
 *
 * 【颜色来源】用 `LooseLeafTheme.colors` 而不是账本自己的 `LedgerTheme.colors`：
 * `LedgerTheme` 只 provide `LocalLedgerColors`，`LocalArchiveColors` 由外层
 * `LooseLeafTheme` 透传，两者都由同一个三态 `isDarkTheme` 推导，取值一致。
 */
@Composable
fun ArchivePageHeader(
    title: String,
    subtitle: String? = null,
    /** 副标题可点（如账本的月份选择器）；传 null 则退化为纯文本 */
    onSubtitleClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            navigationIcon?.invoke()
            Column {
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = ArchiveTypeScale.h2,
                    letterSpacing = 1.sp,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.caption,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onSubtitleClick != null) {
                            Modifier
                                .clip(ArchiveShapes.small)
                                .clickable(role = Role.Button, onClick = onSubtitleClick)
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            actions()
        }
    }
}
