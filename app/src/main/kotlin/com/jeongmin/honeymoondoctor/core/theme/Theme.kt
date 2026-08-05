package com.jeongmin.honeymoondoctor.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = OnDeepNavy,
    secondary = Coral,
    onSecondary = OnDeepNavy,
    tertiary = DeepNavyVariant,
    background = Ivory,
    onBackground = OnIvory,
    surface = Ivory,
    onSurface = OnIvory,
    surfaceVariant = IvoryDim,
    error = CoralDim,
)

private val DarkColors = darkColorScheme(
    primary = Ivory,
    onPrimary = OnIvory,
    secondary = Coral,
    onSecondary = OnIvory,
    tertiary = DeepNavyVariant,
    background = NightBackground,
    onBackground = OnDeepNavy,
    surface = NightSurface,
    onSurface = OnDeepNavy,
    surfaceVariant = NightSurfaceVariant,
    error = Coral,
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
        content = content,
    )
}
