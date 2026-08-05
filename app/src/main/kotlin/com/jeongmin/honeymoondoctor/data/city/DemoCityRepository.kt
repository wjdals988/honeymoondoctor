package com.jeongmin.honeymoondoctor.data.city

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_CITIES_KEY = stringPreferencesKey("demo_cities_json")

@Serializable
data class DemoCityStateDto(
    val tripId: String,
    val cities: List<DemoCityDto> = emptyList(),
)

@Serializable
data class DemoCityDto(
    val id: String,
    val displayName: String,
    val countryCode: String,
    val timeZoneId: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val referenceLatitude: Double? = null,
    val referenceLongitude: Double? = null,
    val notes: String? = null,
)

/** 데모 모드 도시 저장소. Phase 4에서는 시드 삽입과 조회만 필요하다(도시 CRUD UI는 이후 단계). */
@Singleton
class DemoCityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CityRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    override fun observeCities(tripId: String): Flow<List<City>> = dataStore.data.map { prefs ->
        val state = prefs[DEMO_CITIES_KEY]?.let { json.decodeFromString(DemoCityStateDto.serializer(), it) }
        state?.takeIf { it.tripId == tripId }?.cities.orEmpty().map { it.toDomain() }
    }

    suspend fun seedForNewTrip(tripId: String, cities: List<City>) {
        val state = DemoCityStateDto(
            tripId = tripId,
            cities = cities.map {
                DemoCityDto(
                    id = it.id,
                    displayName = it.displayName,
                    countryCode = it.countryCode,
                    timeZoneId = it.timeZoneId,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    referenceLatitude = it.referenceLatitude,
                    referenceLongitude = it.referenceLongitude,
                    notes = it.notes,
                )
            },
        )
        dataStore.edit { it[DEMO_CITIES_KEY] = json.encodeToString(DemoCityStateDto.serializer(), state) }
    }

    private fun DemoCityDto.toDomain() = City(
        id = id,
        displayName = displayName,
        countryCode = countryCode,
        timeZoneId = timeZoneId,
        startDate = startDate,
        endDate = endDate,
        referenceLatitude = referenceLatitude,
        referenceLongitude = referenceLongitude,
        notes = notes,
    )
}
