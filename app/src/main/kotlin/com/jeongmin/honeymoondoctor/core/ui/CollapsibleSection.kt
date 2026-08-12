package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 편집 화면의 선택 입력을 접어 두는 구역.
 *
 * 왜 필요한가: 장소를 하나 넣는데 실제로 채우는 건 이름 하나인데 위도·경도·평점·리뷰 수까지
 * 일곱 칸이 늘 펼쳐져 있었다. 개수를 줄이지 않아도 접어 두는 것만으로 "채워야 할 것"이
 * 하나로 보인다.
 *
 * [initiallyExpanded]에 "이미 값이 들어 있는지"를 넘긴다. 수정으로 들어왔는데 접혀 있으면
 * 사용자가 이전에 넣은 값이 사라진 것처럼 보인다 — 값이 있으면 펼친 채로 시작해야 한다.
 */
@Composable
fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(if (expanded) "$title 접기" else "+ $title")
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}
