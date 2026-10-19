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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalArchiveColors = staticCompositionLocalOf { LightArchiveColors }

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
    content: @Composable () -> Unit
) {
    val archiveColors = if (darkTheme) DarkArchiveColors else LightArchiveColors

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = archiveColors.accentWarm,
            onPrimary = archiveColors.archivePaper,
            background = archiveColors.archivePaper,
            onBackground = archiveColors.inkPrimary,
            surface = archiveColors.archiveSubtle,
            onSurface = archiveColors.inkPrimary,
            outline = archiveColors.borderDivider
        )
    } else {
        lightColorScheme(
            primary = archiveColors.accentWarm,
            onPrimary = archiveColors.archivePaper,
            background = archiveColors.archivePaper,
            onBackground = archiveColors.inkPrimary,
            surface = archiveColors.archiveSubtle,
            onSurface = archiveColors.inkPrimary,
            outline = archiveColors.borderDivider
        )
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

    CompositionLocalProvider(
        LocalArchiveColors provides archiveColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = createArchiveTypography(archiveColors),
            content = content
        )
    }
}
