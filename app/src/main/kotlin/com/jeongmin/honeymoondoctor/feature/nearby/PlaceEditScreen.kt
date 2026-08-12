package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.ui.CityPickerField
import com.jeongmin.honeymoondoctor.core.ui.CollapsibleSection
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlaceEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form?.placeId == null) "장소 추가" else "장소 수정") },
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
                value = currentForm.name,
                onValueChange = { value -> viewModel.updateForm { it.copy(name = value) } },
                label = { Text("장소명 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CityPickerField(
                selectedCityId = currentForm.cityId,
                cities = uiState.cities,
                onSelect = { city -> viewModel.updateForm { it.copy(cityId = city?.id) } },
                onCreateCity = viewModel::createCity,
            )
            DropdownSelector(
                label = "카테고리",
                selectedLabel = currentForm.category.labelKo,
                options = PlaceCategory.entries,
                optionLabel = { it.labelKo },
                onSelect = { category -> viewModel.updateForm { it.copy(category = category) } },
            )
            DropdownSelector(
                label = "우선순위",
                selectedLabel = currentForm.priority.labelKo,
                options = PlacePriority.entries,
                optionLabel = { it.labelKo },
                onSelect = { priority -> viewModel.updateForm { it.copy(priority = priority) } },
            )

            Text("추천 시간대", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PreferredTime.entries.forEach { time ->
                    FilterChip(
                        selected = time in currentForm.preferredTimes,
                        onClick = { viewModel.togglePreferredTime(time) },
                        label = { Text(time.labelKo) },
                    )
                }
            }

            // 위도·경도·URL·평점·메모는 대부분 비워 두는 값이다. 접어 두면 새 장소를
            // 넣을 때 채워야 할 것이 "장소명" 하나로 보인다. 값이 이미 있으면 펼친 채로 연다.
            CollapsibleSection(
                title = "자세히 입력",
                initiallyExpanded = currentForm.latitudeText.isNotBlank() ||
                    currentForm.longitudeText.isNotBlank() ||
                    currentForm.mapsUrl.isNotBlank() ||
                    currentForm.ratingText.isNotBlank() ||
                    currentForm.reviewCountText.isNotBlank() ||
                    currentForm.notes.isNotBlank(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentForm.latitudeText,
                        onValueChange = { value -> viewModel.updateForm { it.copy(latitudeText = value) } },
                        label = { Text("위도") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = currentForm.longitudeText,
                        onValueChange = { value -> viewModel.updateForm { it.copy(longitudeText = value) } },
                        label = { Text("경도") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = currentForm.mapsUrl,
                    onValueChange = { value -> viewModel.updateForm { it.copy(mapsUrl = value) } },
                    label = { Text("Google Maps URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentForm.ratingText,
                        onValueChange = { value -> viewModel.updateForm { it.copy(ratingText = value) } },
                        label = { Text("평점(0~5)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = currentForm.reviewCountText,
                        onValueChange = { value -> viewModel.updateForm { it.copy(reviewCountText = value) } },
                        label = { Text("리뷰 수") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "평점·리뷰 수는 실시간 수집하지 않는 스냅샷입니다." +
                        (
                            currentForm.sourceUpdatedAt?.let {
                                " 확인일: ${LocalTimes.formatDate(it, "Asia/Seoul")}"
                            } ?: " 저장 시 오늘 날짜로 확인일이 기록됩니다."
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = currentForm.notes,
                    onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
                    label = { Text("개인 메모") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { viewModel.save(onSaved = onNavigateBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (currentForm.placeId == null) "장소 추가" else "변경 사항 저장") }
        }
    }
}
