package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 로딩 중 전면 스피너 대신 쓰는 뼈대 화면 재료(백로그 3-1f). 화면이 비어 있다가
 * 한꺼번에 채워지는 깜빡임을 줄이는 것이 목적이라, 최종 콘텐츠와 대략 같은
 * 크기·배치의 사각형을 먼저 보여준다. 각 화면은 [SkeletonBar](텍스트 한 줄)와
 * [SkeletonBlock](카드·칩·지도 같은 면적 요소) 두 조각을 자기 콘텐츠 모양에 맞게
 * 배치해 스켈레톤을 구성한다 — 화면마다 실제 레이아웃이 달라 공용 "카드 스켈레톤"
 * 하나로 통일하지 않았다.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-shimmer-translate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate * 600f - 300f, 0f),
        end = Offset(translate * 600f + 300f, 0f),
    )
}

/** 텍스트 한 줄을 흉내내는 얇은 막대. */
@Composable
fun SkeletonBar(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(shimmerBrush()),
    )
}

/** 카드·칩·지도처럼 면적이 있는 요소를 흉내내는 사각형. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(shimmerBrush()),
    )
}
