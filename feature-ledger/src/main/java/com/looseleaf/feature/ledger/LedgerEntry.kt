package com.looseleaf.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looseleaf.feature.ledger.theme.LedgerTheme
import com.looseleaf.feature.ledger.ui.LedgerScreen

/**
 * 账本模块唯一对外暴露的入口 Composable (LedgerEntry)
 * 内部自动包裹 LedgerTheme，确保视觉风格独立通透，与主应用纸墨装饰彻底解耦。
 *
 * [isDarkTheme] 必须由宿主传入「应用自己解析好的外观三态」结果。
 */
@Composable
fun LedgerEntry(
    /** 当前生效的深色外观（由宿主三态 system/paper/darkroom 解析得出，必填） */
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    /** 外部请求切换到的子分段（0 = 流水，1 = 心愿单），消费后由调用方清空 */
    requestedTab: Int? = null,
    onRequestedTabConsumed: (() -> Unit)? = null,
    /** 心愿单子 Tab 内容槽（由宿主注入，连接 SyncManager.wishesFlow） */
    wishlistTabContent: (@Composable (Modifier) -> Unit)? = null,
    /** 心愿单条目数量（用于 Tab 徽标展示） */
    wishlistCount: Int? = null,
    /** 心愿单子 Tab 下拉刷新动作 */
    onRefreshWishlist: (suspend () -> Unit)? = null,
    /** 心愿单子 Tab 右下角新增按钮动作 */
    onAddWishClick: (() -> Unit)? = null,
    /** 顶栏更多菜单中的设置入口 */
    onOpenSettings: (() -> Unit)? = null
) {
    LedgerTheme(darkTheme = isDarkTheme) {
        LedgerScreen(
            modifier = modifier,
            requestedTab = requestedTab,
            onRequestedTabConsumed = onRequestedTabConsumed,
            wishlistTabContent = wishlistTabContent,
            wishlistCount = wishlistCount,
            onRefreshWishlist = onRefreshWishlist,
            onAddWishClick = onAddWishClick,
            onOpenSettings = onOpenSettings
        )
    }
}
