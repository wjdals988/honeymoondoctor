package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.CityFormDialog
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.ui.confirm
import com.jeongmin.honeymoondoctor.core.ui.copyToClipboard
import com.jeongmin.honeymoondoctor.core.ui.shareText
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripRole
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TripInfoScreen(modifier: Modifier = Modifier, viewModel: TripInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val trip = state.trip ?: return
    val isOwner = state.currentUser?.uid == trip.ownerId
    var showCityDialog by remember { mutableStateOf(false) }
    var deleteCityTarget by remember { mutableStateOf<City?>(null) }
    var deleteCityReferenceCount by remember { mutableStateOf(0) }
    // 삭제 확인을 띄우기 전에 참조 개수를 세어 경고 문구에 넣는다.
    LaunchedEffect(deleteCityTarget) {
        val target = deleteCityTarget
        val tripId = state.trip?.id
        deleteCityReferenceCount = if (target != null && tripId != null) {
            viewModel.countCityReferences(tripId, target.id)
        } else {
            0
        }
    }
    var editingCity by remember { mutableStateOf<City?>(null) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (isOwner && !trip.isReadOnly) {
                    TextButton(onClick = { showEditDialog = true }) { Text("수정") }
                }
            }
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
            state.actionError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (isOwner) {
                if (trip.isReadOnly && trip.isPublic) {
                    Text(
                        "공개 중에는 다시 활성화할 수 없습니다 — 먼저 공개를 중단하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    TextButton(
                        onClick = {
                            // 완료 처리는 모든 탭이 읽기전용으로 바뀌는 큰 변화라 확인을 받는다.
                            // 다시 활성화는 되돌리는 방향이라 바로 실행한다.
                            if (trip.isReadOnly) {
                                viewModel.setStatus(trip.id, TripStatus.ACTIVE)
                            } else {
                                showCompleteConfirm = true
                            }
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(if (trip.isReadOnly) "다시 활성화" else "여행 완료 처리")
                    }
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
            SectionHeader(
                title = "구성원 (${state.members.size}/2)",
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
            SectionHeader(
                title = "도시",
                trailing = if (!trip.isReadOnly) {
                    { TextButton(onClick = { editingCity = null; showCityDialog = true }) { Text("+ 도시 추가") } }
                } else {
                    null
                },
            )
        }
        if (state.cities.isEmpty()) {
            item {
                EmptyState(
                    title = "등록된 도시가 없습니다. 일정·장소·경비 화면에서도 바로 추가할 수 있습니다.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(state.cities) { city ->
            ListItem(
                headlineContent = { Text(city.displayName) },
                supportingContent = {
                    // 체류 기간을 넣은 도시는 그 기간도 함께 보여준다 — 홈의 현지 시각이
                    // 이 기간에만 이 도시 시간대로 바뀌므로 확인할 수 있어야 한다.
                    val stay = if (city.startDate != null && city.endDate != null) {
                        "${city.startDate} ~ ${city.endDate}"
                    } else {
                        null
                    }
                    Text(
                        listOfNotNull(city.countryCode.ifBlank { null }, city.timeZoneId, stay)
                            .joinToString(" · "),
                    )
                },
                trailingContent = {
                    if (!trip.isReadOnly) {
                        Row {
                            TextButton(onClick = { editingCity = city; showCityDialog = true }) { Text("수정") }
                            TextButton(onClick = { deleteCityTarget = city }) { Text("삭제") }
                        }
                    }
                },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        if (isOwner) {
            item {
                SectionHeader(title = "초대")
                if (state.members.size >= 2) {
                    Text(
                        "이미 구성원이 2명입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (trip.isPublic) {
                    Text(
                        "공개 중인 여행은 초대코드를 발급할 수 없습니다 — 공개를 중단하면 다시 사용할 수 있습니다.",
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
                            "새 초대코드: $code",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "지금 복사하거나 공유하세요 — 화면을 벗어나면 다시 볼 수 없습니다" +
                                "(해시만 저장하는 설계라 서버에도 원문이 남지 않습니다).",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            TextButton(onClick = { copyToClipboard(context, "초대코드", code) }) {
                                Text("복사")
                            }
                            TextButton(onClick = { shareText(context, code) }) {
                                Text("공유")
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionHeader(title = "참여 요청")
            }
            if (state.pendingJoinRequests.isEmpty()) {
                item {
                    EmptyState(
                        title = "대기 중인 참여 요청이 없습니다.",
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

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            val partnerExists = trip.memberIds.size > 1
            SectionHeader(title = if (partnerExists) "여행에서 나가기" else "여행 삭제")
            Text(
                text = if (partnerExists) {
                    "나만 이 여행에서 빠집니다. 남은 구성원의 여행과 기록은 그대로 유지됩니다." +
                        if (isOwner) " 소유자 권한은 남은 구성원에게 넘어갑니다." else ""
                } else {
                    "이 여행과 모든 기록(일정·예약·준비물·경비·장소)이 영구히 삭제됩니다. 되돌릴 수 없습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = if (partnerExists) "여행에서 나가기" else "여행 삭제",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = { Text("여행을 완료 처리할까요?") },
            text = {
                Text(
                    "완료하면 모든 탭이 읽기 전용이 되어 일정·경비·준비물을 더 이상 고칠 수 없습니다.\n" +
                        "전체 → 여행 정보에서 언제든 다시 활성화할 수 있습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(trip.id, TripStatus.COMPLETED)
                    showCompleteConfirm = false
                }) { Text("완료 처리") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) { Text("취소") }
            },
        )
    }

    if (showDeleteConfirm) {
        val partnerExists = trip.memberIds.size > 1
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (partnerExists) "이 여행에서 나갈까요?" else "여행을 삭제할까요?") },
            text = {
                Text(
                    if (partnerExists) {
                        "\"${trip.name}\"에서 나갑니다. 남은 구성원의 기록은 그대로 유지됩니다."
                    } else {
                        "\"${trip.name}\"과 모든 기록이 영구히 삭제됩니다. 되돌릴 수 없습니다."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 성공하면 여행이 사라지므로 AuthGate가 여행 만들기 화면으로 알아서 돌아간다.
                    viewModel.deleteOrLeaveTrip(trip) { }
                    showDeleteConfirm = false
                }) {
                    Text(
                        text = if (partnerExists) "나가기" else "삭제",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }

    deleteCityTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCityTarget = null },
            title = { Text("도시 삭제") },
            text = {
                Text(
                    buildString {
                        append("\"${target.displayName}\" 도시를 삭제할까요?")
                        if (deleteCityReferenceCount > 0) {
                            append("\n\n이 도시를 쓰는 일정·지출·장소 ${deleteCityReferenceCount}건은 ")
                            append("삭제되지 않고 \"도시 없음\"으로 남습니다.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.confirm()
                    state.trip?.id?.let { viewModel.deleteCity(it, target.id) }
                    deleteCityTarget = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteCityTarget = null }) { Text("취소") } },
        )
    }

    if (showCityDialog) {
        CityFormDialog(
            initial = editingCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                if (editingCity == null) viewModel.createCity(trip.id, city) else viewModel.updateCity(trip.id, city)
                showCityDialog = false
            },
            otherCities = state.cities,
        )
    }

    if (showEditDialog) {
        TripInfoEditDialog(
            trip = trip,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, startDate, endDate, currency ->
                viewModel.updateTripInfo(
                    trip.id,
                    name,
                    startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    currency.code,
                )
                showEditDialog = false
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

@Composable
private fun TripInfoEditDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDate: LocalDate, endDate: LocalDate, currency: TravelCurrency) -> Unit,
) {
    var name by remember { mutableStateOf(trip.name) }
    var startDate by remember { mutableStateOf(LocalDate.parse(trip.startDate)) }
    var endDate by remember { mutableStateOf(LocalDate.parse(trip.endDate)) }
    var currency by remember {
        mutableStateOf(TravelCurrency.entries.firstOrNull { it.code == trip.defaultCurrency } ?: TravelCurrency.KRW)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("여행 정보 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("여행 이름 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "시작일",
                    date = startDate,
                    onDateChange = { date ->
                        startDate = date
                        if (endDate.isBefore(date)) endDate = date
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
                DateField(
                    label = "종료일",
                    date = endDate,
                    onDateChange = { date -> endDate = date },
                    modifier = Modifier.padding(top = 8.dp),
                )
                DropdownSelector(
                    label = "기본 통화",
                    selectedLabel = "${currency.code} (${currency.symbol})",
                    options = TravelCurrency.entries,
                    optionLabel = { "${it.code} (${it.symbol})" },
                    onSelect = { currency = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !endDate.isBefore(startDate),
                onClick = { onConfirm(name.trim(), startDate, endDate, currency) },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
