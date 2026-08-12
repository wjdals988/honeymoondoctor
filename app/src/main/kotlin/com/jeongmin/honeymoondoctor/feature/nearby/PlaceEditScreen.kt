package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.jeongmin.honeymoondoctor.core.ui.RatingStars
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
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
            ChipSelector(
                label = "카테고리",
                options = PlaceCategory.entries,
                selected = currentForm.category,
                optionLabel = { it.labelKo },
                onSelect = { category -> viewModel.updateForm { it.copy(category = category) } },
            )
            ChipSelector(
                label = "우선순위",
                options = PlacePriority.entries,
                selected = currentForm.priority,
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
                    currentForm.rating != null ||
                    currentForm.notes.isNotBlank(),
            ) {
                // 위도·경도는 사람이 손으로 넣을 값이 아니다. 버튼 두 개로 대신 채우고
                // 직접 입력 칸은 확인·미세 조정용으로만 남긴다.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::fillWithCurrentLocation) {
                        Icon(Icons.Filled.Place, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("현재 위치")
                    }
                    FilledTonalButton(
                        onClick = viewModel::fillFromMapsUrl,
                        enabled = currentForm.mapsUrl.isNotBlank() && !uiState.resolvingLink,
                    ) {
                        if (uiState.resolvingLink) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("링크에서 좌표 채우기")
                    }
                }
                Text(
                    text = "구글 지도에서 \"공유 → 링크 복사\"한 주소를 아래 Google Maps URL 칸에 넣고 누르세요. " +
                        "앱이 만드는 짧은 주소(maps.app.goo.gl)도 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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

                Text("평점", style = MaterialTheme.typography.labelLarge)
                RatingStars(
                    rating = currentForm.rating,
                    onRatingChange = { value -> viewModel.updateForm { it.copy(rating = value) } },
                )
                Text(
                    text = buildString {
                        append("평점은 실시간 수집하지 않는 스냅샷입니다.")
                        // 리뷰 수는 입력 칸이 없지만, 가져오기로 들어온 값이 있으면
                        // 보이지 않게 저장돼 있다는 사실 자체는 알려 준다.
                        currentForm.reviewCount?.let { append(" 가져온 리뷰 수: ${it}개.") }
                        append(
                            currentForm.sourceUpdatedAt?.let {
                                " 확인일: ${LocalTimes.formatDate(it, "Asia/Seoul")}"
                            } ?: " 저장 시 오늘 날짜로 확인일이 기록됩니다.",
                        )
                    },
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
