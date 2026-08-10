package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** 카드가 화면에서 맡는 역할. 화면마다 제각각이던 primaryContainer/errorContainer/surfaceVariant 분기를 여기 하나로 모은다. */
enum class CardTone {
    Neutral,
    Highlight,
    Warn,
    Done,
}

@Composable
@ReadOnlyComposable
fun CardTone.containerColor(): Color = when (this) {
    CardTone.Neutral -> MaterialTheme.colorScheme.surface
    CardTone.Highlight -> MaterialTheme.colorScheme.primaryContainer
    CardTone.Warn -> MaterialTheme.colorScheme.errorContainer
    CardTone.Done -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
@ReadOnlyComposable
fun CardTone.contentColor(): Color = when (this) {
    CardTone.Neutral -> MaterialTheme.colorScheme.onSurface
    CardTone.Highlight -> MaterialTheme.colorScheme.onPrimaryContainer
    CardTone.Warn -> MaterialTheme.colorScheme.onErrorContainer
    CardTone.Done -> MaterialTheme.colorScheme.onSurfaceVariant
}
