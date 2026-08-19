package com.jeongmin.honeymoondoctor.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import com.jeongmin.honeymoondoctor.domain.usecase.SettlementCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 쇼핑 분리 필터(스펙 7-6): 전체 / 쇼핑 제외(일반 여행경비) / 쇼핑만 */
/**
 * 목록 필터. 원래 전체/쇼핑 제외/쇼핑만 3종이었는데, 카테고리가 8종이 되면서
 * "쇼핑만"의 자리에 카테고리 전부를 놓는 편이 일관적이라 이렇게 바꿨다.
 * "쇼핑 제외"는 남긴다 — 기념품 씀씀이를 뺀 생활비 파악이라는 별도 용도가 있다.
 */
sealed interface ExpenseFilter {
    data object All : ExpenseFilter
    data object ExcludeShopping : ExpenseFilter
    data class ByCategory(val category: ExpenseCategory) : ExpenseFilter
}

data class CategoryTotal(
    val category: ExpenseCategory,
    val totalKrw: Long,
)

data class ExpenseUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val expenses: List<Expense> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val cities: List<City> = emptyList(),
    val filter: ExpenseFilter = ExpenseFilter.All,
    /** 필터와 무관한 전체 합계(예산 대비 잔여 계산용) */
    val totalSpentKrw: Long = 0,
    val totalBudgetKrw: Long = 0,
    /** 필터 적용된 합계(화면 목록 위 합계 표시용) */
    val filteredTotalKrw: Long = 0,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    /** 공동지출 합계와 "누가 누구에게 얼마"(스펙 7-6) */
    val sharedTotalKrw: Long = 0,
    val settlement: SettlementCalculator.SettlementResult = SettlementCalculator.SettlementResult(),
    /** 예약의 예상 비용 합계 — 실제 지출과 구분해 표시(스펙 7-6) */
    val reservationEstimateKrw: Long = 0,
    val actionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    cityRepository: CityRepository,
    reservationRepository: ReservationRepository,
    budgetRepository: BudgetRepository,
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<ExpenseFilter>(ExpenseFilter.All)
    private val actionError = ActionErrorState()

    /** 삭제 되돌리기. 화면이 pending을 구독해 스낵바를 띄운다. */
    val undoDelete = UndoDeleteState<Expense>()

    val uiState: StateFlow<ExpenseUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ExpenseUiState(loading = false))
            } else {
                combine(
                    expenseRepository.observeExpenses(trip.id),
                    budgetRepository.observeBudgets(trip.id),
                    tripRepository.observeMembers(trip.id),
                    cityRepository.observeCities(trip.id),
                    reservationRepository.observeReservations(trip.id),
                ) { expenses, budgets, members, cities, reservations ->
                    Triple(expenses, budgets, Triple(members, cities, reservations))
                }.combine(filter) { (expenses, budgets, rest), filterValue ->
                    Pair(Triple(expenses, budgets, rest), filterValue)
                }.combine(actionError.message) { (data, filterValue), error ->
                    val (expenses, budgets, rest) = data
                    val (members, cities, reservations) = rest
                    val filtered = when (filterValue) {
                        ExpenseFilter.All -> expenses
                        ExpenseFilter.ExcludeShopping -> expenses.filter { it.category != ExpenseCategory.SHOPPING }
                        is ExpenseFilter.ByCategory -> expenses.filter { it.category == filterValue.category }
                    }
                    val sharedTotal = expenses.filter { it.shared }.sumOf { it.amountKrw }
                    ExpenseUiState(
                        loading = false,
                        tripId = trip.id,
                        expenses = filtered,
                        members = members,
                        cities = cities,
                        filter = filterValue,
                        totalSpentKrw = expenses.sumOf { it.amountKrw },
                        totalBudgetKrw = budgets.sumOf { it.budgetKrw },
                        filteredTotalKrw = filtered.sumOf { it.amountKrw },
                        categoryTotals = filtered.groupBy { it.category }
                            .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amountKrw }) }
                            .sortedByDescending { it.totalKrw },
                        sharedTotalKrw = sharedTotal,
                        settlement = SettlementCalculator.compute(expenses, members.map { it.uid }),
                        reservationEstimateKrw = reservations.sumOf { it.estimatedKrw ?: 0L },
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun setFilter(value: ExpenseFilter) {
        filter.value = value
    }

    fun clearActionError() = actionError.clear()

    fun delete(expense: Expense) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            val deleted = actionError.runReporting("지출을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                expenseRepository.delete(tripId, expense.id)
            }
            if (deleted) undoDelete.offer(expense, "지출을 삭제했습니다.")
        }
    }

    /** 되돌리기: 같은 id로 다시 만들면 완전 복원이다(문서 id를 클라이언트가 정한다). */
    fun restoreDeleted() {
        val tripId = uiState.value.tripId ?: return
        val expense = undoDelete.consume() ?: return
        viewModelScope.launch {
            actionError.runReporting("지출을 복원하지 못했습니다.") {
                expenseRepository.create(tripId, expense)
            }
        }
    }
}
