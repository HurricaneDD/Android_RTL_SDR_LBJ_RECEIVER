package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueSoftLight,
    onPrimaryContainer = PrimaryBlueDarkLight,
    secondary = EmeraldGreenLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoftLight,
    onSecondaryContainer = EmeraldGreenLight,
    tertiary = AmberSignalLight,
    onTertiary = Color.White,
    background = BackgroundLightColor,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceSecondaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = RedAlertLight,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDarkTheme,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = PrimaryBlueSoftDarkTheme,
    onPrimaryContainer = PrimaryBlueDarkDarkTheme,
    secondary = EmeraldGreenDarkTheme,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = EmeraldSoftDarkTheme,
    onSecondaryContainer = EmeraldGreenDarkTheme,
    tertiary = AmberSignalDarkTheme,
    onTertiary = Color(0xFF0F172A),
    background = BackgroundDarkColor,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceSecondaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = RedAlertDarkTheme,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = appColors.background.toArgb()
            window.navigationBarColor = appColors.surfaceCard.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
