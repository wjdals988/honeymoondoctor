package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 목록 화면 공용 검색 칸. 항목이 수십 개가 되면 스크롤로만 찾는 것이 백로그 1-4의
 * 문제였다 — 별도 검색 화면을 만들지 않고 목록 위에 인라인으로 둔다(입력 즉시 필터,
 * 이동·확정 단계 없음).
 *
 * 지우기(X) 버튼을 반드시 두는 이유: 검색 상태는 잊히기 쉽다. "왜 항목이 3개뿐이지?"의
 * 대부분은 남아 있는 검색어다. 한 탭으로 전체 목록으로 돌아갈 수 있어야 한다.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "검색어 지우기")
                }
            }
        } else {
            null
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 공백·대소문자 차이를 무시하는 포함 검색. 화면마다 같은 규칙을 쓰도록 한 곳에 둔다. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return fields.any { it?.contains(normalized, ignoreCase = true) == true }
}
