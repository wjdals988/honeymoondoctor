package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.CityFormDialog
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.TripRole
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly

@Composable
fun TripInfoScreen(modifier: Modifier = Modifier, viewModel: TripInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val trip = state.trip ?: return
    val isOwner = state.currentUser?.uid == trip.ownerId
    var showCityDialog by remember { mutableStateOf(false) }
    var editingCity by remember { mutableStateOf<City?>(null) }
    var showPublishDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text(trip.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${trip.startDate} ~ ${trip.endDate} · ${trip.defaultCurrency}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (trip.isReadOnly) {
                Text(
                    "완료된 여행 — 더 이상 수정할 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (isOwner) {
                TextButton(
                    onClick = {
                        viewModel.setStatus(
                            trip.id,
                            if (trip.isReadOnly) TripStatus.ACTIVE else TripStatus.COMPLETED,
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(if (trip.isReadOnly) "다시 활성화" else "여행 완료 처리")
                }
            }
            if (isOwner && trip.isReadOnly) {
                if (trip.isPublic) {
                    Text(
                        "다른 사용자에게 공개 중입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = { viewModel.unpublish(trip.id) }) { Text("공개 중단") }
                } else {
                    TextButton(onClick = { showPublishDialog = true }) { Text("다른 사용자에게 공개") }
                }
            }
            Text(
                "구성원 (${state.members.size}/2)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(state.members) { member ->
            ListItem(
                headlineContent = { Text(member.displayName) },
                supportingContent = { Text(if (member.role == TripRole.OWNER) "소유자" else "구성원") },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("도시", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!trip.isReadOnly) {
                    TextButton(onClick = { editingCity = null; showCityDialog = true }) { Text("+ 도시 추가") }
                }
            }
        }
        if (state.cities.isEmpty()) {
            item {
                Text(
                    "등록된 도시가 없습니다. 일정·장소·경비 화면에서도 바로 추가할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(state.cities) { city ->
            ListItem(
                headlineContent = { Text(city.displayName) },
                supportingContent = { Text(listOfNotNull(city.countryCode.ifBlank { null }, city.timeZoneId).joinToString(" · ")) },
                trailingContent = {
                    if (!trip.isReadOnly) {
                        TextButton(onClick = { editingCity = city; showCityDialog = true }) { Text("수정") }
                    }
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        if (isOwner) {
            item {
                Text("초대", style = MaterialTheme.typography.titleMedium)
                if (state.members.size >= 2) {
                    Text(
                        "이미 구성원이 2명입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { viewModel.regenerateInviteCode(trip.id) }) {
                            Text("초대코드 생성·재발급")
                        }
                        TextButton(onClick = { viewModel.expireInviteCode(trip.id) }) {
                            Text("초대코드 만료")
                        }
                    }
                    state.lastGeneratedInviteCode?.let { code ->
                        Text(
                            "새 초대코드(한 번만 표시됩니다): $code",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("참여 요청", style = MaterialTheme.typography.titleMedium)
            }
            if (state.pendingJoinRequests.isEmpty()) {
                item {
                    Text(
                        "대기 중인 참여 요청이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(state.pendingJoinRequests) { request ->
                ListItem(
                    headlineContent = { Text(request.applicantDisplayName) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { viewModel.approve(trip.id, request.id) }) { Text("승인") }
                            TextButton(onClick = { viewModel.reject(trip.id, request.id) }) { Text("거절") }
                        }
                    },
                )
            }
        }
    }

    if (showCityDialog) {
        CityFormDialog(
            initial = editingCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                if (editingCity == null) viewModel.createCity(trip.id, city) else viewModel.updateCity(trip.id, city)
                showCityDialog = false
            },
        )
    }

    if (showPublishDialog) {
        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            title = { Text("다른 사용자에게 공개할까요?") },
            text = {
                Text(
                    "공개되는 내용: 여행 이름·기간·도시 목록·일정의 제목·시각·장소명\n\n" +
                        "공개되지 않는 내용: 예약함·경비·준비물·결정함·장소, 그리고 일정의 메모·예상경비·담당자\n\n" +
                        "이 앱을 쓰는 다른 계정의 사용자가 \"여행 둘러보기\"에서 볼 수 있게 됩니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.publish(trip); showPublishDialog = false }) { Text("공개") }
            },
            dismissButton = { TextButton(onClick = { showPublishDialog = false }) { Text("취소") } },
        )
    }
}
