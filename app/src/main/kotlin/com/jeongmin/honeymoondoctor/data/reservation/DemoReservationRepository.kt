package com.jeongmin.honeymoondoctor.data.reservation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val DEMO_RESERVATIONS_KEY = stringPreferencesKey("demo_reservations_json")

@Serializable
data class DemoReservationStateDto(
    val tripId: String,
    val items: List<DemoReservationDto> = emptyList(),
)

/**
 * 예약번호·PIN도 데모 스냅샷에 원문으로 저장된다(이 기기의 DataStore 안에만 존재).
 * 로그·목록 표시는 항상 maskSecret()을 거친다.
 */
@Serializable
data class DemoReservationDto(
    val id: String,
    val type: String,
    val vendor: String,
    val title: String,
    val status: String,
    val confirmationCode: String? = null,
    val pin: String? = null,
    val startAtEpochMillis: Long? = null,
    val endAtEpochMillis: Long? = null,
    val allDay: Boolean = false,
    val timeZone: String = "Asia/Seoul",
    val endTimeZone: String? = null,
    val linkedItineraryId: String? = null,
    val estimatedKrw: Long? = null,
    val notes: String? = null,
    val assigneeUid: String? = null,
)

@Singleton
class DemoReservationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReservationRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoReservationStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_RESERVATIONS_KEY]?.let { json.decodeFromString(DemoReservationStateDto.serializer(), it) }
    }

    override fun observeReservations(tripId: String): Flow<List<Reservation>> = stateFlow.map { state ->
        state?.takeIf { it.tripId == tripId }?.items.orEmpty().map { it.toDomain() }
    }

    override suspend fun create(tripId: String, reservation: Reservation) {
        mutate(tripId) { it + reservation.toDto() }
    }

    override suspend fun update(tripId: String, reservation: Reservation) {
        mutate(tripId) { items -> items.map { if (it.id == reservation.id) reservation.toDto() else it } }
    }

    override suspend fun delete(tripId: String, reservationId: String) {
        mutate(tripId) { items -> items.filterNot { it.id == reservationId } }
    }

    suspend fun seedForNewTrip(tripId: String, items: List<Reservation>) {
        saveState(DemoReservationStateDto(tripId = tripId, items = items.map { it.toDto() }))
    }

    private suspend fun mutate(tripId: String, transform: (List<DemoReservationDto>) -> List<DemoReservationDto>) {
        val state = stateFlow.first() ?: DemoReservationStateDto(tripId = tripId)
        check(state.tripId == tripId) { "예약 저장소가 다른 여행을 가리키고 있습니다: ${state.tripId}" }
        saveState(state.copy(items = transform(state.items)))
    }

    private suspend fun saveState(state: DemoReservationStateDto) {
        dataStore.edit {
            it[DEMO_RESERVATIONS_KEY] = json.encodeToString(DemoReservationStateDto.serializer(), state)
        }
    }

    private fun Reservation.toDto() = DemoReservationDto(
        id = id,
        type = type.name,
        vendor = vendor,
        title = title,
        status = status.name,
        confirmationCode = confirmationCode,
        pin = pin,
        startAtEpochMillis = startAt?.toEpochMilli(),
        endAtEpochMillis = endAt?.toEpochMilli(),
        allDay = allDay,
        timeZone = timeZone,
        endTimeZone = endTimeZone,
        linkedItineraryId = linkedItineraryId,
        estimatedKrw = estimatedKrw,
        notes = notes,
        assigneeUid = assigneeUid,
    )

    private fun DemoReservationDto.toDomain() = Reservation(
        id = id,
        type = runCatching { ReservationType.valueOf(type) }.getOrDefault(ReservationType.ETC),
        vendor = vendor,
        title = title,
        status = runCatching { ReservationStatus.valueOf(status) }.getOrDefault(ReservationStatus.NEEDS_CHECK),
        confirmationCode = confirmationCode,
        pin = pin,
        startAt = startAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        endAt = endAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        allDay = allDay,
        timeZone = timeZone,
        endTimeZone = endTimeZone,
        linkedItineraryId = linkedItineraryId,
        estimatedKrw = estimatedKrw,
        notes = notes,
        assigneeUid = assigneeUid,
    )
}
