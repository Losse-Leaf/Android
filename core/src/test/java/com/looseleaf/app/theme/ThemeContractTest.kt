package com.looseleaf.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题契约测试（纯 JVM，无 Robolectric）
 *
 * 锁死「配色必须满足 WCAG 2.1 AA 正文对比度 4.5:1」这条硬约束。
 * 第 1 批修掉的四个字段（inkMuted / tagProject / tagWish / alertRed）此前全部不达标，
 * 而颜色是最容易被"顺手调一下好看点"改回去的东西 —— 没有断言，下一批还会再犯。
 *
 * 计算口径严格按 WCAG 2.1：
 * - 相对亮度 L = 0.2126R + 0.7152G + 0.0722B，通道先做 sRGB 反伽马
 * - 对比度 = (L1 + 0.05) / (L2 + 0.05)，L1 为较亮的一方
 * - 半透明前景必须**先做 alpha 合成再算**（直接拿原始值算会得到错误的 4.70 之类）
 *
 * 断言矩阵：前景 {inkMuted, tagProject, tagWish, alertRed, accentSmallText}
 *         × 背景 {canvas, surface} × 主题 {纸, 暗房}，另加两组：
 * - accentSmallText × 自身 12% 淡底（全站选中态最常见的组合：12% 赤陶淡底 + 赤陶小字）
 * - accentPress × 其上文字（按下瞬间的文字可读性，见下）
 *
 * 【只记录、不硬断言】8% 淡底实测：纸 inkMuted 4.39 / tagProject 4.74 / tagWish 5.03 /
 * alertRed 4.74 / accentSmallText 5.49；暗房均 > 4.9。8% 正好卡在 4.5 附近，
 * 四舍五入误差会让测试偶发红，故不写成断言。
 *
 * 【为什么 tagProject / alertRed 没有"自身 12% 淡底"断言】
 * 全仓检索确认这两个颜色**从不会压在自己的淡底上**：`tagXxx.copy(alpha=…)` 零引用，
 * `alertRed.copy(alpha=0.12f)` 只有一处（ArchiveSwipeRevealRow 的删除背景，其上文字是
 * archivePaper 而不是 alertRed）。硬断言不存在的组合只会逼着把令牌改黑，
 * 故只对真实存在的 accentSmallText × 淡底设断言。若将来真要用，纸面 tagProject 4.48 /
 * alertRed 4.47 会红 —— 那时应当调令牌，而不是删测试。
 */
class ThemeContractTest {

    private companion object {
        /** WCAG AA 正文最小对比度 */
        const val AA_NORMAL = 4.5
    }

    // ---------- WCAG 2.1 公式 ----------

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 把 [foreground] 以 [alpha] 叠在 [background] 上，得到实际观感色 */
    private fun composite(foreground: Color, alpha: Float, background: Color): Color = Color(
        red = foreground.red * alpha + background.red * (1f - alpha),
        green = foreground.green * alpha + background.green * (1f - alpha),
        blue = foreground.blue * alpha + background.blue * (1f - alpha),
        alpha = 1f
    )

    private fun assertAa(label: String, text: Color, background: Color) {
        val ratio = contrastRatio(text, background)
        assertTrue(
            "$label 对比度 ${"%.2f".format(ratio)}:1 未达 WCAG AA（需 >= $AA_NORMAL:1）",
            ratio >= AA_NORMAL
        )
    }

    // ========== 纸色矩阵：5 前景 × {canvas, surface} ==========

    @Test fun paper_inkMuted_onCanvas_meetsAa() =
        assertAa("纸 inkMuted/canvas", LightArchiveColors.inkMuted, LightArchiveColors.archivePaper)

    @Test fun paper_inkMuted_onSurface_meetsAa() =
        assertAa("纸 inkMuted/surface", LightArchiveColors.inkMuted, LightArchiveColors.archiveSubtle)

    @Test fun paper_tagProject_onCanvas_meetsAa() =
        assertAa("纸 tagProject/canvas", LightArchiveColors.tagProject, LightArchiveColors.archivePaper)

    @Test fun paper_tagProject_onSurface_meetsAa() =
        assertAa("纸 tagProject/surface", LightArchiveColors.tagProject, LightArchiveColors.archiveSubtle)

    @Test fun paper_tagWish_onCanvas_meetsAa() =
        assertAa("纸 tagWish/canvas", LightArchiveColors.tagWish, LightArchiveColors.archivePaper)

    @Test fun paper_tagWish_onSurface_meetsAa() =
        assertAa("纸 tagWish/surface", LightArchiveColors.tagWish, LightArchiveColors.archiveSubtle)

    @Test fun paper_alertRed_onCanvas_meetsAa() =
        assertAa("纸 alertRed/canvas", LightArchiveColors.alertRed, LightArchiveColors.archivePaper)

    @Test fun paper_alertRed_onSurface_meetsAa() =
        assertAa("纸 alertRed/surface", LightArchiveColors.alertRed, LightArchiveColors.archiveSubtle)

    @Test fun paper_accentSmallText_onCanvas_meetsAa() = assertAa(
        "纸 accentSmallText/canvas",
        LightArchiveColors.accentSmallText, LightArchiveColors.archivePaper
    )

    @Test fun paper_accentSmallText_onSurface_meetsAa() = assertAa(
        "纸 accentSmallText/surface",
        LightArchiveColors.accentSmallText, LightArchiveColors.archiveSubtle
    )

    // ========== 暗房矩阵：5 前景 × {canvas, surface} ==========

    @Test fun dark_inkMuted_onCanvas_meetsAa() =
        assertAa("暗房 inkMuted/canvas", DarkArchiveColors.inkMuted, DarkArchiveColors.archivePaper)

    @Test fun dark_inkMuted_onSurface_meetsAa() =
        assertAa("暗房 inkMuted/surface", DarkArchiveColors.inkMuted, DarkArchiveColors.archiveSubtle)

    @Test fun dark_tagProject_onCanvas_meetsAa() =
        assertAa("暗房 tagProject/canvas", DarkArchiveColors.tagProject, DarkArchiveColors.archivePaper)

    @Test fun dark_tagProject_onSurface_meetsAa() =
        assertAa("暗房 tagProject/surface", DarkArchiveColors.tagProject, DarkArchiveColors.archiveSubtle)

    @Test fun dark_tagWish_onCanvas_meetsAa() =
        assertAa("暗房 tagWish/canvas", DarkArchiveColors.tagWish, DarkArchiveColors.archivePaper)

    @Test fun dark_tagWish_onSurface_meetsAa() =
        assertAa("暗房 tagWish/surface", DarkArchiveColors.tagWish, DarkArchiveColors.archiveSubtle)

    @Test fun dark_alertRed_onCanvas_meetsAa() =
        assertAa("暗房 alertRed/canvas", DarkArchiveColors.alertRed, DarkArchiveColors.archivePaper)

    @Test fun dark_alertRed_onSurface_meetsAa() =
        assertAa("暗房 alertRed/surface", DarkArchiveColors.alertRed, DarkArchiveColors.archiveSubtle)

    @Test fun dark_accentSmallText_onCanvas_meetsAa() = assertAa(
        "暗房 accentSmallText/canvas",
        DarkArchiveColors.accentSmallText, DarkArchiveColors.archivePaper
    )

    @Test fun dark_accentSmallText_onSurface_meetsAa() = assertAa(
        "暗房 accentSmallText/surface",
        DarkArchiveColors.accentSmallText, DarkArchiveColors.archiveSubtle
    )

    // ========== 选中态小字：accentSmallText 压在自身 12% 淡底上 ==========
    // 这是本轮唯一能防住"选中态小字看不清"的护栏：
    // 年份 chip / 分段计数徽标 / 筛选胶囊 全是这个组合。

    @Test fun paper_accentSmallText_onOwn12pctTint_meetsAa() = assertAa(
        "纸 accentSmallText/自身 12% 淡底",
        LightArchiveColors.accentSmallText,
        composite(LightArchiveColors.accentSmallText, 0.12f, LightArchiveColors.archivePaper)
    )

    @Test fun dark_accentSmallText_onOwn12pctTint_meetsAa() = assertAa(
        "暗房 accentSmallText/自身 12% 淡底",
        DarkArchiveColors.accentSmallText,
        composite(DarkArchiveColors.accentSmallText, 0.12f, DarkArchiveColors.archivePaper)
    )

    // ========== 按下态：按钮文字压在 accentPress 上 ==========
    // 规则：按下时相对亮度朝"与文字相反方向"移动 —— 浅底按下更深、深底按下更亮。
    // 暗房曾经写 accentWarm.copy(alpha = 0.85f)，合成到 #181512 得 #B45537，
    // 文字 #181512 压上去只有 3.72:1，按下瞬间字会糊。

    @Test fun paper_pressedButtonLabel_meetsAa() = assertAa(
        "纸 按下态按钮文字/底色",
        LightArchiveColors.archivePaper,
        composite(LightArchiveColors.accentPress, LightArchiveColors.accentPress.alpha, LightArchiveColors.archivePaper)
    )

    @Test fun dark_pressedButtonLabel_meetsAa() = assertAa(
        "暗房 按下态按钮文字/底色",
        DarkArchiveColors.archivePaper,
        composite(DarkArchiveColors.accentPress, DarkArchiveColors.accentPress.alpha, DarkArchiveColors.archivePaper)
    )

    // ========== 选中态小字：accentSmallText 压在自身 14% 淡底上 ==========
    // ArchiveTabs 的分段计数徽标用 14% 赤陶淡底，比 12% 更浓，单独钉一条。

    @Test fun paper_accentSmallText_onOwn14pctTint_meetsAa() = assertAa(
        "纸 accentSmallText/自身 14% 淡底",
        LightArchiveColors.accentSmallText,
        composite(LightArchiveColors.accentSmallText, 0.14f, LightArchiveColors.archivePaper)
    )

    @Test fun dark_accentSmallText_onOwn14pctTint_meetsAa() = assertAa(
        "暗房 accentSmallText/自身 14% 淡底",
        DarkArchiveColors.accentSmallText,
        composite(DarkArchiveColors.accentSmallText, 0.14f, DarkArchiveColors.archivePaper)
    )

    // ========== ColorScheme 容器透明度：纸 12% / 暗 22% 刻意不对称 ==========
    // 这两套数值曾被读反（以为暗房是 12%），据此发出过「纸面不用改」的错误结论。
    // 断言把分支钉死：谁再把透明度统一成同一个数，先解释清楚为什么可以。
    // delta 取 0.002f 而非 0.001f：Compose Color 的 alpha 底层是 8-bit 通道，
    // 0.12f 会被量化成 31/255 = 0.12156863（差 0.0016），0.22f 量化成 56/255（差 0.0004）。
    // 两分支相差 0.1，这个容差仍能可靠区分，不会放过「顺手统一」。

    @Test fun paper_primaryContainer_is12pct() = assertEquals(
        0.12f,
        archiveColorScheme(LightArchiveColors, darkTheme = false).primaryContainer.alpha,
        0.002f
    )

    @Test fun dark_primaryContainer_is22pct() = assertEquals(
        0.22f,
        archiveColorScheme(DarkArchiveColors, darkTheme = true).primaryContainer.alpha,
        0.002f
    )

    @Test fun paper_secondaryContainer_is12pct() = assertEquals(
        0.12f,
        archiveColorScheme(LightArchiveColors, darkTheme = false).secondaryContainer.alpha,
        0.002f
    )

    @Test fun dark_secondaryContainer_is22pct() = assertEquals(
        0.22f,
        archiveColorScheme(DarkArchiveColors, darkTheme = true).secondaryContainer.alpha,
        0.002f
    )
}
