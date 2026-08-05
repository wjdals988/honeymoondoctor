package com.jeongmin.honeymoondoctor.data.reservation

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val RESERVATIONS = "reservations"

@Singleton
class FirebaseReservationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ReservationRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(RESERVATIONS)

    override fun observeReservations(tripId: String): Flow<List<Reservation>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toReservation() } }

    override suspend fun create(tripId: String, reservation: Reservation) {
        collection(tripId).document(reservation.id)
            .set(
                reservation.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun update(tripId: String, reservation: Reservation) {
        collection(tripId).document(reservation.id)
            .set(
                reservation.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, reservationId: String) {
        collection(tripId).document(reservationId).delete().await()
    }
}

internal fun Reservation.toFirestoreMap(): Map<String, Any?> = mapOf(
    "type" to type.name,
    "vendor" to vendor,
    "title" to title,
    "status" to status.name,
    "confirmationCode" to confirmationCode,
    "pin" to pin,
    "startAt" to startAt?.let { Timestamp(it.epochSecond, it.nano) },
    "endAt" to endAt?.let { Timestamp(it.epochSecond, it.nano) },
    "allDay" to allDay,
    "timeZone" to timeZone,
    "endTimeZone" to endTimeZone,
    "linkedItineraryId" to linkedItineraryId,
    "estimatedKrw" to estimatedKrw,
    "notes" to notes,
    "assigneeUid" to assigneeUid,
)

internal fun DocumentSnapshot.toReservation(): Reservation? {
    val title = getString("title") ?: return null
    return Reservation(
        id = id,
        type = runCatching { ReservationType.valueOf(getString("type").orEmpty()) }
            .getOrDefault(ReservationType.ETC),
        vendor = getString("vendor").orEmpty(),
        title = title,
        status = runCatching { ReservationStatus.valueOf(getString("status").orEmpty()) }
            .getOrDefault(ReservationStatus.NEEDS_CHECK),
        confirmationCode = getString("confirmationCode"),
        pin = getString("pin"),
        startAt = getTimestamp("startAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
        endAt = getTimestamp("endAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
        allDay = getBoolean("allDay") ?: false,
        timeZone = getString("timeZone") ?: "Asia/Seoul",
        endTimeZone = getString("endTimeZone"),
        linkedItineraryId = getString("linkedItineraryId"),
        estimatedKrw = getLong("estimatedKrw"),
        notes = getString("notes"),
        assigneeUid = getString("assigneeUid"),
    )
}
