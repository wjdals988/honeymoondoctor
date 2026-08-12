package com.jeongmin.honeymoondoctor.data.city

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val CITIES = "cities"

@Singleton
class FirebaseCityRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : CityRepository {

    private fun collection(tripId: String) = firestore.collection(TRIPS).document(tripId).collection(CITIES)

    override fun observeCities(tripId: String): Flow<List<City>> =
        collection(tripId)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toCity() } }

    override suspend fun create(tripId: String, city: City) {
        collection(tripId).document(city.id)
            .set(city.toFirestoreMap() + mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun update(tripId: String, city: City) {
        // merge로 저장해 create가 넣어둔 createdAt은 보존하고 updatedAt만 갱신한다.
        collection(tripId).document(city.id)
            .set(city.toFirestoreMap() + mapOf("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    override suspend fun delete(tripId: String, cityId: String) {
        collection(tripId).document(cityId).delete().await()
    }

    private fun DocumentSnapshot.toCity(): City? {
        val displayName = getString("displayName") ?: return null
        return City(
            id = id,
            displayName = displayName,
            countryCode = getString("countryCode").orEmpty(),
            timeZoneId = getString("timeZoneId") ?: "Asia/Seoul",
            startDate = getString("startDate"),
            endDate = getString("endDate"),
            referenceLatitude = getDouble("referenceLatitude"),
            referenceLongitude = getDouble("referenceLongitude"),
            notes = getString("notes"),
        )
    }
}

/** trips/{tripId}/cities/{cityId} 문서 필드(스펙 8장). 시드 삽입(FirebaseTripRepository)에서 사용한다. */
internal fun City.toFirestoreMap(): Map<String, Any?> = mapOf(
    "displayName" to displayName,
    "countryCode" to countryCode,
    "timeZoneId" to timeZoneId,
    "startDate" to startDate,
    "endDate" to endDate,
    "referenceLatitude" to referenceLatitude,
    "referenceLongitude" to referenceLongitude,
    "notes" to notes,
)
