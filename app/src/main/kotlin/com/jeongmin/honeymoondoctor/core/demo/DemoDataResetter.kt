package com.jeongmin.honeymoondoctor.core.demo

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 데모 모드 전용 초기화. 실제 Firestore 여행 데이터에는 이런 자동 초기화 기능을 제공하지 않는다
 * (스펙 4장). 지금은 여행/구성원/참여요청 스냅샷만 지우지만, 이후 단계에서 로컬에만 존재하는
 * 준비물·경비·바우처 메타데이터 등이 늘어나면 이 클래스에서 함께 정리해야 한다.
 */
@Singleton
class DemoDataResetter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun resetAll() {
        context.appDataStore.edit { it.clear() }
    }
}
