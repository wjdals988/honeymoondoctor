package com.jeongmin.honeymoondoctor.data.expense

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.Budget
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_EXPENSES_KEY = stringPreferencesKey("demo_expenses_json")
private val DEMO_BUDGETS_KEY = stringPreferencesKey("demo_budgets_json")

@Serializable
data class DemoExpenseStateDto(
    val tripId: String,
    val items: List<DemoExpenseDto> = emptyList(),
)

@Serializable
data class DemoExpenseDto(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateToKrw: Double,
    val amountKrw: Long,
    val category: String,
    val paidByUid: String? = null,
    val shared: Boolean = true,
    val cityId: String? = null,
    val spentAtEpochMillis: Long,
    val linkedItineraryId: String? = null,
    val linkedReservationId: String? = null,
    val memo: String? = null,
)

@Serializable
data class DemoBudgetStateDto(
    val tripId: String,
    val items: List<DemoBudgetDto> = emptyList(),
)

@Serializable
data class DemoBudgetDto(
    val id: String,
    val cityId: String? = null,
    val category: String? = null,
    val budgetKrw: Long,
)

@Singleton
class DemoExpenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExpenseRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoExpenseStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_EXPENSES_KEY]?.let { json.decodeFromString(DemoExpenseStateDto.serializer(), it) }
    }

    override fun observeExpenses(tripId: String): Flow<List<Expense>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
            .sortedByDescending { it.spentAt }
    }

    override suspend fun create(tripId: String, expense: Expense) {
        mutate(tripId) { it + expense.toDto() }
    }

    override suspend fun update(tripId: String, expense: Expense) {
        mutate(tripId) { items -> items.map { if (it.id == expense.id) expense.toDto() else it } }
    }

    override suspend fun delete(tripId: String, expenseId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == expenseId } }
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoExpenseDto>) -> List<DemoExpenseDto>) {
        val state = stateFlow.first() ?: DemoExpenseStateDto(tripId = tripId)
        check(state.tripId == tripId) { "경비 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        dataStore.edit {
            it[DEMO_EXPENSES_KEY] =
                json.encodeToString(DemoExpenseStateDto.serializer(), state.copy(items = transform(state.items)))
        }
    }

    private fun Expense.toDto() = DemoExpenseDto(
        id = id,
        amountMinor = amountMinor,
        currency = currency.name,
        fxRateToKrw = fxRateToKrw,
        amountKrw = amountKrw,
        category = category.name,
        paidByUid = paidByUid,
        shared = shared,
        cityId = cityId,
        spentAtEpochMillis = spentAt.toEpochMilli(),
        linkedItineraryId = linkedItineraryId,
        linkedReservationId = linkedReservationId,
        memo = memo,
    )

    private fun DemoExpenseDto.toDomain() = Expense(
        id = id,
        amountMinor = amountMinor,
        currency = runCatching { TravelCurrency.valueOf(currency) }.getOrDefault(TravelCurrency.KRW),
        fxRateToKrw = fxRateToKrw,
        amountKrw = amountKrw,
        category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.ETC),
        paidByUid = paidByUid,
        shared = shared,
        cityId = cityId,
        spentAt = Instant.ofEpochMilli(spentAtEpochMillis),
        linkedItineraryId = linkedItineraryId,
        linkedReservationId = linkedReservationId,
        memo = memo,
    )
}

@Singleton
class DemoBudgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : BudgetRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoBudgetStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_BUDGETS_KEY]?.let { json.decodeFromString(DemoBudgetStateDto.serializer(), it) }
    }

    override fun observeBudgets(tripId: String): Flow<List<Budget>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
    }

    override suspend fun upsert(tripId: String, budget: Budget) {
        val state = stateFlow.first() ?: DemoBudgetStateDto(tripId = tripId)
        check(state.tripId == tripId) { "예산 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        val items = if (state.items.any { it.id == budget.id }) {
            state.items.map { if (it.id == budget.id) budget.toDto() else it }
        } else {
            state.items + budget.toDto()
        }
        dataStore.edit {
            it[DEMO_BUDGETS_KEY] = json.encodeToString(DemoBudgetStateDto.serializer(), state.copy(items = items))
        }
    }

    override suspend fun delete(tripId: String, budgetId: String) {
        val state = stateFlow.first() ?: return
        check(state.tripId == tripId) { "예산 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        dataStore.edit {
            it[DEMO_BUDGETS_KEY] = json.encodeToString(
                DemoBudgetStateDto.serializer(),
                state.copy(items = state.items.filterNot { item -> item.id == budgetId }),
            )
        }
    }

    private fun Budget.toDto() = DemoBudgetDto(id = id, cityId = cityId, category = category?.name, budgetKrw = budgetKrw)

    private fun DemoBudgetDto.toDomain() = Budget(
        id = id,
        cityId = cityId,
        category = category?.let { runCatching { ExpenseCategory.valueOf(it) }.getOrNull() },
        budgetKrw = budgetKrw,
    )
}
