package com.jeongmin.honeymoondoctor.data.note

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.TripNote
import com.jeongmin.honeymoondoctor.domain.repository.TripNoteRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val NOTES = "notes"

@Singleton
class FirebaseTripNoteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : TripNoteRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(NOTES)

    override fun observeNotes(tripId: String): Flow<List<TripNote>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot ->
                snapshot?.documents.orEmpty().mapNotNull { it.toTripNote() }.sortedBy { it.createdAt }
            }

    override suspend fun send(tripId: String, note: TripNote) {
        collection(tripId).document(note.id)
            .set(
                mapOf(
                    "senderUid" to note.senderUid,
                    "text" to note.text,
                    // 두 기기의 시계가 어긋나도 정렬이 흔들리지 않게 서버 시각을 쓴다.
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun markRead(tripId: String, noteId: String) {
        collection(tripId).document(noteId)
            .update("readAt", FieldValue.serverTimestamp())
            .await()
    }

    override suspend fun delete(tripId: String, noteId: String) {
        collection(tripId).document(noteId).delete().await()
    }

    private fun DocumentSnapshot.toTripNote(): TripNote? {
        val senderUid = getString("senderUid") ?: return null
        val text = getString("text") ?: return null
        return TripNote(
            id = id,
            senderUid = senderUid,
            text = text,
            // serverTimestamp가 아직 확정되지 않은 로컬 스냅샷에서는 null이 온다 —
            // "방금"으로 취급해 현재 시각을 쓴다(정렬 목적이라 오차 무해).
            createdAt = getTimestamp("createdAt")?.toInstant() ?: Instant.now(),
            readAt = getTimestamp("readAt")?.toInstant(),
        )
    }

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
}
