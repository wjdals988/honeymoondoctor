package com.jeongmin.honeymoondoctor.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherMetadataDao {
    @Query("SELECT * FROM voucher_metadata WHERE reservationId = :reservationId")
    fun observeForReservation(reservationId: String): Flow<List<VoucherMetadataEntity>>

    @Query("SELECT * FROM voucher_metadata WHERE reservationId = :reservationId")
    suspend fun listForReservation(reservationId: String): List<VoucherMetadataEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM voucher_metadata")
    suspend fun totalSizeBytes(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VoucherMetadataEntity)

    @Delete
    suspend fun delete(entity: VoucherMetadataEntity)
}
