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
    /** 相机取景框的纯黑衬底：Preview surface 外的 letterbox 必须是绝对黑，两种模式共用 */
    val cameraViewfinderBg: Color = Color(0xFF000000),
    val isDark: Boolean = false
)

// 暖色棉纸模式 (Paper Mode)
val LightArchiveColors = ArchiveColors(
    archivePaper = Color(0xFFF9F8F5),
    archiveSubtle = Color(0xFFF2F0EB),
    inkPrimary = Color(0xFF181716),
    inkSecondary = Color(0xFF57544E),
    // MOB-P1-73：原 #8C8880 与纸底 #F9F8F5 的对比度仅 3.32:1（AA 需要 4.5:1），
    // 而它承担了全 App 40+ 处 10~11sp 次级文字。调深至 #6F6B63 ≈ 4.9:1。
    inkMuted = Color(0xFF6F6B63),
    borderSubtle = Color(0x14181716),   // rgba(24, 23, 22, 0.08)
    borderDivider = Color(0x1F181716),  // rgba(24, 23, 22, 0.12)
    accentWarm = Color(0xFFA24F2B),     // 暖赭石
    accentMoss = Color(0xFF3F5A4A),     // 苔藓绿
    grainColor = Color(0x08181716),
    frameShadowAmbient = Color(0x1F181716),
    frameShadowSpot = Color(0x2E181716),
    filmMatteBg = Color(0xFFE8E5DF),
    isDark = false
)

// 暗房安全灯模式 (Darkroom Mode)
val DarkArchiveColors = ArchiveColors(
    archivePaper = Color(0xFF141312),
    archiveSubtle = Color(0xFF1D1B19),
    inkPrimary = Color(0xFFEDE8E1),
    inkSecondary = Color(0xFFA8A399),
    // MOB-P1-73：暗房底 #141312 上的对比度由 3.50:1 提升到 ≈ 5.0:1
    inkMuted = Color(0xFF8E8A82),
    borderSubtle = Color(0x14EDE8E1),   // rgba(237, 232, 225, 0.08)
    borderDivider = Color(0x24EDE8E1),  // rgba(237, 232, 225, 0.14)
    accentWarm = Color(0xFFD0603D),     // 暗房安全灯赤红（提亮以满足 4.5:1）
    accentMoss = Color(0xFF6E9479),     // 苔藓绿提亮，暗房底上 ≈ 4.8:1
    grainColor = Color(0x06FFFFFF),
    frameShadowAmbient = Color(0x26000000),
    frameShadowSpot = Color(0x40000000),
    filmMatteBg = Color(0xFF0D0C0B),
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

    // 暗房模式下的同色系提亮版本：深底 (#141312) 上原色对比度不足 3:1
    private val DiningDark = Color(0xFFC97A52)
    private val TransitDark = Color(0xFF7C9CB3)
    private val ShoppingDark = Color(0xFFC0A075)
    private val LeisureDark = Color(0xFFB08CB8)
    private val HousingDark = Color(0xFF6F9478)
    private val StudyDark = Color(0xFF93A2B3)
    private val MedicalDark = Color(0xFFD08888)
    private val OtherDark = Color(0xFFB5AFA3)

    fun colorForCategory(category: String, isDark: Boolean = false): Color {
        return when (category.trim()) {
            "餐饮" -> if (isDark) DiningDark else Dining
            "交通" -> if (isDark) TransitDark else Transit
            "购物", "数码器材" -> if (isDark) ShoppingDark else Shopping
            "娱乐" -> if (isDark) LeisureDark else Leisure
            "住房" -> if (isDark) HousingDark else Housing
            "学习" -> if (isDark) StudyDark else Study
            "医疗" -> if (isDark) MedicalDark else Medical
            else -> if (isDark) OtherDark else Other
        }
    }
}
