package com.looseleaf.app.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

    // MOB-P1-74：原先只覆写了 7 个角色，其余全部沿用 Material3 基线 ——
    // surfaceVariant 是薰衣草紫 (#E7E0EC)、onSurfaceVariant 是紫灰 (#49454F)，
    // 于是 Switch 的未选中轨道、Checkbox 的未选中描边、原生 AlertDialog 的正文
    // 全都带上了 M3 默认紫调，与「宣纸 + 朱砂」的纸张美学完全冲突。
    // 这里把整套角色映射到大地色盘。
    val materialColorScheme = remember(darkTheme, highContrast, archiveColors) {
        if (darkTheme) {
        darkColorScheme(
            primary = archiveColors.accentWarm,
            onPrimary = archiveColors.archivePaper,
            primaryContainer = archiveColors.accentWarm.copy(alpha = 0.22f),
            onPrimaryContainer = archiveColors.accentWarm,
            inversePrimary = archiveColors.accentWarm,
            secondary = archiveColors.accentMoss,
            onSecondary = archiveColors.archivePaper,
            secondaryContainer = archiveColors.accentMoss.copy(alpha = 0.22f),
            onSecondaryContainer = archiveColors.accentMoss,
            tertiary = IndigoTertiary,
            onTertiary = archiveColors.archivePaper,
            tertiaryContainer = IndigoTertiary.copy(alpha = 0.22f),
            onTertiaryContainer = IndigoTertiary,
            background = archiveColors.archivePaper,
            onBackground = archiveColors.inkPrimary,
            surface = archiveColors.archiveSubtle,
            onSurface = archiveColors.inkPrimary,
            surfaceVariant = archiveColors.archiveSubtle,
            onSurfaceVariant = archiveColors.inkSecondary,
            surfaceTint = archiveColors.accentWarm,
            inverseSurface = archiveColors.inkPrimary,
            inverseOnSurface = archiveColors.archivePaper,
            error = archiveColors.accentWarm,
            onError = archiveColors.archivePaper,
            errorContainer = archiveColors.accentWarm.copy(alpha = 0.18f),
            onErrorContainer = archiveColors.inkPrimary,
            outline = archiveColors.inkMuted,
            outlineVariant = archiveColors.inkMuted.copy(alpha = 0.45f),
            scrim = Color.Black,
            surfaceBright = archiveColors.archiveSubtle,
            surfaceContainer = archiveColors.archiveSubtle,
            surfaceContainerHigh = archiveColors.archiveSubtle,
            surfaceContainerHighest = archiveColors.archiveSubtle,
            surfaceContainerLow = archiveColors.archivePaper,
            surfaceContainerLowest = archiveColors.archivePaper,
            surfaceDim = archiveColors.archivePaper
        )
    } else {
        lightColorScheme(
            primary = archiveColors.accentWarm,
            onPrimary = archiveColors.archivePaper,
            primaryContainer = archiveColors.accentWarm.copy(alpha = 0.12f),
            onPrimaryContainer = archiveColors.accentWarm,
            inversePrimary = archiveColors.accentWarm,
            secondary = archiveColors.accentMoss,
            onSecondary = archiveColors.archivePaper,
            secondaryContainer = archiveColors.accentMoss.copy(alpha = 0.12f),
            onSecondaryContainer = archiveColors.accentMoss,
            tertiary = IndigoTertiary,
            onTertiary = archiveColors.archivePaper,
            tertiaryContainer = IndigoTertiary.copy(alpha = 0.12f),
            onTertiaryContainer = IndigoTertiary,
            background = archiveColors.archivePaper,
            onBackground = archiveColors.inkPrimary,
            surface = archiveColors.archiveSubtle,
            onSurface = archiveColors.inkPrimary,
            surfaceVariant = archiveColors.archiveSubtle,
            onSurfaceVariant = archiveColors.inkSecondary,
            surfaceTint = archiveColors.accentWarm,
            inverseSurface = archiveColors.inkPrimary,
            inverseOnSurface = archiveColors.archivePaper,
            error = archiveColors.accentWarm,
            onError = archiveColors.archivePaper,
            errorContainer = archiveColors.accentWarm.copy(alpha = 0.12f),
            onErrorContainer = archiveColors.inkPrimary,
            outline = archiveColors.inkMuted,
            outlineVariant = archiveColors.inkMuted.copy(alpha = 0.45f),
            scrim = Color.Black,
            surfaceBright = archiveColors.archivePaper,
            surfaceContainer = archiveColors.archiveSubtle,
            surfaceContainerHigh = archiveColors.archiveSubtle,
            surfaceContainerHighest = archiveColors.archiveSubtle,
            surfaceContainerLow = archiveColors.archivePaper,
            surfaceContainerLowest = archiveColors.archivePaper,
            surfaceDim = archiveColors.archiveSubtle
        )
    }
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
