package com.jeongmin.honeymoondoctor.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.jeongmin.honeymoondoctor.core.network.ConnectivityObserver
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlowIncludingMetadata
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.SyncStatus
import com.jeongmin.honeymoondoctor.domain.repository.SyncStatusRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val TRIPS = "trips"

/** 대기 변경 수·마지막 동기화 시각을 계산하려고 열어보는 여행의 서브컬렉션들. */
private val TRACKED_SUBCOLLECTIONS = listOf(
    "cities", "itinerary", "reservations", "checklistItems", "expenses", "budgets", "places", "decisions", "notes",
)

/** 하나의 쿼리/문서 스냅샷에서 뽑아낸 "동기화 상태 기여분". */
private data class SourceState(val pendingCount: Int, val confirmedSynced: Boolean)

/**
 * Firestore SDK는 "전체 대기 쓰기 개수"를 직접 주지 않으므로(스펙 7-8), 현재 여행 문서와
 * 모든 서브컬렉션을 MetadataChanges.INCLUDE로 구독해 각 문서의 `hasPendingWrites`를 합산한다.
 * 서버 확인(`!hasPendingWrites && !isFromCache`) 이벤트가 하나라도 오면 그 시점을
 * "마지막 동기화 시각"으로 기록해 AppPreferences에 영속화한다(프로세스 재시작에도 유지).
 */
@Singleton
class FirebaseSyncStatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val connectivityObserver: ConnectivityObserver,
    private val appPreferences: AppPreferences,
) : SyncStatusRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSyncStatus(tripId: String): Flow<SyncStatus> {
        val tripDocState: Flow<SourceState> = firestore.collection(TRIPS).document(tripId)
            .snapshotFlowIncludingMetadata()
            .map { it.toSourceState() }

        val subcollectionStates: List<Flow<SourceState>> = TRACKED_SUBCOLLECTIONS.map { name ->
            firestore.collection(TRIPS).document(tripId).collection(name)
                .snapshotFlowIncludingMetadata()
                .map { it.toSourceState() }
        }

        val aggregate: Flow<Pair<Int, Boolean>> = combine(listOf(tripDocState) + subcollectionStates) { states ->
            val totalPending = states.sumOf { it.pendingCount }
            val anyConfirmed = states.any { it.confirmedSynced }
            totalPending to anyConfirmed
        }
            .onEach { (_, anyConfirmed) ->
                if (anyConfirmed) appPreferences.setLastSyncAt(Instant.now().toEpochMilli())
            }

        return combine(connectivityObserver.observeIsOnline(), aggregate, appPreferences.snapshot) {
            online, (pendingCount, _), prefs ->
            SyncStatus(
                isOnline = online,
                lastSyncAt = prefs.lastSyncAtEpochMillis?.let { Instant.ofEpochMilli(it) },
                pendingChangeCount = pendingCount,
                isDemoMode = false,
            )
        }
    }

    private fun QuerySnapshot?.toSourceState(): SourceState {
        if (this == null) return SourceState(0, confirmedSynced = false)
        val pending = documents.count { it.metadata.hasPendingWrites() }
        val confirmed = !metadata.hasPendingWrites() && !metadata.isFromCache
        return SourceState(pending, confirmed)
    }

    private fun DocumentSnapshot?.toSourceState(): SourceState {
        if (this == null || !exists()) return SourceState(0, confirmedSynced = false)
        val pending = if (metadata.hasPendingWrites()) 1 else 0
        val confirmed = !metadata.hasPendingWrites() && !metadata.isFromCache
        return SourceState(pending, confirmed)
    }
}
