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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.usecase.KrwConverter
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
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
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val pendingUndo by viewModel.undoDelete.pending.collectAsState()
    UndoDeleteSnackbarEffect(
        hostState = snackbarHostState,
        pending = pendingUndo,
        onUndo = viewModel::restoreDeleted,
        onDismissed = viewModel.undoDelete::dismiss,
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = FabSpacing.ContentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(uiState = uiState, onOpenBudgets = onOpenBudgets)
            }
            item {
                // 편집 화면의 카테고리 칩과 같은 얼굴. 여기는 목록이라 세로 공간이
                // 귀해 줄바꿈(FlowRow) 대신 가로 스크롤 한 줄로 둔다 — 필터는 선택
                // 입력이 아니라 탐색 도구라, 전부 안 보여도 스크롤로 충분하다.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = uiState.filter == ExpenseFilter.All,
                        onClick = { viewModel.setFilter(ExpenseFilter.All) },
                        label = { Text("전체") },
                    )
                    FilterChip(
                        selected = uiState.filter == ExpenseFilter.ExcludeShopping,
                        onClick = { viewModel.setFilter(ExpenseFilter.ExcludeShopping) },
                        label = { Text("쇼핑 제외") },
                    )
                    ExpenseCategory.entries.forEach { category ->
                        FilterChip(
                            selected = uiState.filter == ExpenseFilter.ByCategory(category),
                            onClick = { viewModel.setFilter(ExpenseFilter.ByCategory(category)) },
                            label = { Text(category.display) },
                        )
                    }
                }
            }
            if (uiState.categoryTotals.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Neutral) {
                        SectionHeader(title = "카테고리별 지출")
                        uiState.categoryTotals.forEach { total ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(total.category.display, modifier = Modifier.weight(1f))
                                Text(formatKrw(total.totalKrw))
                            }
                        }
                    }
                }
            }
            if (uiState.expenses.isEmpty()) {
                item {
                    EmptyState(
                        title = "아직 기록한 지출이 없습니다",
                        modifier = Modifier.padding(top = 48.dp),
                        description = "여행 중 쓴 돈을 기록하면 정산까지 계산해 줍니다.",
                        action = if (LocalTripReadOnly.current) {
                            null
                        } else {
                            { FilledTonalButton(onClick = onAddExpense) { Text("첫 지출 추가") } }
                        },
                    )
                }
            } else {
                // 가계부 앱 표준 패턴: 날짜(오늘/어제/M월 d일) 헤더 + 그날 합계.
                // 행마다 날짜를 반복해서 적는 것보다 "그날 얼마 썼는지"가 한 줄로 보인다.
                // expenses는 이미 최신순 정렬이라 groupBy가 날짜 역순 그룹을 유지한다.
                val zone = ZoneId.of("Asia/Seoul")
                val today = LocalDate.now(zone)
                val grouped = uiState.expenses.groupBy { it.spentAt.atZone(zone).toLocalDate() }
                grouped.forEach { (date, dayExpenses) ->
                    item(key = "day-$date") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (date) {
                                    today -> "오늘"
                                    today.minusDays(1) -> "어제"
                                    else -> "${date.monthValue}월 ${date.dayOfMonth}일 (${koreanDayOfWeek(date)})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatKrw(dayExpenses.sumOf { it.amountKrw }),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(dayExpenses, key = { it.id }) { expense ->
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.Highlight,
        shape = MaterialTheme.shapes.large,
    ) {
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
                        MaterialTheme.colorScheme.onPrimaryContainer
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

@Composable
private fun ExpenseRow(
    expense: Expense,
    uiState: ExpenseUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(onClick = onEdit, modifier = Modifier.fillMaxWidth(), tone = CardTone.Neutral) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
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
                        expense.category.display,
                        if (expense.shared) "공동" else "개인",
                        payer?.let { "결제 $it" },
                        city,
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
            // 완료된 여행에서는 삭제 버튼 자체를 내린다(서버가 거부해 크래시로 이어졌던 경로).
            if (!LocalTripReadOnly.current) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "지출 삭제")
                }
            }
        }
    }
}

/** "8월 12일 (수)"의 요일. DayOfWeek.getDisplayName은 로케일 의존이라 직접 매핑한다. */
private fun koreanDayOfWeek(date: LocalDate): String =
    listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
