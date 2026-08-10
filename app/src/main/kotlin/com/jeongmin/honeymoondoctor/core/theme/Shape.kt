package com.jeongmin.honeymoondoctor.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// M3 기본값(4/8/12/16/28dp)보다 한 톤 더 둥글게 — 다이어리 톤에 맞춰 부드럽게, 알약 모양까진 가지 않게.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
