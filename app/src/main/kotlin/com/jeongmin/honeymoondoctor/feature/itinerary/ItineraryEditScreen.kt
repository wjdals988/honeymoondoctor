package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formDateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val formTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

            DropdownSelector(
                label = "도시",
                selectedLabel = uiState.cities.firstOrNull { it.id == currentForm.cityId }?.displayName ?: "선택 안 함",
                options = uiState.cities + listOf(null),
                optionLabel = { it?.displayName ?: "선택 안 함" },
                onSelect = viewModel::selectCity,
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

            Button(
                onClick = { viewModel.save(onSaved = onNavigateBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (currentForm.itemId == null) "일정 추가" else "변경 사항 저장") }
        }
    }
}

/** readOnly OutlinedTextField는 클릭을 먹지 않아, 투명 오버레이로 클릭을 받는 셀렉터. */
@Composable
private fun <T> DropdownSelector(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date.format(formDateFormatter),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }
    if (showDialog) {
        // DatePicker의 selectedDateMillis는 UTC 자정 기준이다
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDialog = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = time.format(formTimeFormatter),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }
    if (showDialog) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    showDialog = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } },
        )
    }
}
