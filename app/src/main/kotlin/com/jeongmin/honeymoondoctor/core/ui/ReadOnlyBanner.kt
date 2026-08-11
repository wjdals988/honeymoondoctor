package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 완료된 여행에서 모든 탭 상단에 띄우는 읽기전용 안내.
 *
 * 왜 필요한가: 완료 처리하면 추가·수정·삭제 버튼이 전부 사라진다(그래야 서버가 거부하는
 * 쓰기를 시도하지 않는다). 그런데 버튼만 없애면 사용자는 **왜** 사라졌는지 알 수 없어
 * 앱이 고장 난 것으로 읽는다. 상태와 되돌리는 경로를 함께 알려준다.
 */
@Composable
fun ReadOnlyBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "완료된 여행 · 읽기 전용",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = "전체 → 여행 정보에서 다시 활성화하면 수정할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
