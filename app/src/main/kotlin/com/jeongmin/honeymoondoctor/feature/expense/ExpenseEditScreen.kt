package com.jeongmin.honeymoondoctor.feature.expense

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
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency

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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (currentForm.currency != TravelCurrency.KRW) {
                OutlinedTextField(
                    value = currentForm.fxRateText,
                    onValueChange = { value -> viewModel.updateForm { it.copy(fxRateText = value) } },
                    label = { Text("환율: 1 ${currentForm.currency.code} = ? KRW (직접 입력·보존)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            currentForm.previewKrw?.let { preview ->
                Text(
                    text = "KRW 환산(HALF_UP): ${formatKrw(preview)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            DropdownSelector(
                label = "카테고리",
                selectedLabel = currentForm.category.labelKo,
                options = ExpenseCategory.entries,
                optionLabel = { it.labelKo },
                onSelect = { category -> viewModel.updateForm { it.copy(category = category) } },
            )
            DropdownSelector(
                label = "결제자",
                selectedLabel = uiState.members.firstOrNull { it.uid == currentForm.paidByUid }?.displayName ?: "미지정",
                options = listOf(null) + uiState.members,
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
            DropdownSelector(
                label = "도시",
                selectedLabel = uiState.cities.firstOrNull { it.id == currentForm.cityId }?.displayName ?: "선택 안 함",
                options = listOf(null) + uiState.cities,
                optionLabel = { it?.displayName ?: "선택 안 함" },
                onSelect = { city -> viewModel.updateForm { it.copy(cityId = city?.id) } },
            )
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
