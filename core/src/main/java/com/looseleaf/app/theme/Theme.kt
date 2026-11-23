package com.looseleaf.app.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalArchiveColors = staticCompositionLocalOf { LightArchiveColors }

/**
 * 散页 · Folio 设计系统入口。
 */
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
    val lines: ArchiveLines = ArchiveLines
    val shadows: ArchiveShadows = ArchiveShadows
    val text: ArchiveTextStyles = ArchiveTextStyles
    val motion: ArchiveMotion = ArchiveMotion
}

/**
 * 将 [ArchiveColors] 映射为 Material3 [ColorScheme]，保证未被自定义组件覆盖的 M3 控件
 * （Switch、DropdownMenu、进度条等）也落在同一套色阶里。
 */
fun archiveColorScheme(colors: ArchiveColors, darkTheme: Boolean): ColorScheme {
    val containerAlpha = if (darkTheme) ArchiveAlpha.containerDark else ArchiveAlpha.containerLight
    return if (darkTheme) {
        darkColorScheme(
            primary = colors.accentWarm,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentWarm.copy(alpha = containerAlpha),
            onPrimaryContainer = colors.accentSmallText,
            inversePrimary = colors.inverseAccent,
            secondary = colors.accentMoss,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accentMoss.copy(alpha = containerAlpha),
            onSecondaryContainer = colors.accentMoss,
            tertiary = colors.tagWish,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.tagWish.copy(alpha = containerAlpha),
            onTertiaryContainer = colors.tagWish,
            background = colors.archivePaper,
            onBackground = colors.inkPrimary,
            surface = colors.archiveCard,
            onSurface = colors.inkPrimary,
            surfaceVariant = colors.archiveSubtle,
            onSurfaceVariant = colors.inkSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseInk,
            error = colors.alertRed,
            onError = colors.onAccent,
            errorContainer = colors.alertRed.copy(alpha = containerAlpha),
            onErrorContainer = colors.alertRed,
            outline = colors.inkMuted,
            outlineVariant = colors.borderDivider,
            scrim = Color.Black,
            surfaceBright = colors.surfaceRaised,
            surfaceContainer = colors.archiveCard,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.archiveSubtle,
            surfaceContainerLow = colors.archiveCard,
            surfaceContainerLowest = colors.archivePaper,
            surfaceDim = colors.archivePaper
        )
    } else {
        lightColorScheme(
            primary = colors.accentWarm,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentWarm.copy(alpha = containerAlpha),
            onPrimaryContainer = colors.accentSmallText,
            inversePrimary = colors.inverseAccent,
            secondary = colors.accentMoss,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accentMoss.copy(alpha = containerAlpha),
            onSecondaryContainer = colors.accentMoss,
            tertiary = colors.tagWish,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.tagWish.copy(alpha = containerAlpha),
            onTertiaryContainer = colors.tagWish,
            background = colors.archivePaper,
            onBackground = colors.inkPrimary,
            surface = colors.archiveCard,
            onSurface = colors.inkPrimary,
            surfaceVariant = colors.archiveSubtle,
            onSurfaceVariant = colors.inkSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseInk,
            error = colors.alertRed,
            onError = colors.onAccent,
            errorContainer = colors.alertRed.copy(alpha = containerAlpha),
            onErrorContainer = colors.alertRed,
            outline = colors.inkMuted,
            outlineVariant = colors.borderDivider,
            scrim = Color.Black,
            surfaceBright = colors.surfaceRaised,
            surfaceContainer = colors.archiveCard,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.archiveSubtle,
            surfaceContainerLow = colors.archiveCard,
            surfaceContainerLowest = colors.archiveCard,
            surfaceDim = colors.archiveSubtle
        )
    }
}

private val ArchiveMaterialShapes = Shapes(
    extraSmall = ArchiveShapes.xs,
    small = ArchiveShapes.sm,
    medium = ArchiveShapes.md,
    large = ArchiveShapes.lg,
    extraLarge = ArchiveShapes.xl
)

/**
 * 高对比度投影：加深次级文字与分隔线，主色与画布保持不变。
 */
private fun ArchiveColors.highContrast(darkTheme: Boolean): ArchiveColors = copy(
    inkSecondary = if (darkTheme) Color(0xFFD9D3C7) else Color(0xFF3A362F),
    inkMuted = if (darkTheme) Color(0xFFC2BBAE) else Color(0xFF4A453D),
    borderSubtle = if (darkTheme) Color(0x33EDE7DB) else Color(0x2E1D1B17),
    borderDivider = if (darkTheme) Color(0x52EDE7DB) else Color(0x471D1B17),
    borderCard = if (darkTheme) Color(0x3DEDE7DB) else Color(0x331D1B17)
)

@Suppress("DEPRECATION")
@Composable
fun LooseLeafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 应用内高对比度开关 */
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val archiveColors = remember(darkTheme, highContrast) {
        val baseColors = if (darkTheme) DarkArchiveColors else LightArchiveColors
        if (highContrast) baseColors.highContrast(darkTheme) else baseColors
    }

    val materialColorScheme = remember(darkTheme, archiveColors) {
        archiveColorScheme(archiveColors, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(archiveColors.archivePaper.toArgb()))
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val typography = remember(archiveColors) { createArchiveTypography(archiveColors) }

    CompositionLocalProvider(LocalArchiveColors provides archiveColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            shapes = ArchiveMaterialShapes,
            content = content
        )
    }
}
