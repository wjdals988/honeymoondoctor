package com.jeongmin.honeymoondoctor.data.decision

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.Decision
import com.jeongmin.honeymoondoctor.domain.model.DecisionCategory
import com.jeongmin.honeymoondoctor.domain.model.DecisionOption
import com.jeongmin.honeymoondoctor.domain.model.DecisionStatus
import com.jeongmin.honeymoondoctor.domain.repository.DecisionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_DECISIONS_KEY = stringPreferencesKey("demo_decisions_json")

@Serializable
data class DemoDecisionStateDto(
    val tripId: String,
    val items: List<DemoDecisionDto> = emptyList(),
)

@Serializable
data class DemoDecisionDto(
    val id: String,
    val title: String,
    val category: String,
    val status: String,
    val options: List<DemoDecisionOptionDto> = emptyList(),
    val selectedOptionId: String? = null,
    val dueAtEpochMillis: Long? = null,
    val notes: String? = null,
)

@Serializable
data class DemoDecisionOptionDto(
    val id: String,
    val label: String,
)

@Singleton
class DemoDecisionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DecisionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoDecisionStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_DECISIONS_KEY]?.let { json.decodeFromString(DemoDecisionStateDto.serializer(), it) }
    }

    override fun observeDecisions(tripId: String): Flow<List<Decision>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
    }

    override suspend fun create(tripId: String, decision: Decision) {
        mutate(tripId) { it + decision.toDto() }
    }

    override suspend fun update(tripId: String, decision: Decision) {
        mutate(tripId) { items -> items.map { if (it.id == decision.id) decision.toDto() else it } }
    }

    override suspend fun delete(tripId: String, decisionId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == decisionId } }
    }

    suspend fun seedForNewTrip(tripId: String, items: List<Decision>) {
        saveState(DemoDecisionStateDto(tripId = tripId, items = items.map { it.toDto() }))
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoDecisionDto>) -> List<DemoDecisionDto>) {
        val state = stateFlow.first() ?: DemoDecisionStateDto(tripId = tripId)
        check(state.tripId == tripId) { "결정함 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        saveState(state.copy(items = transform(state.items)))
    }

    private suspend fun saveState(state: DemoDecisionStateDto) {
        dataStore.edit {
            it[DEMO_DECISIONS_KEY] = json.encodeToString(DemoDecisionStateDto.serializer(), state)
        }
    }

    private fun Decision.toDto() = DemoDecisionDto(
        id = id,
        title = title,
        category = category.name,
        status = status.name,
        options = options.map { DemoDecisionOptionDto(it.id, it.label) },
        selectedOptionId = selectedOptionId,
        dueAtEpochMillis = dueAt?.toEpochMilli(),
        notes = notes,
    )

    private fun DemoDecisionDto.toDomain() = Decision(
        id = id,
        title = title,
        category = runCatching { DecisionCategory.valueOf(category) }.getOrDefault(DecisionCategory.ETC),
        status = runCatching { DecisionStatus.valueOf(status) }.getOrDefault(DecisionStatus.NEEDS_DECISION),
        options = options.map { DecisionOption(it.id, it.label) },
        selectedOptionId = selectedOptionId,
        dueAt = dueAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        notes = notes,
    )
}
