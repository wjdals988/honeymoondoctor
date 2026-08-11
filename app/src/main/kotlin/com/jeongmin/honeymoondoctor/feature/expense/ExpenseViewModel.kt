package com.jeongmin.honeymoondoctor.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
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
enum class ShoppingFilter(val labelKo: String) {
    ALL("전체"),
    EXCLUDE_SHOPPING("쇼핑 제외"),
    SHOPPING_ONLY("쇼핑만"),
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
    val filter: ShoppingFilter = ShoppingFilter.ALL,
    /** 필터와 무관한 전체 합계(예산 대비 잔여 계산용) */
    val totalSpentKrw: Long = 0,
    val totalBudgetKrw: Long = 0,
    /** 필터 적용된 합계(화면 목록 위 합계 표시용) */
    val filteredTotalKrw: Long = 0,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    /** 공동지출 합계와 1/2 정산 예상액(스펙 7-6) */
    val sharedTotalKrw: Long = 0,
    val settlementPerPersonKrw: Long = 0,
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

    private val filter = MutableStateFlow(ShoppingFilter.ALL)
    private val actionError = ActionErrorState()

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
                        ShoppingFilter.ALL -> expenses
                        ShoppingFilter.EXCLUDE_SHOPPING -> expenses.filter { it.category != ExpenseCategory.SHOPPING }
                        ShoppingFilter.SHOPPING_ONLY -> expenses.filter { it.category == ExpenseCategory.SHOPPING }
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
                        settlementPerPersonKrw = sharedTotal / 2,
                        reservationEstimateKrw = reservations.sumOf { it.estimatedKrw ?: 0L },
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun setFilter(value: ShoppingFilter) {
        filter.value = value
    }

    fun clearActionError() = actionError.clear()

    fun delete(expense: Expense) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("지출을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                expenseRepository.delete(tripId, expense.id)
            }
        }
    }
}
