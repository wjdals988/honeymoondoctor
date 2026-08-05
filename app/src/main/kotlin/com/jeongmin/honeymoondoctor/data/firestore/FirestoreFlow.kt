package com.jeongmin.honeymoondoctor.data.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Query.addSnapshotListener를 Flow로 감싼다. 권한이 없는 컬렉션(예: 소유자 전용 joinRequests를
 * 일반 구성원이 구독하는 경우)은 예외를 던지지 않고 빈 스냅샷으로 취급해 UI가 조용히 비어 보이게 한다.
 */
fun Query.snapshotFlow(): Flow<QuerySnapshot?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                trySend(null)
            } else {
                close(error)
            }
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

fun com.google.firebase.firestore.DocumentReference.snapshotFlow(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                trySend(null)
            } else {
                close(error)
            }
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
