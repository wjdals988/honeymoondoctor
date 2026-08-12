package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 선택지가 적고 라벨이 짧은 단일 선택을 **항상 펼쳐진 칩**으로 받는다.
 *
 * 왜 드롭다운을 대체하나: 드롭다운은 "펼치기 → 고르기" 두 탭이고, 펼치기 전에는
 * 무엇을 고를 수 있는지 보이지 않는다. 지출 카테고리(7개)·예약 유형(6개)처럼
 * 선택지가 한 화면에 다 들어가는 경우 가계부 앱들은 카테고리를 타일·칩으로 항상
 * 펼쳐 둔다 — 한 탭으로 끝나고, 현재 값과 대안이 같이 보인다.
 *
 * 드롭다운([DropdownSelector])을 계속 쓰는 경우: 도시·통화·연결 일정처럼 목록이
 * 길거나(10개 이상) 항목이 동적으로 늘어나는 것. 칩 20개는 화면 절반을 먹는다.
 *
 * FlowRow로 줄바꿈한다. 가로 스크롤 한 줄이 아니라 — 화면 밖으로 밀린 칩은
 * 없는 것과 같아서, "전체가 한눈에"라는 목적이 깨진다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}
