package com.jeongmin.honeymoondoctor.data.note

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.TripNote
import com.jeongmin.honeymoondoctor.domain.repository.TripNoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_NOTES_KEY = stringPreferencesKey("demo_notes_json")

@Serializable
data class DemoNoteStateDto(
    val tripId: String,
    val items: List<DemoNoteDto> = emptyList(),
)

@Serializable
data class DemoNoteDto(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val readAtEpochMillis: Long? = null,
)

/**
 * 데모 모드용. 상대가 없어 화면에서는 쪽지함 메뉴 자체를 숨기지만(MoreScreen),
 * DI 이중 구조를 다른 리포지토리와 동일하게 유지하기 위해 구현은 둔다.
 */
@Singleton
class DemoTripNoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : TripNoteRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    override fun observeNotes(tripId: String): Flow<List<TripNote>> = dataStore.data.map { prefs ->
        val state = prefs[DEMO_NOTES_KEY]?.let { json.decodeFromString(DemoNoteStateDto.serializer(), it) }
        if (state?.tripId != tripId) {
            emptyList()
        } else {
            state.items.map { it.toDomain() }.sortedBy { it.createdAt }
        }
    }

    override suspend fun send(tripId: String, note: TripNote) {
        mutate(tripId) { items -> items + note.toDto() }
    }

    override suspend fun markRead(tripId: String, noteId: String) {
        mutate(tripId) { items ->
            items.map { if (it.id == noteId) it.copy(readAtEpochMillis = Instant.now().toEpochMilli()) else it }
        }
    }

    override suspend fun delete(tripId: String, noteId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == noteId } }
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoNoteDto>) -> List<DemoNoteDto>) {
        val current = dataStore.data.first()[DEMO_NOTES_KEY]
            ?.let { json.decodeFromString(DemoNoteStateDto.serializer(), it) }
            ?.takeIf { it.tripId == tripId }
            ?: DemoNoteStateDto(tripId = tripId)
        val next = current.copy(items = transform(current.items))
        dataStore.edit { it[DEMO_NOTES_KEY] = json.encodeToString(DemoNoteStateDto.serializer(), next) }
    }

    private fun TripNote.toDto() = DemoNoteDto(
        id = id,
        senderUid = senderUid,
        text = text,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        readAtEpochMillis = readAt?.toEpochMilli(),
    )

    private fun DemoNoteDto.toDomain() = TripNote(
        id = id,
        senderUid = senderUid,
        text = text,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        readAt = readAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    )
}
