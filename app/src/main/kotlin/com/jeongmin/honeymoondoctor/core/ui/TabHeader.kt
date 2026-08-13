package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 하단 탭 5개(홈·일정·주변·경비·전체) 화면 맨 위에 붙는 작은 식별 라벨.
 *
 * 왜 필요한가: 하단 내비게이션이 선택된 탭을 표시하지만, 화면을 스크롤하거나 빠르게
 * 넘겨 볼 때는 시야에 안 들어온다는 피드백 — "여기가 무슨 탭인지" 화면 맨 위 눈이
 * 먼저 가는 자리에서도 바로 알 수 있게 한다. 각 화면 본문의 진짜 제목(여행 이름,
 * 카드 등)과 경쟁하면 안 되므로 작고 옅은 눈썹(eyebrow) 라벨로만 둔다 — 풀사이즈
 * TopAppBar를 쓰면 이미 콘텐츠가 꽉 찬 화면(홈 등)에 중복된 무게가 하나 더 생긴다.
 */
@Composable
fun TabHeader(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
