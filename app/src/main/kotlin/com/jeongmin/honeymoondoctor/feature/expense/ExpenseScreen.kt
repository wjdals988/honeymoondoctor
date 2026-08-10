package com.jeongmin.honeymoondoctor.feature.expense

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.usecase.KrwConverter
import java.text.NumberFormat
import java.util.Locale

private val krwFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

internal fun formatKrw(amount: Long): String = "${krwFormat.format(amount)}원"

@Composable
fun ExpenseScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = onAddExpense) {
                    Icon(Icons.Filled.Add, contentDescription = "지출 추가")
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(uiState = uiState, onOpenBudgets = onOpenBudgets)
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ShoppingFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.labelKo) },
                        )
                    }
                }
            }
            if (uiState.categoryTotals.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("카테고리별 지출", style = MaterialTheme.typography.titleSmall)
                            uiState.categoryTotals.forEach { total ->
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    Text(total.category.labelKo, modifier = Modifier.weight(1f))
                                    Text(formatKrw(total.totalKrw))
                                }
                            }
                        }
                    }
                }
            }
            if (uiState.expenses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "아직 기록한 지출이 없습니다.\n+ 버튼으로 첫 지출을 추가해 보세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(uiState.expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        uiState = uiState,
                        onEdit = { onEditExpense(expense.id) },
                        onDelete = { deleteTarget = expense },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("지출 삭제") },
            text = { Text("${formatKrw(target.amountKrw)} 지출 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SummaryCard(uiState: ExpenseUiState, onOpenBudgets: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("경비 요약", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenBudgets) { Text("예산 관리") }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("총지출", modifier = Modifier.weight(1f))
                Text(formatKrw(uiState.totalSpentKrw), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.totalBudgetKrw > 0) {
                val remaining = uiState.totalBudgetKrw - uiState.totalSpentKrw
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("총예산", modifier = Modifier.weight(1f))
                    Text(formatKrw(uiState.totalBudgetKrw))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(if (remaining >= 0) "잔여 예산" else "예산 초과", modifier = Modifier.weight(1f))
                    Text(
                        text = formatKrw(if (remaining >= 0) remaining else -remaining),
                        color = if (remaining >= 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("공동지출 1/2 정산 예상", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(formatKrw(uiState.settlementPerPersonKrw), style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "예약 예상비 합계(실지출과 별도)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatKrw(uiState.reservationEstimateKrw),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    uiState: ExpenseUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(formatKrw(expense.amountKrw))
                        if (expense.currency != TravelCurrency.KRW) {
                            append(" (")
                            append(KrwConverter.formatMajor(expense.amountMinor, expense.currency))
                            append(" ")
                            append(expense.currency.code)
                            append(")")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val payer = uiState.members.firstOrNull { it.uid == expense.paidByUid }?.displayName
                val city = uiState.cities.firstOrNull { it.id == expense.cityId }?.displayName
                Text(
                    text = listOfNotNull(
                        expense.category.labelKo,
                        if (expense.shared) "공동" else "개인",
                        payer?.let { "결제 $it" },
                        city,
                        LocalTimes.formatDate(expense.spentAt, "Asia/Seoul"),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                expense.memo?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "지출 삭제")
            }
        }
    }
}
