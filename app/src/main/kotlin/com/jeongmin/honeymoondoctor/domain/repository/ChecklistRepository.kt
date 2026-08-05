package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import kotlinx.coroutines.flow.Flow

interface ChecklistRepository {
    fun observeChecklist(tripId: String): Flow<List<ChecklistItem>>

    suspend fun create(tripId: String, item: ChecklistItem)

    suspend fun update(tripId: String, item: ChecklistItem)

    suspend fun delete(tripId: String, itemId: String)
}
