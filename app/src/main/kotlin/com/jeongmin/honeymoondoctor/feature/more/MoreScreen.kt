package com.jeongmin.honeymoondoctor.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 전체 탭 메뉴. "여행 정보 및 구성원"만 Phase 3에서 실제 화면으로 연결했고, 나머지는
 * 예약함/준비물/결정함(Phase 5), 가져오기·내보내기·동기화 상태(Phase 6-7), 설정(추후)에서
 * 순차적으로 연결할 자리표시자다.
 */
private val menuItems = listOf(
    "예약함", "준비물", "결정함", "긴급상황",
    "여행 정보 및 구성원", "장소 가져오기·내보내기", "동기화 상태", "설정",
)

@Composable
fun MoreScreen(
    isDemoMode: Boolean,
    onNavigateToTripInfo: () -> Unit,
    onResetDemoData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        items(menuItems) { label ->
            ListItem(
                headlineContent = { Text(label) },
                modifier = if (label == "여행 정보 및 구성원") Modifier.clickable(onClick = onNavigateToTripInfo) else Modifier,
            )
            HorizontalDivider()
        }
        if (isDemoMode) {
            item {
                ListItem(
                    headlineContent = { Text("데모 데이터 초기화") },
                    modifier = Modifier.clickable(onClick = onResetDemoData),
                )
            }
        }
    }
}
