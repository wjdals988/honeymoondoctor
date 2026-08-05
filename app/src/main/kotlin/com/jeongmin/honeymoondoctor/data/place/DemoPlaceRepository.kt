package com.jeongmin.honeymoondoctor.data.place

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_PLACES_KEY = stringPreferencesKey("demo_places_json")

@Serializable
data class DemoPlaceStateDto(
    val tripId: String,
    val items: List<DemoPlaceDto> = emptyList(),
)

@Serializable
data class DemoPlaceDto(
    val id: String,
    val name: String,
    val cityId: String? = null,
    val category: String,
    val priority: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val notes: String? = null,
    val visitedAtEpochMillis: Long? = null,
    val ratingSnapshot: Double? = null,
    val reviewCountSnapshot: Long? = null,
    val sourceUpdatedAtEpochMillis: Long? = null,
    val preferredTimes: List<String> = emptyList(),
)

@Singleton
class DemoPlaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaceRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoPlaceStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_PLACES_KEY]?.let { json.decodeFromString(DemoPlaceStateDto.serializer(), it) }
    }

    override fun observePlaces(tripId: String): Flow<List<Place>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
    }

    override suspend fun create(tripId: String, place: Place) {
        mutate(tripId) { it + place.toDto() }
    }

    override suspend fun createAll(tripId: String, places: List<Place>) {
        mutate(tripId) { it + places.map { place -> place.toDto() } }
    }

    override suspend fun update(tripId: String, place: Place) {
        mutate(tripId) { items -> items.map { if (it.id == place.id) place.toDto() else it } }
    }

    override suspend fun delete(tripId: String, placeId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == placeId } }
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoPlaceDto>) -> List<DemoPlaceDto>) {
        val state = stateFlow.first() ?: DemoPlaceStateDto(tripId = tripId)
        check(state.tripId == tripId) { "장소 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        dataStore.edit {
            it[DEMO_PLACES_KEY] =
                json.encodeToString(DemoPlaceStateDto.serializer(), state.copy(items = transform(state.items)))
        }
    }

    private fun Place.toDto() = DemoPlaceDto(
        id = id,
        name = name,
        cityId = cityId,
        category = category.name,
        priority = priority.name,
        latitude = latitude,
        longitude = longitude,
        mapsUrl = mapsUrl,
        notes = notes,
        visitedAtEpochMillis = visitedAt?.toEpochMilli(),
        ratingSnapshot = ratingSnapshot,
        reviewCountSnapshot = reviewCountSnapshot,
        sourceUpdatedAtEpochMillis = sourceUpdatedAt?.toEpochMilli(),
        preferredTimes = preferredTimes.map { it.name },
    )

    private fun DemoPlaceDto.toDomain() = Place(
        id = id,
        name = name,
        cityId = cityId,
        category = runCatching { PlaceCategory.valueOf(category) }.getOrDefault(PlaceCategory.ETC),
        priority = runCatching { PlacePriority.valueOf(priority) }.getOrDefault(PlacePriority.WANT_TO_GO),
        latitude = latitude,
        longitude = longitude,
        mapsUrl = mapsUrl,
        notes = notes,
        visitedAt = visitedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        ratingSnapshot = ratingSnapshot,
        reviewCountSnapshot = reviewCountSnapshot,
        sourceUpdatedAt = sourceUpdatedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        preferredTimes = preferredTimes.mapNotNull { name ->
            runCatching { PreferredTime.valueOf(name) }.getOrNull()
        },
    )
}
