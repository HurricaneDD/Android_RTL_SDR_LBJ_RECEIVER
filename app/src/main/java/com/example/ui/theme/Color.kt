package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Theme Specific Colors
val BackgroundLightColor = Color(0xFFF8FAFC)
val SurfaceCardLight = Color(0xFFFFFFFF)
val SurfaceSecondaryLight = Color(0xFFF1F5F9)
val BorderLightColor = Color(0xFFE2E8F0)
val BorderMediumColor = Color(0xFFCBD5E1)

val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF64748B)
val TextSubtleLight = Color(0xFF94A3B8)

val PrimaryBlueLight = Color(0xFF0284C7)
val PrimaryBlueDarkLight = Color(0xFF0369A1)
val PrimaryBlueSoftLight = Color(0xFFE0F2FE)

val EmeraldGreenLight = Color(0xFF059669)
val EmeraldSoftLight = Color(0xFFD1FAE5)

val BlueUpLight = Color(0xFF2563EB)
val BlueUpSoftLight = Color(0xFFDBEAFE)

val AmberSignalLight = Color(0xFFD97706)
val AmberSoftLight = Color(0xFFFEF3C7)

val RedAlertLight = Color(0xFFDC2626)
val RedSoftLight = Color(0xFFFEE2E2)

val PurpleTechLight = Color(0xFF7C3AED)
val PurpleSoftLight = Color(0xFFEDE9FE)

// Dark Theme Specific Colors
val BackgroundDarkColor = Color(0xFF0B0F19)
val SurfaceCardDark = Color(0xFF161E2E)
val SurfaceSecondaryDark = Color(0xFF1E293B)
val BorderLightDark = Color(0xFF25334D)
val BorderMediumDark = Color(0xFF334155)

val TextPrimaryDark = Color(0xFFF1F5F9)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextMutedDark = Color(0xFF64748B)
val TextSubtleDark = Color(0xFF475569)

val PrimaryBlueDarkTheme = Color(0xFF38BDF8)
val PrimaryBlueDarkDarkTheme = Color(0xFF7DD3FC)
val PrimaryBlueSoftDarkTheme = Color(0xFF0C3558)

val EmeraldGreenDarkTheme = Color(0xFF34D399)
val EmeraldSoftDarkTheme = Color(0xFF064E3B)

val BlueUpDarkTheme = Color(0xFF60A5FA)
val BlueUpSoftDarkTheme = Color(0xFF1E3A8A)

val AmberSignalDarkTheme = Color(0xFFFBBF24)
val AmberSoftDarkTheme = Color(0xFF78350F)

val RedAlertDarkTheme = Color(0xFFF87171)
val RedSoftDarkTheme = Color(0xFF7F1D1D)

val PurpleTechDarkTheme = Color(0xFFA78BFA)
val PurpleSoftDarkTheme = Color(0xFF4C1D95)

data class AppColors(
    val background: Color,
    val surfaceCard: Color,
    val surfaceSecondary: Color,
    val borderLight: Color,
    val borderMedium: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val primaryBlue: Color,
    val primaryBlueDark: Color,
    val primaryBlueSoft: Color,
    val emeraldGreen: Color,
    val emeraldSoft: Color,
    val blueUp: Color,
    val blueUpSoft: Color,
    val amberSignal: Color,
    val amberSoft: Color,
    val redAlert: Color,
    val redSoft: Color,
    val purpleTech: Color,
    val purpleSoft: Color,
    val isDark: Boolean
)

val LightAppColors = AppColors(
    background = BackgroundLightColor,
    surfaceCard = SurfaceCardLight,
    surfaceSecondary = SurfaceSecondaryLight,
    borderLight = BorderLightColor,
    borderMedium = BorderMediumColor,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    textSubtle = TextSubtleLight,
    primaryBlue = PrimaryBlueLight,
    primaryBlueDark = PrimaryBlueDarkLight,
    primaryBlueSoft = PrimaryBlueSoftLight,
    emeraldGreen = EmeraldGreenLight,
    emeraldSoft = EmeraldSoftLight,
    blueUp = BlueUpLight,
    blueUpSoft = BlueUpSoftLight,
    amberSignal = AmberSignalLight,
    amberSoft = AmberSoftLight,
    redAlert = RedAlertLight,
    redSoft = RedSoftLight,
    purpleTech = PurpleTechLight,
    purpleSoft = PurpleSoftLight,
    isDark = false
)

val DarkAppColors = AppColors(
    background = BackgroundDarkColor,
    surfaceCard = SurfaceCardDark,
    surfaceSecondary = SurfaceSecondaryDark,
    borderLight = BorderLightDark,
    borderMedium = BorderMediumDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    textSubtle = TextSubtleDark,
    primaryBlue = PrimaryBlueDarkTheme,
    primaryBlueDark = PrimaryBlueDarkDarkTheme,
    primaryBlueSoft = PrimaryBlueSoftDarkTheme,
    emeraldGreen = EmeraldGreenDarkTheme,
    emeraldSoft = EmeraldSoftDarkTheme,
    blueUp = BlueUpDarkTheme,
    blueUpSoft = BlueUpSoftDarkTheme,
    amberSignal = AmberSignalDarkTheme,
    amberSoft = AmberSoftDarkTheme,
    redAlert = RedAlertDarkTheme,
    redSoft = RedSoftDarkTheme,
    purpleTech = PurpleTechDarkTheme,
    purpleSoft = PurpleSoftDarkTheme,
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val BackgroundLight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.background

val SurfaceCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surfaceCard

val SurfaceSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surfaceSecondary

val BorderLight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.borderLight

val BorderMedium: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.borderMedium

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textMuted

val TextSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textSubtle

val PrimaryBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.primaryBlue

val PrimaryBlueDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.primaryBlueDark

val PrimaryBlueSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.primaryBlueSoft

val EmeraldGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.emeraldGreen

val EmeraldSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.emeraldSoft

val BlueUp: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.blueUp

val BlueUpSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.blueUpSoft

val AmberSignal: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.amberSignal

val AmberSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.amberSoft

val RedAlert: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.redAlert

val RedSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.redSoft

val PurpleTech: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.purpleTech

val PurpleSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.purpleSoft
