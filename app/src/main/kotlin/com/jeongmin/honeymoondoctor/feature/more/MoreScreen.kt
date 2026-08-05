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
 * 전체 탭 메뉴(스펙 6장). 예약함·준비물·결정함·여행 정보는 실제 화면으로 연결됐고,
 * 긴급상황·가져오기/내보내기·동기화 상태·설정은 Phase 6~8에서 연결할 자리표시자다.
 */
private data class MoreMenu(val label: String, val onClickKey: String?)

@Composable
fun MoreScreen(
    isDemoMode: Boolean,
    onNavigateToTripInfo: () -> Unit,
    onNavigateToReservations: () -> Unit,
    onNavigateToChecklist: () -> Unit,
    onNavigateToDecisions: () -> Unit,
    onResetDemoData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuItems = listOf(
        MoreMenu("예약함", "reservations"),
        MoreMenu("준비물", "checklist"),
        MoreMenu("결정함", "decisions"),
        MoreMenu("긴급상황", null),
        MoreMenu("여행 정보 및 구성원", "trip_info"),
        MoreMenu("장소 가져오기·내보내기", null),
        MoreMenu("동기화 상태", null),
        MoreMenu("설정", null),
    )
    LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
        items(menuItems) { menu ->
            val onClick: (() -> Unit)? = when (menu.onClickKey) {
                "reservations" -> onNavigateToReservations
                "checklist" -> onNavigateToChecklist
                "decisions" -> onNavigateToDecisions
                "trip_info" -> onNavigateToTripInfo
                else -> null
            }
            ListItem(
                headlineContent = { Text(menu.label) },
                supportingContent = if (onClick == null) {
                    { Text("추후 단계에서 제공") }
                } else {
                    null
                },
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
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
