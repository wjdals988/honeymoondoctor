package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncStatusRepository {
    fun observeSyncStatus(tripId: String): Flow<SyncStatus>
}
