package com.looseleaf.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looseleaf.feature.ledger.theme.LedgerTheme
import com.looseleaf.feature.ledger.ui.LedgerScreen

/**
 * 账本模块唯一对外暴露的入口 Composable (LedgerEntry)
 * 内部自动包裹 LedgerTheme，确保视觉风格独立通透，与主应用纸墨装饰彻底解耦。
 */
@Composable
fun LedgerEntry(
    modifier: Modifier = Modifier,
    /** 需要定位并高亮的装备 id（展台「随行装备」跳转用），消费后由调用方清空 */
    focusGearId: String? = null,
    onFocusConsumed: (() -> Unit)? = null
) {
    LedgerTheme {
        LedgerScreen(
            modifier = modifier,
            focusGearId = focusGearId,
            onFocusConsumed = onFocusConsumed
        )
    }
}
