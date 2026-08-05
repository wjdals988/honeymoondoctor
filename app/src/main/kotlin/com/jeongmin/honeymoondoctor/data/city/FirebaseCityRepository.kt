package com.jeongmin.honeymoondoctor.data.city

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TRIPS = "trips"
private const val CITIES = "cities"

@Singleton
class FirebaseCityRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : CityRepository {

    override fun observeCities(tripId: String): Flow<List<City>> =
        firestore.collection(TRIPS).document(tripId).collection(CITIES)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toCity() } }

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
