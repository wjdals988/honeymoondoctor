package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.domain.model.TripRole

@Composable
fun TripInfoScreen(modifier: Modifier = Modifier, viewModel: TripInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val trip = state.trip ?: return
    val isOwner = state.currentUser?.uid == trip.ownerId

    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text(trip.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${trip.startDate} ~ ${trip.endDate} · ${trip.defaultCurrency}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Text("구성원 (${state.members.size}/2)", style = MaterialTheme.typography.titleMedium)
        }
        items(state.members) { member ->
            ListItem(
                headlineContent = { Text(member.displayName) },
                supportingContent = { Text(if (member.role == TripRole.OWNER) "소유자" else "구성원") },
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
}
