package com.looseleaf.feature.ledger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looseleaf.feature.ledger.theme.LedgerTheme
import com.looseleaf.feature.ledger.ui.LedgerScreen

/**
 * 账本模块唯一对外暴露的入口 Composable (LedgerEntry)
 * 内部自动包裹 LedgerTheme，确保视觉风格独立通透，与主应用纸墨装饰彻底解耦。
 *
 * [isDarkTheme] 必须由宿主传入「应用自己解析好的外观三态」结果，
 * 不能让账本自己去问 `isSystemInDarkTheme()` —— 否则用户选「暖色棉纸」
 * 而系统是深色时，一进账本就整页变黑（第 1 批修复的 P0 变天 Bug）。
 * 默认值仅为独立预览/测试提供兜底，正式链路一定要显式传。
 */
@Composable
fun LedgerEntry(
    modifier: Modifier = Modifier,
    /** 当前生效的深色外观（由宿主三态 system/paper/darkroom 解析得出） */
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    /** 需要定位并高亮的装备 id（展台「随行装备」跳转用），消费后由调用方清空 */
    focusGearId: String? = null,
    onFocusConsumed: (() -> Unit)? = null
) {
    LedgerTheme(darkTheme = isDarkTheme) {
        LedgerScreen(
            modifier = modifier,
            focusGearId = focusGearId,
            onFocusConsumed = onFocusConsumed
        )
    }
}
