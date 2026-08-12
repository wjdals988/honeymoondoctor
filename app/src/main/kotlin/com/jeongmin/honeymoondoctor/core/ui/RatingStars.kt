package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 평점을 별 5개 탭으로 입력한다. 예전에는 "평점(0~5)" 텍스트 칸이었는데, 손으로
 * 숫자를 치게 할 이유가 없는 값이다("4.5"를 치려고 키보드를 여는 일 자체가 과하다).
 *
 * 소수점 값을 버리지 않는 이유: TSV/JSON 가져오기로 들어온 값은 4.7처럼 소수일 수
 * 있다. 별은 반 칸까지 표시하고, 옆에 실제 숫자를 그대로 적어 둔다. 별을 누르면
 * 정수로 바뀌지만, 안 누르면 가져온 값이 그대로 유지된다.
 */
@Composable
fun RatingStars(
    rating: Double?,
    onRatingChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onRatingChange(star.toDouble()) },
                    enabled = enabled,
                ) {
                    val value = rating ?: 0.0
                    Icon(
                        imageVector = when {
                            value >= star -> Icons.Filled.Star
                            // 4.7이면 다섯 번째 별을 반 칸으로 — 5점과 구분된다.
                            value >= star - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                            else -> Icons.Outlined.StarOutline
                        },
                        contentDescription = "별 ${star}점",
                        tint = if (value >= star - 0.5) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            if (rating != null) {
                Text(
                    text = formatRating(rating),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (enabled) {
                    TextButton(onClick = { onRatingChange(null) }) { Text("지우기") }
                }
            } else {
                Text(
                    text = "평점 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 4.0은 "4점", 4.7은 "4.7점"으로. 정수에 굳이 ".0"을 붙이지 않는다. */
private fun formatRating(rating: Double): String =
    if (rating % 1.0 == 0.0) "${rating.toInt()}점" else "${rating}점"
