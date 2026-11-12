package com.looseleaf.app.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext

val LocalArchiveColors = staticCompositionLocalOf { LightArchiveColors }

/** MOB-P1-74：M3 tertiary 角色的大地色（靛青），与 Web 端 --accent-indigo 同值 */
private val IndigoTertiary = Color(0xFF3B4F6B)

object LooseLeafTheme {
    val colors: ArchiveColors
        @Composable
        @ReadOnlyComposable
        get() = LocalArchiveColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val typeScale: ArchiveTypeScale = ArchiveTypeScale
    val shapes: ArchiveShapes = ArchiveShapes
    val spacing: ArchiveSpacing = ArchiveSpacing
}

/**
 * 把散页大地色盘映射成完整的 Material3 ColorScheme。
 *
 * MOB-P1-74：原先只覆写了 7 个角色，其余全部沿用 Material3 基线 ——
 * surfaceVariant 是薰衣草紫 (#E7E0EC)、onSurfaceVariant 是紫灰 (#49454F)，
 * 于是 Switch 的未选中轨道、Checkbox 的未选中描边、原生 AlertDialog 的正文
 * 全都带上了 M3 默认紫调，与「宣纸 + 朱砂」的纸张美学完全冲突。
 *
 * 这里把整套角色映射到大地色盘，并且**只保留这一份映射**：
 * 全局 [LooseLeafTheme] 与账本 `LedgerTheme` 都调用它，
 * 免得账本自己再映射一遍、又退回 M3 基线变成一页紫。
 */
fun archiveColorScheme(colors: ArchiveColors, darkTheme: Boolean): ColorScheme = if (darkTheme) {
    darkColorScheme(
        primary = colors.accentWarm,
        onPrimary = colors.archivePaper,
        primaryContainer = colors.accentWarm.copy(alpha = 0.22f),
        onPrimaryContainer = colors.accentWarm,
        inversePrimary = colors.accentWarm,
        secondary = colors.accentMoss,
        onSecondary = colors.archivePaper,
        secondaryContainer = colors.accentMoss.copy(alpha = 0.22f),
        onSecondaryContainer = colors.accentMoss,
        tertiary = IndigoTertiary,
        onTertiary = colors.archivePaper,
        tertiaryContainer = IndigoTertiary.copy(alpha = 0.22f),
        onTertiaryContainer = IndigoTertiary,
        background = colors.archivePaper,
        onBackground = colors.inkPrimary,
        surface = colors.archiveSubtle,
        onSurface = colors.inkPrimary,
        surfaceVariant = colors.archiveSubtle,
        onSurfaceVariant = colors.inkSecondary,
        surfaceTint = colors.accentWarm,
        inverseSurface = colors.inkPrimary,
        inverseOnSurface = colors.archivePaper,
        // 破坏性/异常 = 砖红（alertRed），不是赤陶。赤陶是"当前/可操作/主按钮"，
        // 若把 error 也映射成赤陶，Snackbar 报错与"确认"按钮会同一个颜色。
        error = colors.alertRed,
        onError = colors.archivePaper,
        errorContainer = colors.alertRed.copy(alpha = 0.18f),
        onErrorContainer = colors.inkPrimary,
        outline = colors.inkMuted,
        outlineVariant = colors.inkMuted.copy(alpha = 0.45f),
        scrim = Color.Black,
        surfaceBright = colors.archiveSubtle,
        surfaceContainer = colors.archiveSubtle,
        surfaceContainerHigh = colors.archiveSubtle,
        surfaceContainerHighest = colors.archiveSubtle,
        surfaceContainerLow = colors.archivePaper,
        surfaceContainerLowest = colors.archivePaper,
        surfaceDim = colors.archivePaper
    )
} else {
    lightColorScheme(
        primary = colors.accentWarm,
        onPrimary = colors.archivePaper,
        primaryContainer = colors.accentWarm.copy(alpha = 0.12f),
        onPrimaryContainer = colors.accentWarm,
        inversePrimary = colors.accentWarm,
        secondary = colors.accentMoss,
        onSecondary = colors.archivePaper,
        secondaryContainer = colors.accentMoss.copy(alpha = 0.12f),
        onSecondaryContainer = colors.accentMoss,
        tertiary = IndigoTertiary,
        onTertiary = colors.archivePaper,
        tertiaryContainer = IndigoTertiary.copy(alpha = 0.12f),
        onTertiaryContainer = IndigoTertiary,
        background = colors.archivePaper,
        onBackground = colors.inkPrimary,
        surface = colors.archiveSubtle,
        onSurface = colors.inkPrimary,
        surfaceVariant = colors.archiveSubtle,
        onSurfaceVariant = colors.inkSecondary,
        surfaceTint = colors.accentWarm,
        inverseSurface = colors.inkPrimary,
        inverseOnSurface = colors.archivePaper,
        // 破坏性/异常 = 砖红（alertRed），不是赤陶。赤陶是"当前/可操作/主按钮"，
        // 若把 error 也映射成赤陶，Snackbar 报错与"确认"按钮会同一个颜色。
        error = colors.alertRed,
        onError = colors.archivePaper,
        errorContainer = colors.alertRed.copy(alpha = 0.12f),
        onErrorContainer = colors.inkPrimary,
        outline = colors.inkMuted,
        outlineVariant = colors.inkMuted.copy(alpha = 0.45f),
        scrim = Color.Black,
        surfaceBright = colors.archivePaper,
        surfaceContainer = colors.archiveSubtle,
        surfaceContainerHigh = colors.archiveSubtle,
        surfaceContainerHighest = colors.archiveSubtle,
        surfaceContainerLow = colors.archivePaper,
        surfaceContainerLowest = colors.archivePaper,
        surfaceDim = colors.archiveSubtle
    )
}

@Suppress("DEPRECATION")
@Composable
fun LooseLeafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** MOB-P2-45：应用内高对比度开关（Android 未公开系统级高对比度 API） */
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    // AND-7：颜色盘与 M3 ColorScheme 都缓存 —— 原先每次重组都会重建
    // 两个 ColorScheme（几十个 Color 装箱 + 拷贝），下游全部被判为「参数已变」。
    val archiveColors = remember(darkTheme, highContrast) {
        val baseColors = if (darkTheme) DarkArchiveColors else LightArchiveColors
        if (highContrast) {
            baseColors.copy(
                inkMuted = if (darkTheme) Color(0xFFC9C4BA) else Color(0xFF4F4B44),
                borderDivider = if (darkTheme) Color(0x66EDE8E1) else Color(0x4D181716),
                borderSubtle = if (darkTheme) Color(0x40EDE8E1) else Color(0x33181716)
            )
        } else {
            baseColors
        }
    }

    val materialColorScheme = remember(darkTheme, highContrast, archiveColors) {
        archiveColorScheme(archiveColors, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Android 15（targetSdk 35）强制 edge-to-edge，statusBarColor 实际已失效：
                // 用窗口背景托住系统栏区域，暗房模式才不会出现「亮底 + 浅色图标」。
                window.setBackgroundDrawable(
                    ColorDrawable(archiveColors.archivePaper.toArgb())
                )
                window.statusBarColor = archiveColors.archivePaper.toArgb()
                window.navigationBarColor = archiveColors.archivePaper.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // MOB-P2-29：排版对象每次重组都重建（15 个 TextStyle + TextUnit 装箱），
    // 会让所有 MaterialTheme 消费者看到「引用已变」而全树重组。
    val typography = remember(archiveColors) { createArchiveTypography(archiveColors) }

    CompositionLocalProvider(
        LocalArchiveColors provides archiveColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            content = content
        )
    }
}
