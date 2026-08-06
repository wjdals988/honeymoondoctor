package com.jeongmin.honeymoondoctor.data.sync

import com.jeongmin.honeymoondoctor.core.network.ConnectivityObserver
import com.jeongmin.honeymoondoctor.domain.model.SyncStatus
import com.jeongmin.honeymoondoctor.domain.repository.SyncStatusRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 데모 모드는 모든 데이터가 이 기기의 DataStore/Room에만 있어 "원격 동기화"가 존재하지 않는다.
 * 그래도 오프라인 상태 자체는 실제 기기 네트워크 상태를 그대로 보여준다(스펙 7-2).
 */
@Singleton
class DemoSyncStatusRepository @Inject constructor(
    private val connectivityObserver: ConnectivityObserver,
) : SyncStatusRepository {

    override fun observeSyncStatus(tripId: String): Flow<SyncStatus> =
        connectivityObserver.observeIsOnline().map { online ->
            SyncStatus(isOnline = online, lastSyncAt = null, pendingChangeCount = 0, isDemoMode = true)
        }
}
