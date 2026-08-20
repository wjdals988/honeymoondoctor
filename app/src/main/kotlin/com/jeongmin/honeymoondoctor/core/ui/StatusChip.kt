package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 상태를 색으로 구분하는 작은 칩. 결정함에서 먼저 쓰던 인라인 Surface를 공용으로
 * 뽑아, 예약함·일정 탭도 같은 색상 언어("아직 안 됨"=Warn, "끝났다"=Done)를 쓴다.
 */
@Composable
fun StatusChip(label: String, tone: CardTone, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tone.containerColor(),
        contentColor = tone.contentColor(),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
