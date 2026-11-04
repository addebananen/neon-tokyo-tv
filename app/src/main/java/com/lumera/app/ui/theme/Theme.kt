package com.lumera.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lumera.app.data.model.ThemeEntity
// 1. Alias the Mobile Theme (For Settings, Dialogs)
import androidx.compose.material3.MaterialTheme as MobileMaterialTheme
import androidx.compose.material3.darkColorScheme as mobileDarkColorScheme
// 2. Alias the TV Theme (For Cards, Home Screen)
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

// CompositionLocal for poster corner style (true = round, false = sharp)
val LocalRoundCorners = staticCompositionLocalOf { true }

// CompositionLocal for hub corner style (true = round, false = sharp)
val LocalHubRoundCorners = staticCompositionLocalOf { true }

// CompositionLocal for the active theme's ID (lets components add theme-specific decoration)
val LocalThemeId = staticCompositionLocalOf { DefaultThemes.NEON_TOKYO.id }

// Secondary text and idle icons, from the theme's muted colour (lilac grey in Neon Tokyo)
val LocalTextMuted = staticCompositionLocalOf { Color(DefaultThemes.NEON_TOKYO.textMutedColor.toInt()) }

// Animation duration for theme transitions
private const val THEME_ANIMATION_DURATION = 400

@Composable
fun LumeraTheme(
    theme: ThemeEntity = DefaultThemes.VOID,
    content: @Composable () -> Unit
) {
    val cinema = theme.id == DefaultThemes.AURORA_CINEMA.id
    // Animate all colors for smooth theme transitions
    val primaryColor by animateColorAsState(
        targetValue = Color(theme.primaryColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION)
    )
    val backgroundColor by animateColorAsState(
        targetValue = Color(theme.backgroundColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION)
    )
    val surfaceColor by animateColorAsState(
        targetValue = Color(theme.surfaceColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION)
    )
    val textColor by animateColorAsState(
        targetValue = Color(theme.textColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION)
    )
    val errorColor by animateColorAsState(
        targetValue = Color(theme.errorColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION)
    )

    val mutedColor by animateColorAsState(
        targetValue = Color(theme.textMutedColor.toInt()),
        animationSpec = tween(THEME_ANIMATION_DURATION), label = "ThemeMuted"
    )

    // A. Configure Mobile Colors
    val mobileScheme = mobileDarkColorScheme(
        primary = primaryColor,
        secondary = primaryColor,
        background = backgroundColor,
        surface = surfaceColor,
        surfaceVariant = surfaceColor,
        onSurfaceVariant = mutedColor,
        onPrimary = Color.Black,
        onBackground = textColor,
        primaryContainer = surfaceColor,
        onPrimaryContainer = textColor,
        secondaryContainer = surfaceColor,
        onSecondaryContainer = textColor,
        surfaceTint = primaryColor,
        outline = mutedColor.copy(alpha = 0.45f),
        onSurface = textColor,
        error = errorColor
    )

    // B. Configure TV Colors (Crucial for LumeraCard Glow)
    val tvScheme = tvDarkColorScheme(
        primary = primaryColor,
        secondary = primaryColor,
        background = backgroundColor,
        surface = surfaceColor,
        surfaceVariant = surfaceColor,
        onSurfaceVariant = mutedColor,
        onPrimary = Color.Black,
        onSurface = textColor,
        error = errorColor
    )

    MobileMaterialTheme(
        colorScheme = mobileScheme,
        typography = if (cinema) CinemaTypography else LumeraTypography
    ) {
        TvMaterialTheme(
            colorScheme = tvScheme,
            typography = if (cinema) androidx.tv.material3.Typography(
                titleLarge = CinemaTypography.titleLarge,
                titleMedium = CinemaTypography.titleMedium,
                bodyLarge = CinemaTypography.bodyLarge,
                labelSmall = CinemaTypography.labelSmall
            ) else androidx.tv.material3.Typography()
        ) {
            CompositionLocalProvider(
                LocalThemeId provides theme.id,
                LocalCinemaStyle provides cinema,
                LocalTextMuted provides mutedColor,
                content = content
            )
        }
    }
}