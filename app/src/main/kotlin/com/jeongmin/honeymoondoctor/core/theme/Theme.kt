package com.jeongmin.honeymoondoctor.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TerracottaClay,
    onPrimary = OnTerracottaClay,
    primaryContainer = TerracottaClayContainer,
    onPrimaryContainer = OnTerracottaClayContainer,
    secondary = DustyRose,
    onSecondary = OnDustyRose,
    secondaryContainer = DustyRoseContainer,
    onSecondaryContainer = OnDustyRoseContainer,
    tertiary = SagePine,
    onTertiary = OnSagePine,
    tertiaryContainer = SagePineContainer,
    onTertiaryContainer = OnSagePineContainer,
    background = WarmPaper,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSand,
    onSurfaceVariant = OnWarmSand,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = WarmError,
    onError = OnWarmError,
    errorContainer = WarmErrorContainer,
    onErrorContainer = OnWarmErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = NightTerracotta,
    onPrimary = OnNightTerracotta,
    primaryContainer = NightTerracottaContainer,
    onPrimaryContainer = OnNightTerracottaContainer,
    secondary = NightRose,
    onSecondary = OnNightRose,
    secondaryContainer = NightRoseContainer,
    onSecondaryContainer = OnNightRoseContainer,
    tertiary = NightSage,
    onTertiary = OnNightSage,
    tertiaryContainer = NightSageContainer,
    onTertiaryContainer = OnNightSageContainer,
    background = NightPlum,
    onBackground = OnNightPlum,
    surface = NightSurface,
    onSurface = OnNightPlum,
    surfaceVariant = NightSand,
    onSurfaceVariant = OnNightSand,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    error = NightError,
    onError = OnNightError,
    errorContainer = NightErrorContainer,
    onErrorContainer = OnNightErrorContainer,
)

@Composable
fun HoneymoonDoctorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HoneymoonTypography,
        shapes = AppShapes,
        content = content,
    )
}
