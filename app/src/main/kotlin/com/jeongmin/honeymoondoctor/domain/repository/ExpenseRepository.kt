package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.Budget
import com.jeongmin.honeymoondoctor.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun observeExpenses(tripId: String): Flow<List<Expense>>

    suspend fun create(tripId: String, expense: Expense)

    suspend fun update(tripId: String, expense: Expense)

    suspend fun delete(tripId: String, expenseId: String)
}

interface BudgetRepository {
    fun observeBudgets(tripId: String): Flow<List<Budget>>

    suspend fun upsert(tripId: String, budget: Budget)

    suspend fun delete(tripId: String, budgetId: String)
}
