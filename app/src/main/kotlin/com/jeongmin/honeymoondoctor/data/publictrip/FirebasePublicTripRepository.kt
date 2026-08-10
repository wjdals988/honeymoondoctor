package com.jeongmin.honeymoondoctor.data.publictrip

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import com.jeongmin.honeymoondoctor.domain.model.PublicTripSummary
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val PUBLIC_TRIPS = "publicTrips"
private const val CITIES = "cities"
private const val ITINERARY = "itinerary"

/**
 * publicTrips/{tripId}는 trips/{tripId}(원본)와 분리된 사본 컬렉션이다. 원본을 그대로
 * 공개하면 inviteCodeHash가 노출돼 참여 요청이 위조될 수 있고, 일정 문서에는 메모·예상경비
 * 같은 개인 정보가 섞여 있어 문서 단위 규칙만으로는 가릴 수 없다 — 그래서 발행 시점에
 * 여기서 허용 필드만 직접 골라 쓴다(TripInfoViewModel.publish 참고).
 */
@Singleton
class FirebasePublicTripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PublicTripRepository {

    private fun tripDoc(tripId: String) = firestore.collection(PUBLIC_TRIPS).document(tripId)

    override fun observePublicTrips(): Flow<List<PublicTripSummary>> =
        firestore.collection(PUBLIC_TRIPS)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toSummary() } }

    override fun observePublicTrip(tripId: String): Flow<PublicTripSummary?> =
        tripDoc(tripId).snapshotFlow().map { it?.toSummary() }

    override fun observePublicCities(tripId: String): Flow<List<City>> =
        tripDoc(tripId).collection(CITIES)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toCity() } }

    override fun observePublicItinerary(tripId: String): Flow<List<ItineraryItem>> =
        tripDoc(tripId).collection(ITINERARY)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toItineraryItem() } }

    override suspend fun publish(trip: Trip, cities: List<City>, itinerary: List<ItineraryItem>) {
        val summary = mapOf(
            "name" to trip.name,
            "startDate" to trip.startDate,
            "endDate" to trip.endDate,
            "cityNames" to cities.map { it.displayName },
            "itineraryCount" to itinerary.size,
            "publishedAt" to FieldValue.serverTimestamp(),
        )
        tripDoc(trip.id).set(summary).await()

        // 기존 공개 사본을 지우고 다시 쓴다 — 재공개 시 지운 도시·일정이 그대로 남지 않게 한다.
        clearSubcollection(tripDoc(trip.id).collection(CITIES))
        clearSubcollection(tripDoc(trip.id).collection(ITINERARY))

        cities.forEach { city ->
            tripDoc(trip.id).collection(CITIES).document(city.id).set(city.toPublicFirestoreMap()).await()
        }
        itinerary.forEach { item ->
            tripDoc(trip.id).collection(ITINERARY).document(item.id)
                .set(item.toPublicFirestoreMap())
                .await()
        }
    }

    override suspend fun unpublish(tripId: String) {
        clearSubcollection(tripDoc(tripId).collection(CITIES))
        clearSubcollection(tripDoc(tripId).collection(ITINERARY))
        tripDoc(tripId).delete().await()
    }

    private suspend fun clearSubcollection(collection: CollectionReference) {
        collection.get().await().documents.forEach { it.reference.delete().await() }
    }

    private fun DocumentSnapshot.toSummary(): PublicTripSummary? {
        val name = getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val cityNames = (get("cityNames") as? List<String>).orEmpty()
        return PublicTripSummary(
            tripId = id,
            name = name,
            startDate = getString("startDate").orEmpty(),
            endDate = getString("endDate").orEmpty(),
            cityNames = cityNames,
            itineraryCount = (getLong("itineraryCount") ?: 0).toInt(),
            publishedAt = getTimestamp("publishedAt")?.toDate()?.toInstant(),
        )
    }

    /** 공개 범위: 도시명·국가코드·시간대뿐이다 — 좌표·메모·체류 날짜는 뺀다. */
    private fun City.toPublicFirestoreMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "countryCode" to countryCode,
        "timeZoneId" to timeZoneId,
    )

    private fun DocumentSnapshot.toCity(): City? {
        val displayName = getString("displayName") ?: return null
        return City(
            id = id,
            displayName = displayName,
            countryCode = getString("countryCode").orEmpty(),
            timeZoneId = getString("timeZoneId") ?: "Asia/Seoul",
            startDate = null,
            endDate = null,
        )
    }

    /** 공개 범위: 제목·유형·시각·도시·장소명뿐이다 — 메모·예상경비·담당자·주소·예약연결은 뺀다. */
    private fun ItineraryItem.toPublicFirestoreMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "type" to type.name,
        "startAt" to Timestamp(startAt.epochSecond, startAt.nano),
        "endAt" to endAt?.let { Timestamp(it.epochSecond, it.nano) },
        "allDay" to allDay,
        "timeZone" to timeZone,
        "endTimeZone" to endTimeZone,
        "cityId" to cityId,
        "location" to location,
    )

    private fun DocumentSnapshot.toItineraryItem(): ItineraryItem? {
        val title = getString("title") ?: return null
        val startAtTimestamp = getTimestamp("startAt") ?: return null
        val startAt = Instant.ofEpochSecond(startAtTimestamp.seconds, startAtTimestamp.nanoseconds.toLong())
        val endAtTimestamp = getTimestamp("endAt")
        return ItineraryItem(
            id = id,
            title = title,
            type = runCatching { ItineraryType.valueOf(getString("type").orEmpty()) }.getOrDefault(ItineraryType.ETC),
            startAt = startAt,
            endAt = endAtTimestamp?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
            allDay = getBoolean("allDay") ?: false,
            timeZone = getString("timeZone") ?: "Asia/Seoul",
            endTimeZone = getString("endTimeZone"),
            cityId = getString("cityId"),
            location = getString("location"),
        )
    }
}
