package com.jeongmin.honeymoondoctor.data.itinerary

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val DEMO_ITINERARY_KEY = stringPreferencesKey("demo_itinerary_json")

/**
 * 데모 모드 일정 저장소. appDataStore 하나에 JSON 스냅샷으로 저장하므로
 * DemoDataResetter.resetAll()(전체 clear)로 함께 초기화된다.
 */
@Singleton
class DemoItineraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ItineraryRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoItineraryStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_ITINERARY_KEY]?.let { json.decodeFromString(DemoItineraryStateDto.serializer(), it) }
    }

    override fun observeItinerary(tripId: String): Flow<List<ItineraryItem>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty()
            .map { it.toDomain() }
            .sortedBy { it.startAt }
    }

    override suspend fun create(tripId: String, item: ItineraryItem) {
        mutate(tripId) { items -> items + item.toDemoDto() }
    }

    override suspend fun update(tripId: String, item: ItineraryItem) {
        mutate(tripId) { items -> items.map { if (it.id == item.id) item.toDemoDto() else it } }
    }

    override suspend fun delete(tripId: String, itemId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == itemId } }
    }

    /** 여행 생성 직후 1회만 호출되는 시드 삽입(스펙 4장). 기존 스냅샷을 통째로 교체한다. */
    suspend fun seedForNewTrip(tripId: String, items: List<ItineraryItem>) {
        saveState(DemoItineraryStateDto(tripId = tripId, items = items.map { it.toDemoDto() }))
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoItineraryItemDto>) -> List<DemoItineraryItemDto>) {
        val state = stateFlow.first() ?: DemoItineraryStateDto(tripId = tripId)
        check(state.tripId == tripId) { "일정 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        saveState(state.copy(items = transform(state.items)))
    }

    private suspend fun saveState(state: DemoItineraryStateDto) {
        dataStore.edit {
            it[DEMO_ITINERARY_KEY] = json.encodeToString(DemoItineraryStateDto.serializer(), state)
        }
    }
}
