package com.jeongmin.honeymoondoctor.data.checklist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_CHECKLIST_KEY = stringPreferencesKey("demo_checklist_json")

@Serializable
data class DemoChecklistStateDto(
    val tripId: String,
    val items: List<DemoChecklistItemDto> = emptyList(),
)

@Serializable
data class DemoChecklistItemDto(
    val id: String,
    val title: String,
    val category: String,
    val ownerUid: String? = null,
    val required: Boolean = false,
    val completed: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val dueAtEpochMillis: Long? = null,
)

@Singleton
class DemoChecklistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChecklistRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoChecklistStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_CHECKLIST_KEY]?.let { json.decodeFromString(DemoChecklistStateDto.serializer(), it) }
    }

    override fun observeChecklist(tripId: String): Flow<List<ChecklistItem>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
    }

    override suspend fun create(tripId: String, item: ChecklistItem) {
        mutate(tripId) { it + item.toDto() }
    }

    override suspend fun update(tripId: String, item: ChecklistItem) {
        mutate(tripId) { items -> items.map { if (it.id == item.id) item.toDto() else it } }
    }

    override suspend fun delete(tripId: String, itemId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == itemId } }
    }

    suspend fun seedForNewTrip(tripId: String, items: List<ChecklistItem>) {
        saveState(DemoChecklistStateDto(tripId = tripId, items = items.map { it.toDto() }))
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoChecklistItemDto>) -> List<DemoChecklistItemDto>) {
        val state = stateFlow.first() ?: DemoChecklistStateDto(tripId = tripId)
        check(state.tripId == tripId) { "준비물 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        saveState(state.copy(items = transform(state.items)))
    }

    private suspend fun saveState(state: DemoChecklistStateDto) {
        dataStore.edit {
            it[DEMO_CHECKLIST_KEY] = json.encodeToString(DemoChecklistStateDto.serializer(), state)
        }
    }

    private fun ChecklistItem.toDto() = DemoChecklistItemDto(
        id = id,
        title = title,
        category = category.name,
        ownerUid = ownerUid,
        required = required,
        completed = completed,
        completedAtEpochMillis = completedAt?.toEpochMilli(),
        dueAtEpochMillis = dueAt?.toEpochMilli(),
    )

    private fun DemoChecklistItemDto.toDomain() = ChecklistItem(
        id = id,
        title = title,
        category = runCatching { ChecklistCategory.valueOf(category) }.getOrDefault(ChecklistCategory.ETC),
        ownerUid = ownerUid,
        required = required,
        completed = completed,
        completedAt = completedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        dueAt = dueAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    )
}
