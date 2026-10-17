package com.looseleaf.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 散页 (Loose Leaf) · 自然大地色盘 (Earthy Palette) 跨端实现
 * 严格遵循 docs/ecosystem.md §4.2 与 docs/web.md §2.2 / §2.3 规范定义
 */
@Immutable
data class ArchiveColors(
    val archivePaper: Color,
    val archiveSubtle: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val borderSubtle: Color,
    val borderDivider: Color,
    val accentWarm: Color,
    val accentMoss: Color,
    val grainColor: Color,
    val frameShadowAmbient: Color,
    val frameShadowSpot: Color,
    val filmMatteBg: Color,
    val featuredGold: Color,
    val featuredGoldBg: Color,
    val featuredGoldBorder: Color,
    val isDark: Boolean = false
)

// 暖色棉纸模式 (Paper Mode)
val LightArchiveColors = ArchiveColors(
    archivePaper = Color(0xFFF9F8F5),
    archiveSubtle = Color(0xFFF2F0EB),
    inkPrimary = Color(0xFF181716),
    inkSecondary = Color(0xFF57544E),
    inkMuted = Color(0xFF8C8880),
    borderSubtle = Color(0x14181716),   // rgba(24, 23, 22, 0.08)
    borderDivider = Color(0x1F181716),  // rgba(24, 23, 22, 0.12)
    accentWarm = Color(0xFFA24F2B),     // 暖赭石
    accentMoss = Color(0xFF3F5A4A),     // 苔藓绿
    grainColor = Color(0x08181716),
    frameShadowAmbient = Color(0x1F181716),
    frameShadowSpot = Color(0x2E181716),
    filmMatteBg = Color(0xFFE8E5DF),
    featuredGold = Color(0xFFD48806),
    featuredGoldBg = Color(0xFFFFF7E6),
    featuredGoldBorder = Color(0xFFFFD591),
    isDark = false
)

// 暗房安全灯模式 (Darkroom Mode)
val DarkArchiveColors = ArchiveColors(
    archivePaper = Color(0xFF141312),
    archiveSubtle = Color(0xFF1D1B19),
    inkPrimary = Color(0xFFEDE8E1),
    inkSecondary = Color(0xFFA8A399),
    inkMuted = Color(0xFF6E6A62),
    borderSubtle = Color(0x14EDE8E1),   // rgba(237, 232, 225, 0.08)
    borderDivider = Color(0x24EDE8E1),  // rgba(237, 232, 225, 0.14)
    accentWarm = Color(0xFFC85635),     // 暗房安全灯赤红
    accentMoss = Color(0xFF507661),
    grainColor = Color(0x06FFFFFF),
    frameShadowAmbient = Color(0x26000000),
    frameShadowSpot = Color(0x40000000),
    filmMatteBg = Color(0xFF0D0C0B),
    featuredGold = Color(0xFFFFB84D),
    featuredGoldBg = Color(0x33D48806),
    featuredGoldBorder = Color(0x66D48806),
    isDark = true
)

// 支出类目光谱色 (Earthy Spectrum) - docs/web.md §2.3
object SpectrumColors {
    val Dining = Color(0xFFA24F2B)      // 餐饮 - 暖赭石
    val Transit = Color(0xFF4A6B82)     // 交通 - 青石灰
    val Shopping = Color(0xFF8C6D46)    // 购物 - 旧皮褐
    val Leisure = Color(0xFF7A5980)     // 娱乐 - 暮紫
    val Housing = Color(0xFF3F5A4A)     // 住房 - 苔藓绿
    val Study = Color(0xFF5C6773)       // 学习 - 墨色
    val Medical = Color(0xFFB35C5C)     // 医疗 - 枯叶红
    val Other = Color(0xFF8A857B)       // 其他 - 米灰

    fun colorForCategory(category: String): Color {
        return when (category.trim()) {
            "餐饮" -> Dining
            "交通" -> Transit
            "购物", "数码器材" -> Shopping
            "娱乐" -> Leisure
            "住房" -> Housing
            "学习" -> Study
            "医疗" -> Medical
            else -> Other
        }
    }
}
