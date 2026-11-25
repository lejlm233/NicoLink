package com.filmlens.nicolink.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal 提供当前主题色板
 */
val LocalLazyFilmColors = staticCompositionLocalOf { LazyFilmColors.Dark }

/**
 * CompositionLocal 提供触感反馈开关状态
 */
val LocalHapticEnabled = staticCompositionLocalOf { true }

/**
 * CompositionLocal 提供快门声开关状态（关闭=拍照不播自定义快门音）
 */
val LocalShutterSoundEnabled = staticCompositionLocalOf { true }

@Composable
fun LazyFilmTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) LazyFilmColors.Dark else LazyFilmColors.Light

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brandPrimary,
            onPrimary = colors.textInverse,
            secondary = colors.brandLight1,
            onSecondary = colors.textInverse,
            tertiary = colors.brandLight2,
            background = colors.bgPrimary,
            onBackground = colors.textPrimary,
            surface = colors.bgCard,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderDefault,
            outlineVariant = colors.borderDivider,
        )
    } else {
        lightColorScheme(
            primary = colors.brandPrimary,
            onPrimary = colors.textInverse,
            secondary = colors.brandLight1,
            onSecondary = colors.textInverse,
            tertiary = colors.brandLight2,
            background = colors.bgPrimary,
            onBackground = colors.textPrimary,
            surface = colors.bgCard,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderDefault,
            outlineVariant = colors.borderDivider,
        )
    }

    CompositionLocalProvider(LocalLazyFilmColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LazyFilmTypography,
            content = content
        )
    }
}
