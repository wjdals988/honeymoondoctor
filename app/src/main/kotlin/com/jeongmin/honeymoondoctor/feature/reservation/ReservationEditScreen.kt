package com.jeongmin.honeymoondoctor.feature.reservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.CollapsibleSection
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.TimeField
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType

private val timeZoneOptions = listOf("Asia/Seoul", "Europe/Prague", "Europe/Madrid")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReservationEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form?.reservationId == null) "예약 추가" else "예약 수정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved = onNavigateBack) }) { Text("저장") }
                },
            )
        },
    ) { innerPadding ->
        val currentForm = form
        if (uiState.loading || currentForm == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.validationError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = currentForm.title,
                onValueChange = { value -> viewModel.updateForm { it.copy(title = value) } },
                label = { Text("예약명 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = currentForm.vendor,
                onValueChange = { value -> viewModel.updateForm { it.copy(vendor = value) } },
                label = { Text("업체명") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownSelector(
                label = "유형",
                selectedLabel = currentForm.type.labelKo,
                options = ReservationType.entries,
                optionLabel = { it.labelKo },
                onSelect = { type -> viewModel.updateForm { it.copy(type = type) } },
            )
            DropdownSelector(
                label = "상태",
                selectedLabel = currentForm.status.labelKo,
                options = ReservationStatus.entries,
                optionLabel = { it.labelKo },
                onSelect = { status -> viewModel.updateForm { it.copy(status = status) } },
            )
            // 예약번호·PIN은 예약을 잡은 뒤에 받는 값이라 처음 만들 때는 대개 비어 있다.
            CollapsibleSection(
                title = "예약번호·PIN 입력",
                initiallyExpanded = currentForm.confirmationCode.isNotBlank() || currentForm.pin.isNotBlank(),
            ) {
                OutlinedTextField(
                    value = currentForm.confirmationCode,
                    onValueChange = { value -> viewModel.updateForm { it.copy(confirmationCode = value) } },
                    label = { Text("예약번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = currentForm.pin,
                    onValueChange = { value -> viewModel.updateForm { it.copy(pin = value) } },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }


            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("일시 입력", modifier = Modifier.weight(1f))
                Switch(
                    checked = currentForm.hasSchedule,
                    onCheckedChange = { checked -> viewModel.updateForm { it.copy(hasSchedule = checked) } },
                )
            }
            if (currentForm.hasSchedule) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종일(날짜 범위)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentForm.allDay,
                        onCheckedChange = { checked -> viewModel.updateForm { it.copy(allDay = checked) } },
                    )
                }
                DateField(
                    label = "시작 날짜",
                    date = currentForm.startDate,
                    onDateChange = { date ->
                        viewModel.updateForm {
                            it.copy(startDate = date, endDate = if (it.endDate.isBefore(date)) date else it.endDate)
                        }
                    },
                )
                if (!currentForm.allDay) {
                    TimeField(
                        label = "시작 시각",
                        time = currentForm.startTime,
                        onTimeChange = { time -> viewModel.updateForm { it.copy(startTime = time) } },
                    )
                }
                DateField(
                    label = "종료 날짜",
                    date = currentForm.endDate,
                    onDateChange = { date -> viewModel.updateForm { it.copy(endDate = date) } },
                )
                if (!currentForm.allDay) {
                    TimeField(
                        label = "종료 시각",
                        time = currentForm.endTime,
                        onTimeChange = { time -> viewModel.updateForm { it.copy(endTime = time) } },
                    )
                }
                DropdownSelector(
                    label = "시간대",
                    selectedLabel = "${koreanZoneLabel(currentForm.timeZone)} (${currentForm.timeZone})",
                    options = (timeZoneOptions + currentForm.timeZone).distinct(),
                    optionLabel = { "${koreanZoneLabel(it)} ($it)" },
                    onSelect = { zone -> viewModel.updateForm { it.copy(timeZone = zone) } },
                )
                if (!currentForm.allDay) {
                    DropdownSelector(
                        label = "도착(종료) 시간대 — 비행처럼 시간대가 바뀔 때만",
                        selectedLabel = currentForm.endTimeZone
                            ?.let { "${koreanZoneLabel(it)} ($it)" } ?: "출발과 동일",
                        options = listOf<String?>(null) + timeZoneOptions,
                        optionLabel = { it?.let { z -> "${koreanZoneLabel(z)} ($z)" } ?: "출발과 동일" },
                        onSelect = { zone -> viewModel.updateForm { it.copy(endTimeZone = zone) } },
                    )
                }
            }

            DropdownSelector(
                label = "연결 일정",
                selectedLabel = uiState.itinerary.firstOrNull { it.id == currentForm.linkedItineraryId }?.title
                    ?: "연결 안 함",
                options = listOf(null) + uiState.itinerary,
                optionLabel = { it?.title ?: "연결 안 함" },
                onSelect = { item -> viewModel.updateForm { it.copy(linkedItineraryId = item?.id) } },
            )
            DropdownSelector(
                label = "담당자",
                selectedLabel = uiState.members.firstOrNull { it.uid == currentForm.assigneeUid }?.displayName
                    ?: "없음",
                options = listOf(null) + uiState.members,
                optionLabel = { it?.displayName ?: "없음" },
                onSelect = { member -> viewModel.updateForm { it.copy(assigneeUid = member?.uid) } },
            )
            // 예상 비용과 메모도 대부분 비워 둔다.
            CollapsibleSection(
                title = "비용·메모 입력",
                initiallyExpanded = currentForm.estimatedKrwText.isNotBlank() || currentForm.notes.isNotBlank(),
            ) {
                OutlinedTextField(
                    value = currentForm.estimatedKrwText,
                    onValueChange = { value -> viewModel.updateForm { it.copy(estimatedKrwText = value) } },
                    label = { Text("예상 비용 (원)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = currentForm.notes,
                    onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
                    label = { Text("메모") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }


            Button(
                onClick = { viewModel.save(onSaved = onNavigateBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (currentForm.reservationId == null) "예약 추가" else "변경 사항 저장") }
        }
    }
}
