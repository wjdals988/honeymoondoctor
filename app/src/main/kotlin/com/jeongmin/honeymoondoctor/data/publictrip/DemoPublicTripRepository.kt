package com.jeongmin.honeymoondoctor.data.publictrip

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.city.DemoCityRepository
import com.jeongmin.honeymoondoctor.data.itinerary.DemoItineraryRepository
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.PublicTripSummary
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_PUBLIC_TRIP_KEY = stringPreferencesKey("demo_public_trip_json")

@Serializable
private data class DemoPublicTripDto(
    val tripId: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val cityNames: List<String>,
    val itineraryCount: Int,
    val publishedAtEpochMillis: Long?,
)

/**
 * 데모 모드에는 "다른 계정"이라는 개념이 없어 다른 사용자의 공개 여행을 실제로 보여줄 수
 * 없다(가짜 데이터를 만들지 않는다). 대신 이 기기에서 내가 직접 공개한 여행만 목록에
 * 나타나게 해, 발행→둘러보기→비공개 전환까지 전체 흐름을 데모 모드에서도 그대로
 * 눌러볼 수 있게 한다. 도시·일정 내용은 DemoCityRepository/DemoItineraryRepository를
 * 그대로 재사용한다(데모는 계정당 여행이 하나뿐이라 같은 tripId로 충분하다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DemoPublicTripRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demoCityRepository: DemoCityRepository,
    private val demoItineraryRepository: DemoItineraryRepository,
) : PublicTripRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoPublicTripDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_PUBLIC_TRIP_KEY]?.let { json.decodeFromString(DemoPublicTripDto.serializer(), it) }
    }

    override fun observePublicTrips(): Flow<List<PublicTripSummary>> =
        stateFlow.map { state -> state?.let { listOf(it.toDomain()) }.orEmpty() }

    override fun observePublicTrip(tripId: String): Flow<PublicTripSummary?> =
        stateFlow.map { it?.takeIf { s -> s.tripId == tripId }?.toDomain() }

    override fun observePublicCities(tripId: String): Flow<List<City>> =
        stateFlow.flatMapLatest { state ->
            if (state?.tripId == tripId) demoCityRepository.observeCities(tripId) else flowOf(emptyList())
        }

    override fun observePublicItinerary(tripId: String): Flow<List<ItineraryItem>> =
        stateFlow.flatMapLatest { state ->
            if (state?.tripId == tripId) demoItineraryRepository.observeItinerary(tripId) else flowOf(emptyList())
        }

    override suspend fun publish(trip: Trip, cities: List<City>, itinerary: List<ItineraryItem>) {
        val dto = DemoPublicTripDto(
            tripId = trip.id,
            name = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            cityNames = cities.map { it.displayName },
            itineraryCount = itinerary.size,
            publishedAtEpochMillis = Instant.now().toEpochMilli(),
        )
        dataStore.edit { it[DEMO_PUBLIC_TRIP_KEY] = json.encodeToString(DemoPublicTripDto.serializer(), dto) }
    }

    override suspend fun unpublish(tripId: String) {
        dataStore.edit { it.remove(DEMO_PUBLIC_TRIP_KEY) }
    }

    private fun DemoPublicTripDto.toDomain() = PublicTripSummary(
        tripId = tripId,
        name = name,
        startDate = startDate,
        endDate = endDate,
        cityNames = cityNames,
        itineraryCount = itineraryCount,
        publishedAt = publishedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    )
}
