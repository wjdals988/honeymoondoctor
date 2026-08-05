package com.jeongmin.honeymoondoctor.core.demo

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jeongmin.honeymoondoctor.data.local.db.AppDatabase
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 데모 모드 전용 초기화. 실제 Firestore 여행 데이터에는 이런 자동 초기화 기능을 제공하지 않는다
 * (스펙 4장). DataStore의 데모 스냅샷(여행/일정/도시/예약/준비물/결정함/경비/예산)과
 * 기기 전용 Room(바우처 메타데이터·동기화 대기 큐), 내부 바우처 파일까지 함께 지운다.
 */
@Singleton
class DemoDataResetter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        context.appDataStore.edit { it.clear() }
        database.clearAllTables()
        File(context.filesDir, "vouchers").deleteRecursively()
    }
}
