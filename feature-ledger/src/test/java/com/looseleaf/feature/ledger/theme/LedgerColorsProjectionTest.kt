package com.looseleaf.feature.ledger.theme

import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账本色盘投影契约测试（纯 JVM，无 Robolectric）
 *
 * 第 1 批把 [LedgerColors] 从「一整套独立 13 字段色盘」改成 [com.looseleaf.app.theme.ArchiveColors]
 * 的只读语义投影：字段名全部保留（33 处调用点零改动），但色彩真源只剩一份。
 *
 * 这个投影是 `get()` 手写的，编译器不会检查"字段有没有接错/接漏"。
 * 第 3 批要清理字段名（background → archivePaper 等），中间任何一次误改都会静默变色。
 * 所以这里把 13 条映射逐条钉死，纸色与暗房各一遍。
 */
class LedgerColorsProjectionTest {

    // ---------- 纸色 ----------

    @Test
    fun light_background_isArchivePaper() =
        assertEquals(LightArchiveColors.archivePaper, LightLedgerColors.background)

    @Test
    fun light_surface_isArchivePaper() =
        assertEquals(LightArchiveColors.archivePaper, LightLedgerColors.surface)

    @Test
    fun light_surfaceElevated_isArchiveSubtle() =
        assertEquals(LightArchiveColors.archiveSubtle, LightLedgerColors.surfaceElevated)

    @Test
    fun light_textPrimary_isInkPrimary() =
        assertEquals(LightArchiveColors.inkPrimary, LightLedgerColors.textPrimary)

    @Test
    fun light_textSecondary_isInkSecondary() =
        assertEquals(LightArchiveColors.inkSecondary, LightLedgerColors.textSecondary)

    @Test
    fun light_textMuted_isInkMuted() =
        assertEquals(LightArchiveColors.inkMuted, LightLedgerColors.textMuted)

    @Test
    fun light_border_isBorderSubtle() =
        assertEquals(LightArchiveColors.borderSubtle, LightLedgerColors.border)

    @Test
    fun light_borderSubtle_isBorderSubtle() =
        assertEquals(LightArchiveColors.borderSubtle, LightLedgerColors.borderSubtle)

    @Test
    fun light_accent_isAccentWarm() =
        assertEquals(LightArchiveColors.accentWarm, LightLedgerColors.accent)

    @Test
    fun light_expense_isInkPrimary() =
        assertEquals(LightArchiveColors.inkPrimary, LightLedgerColors.expense)

    @Test
    fun light_income_isAccentMoss() =
        assertEquals(LightArchiveColors.accentMoss, LightLedgerColors.income)

    @Test
    fun light_alertRed_isAlertRed() =
        assertEquals(LightArchiveColors.alertRed, LightLedgerColors.alertRed)

    @Test
    fun light_isDark_isFalse() =
        assertEquals(LightArchiveColors.isDark, LightLedgerColors.isDark)

    // ---------- 暗房 ----------

    @Test
    fun dark_background_isArchivePaper() =
        assertEquals(DarkArchiveColors.archivePaper, DarkLedgerColors.background)

    @Test
    fun dark_surface_isArchivePaper() =
        assertEquals(DarkArchiveColors.archivePaper, DarkLedgerColors.surface)

    @Test
    fun dark_surfaceElevated_isArchiveSubtle() =
        assertEquals(DarkArchiveColors.archiveSubtle, DarkLedgerColors.surfaceElevated)

    @Test
    fun dark_textPrimary_isInkPrimary() =
        assertEquals(DarkArchiveColors.inkPrimary, DarkLedgerColors.textPrimary)

    @Test
    fun dark_textSecondary_isInkSecondary() =
        assertEquals(DarkArchiveColors.inkSecondary, DarkLedgerColors.textSecondary)

    @Test
    fun dark_textMuted_isInkMuted() =
        assertEquals(DarkArchiveColors.inkMuted, DarkLedgerColors.textMuted)

    @Test
    fun dark_border_isBorderSubtle() =
        assertEquals(DarkArchiveColors.borderSubtle, DarkLedgerColors.border)

    @Test
    fun dark_borderSubtle_isBorderSubtle() =
        assertEquals(DarkArchiveColors.borderSubtle, DarkLedgerColors.borderSubtle)

    @Test
    fun dark_accent_isAccentWarm() =
        assertEquals(DarkArchiveColors.accentWarm, DarkLedgerColors.accent)

    @Test
    fun dark_expense_isInkPrimary() =
        assertEquals(DarkArchiveColors.inkPrimary, DarkLedgerColors.expense)

    @Test
    fun dark_income_isAccentMoss() =
        assertEquals(DarkArchiveColors.accentMoss, DarkLedgerColors.income)

    @Test
    fun dark_alertRed_isAlertRed() =
        assertEquals(DarkArchiveColors.alertRed, DarkLedgerColors.alertRed)

    @Test
    fun dark_isDark_isTrue() =
        assertEquals(DarkArchiveColors.isDark, DarkLedgerColors.isDark)

    /** 防御：投影指向的真源必须是各自那一套，而不是两个都指到 Light（复制粘贴最常见的错） */
    @Test
    fun darkPaletteIsNotLightPalette() {
        assertEquals(DarkArchiveColors, DarkLedgerColors.archive)
        assertEquals(LightArchiveColors, LightLedgerColors.archive)
        assertTrue(DarkLedgerColors.background != LightLedgerColors.background)
    }
}
