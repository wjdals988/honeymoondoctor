package com.jeongmin.honeymoondoctor.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Firestore SDK는 "대기 중인 쓰기 개수"를 직접 제공하지 않으므로, 오프라인에서 발생한
 * 로컬 변경을 이 큐에 직접 기록해 "동기화 대기 변경 수"를 근사한다.
 * Firestore 쓰기가 서버에 반영되면(addSnapshotListener 의 SnapshotMetadata.hasPendingWrites
 * == false) 해당 항목을 synced = true로 갱신하거나 삭제한다.
 */
@Entity(tableName = "pending_sync_change")
data class PendingSyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionPath: String,
    val documentId: String,
    val changeType: String,
    val createdAtEpochMillis: Long,
    val synced: Boolean = false,
)
