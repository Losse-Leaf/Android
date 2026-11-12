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
 *
 * 【刻意不留默认值】这里曾经是 `= isSystemInDarkTheme()`，那正是 P0「变天」Bug 本身 ——
 * 用户选「暖色棉纸」而系统是深色时，一进账本就整页变黑。正确性全靠"每个调用方记得传"，
 * 漏一个就静默退回原 Bug。改成必填后，新增调用点（例如第 2 批宽屏同屏两处账本）
 * 漏传会直接编译失败，而不是上线后才被用户发现。
 */
@Composable
fun LedgerEntry(
    /** 当前生效的深色外观（由宿主三态 system/paper/darkroom 解析得出，必填） */
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
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
