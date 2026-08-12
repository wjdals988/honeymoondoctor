package com.jeongmin.honeymoondoctor.feature.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.Budget
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetRow(
    val budget: Budget,
    val spentKrw: Long,
)

data class BudgetUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val rows: List<BudgetRow> = emptyList(),
    val cities: List<City> = emptyList(),
    val actionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    cityRepository: CityRepository,
    expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val actionError = ActionErrorState()

    val uiState: StateFlow<BudgetUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(BudgetUiState(loading = false))
            } else {
                combine(
                    budgetRepository.observeBudgets(trip.id),
                    expenseRepository.observeExpenses(trip.id),
                    cityRepository.observeCities(trip.id),
                    actionError.message,
                ) { budgets, expenses, cities, error ->
                    BudgetUiState(
                        loading = false,
                        tripId = trip.id,
                        rows = budgets.map { budget ->
                            // 예산 항목의 도시·카테고리 조건에 맞는 지출만 합산한다
                            val spent = expenses
                                .filter { budget.cityId == null || it.cityId == budget.cityId }
                                .filter { budget.category == null || it.category == budget.category }
                                .sumOf { it.amountKrw }
                            BudgetRow(budget, spent)
                        },
                        cities = cities,
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun clearActionError() = actionError.clear()

    fun upsert(budget: Budget) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("예산을 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                budgetRepository.upsert(
                    tripId,
                    if (budget.id.isEmpty()) budget.copy(id = "budget-${UUID.randomUUID()}") else budget,
                )
            }
        }
    }

    fun delete(budget: Budget) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("예산을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                budgetRepository.delete(tripId, budget.id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editorTarget by remember { mutableStateOf<Budget?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("예산 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = {
                    editorTarget = null
                    showEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "예산 추가")
                }
            }
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (uiState.rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "설정된 예산이 없습니다",
                    description = "+ 버튼으로 도시·카테고리별 예산을 추가하세요.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = FabSpacing.ContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.rows, key = { it.budget.id }) { row ->
                    BudgetCard(
                        row = row,
                        cities = uiState.cities,
                        onEdit = {
                            editorTarget = row.budget
                            showEditor = true
                        },
                        onDelete = { viewModel.delete(row.budget) },
                    )
                }
            }
        }
    }

    if (showEditor) {
        BudgetEditorDialog(
            original = editorTarget,
            cities = uiState.cities,
            onSave = {
                viewModel.upsert(it)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun BudgetCard(
    row: BudgetRow,
    cities: List<City>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val budget = row.budget
    val remaining = budget.budgetKrw - row.spentKrw
    AppCard(onClick = onEdit, modifier = Modifier.fillMaxWidth(), tone = CardTone.Neutral) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(
                        budget.cityId?.let { id -> cities.firstOrNull { it.id == id }?.displayName ?: id } ?: "전체 도시",
                        budget.category?.display ?: "전체 카테고리",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("예산 ${formatKrw(budget.budgetKrw)} · 지출 ${formatKrw(row.spentKrw)}")
                Text(
                    text = if (remaining >= 0) "잔여 ${formatKrw(remaining)}" else "초과 ${formatKrw(-remaining)}",
                    color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "예산 삭제")
            }
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    original: Budget?,
    cities: List<City>,
    onSave: (Budget) -> Unit,
    onDismiss: () -> Unit,
) {
    var cityId by remember { mutableStateOf(original?.cityId) }
    var category by remember { mutableStateOf(original?.category) }
    var amountText by remember { mutableStateOf(original?.budgetKrw?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "예산 추가" else "예산 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector(
                    label = "도시",
                    selectedLabel = cityId?.let { id -> cities.firstOrNull { it.id == id }?.displayName ?: id }
                        ?: "전체 도시",
                    options = listOf<City?>(null) + cities,
                    optionLabel = { it?.displayName ?: "전체 도시" },
                    onSelect = { cityId = it?.id },
                )
                ChipSelector(
                    label = "카테고리",
                    options = listOf<ExpenseCategory?>(null) + ExpenseCategory.entries,
                    selected = category,
                    optionLabel = { it?.display ?: "전체" },
                    onSelect = { category = it },
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("예산 금액 (원) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", "").toLongOrNull()
                if (amount != null && amount > 0) {
                    onSave(
                        Budget(
                            id = original?.id.orEmpty(),
                            cityId = cityId,
                            category = category,
                            budgetKrw = amount,
                        ),
                    )
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
