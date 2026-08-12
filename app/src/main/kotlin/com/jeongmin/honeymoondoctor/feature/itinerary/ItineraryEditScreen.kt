package com.jeongmin.honeymoondoctor.feature.itinerary

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
import com.jeongmin.honeymoondoctor.core.ui.CityPickerField
import com.jeongmin.honeymoondoctor.core.ui.CollapsibleSection
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.TimeField
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType

/** 이번 여행에서 고를 수 있는 시간대 후보. 도시를 고르면 자동으로 바뀌고, 직접 바꿀 수도 있다. */
private val timeZoneOptions = listOf("Asia/Seoul", "Europe/Prague", "Europe/Madrid")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ItineraryEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form?.itemId == null) "일정 추가" else "일정 수정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved = onNavigateBack) }) {
                        Text("저장")
                    }
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
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = currentForm.title,
                onValueChange = { value -> viewModel.updateForm { it.copy(title = value) } },
                label = { Text("일정 이름 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownSelector(
                label = "유형",
                selectedLabel = currentForm.type.labelKo,
                options = ItineraryType.entries,
                optionLabel = { it.labelKo },
                onSelect = { type -> viewModel.updateForm { it.copy(type = type) } },
            )

            CityPickerField(
                selectedCityId = currentForm.cityId,
                cities = uiState.cities,
                onSelect = viewModel::selectCity,
                onCreateCity = viewModel::createCity,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("종일 일정", modifier = Modifier.weight(1f))
                Switch(
                    checked = currentForm.allDay,
                    onCheckedChange = { checked -> viewModel.updateForm { it.copy(allDay = checked) } },
                )
            }

            DateField(
                label = if (currentForm.allDay) "시작 날짜" else "날짜",
                date = currentForm.startDate,
                onDateChange = { date ->
                    viewModel.updateForm {
                        // 종료 날짜가 시작보다 앞서지 않게 따라 움직인다
                        it.copy(startDate = date, endDate = if (it.endDate.isBefore(date)) date else it.endDate)
                    }
                },
            )

            if (currentForm.allDay) {
                DateField(
                    label = "종료 날짜",
                    date = currentForm.endDate,
                    onDateChange = { date -> viewModel.updateForm { it.copy(endDate = date) } },
                )
            } else {
                TimeField(
                    label = "시작 시각",
                    time = currentForm.startTime,
                    onTimeChange = { time -> viewModel.updateForm { it.copy(startTime = time) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종료 시각 입력", modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentForm.hasEnd,
                        onCheckedChange = { checked -> viewModel.updateForm { it.copy(hasEnd = checked) } },
                    )
                }
                if (currentForm.hasEnd) {
                    DateField(
                        label = "종료 날짜",
                        date = currentForm.endDate,
                        onDateChange = { date -> viewModel.updateForm { it.copy(endDate = date) } },
                    )
                    TimeField(
                        label = "종료 시각",
                        time = currentForm.endTime,
                        onTimeChange = { time -> viewModel.updateForm { it.copy(endTime = time) } },
                    )
                }
            }

            DropdownSelector(
                label = "시간대",
                selectedLabel = "${koreanZoneLabel(currentForm.timeZone)} (${currentForm.timeZone})",
                options = (timeZoneOptions + currentForm.timeZone).distinct(),
                optionLabel = { "${koreanZoneLabel(it)} ($it)" },
                onSelect = { zone -> viewModel.updateForm { it.copy(timeZone = zone) } },
            )

            if (!currentForm.allDay && currentForm.hasEnd) {
                DropdownSelector(
                    label = "도착(종료) 시간대 — 비행처럼 시간대가 바뀔 때만",
                    selectedLabel = currentForm.endTimeZone
                        ?.let { "${koreanZoneLabel(it)} ($it)" } ?: "출발과 동일",
                    options = listOf<String?>(null) + (timeZoneOptions + (currentForm.endTimeZone ?: "")).filter { it.isNotEmpty() }.distinct(),
                    optionLabel = { it?.let { z -> "${koreanZoneLabel(z)} ($z)" } ?: "출발과 동일" },
                    onSelect = { zone -> viewModel.updateForm { it.copy(endTimeZone = zone) } },
                )
            }

            // 장소명·주소·담당자·경비·메모는 일정을 잡을 때 대개 나중에 채운다.
            // 처음 만들 때 채워야 할 것은 이름·유형·시각뿐이다.
            CollapsibleSection(
                title = "장소·담당·경비 입력",
                initiallyExpanded = currentForm.location.isNotBlank() ||
                    currentForm.address.isNotBlank() ||
                    currentForm.assigneeUid != null ||
                    currentForm.estimatedKrwText.isNotBlank() ||
                    currentForm.notes.isNotBlank(),
            ) {
                OutlinedTextField(
                    value = currentForm.location,
                    onValueChange = { value -> viewModel.updateForm { it.copy(location = value) } },
                    label = { Text("장소명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = currentForm.address,
                    onValueChange = { value -> viewModel.updateForm { it.copy(address = value) } },
                    label = { Text("주소") },
                    modifier = Modifier.fillMaxWidth(),
                )

                DropdownSelector(
                    label = "담당자",
                    selectedLabel = uiState.members.firstOrNull { it.uid == currentForm.assigneeUid }?.displayName ?: "없음",
                    options = listOf(null) + uiState.members,
                    optionLabel = { it?.displayName ?: "없음" },
                    onSelect = { member -> viewModel.updateForm { it.copy(assigneeUid = member?.uid) } },
                )

                OutlinedTextField(
                    value = currentForm.estimatedKrwText,
                    onValueChange = { value -> viewModel.updateForm { it.copy(estimatedKrwText = value) } },
                    label = { Text("예상 경비 (원)") },
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
            ) { Text(if (currentForm.itemId == null) "일정 추가" else "변경 사항 저장") }
        }
    }
}
