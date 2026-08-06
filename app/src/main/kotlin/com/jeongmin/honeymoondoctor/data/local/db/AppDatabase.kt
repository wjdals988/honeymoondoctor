package com.jeongmin.honeymoondoctor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VoucherMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherMetadataDao(): VoucherMetadataDao
}
