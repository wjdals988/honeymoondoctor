package com.jeongmin.honeymoondoctor.data.expense

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.Budget
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val EXPENSES = "expenses"
private const val BUDGETS = "budgets"

@Singleton
class FirebaseExpenseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ExpenseRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(EXPENSES)

    override fun observeExpenses(tripId: String): Flow<List<Expense>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot ->
                snapshot?.documents.orEmpty().mapNotNull { it.toExpense() }.sortedByDescending { it.spentAt }
            }

    override suspend fun create(tripId: String, expense: Expense) {
        collection(tripId).document(expense.id)
            .set(
                expense.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun update(tripId: String, expense: Expense) {
        collection(tripId).document(expense.id)
            .set(
                expense.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, expenseId: String) {
        collection(tripId).document(expenseId).delete().await()
    }
}

@Singleton
class FirebaseBudgetRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : BudgetRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(BUDGETS)

    override fun observeBudgets(tripId: String): Flow<List<Budget>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toBudget() } }

    override suspend fun upsert(tripId: String, budget: Budget) {
        collection(tripId).document(budget.id)
            .set(
                mapOf(
                    "cityId" to budget.cityId,
                    "category" to budget.category?.name,
                    "budgetKrw" to budget.budgetKrw,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, budgetId: String) {
        collection(tripId).document(budgetId).delete().await()
    }

    private fun DocumentSnapshot.toBudget(): Budget? {
        val budgetKrw = getLong("budgetKrw") ?: return null
        return Budget(
            id = id,
            cityId = getString("cityId"),
            category = getString("category")?.let { runCatching { ExpenseCategory.valueOf(it) }.getOrNull() },
            budgetKrw = budgetKrw,
        )
    }
}

internal fun Expense.toFirestoreMap(): Map<String, Any?> = mapOf(
    "amountMinor" to amountMinor,
    "currency" to currency.name,
    "fxRateToKrw" to fxRateToKrw,
    "amountKrw" to amountKrw,
    "category" to category.name,
    "paidByUid" to paidByUid,
    "shared" to shared,
    "cityId" to cityId,
    "spentAt" to Timestamp(spentAt.epochSecond, spentAt.nano),
    "linkedItineraryId" to linkedItineraryId,
    "linkedReservationId" to linkedReservationId,
    "memo" to memo,
)

internal fun DocumentSnapshot.toExpense(): Expense? {
    val amountMinor = getLong("amountMinor") ?: return null
    val spentAt = getTimestamp("spentAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
        ?: return null
    return Expense(
        id = id,
        amountMinor = amountMinor,
        currency = runCatching { TravelCurrency.valueOf(getString("currency").orEmpty()) }
            .getOrDefault(TravelCurrency.KRW),
        fxRateToKrw = getDouble("fxRateToKrw") ?: 1.0,
        amountKrw = getLong("amountKrw") ?: 0L,
        category = runCatching { ExpenseCategory.valueOf(getString("category").orEmpty()) }
            .getOrDefault(ExpenseCategory.ETC),
        paidByUid = getString("paidByUid"),
        shared = getBoolean("shared") ?: true,
        cityId = getString("cityId"),
        spentAt = spentAt,
        linkedItineraryId = getString("linkedItineraryId"),
        linkedReservationId = getString("linkedReservationId"),
        memo = getString("memo"),
    )
}
