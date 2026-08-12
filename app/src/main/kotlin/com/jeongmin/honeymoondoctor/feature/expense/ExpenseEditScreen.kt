package com.jeongmin.honeymoondoctor.feature.expense

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.CityPickerField
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExpenseEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form?.expenseId == null) "지출 추가" else "지출 수정") },
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

            DropdownSelector(
                label = "통화",
                selectedLabel = "${currentForm.currency.code} (${currentForm.currency.symbol})",
                options = TravelCurrency.entries,
                optionLabel = { "${it.code} (${it.symbol})" },
                onSelect = { currency -> viewModel.updateForm { it.copy(currency = currency) } },
            )
            OutlinedTextField(
                value = currentForm.amountText,
                onValueChange = { value -> viewModel.updateForm { it.copy(amountText = value) } },
                label = {
                    Text(
                        if (currentForm.currency == TravelCurrency.KRW) "금액 (원) *" else "금액 (${currentForm.currency.code}) *",
                    )
                },
                // 금액 칸에서 문자 자판이 뜨면 숫자 줄로 손을 옮기는 것부터가 일이다.
                // Decimal인 이유: EUR 12.34처럼 소수 입력이 필요한 통화가 있다.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (currentForm.currency != TravelCurrency.KRW) {
                OutlinedTextField(
                    value = currentForm.fxRateText,
                    onValueChange = { value -> viewModel.updateForm { it.copy(fxRateText = value) } },
                    label = { Text("환율: 1 ${currentForm.currency.code} = ? KRW") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 자동 조회는 "제안"이고 저장은 이 칸의 값 그대로다. 현금 환전처럼 실제로
                // 적용된 환율이 다르면 직접 고쳐 쓰라고 안내한다.
                //
                // 유럽중앙은행 고시에 없는 통화(VND·TWD)는 버튼을 아예 내밀지 않는다.
                // 눌러 봐야 실패하는 버튼을 보여 주는 것이 더 나쁘다.
                if (currentForm.currency.autoFetchable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = viewModel::fetchTodayRate,
                            enabled = !uiState.fxRateLoading,
                        ) {
                            if (uiState.fxRateLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("오늘 환율 불러오기")
                        }
                    }
                }
                Text(
                    text = uiState.fxRateNotice ?: if (currentForm.currency.autoFetchable) {
                        "직접 입력한 값이 그대로 저장되며, 나중에 환율이 바뀌어도 이 지출은 바뀌지 않습니다."
                    } else {
                        "${currentForm.currency.code}은 유럽중앙은행 고시에 없어 자동 조회를 지원하지 않습니다. " +
                            "환율을 직접 입력해 주세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            currentForm.previewKrw?.let { preview ->
                Text(
                    text = "KRW 환산(HALF_UP): ${formatKrw(preview)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 카테고리·결제자는 선택지가 적고 고정이라 드롭다운(펼치기+고르기 두 탭)
            // 대신 항상 펼쳐진 칩 한 탭으로 받는다. 가계부 앱들의 카테고리 타일과 같은 이유.
            ChipSelector(
                label = "카테고리",
                options = ExpenseCategory.entries,
                selected = currentForm.category,
                optionLabel = { it.display },
                onSelect = { category -> viewModel.updateForm { it.copy(category = category) } },
            )
            ChipSelector(
                label = "결제자",
                options = listOf(null) + uiState.members,
                selected = uiState.members.firstOrNull { it.uid == currentForm.paidByUid },
                optionLabel = { it?.displayName ?: "미지정" },
                onSelect = { member -> viewModel.updateForm { it.copy(paidByUid = member?.uid) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("공동 지출 (1/2 정산 대상)", modifier = Modifier.weight(1f))
                Switch(
                    checked = currentForm.shared,
                    onCheckedChange = { checked -> viewModel.updateForm { it.copy(shared = checked) } },
                )
            }
            CityPickerField(
                selectedCityId = currentForm.cityId,
                cities = uiState.cities,
                onSelect = { city -> viewModel.updateForm { it.copy(cityId = city?.id) } },
                onCreateCity = viewModel::createCity,
            )
            // 여행 중에는 "어제 쓴 걸 오늘 몰아서 적는" 일이 잦다. 달력을 여는 대신
            // 칩 한 탭으로 끝나게 한다. 그 외 날짜는 아래 달력으로.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentForm.spentDate == LocalDate.now(),
                    onClick = { viewModel.updateForm { it.copy(spentDate = LocalDate.now()) } },
                    label = { Text("오늘") },
                )
                FilterChip(
                    selected = currentForm.spentDate == LocalDate.now().minusDays(1),
                    onClick = { viewModel.updateForm { it.copy(spentDate = LocalDate.now().minusDays(1)) } },
                    label = { Text("어제") },
                )
            }
            DateField(
                label = "지출 날짜",
                date = currentForm.spentDate,
                onDateChange = { date -> viewModel.updateForm { it.copy(spentDate = date) } },
            )
            DropdownSelector(
                label = "연결 일정",
                selectedLabel = uiState.itinerary.firstOrNull { it.id == currentForm.linkedItineraryId }?.title
                    ?: "연결 안 함",
                options = listOf(null) + uiState.itinerary,
                optionLabel = { it?.title ?: "연결 안 함" },
                onSelect = { item -> viewModel.updateForm { it.copy(linkedItineraryId = item?.id) } },
            )
            DropdownSelector(
                label = "연결 예약",
                selectedLabel = uiState.reservations.firstOrNull { it.id == currentForm.linkedReservationId }?.title
                    ?: "연결 안 함",
                options = listOf(null) + uiState.reservations,
                optionLabel = { it?.title ?: "연결 안 함" },
                onSelect = { reservation -> viewModel.updateForm { it.copy(linkedReservationId = reservation?.id) } },
            )
            OutlinedTextField(
                value = currentForm.memo,
                onValueChange = { value -> viewModel.updateForm { it.copy(memo = value) } },
                label = { Text("메모") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save(onSaved = onNavigateBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (currentForm.expenseId == null) "지출 추가" else "변경 사항 저장") }
        }
    }
}
