package com.jeongmin.honeymoondoctor.core.di

import android.content.Context
import androidx.room.Room
import com.jeongmin.honeymoondoctor.data.local.db.AppDatabase
import com.jeongmin.honeymoondoctor.data.local.db.VoucherMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "honeymoon_doctor.db").build()

    @Provides
    fun provideVoucherMetadataDao(db: AppDatabase): VoucherMetadataDao = db.voucherMetadataDao()
}
