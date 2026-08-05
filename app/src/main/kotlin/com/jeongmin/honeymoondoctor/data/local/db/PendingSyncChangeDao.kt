package com.jeongmin.honeymoondoctor.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncChangeDao {
    @Query("SELECT * FROM pending_sync_change WHERE synced = 0 ORDER BY createdAtEpochMillis ASC")
    fun observeUnsynced(): Flow<List<PendingSyncChangeEntity>>

    @Query("SELECT COUNT(*) FROM pending_sync_change WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: PendingSyncChangeEntity): Long

    @Query("UPDATE pending_sync_change SET synced = 1 WHERE collectionPath = :collectionPath AND documentId = :documentId")
    suspend fun markSynced(collectionPath: String, documentId: String)

    @Query("DELETE FROM pending_sync_change WHERE synced = 1")
    suspend fun clearSynced()
}
