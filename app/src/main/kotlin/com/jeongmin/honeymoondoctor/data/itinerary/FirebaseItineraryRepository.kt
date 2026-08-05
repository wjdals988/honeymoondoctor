package com.jeongmin.honeymoondoctor.data.itinerary

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val ITINERARY = "itinerary"

@Singleton
class FirebaseItineraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ItineraryRepository {

    private fun collection(tripId: String) =
        firestore.collection(TRIPS).document(tripId).collection(ITINERARY)

    override fun observeItinerary(tripId: String): Flow<List<ItineraryItem>> =
        collection(tripId)
            .orderBy("startAt")
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toItineraryItem() } }

    override suspend fun create(tripId: String, item: ItineraryItem) {
        collection(tripId).document(item.id)
            .set(
                item.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun update(tripId: String, item: ItineraryItem) {
        // merge로 저장해 create가 넣어둔 createdAt은 보존하고 updatedAt만 갱신한다.
        collection(tripId).document(item.id)
            .set(
                item.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun delete(tripId: String, itemId: String) {
        collection(tripId).document(itemId).delete().await()
    }
}
