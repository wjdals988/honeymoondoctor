package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.Decision
import kotlinx.coroutines.flow.Flow

interface DecisionRepository {
    fun observeDecisions(tripId: String): Flow<List<Decision>>

    suspend fun create(tripId: String, decision: Decision)

    suspend fun update(tripId: String, decision: Decision)

    suspend fun delete(tripId: String, decisionId: String)
}
