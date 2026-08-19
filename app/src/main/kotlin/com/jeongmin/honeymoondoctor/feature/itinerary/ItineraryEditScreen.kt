package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.timeZoneChoices
import com.jeongmin.honeymoondoctor.core.time.zoneOptionLabel
import com.jeongmin.honeymoondoctor.core.ui.CityPickerField
import com.jeongmin.honeymoondoctor.core.ui.CollapsibleSection
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.TimeField
import com.jeongmin.honeymoondoctor.core.ui.confirm
import com.jeongmin.honeymoondoctor.domain.model.ItineraryTitleSuggestions
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ItineraryEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val newPlaceForm by viewModel.newPlaceForm.collectAsState()

    newPlaceForm?.let { newPlace ->
        NewPlaceDialog(
            form = newPlace,
            onUpdate = viewModel::updateNewPlaceForm,
            onUseCurrentLocation = viewModel::fillNewPlaceWithCurrentLocation,
            onFillFromLink = viewModel::fillNewPlaceFromMapsUrl,
            onConfirm = viewModel::createAndLinkPlace,
            onDismiss = viewModel::dismissNewPlaceForm,
        )
    }

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
        val haptic = LocalHapticFeedback.current
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

            ChipSelector(
                label = "유형",
                options = ItineraryType.entries,
                selected = currentForm.type,
                optionLabel = { it.labelKo },
                onSelect = { type -> viewModel.updateForm { it.copy(type = type) } },
            )

            // 이름은 필수라 칸을 없앨 수 없지만, 유형을 고르면 그 유형에서 흔한 이름을
            // 칩으로 내밀어 타이핑을 건너뛸 수 있게 한다. 누른 칩이 곧 현재 이름이면
            // 선택 상태로 보여, 한 번 더 누르는 헛수고를 막는다.
            val suggestions = ItineraryTitleSuggestions.forType(currentForm.type)
            if (suggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    suggestions.forEach { suggestion ->
                        FilterChip(
                            selected = currentForm.title.trim() == suggestion,
                            onClick = { viewModel.updateForm { it.copy(title = suggestion) } },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

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
                // 여행 일정의 시작 시각은 대부분 정시·30분 단위다. 다이얼 피커(탭 3번)
                // 전에 흔한 시각 4개를 칩(탭 1번)으로 내민다. 그 외 시각은 아래 피커로.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf(
                        LocalTime.of(9, 0), LocalTime.of(12, 0),
                        LocalTime.of(14, 0), LocalTime.of(18, 0),
                    ).forEach { preset ->
                        FilterChip(
                            selected = currentForm.startTime == preset,
                            onClick = { viewModel.updateForm { it.copy(startTime = preset) } },
                            label = { Text("%d:%02d".format(preset.hour, preset.minute)) },
                        )
                    }
                }
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
                selectedLabel = zoneOptionLabel(currentForm.timeZone),
                options = timeZoneChoices(uiState.cities.map { it.timeZoneId }, currentForm.timeZone),
                optionLabel = { zoneOptionLabel(it) },
                onSelect = { zone -> viewModel.updateForm { it.copy(timeZone = zone) } },
            )

            if (!currentForm.allDay && currentForm.hasEnd) {
                DropdownSelector(
                    label = "도착(종료) 시간대 — 비행처럼 시간대가 바뀔 때만",
                    selectedLabel = currentForm.endTimeZone?.let(::zoneOptionLabel) ?: "출발과 동일",
                    options = listOf<String?>(null) +
                        timeZoneChoices(uiState.cities.map { it.timeZoneId }, currentForm.endTimeZone),
                    optionLabel = { it?.let(::zoneOptionLabel) ?: "출발과 동일" },
                    onSelect = { zone -> viewModel.updateForm { it.copy(endTimeZone = zone) } },
                )
            }

            // 장소명·주소·담당자·경비·메모는 일정을 잡을 때 대개 나중에 채운다.
            // 처음 만들 때 채워야 할 것은 이름·유형·시각뿐이다.
            CollapsibleSection(
                title = "장소·담당·경비 입력",
                initiallyExpanded = currentForm.location.isNotBlank() ||
                    currentForm.address.isNotBlank() ||
                    currentForm.placeId != null ||
                    currentForm.assigneeUid != null ||
                    currentForm.estimatedKrwText.isNotBlank() ||
                    currentForm.notes.isNotBlank(),
            ) {
                // 주변 탭에 이미 좌표를 가진 장소가 있으면 연결해 지도 보기에서 핀을 찍는다.
                // 아래 장소명·주소는 자유 텍스트라 계속 별도로 둔다.
                //
                // 저장된 장소가 하나도 없으면 드롭다운에 "연결 안 함" 한 줄만 남아, 왜 비어
                // 있는지도 어디서 만드는지도 알 수 없는 막힌 길이 된다(v0.7.0까지의 문제).
                // 그럴 때는 드롭다운 대신 안내를 보여준다.
                if (uiState.places.isEmpty()) {
                    Text(
                        text = "저장된 장소가 없습니다",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "여기서 바로 만들면 이 일정에 연결되고, 지도 보기에 핀으로 표시됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DropdownSelector(
                        label = "저장된 장소에서 선택",
                        selectedLabel = uiState.places.firstOrNull { it.id == currentForm.placeId }?.name
                            ?: "연결 안 함",
                        options = listOf(null) + uiState.places,
                        optionLabel = { it?.name ?: "연결 안 함" },
                        onSelect = { place -> viewModel.updateForm { it.copy(placeId = place?.id) } },
                    )
                    val linkedPlace = uiState.places.firstOrNull { it.id == currentForm.placeId }
                    Text(
                        text = when {
                            linkedPlace == null -> "장소를 연결하면 일정 탭의 지도 보기에 핀으로 표시됩니다."
                            linkedPlace.hasCoordinates -> "지도 보기에 핀으로 표시됩니다."
                            // 좌표 없는 장소를 연결하면 지도에 안 뜨는데, 이유를 알려주지
                            // 않으면 "연결했는데 왜 안 보이지"가 된다.
                            else -> "이 장소에는 좌표가 없어 지도에는 표시되지 않습니다 — " +
                                "주변 탭에서 좌표를 채워 주세요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 주변 탭으로 나갔다 오면 작성 중인 일정 폼이 사라진다. 여기서 만들 수 있게 한다.
                TextButton(onClick = viewModel::openNewPlaceForm) { Text("+ 새 장소 만들기") }

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

                ChipSelector(
                    label = "담당자",
                    options = listOf(null) + uiState.members,
                    selected = uiState.members.firstOrNull { it.uid == currentForm.assigneeUid },
                    optionLabel = { it?.displayName ?: "없음" },
                    onSelect = { member -> viewModel.updateForm { it.copy(assigneeUid = member?.uid) } },
                )

                OutlinedTextField(
                    value = currentForm.estimatedKrwText,
                    onValueChange = { value -> viewModel.updateForm { it.copy(estimatedKrwText = value) } },
                    label = { Text("예상 경비 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                onClick = {
                    haptic.confirm()
                    viewModel.save(onSaved = onNavigateBack)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (currentForm.itemId == null) "일정 추가" else "변경 사항 저장") }
        }
    }
}

/**
 * 일정 편집에서 장소를 바로 만드는 대화상자. 좌표는 손으로 넣을 값이 아니라서 "현재 위치"
 * 와 "구글 지도 링크"로만 채우게 하고(장소 화면과 같은 두 수단), 좌표 없이 저장하는 것도
 * 막지 않되 지도에 안 뜬다는 점을 미리 알려 준다.
 */
@Composable
private fun NewPlaceDialog(
    form: NewPlaceForm,
    onUpdate: ((NewPlaceForm) -> NewPlaceForm) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onFillFromLink: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 장소 만들기") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value) } },
                    label = { Text("장소명 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipSelector(
                    label = "종류",
                    options = PlaceCategory.entries,
                    selected = form.category,
                    optionLabel = { it.display },
                    onSelect = { category -> onUpdate { it.copy(category = category) } },
                )
                Text(
                    text = if (form.hasCoordinates) {
                        "좌표 ${form.latitudeText}, ${form.longitudeText} — 지도에 표시됩니다."
                    } else {
                        "좌표를 채우면 지도 보기에 핀으로 표시됩니다. 없어도 저장은 됩니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onUseCurrentLocation) { Text("현재 위치로 좌표 채우기") }
                OutlinedTextField(
                    value = form.mapsUrl,
                    onValueChange = { value -> onUpdate { it.copy(mapsUrl = value) } },
                    label = { Text("구글 지도 링크") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = onFillFromLink,
                    enabled = form.mapsUrl.isNotBlank() && !form.resolvingLink,
                ) { Text(if (form.resolvingLink) "링크 펼치는 중…" else "링크에서 좌표 채우기") }
                form.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = form.canSave) { Text("만들고 연결") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
