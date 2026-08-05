package com.jeongmin.honeymoondoctor.data.place

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val PLACES = "places"

@Singleton
class FirebasePlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PlaceRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(PLACES)

    override fun observePlaces(tripId: String): Flow<List<Place>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toPlace() } }

    override suspend fun create(tripId: String, place: Place) {
        collection(tripId).document(place.id)
            .set(
                place.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun createAll(tripId: String, places: List<Place>) {
        // Firestore 배치는 500건 한도 — 가져오기 최대치(41건)보다 훨씬 커서 하나의 배치로 충분하다
        val batch = firestore.batch()
        places.forEach { place ->
            batch.set(
                collection(tripId).document(place.id),
                place.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
    }

    override suspend fun update(tripId: String, place: Place) {
        collection(tripId).document(place.id)
            .set(
                place.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, placeId: String) {
        collection(tripId).document(placeId).delete().await()
    }

    private fun Place.toFirestoreMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "cityId" to cityId,
        "category" to category.name,
        "priority" to priority.name,
        "latitude" to latitude,
        "longitude" to longitude,
        "mapsUrl" to mapsUrl,
        "notes" to notes,
        "visitedAt" to visitedAt?.let { Timestamp(it.epochSecond, it.nano) },
        "ratingSnapshot" to ratingSnapshot,
        "reviewCountSnapshot" to reviewCountSnapshot,
        "sourceUpdatedAt" to sourceUpdatedAt?.let { Timestamp(it.epochSecond, it.nano) },
        "preferredTimes" to preferredTimes.map { it.name },
    )

    private fun DocumentSnapshot.toPlace(): Place? {
        val name = getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val preferredTimes = (get("preferredTimes") as? List<String>).orEmpty()
        return Place(
            id = id,
            name = name,
            cityId = getString("cityId"),
            category = runCatching { PlaceCategory.valueOf(getString("category").orEmpty()) }
                .getOrDefault(PlaceCategory.ETC),
            priority = runCatching { PlacePriority.valueOf(getString("priority").orEmpty()) }
                .getOrDefault(PlacePriority.WANT_TO_GO),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            mapsUrl = getString("mapsUrl"),
            notes = getString("notes"),
            visitedAt = getTimestamp("visitedAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
            ratingSnapshot = getDouble("ratingSnapshot"),
            reviewCountSnapshot = getLong("reviewCountSnapshot"),
            sourceUpdatedAt = getTimestamp("sourceUpdatedAt")
                ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
            preferredTimes = preferredTimes.mapNotNull { runCatching { PreferredTime.valueOf(it) }.getOrNull() },
        )
    }
}
