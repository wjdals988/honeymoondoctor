package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/**
 * 오프라인/동기화 상태(스펙 7-2, 7-8).
 * [pendingChangeCount]는 Firestore SDK가 정확한 대기 쓰기 개수를 제공하지 않으므로,
 * 현재 열람 중인 여행의 각 컬렉션 스냅샷에서 `metadata.hasPendingWrites`가 true인
 * 문서 수를 합산한 근사치다(FirebaseSyncStatusRepository 참고).
 * 데모 모드에는 원격 동기화 개념이 없어 [lastSyncAt]은 항상 null, [pendingChangeCount]는 0이다.
 */
data class SyncStatus(
    val isOnline: Boolean,
    val lastSyncAt: Instant?,
    val pendingChangeCount: Int,
    val isDemoMode: Boolean,
)
