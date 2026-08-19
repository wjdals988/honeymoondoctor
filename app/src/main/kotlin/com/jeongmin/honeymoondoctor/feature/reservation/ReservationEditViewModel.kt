package com.jeongmin.honeymoondoctor.feature.reservation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReservationEditForm(
    val reservationId: String?, // null이면 새 예약
    val title: String = "",
    val vendor: String = "",
    val type: ReservationType = ReservationType.ETC,
    val status: ReservationStatus = ReservationStatus.NEEDS_CHECK,
    val confirmationCode: String = "",
    val pin: String = "",
    val hasSchedule: Boolean = false,
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime = LocalTime.of(10, 0),
    val endDate: LocalDate,
    val endTime: LocalTime = LocalTime.of(11, 0),
    val timeZone: String = "Asia/Seoul",
    val endTimeZone: String? = null,
    val linkedItineraryId: String? = null,
    val estimatedKrwText: String = "",
    val notes: String = "",
    val assigneeUid: String? = null,
)

data class ReservationEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val itinerary: List<ItineraryItem> = emptyList(),
    val members: List<TripMember> = emptyList(),
    /** 시간대 선택 후보를 이 여행의 도시에서 뽑기 위해 필요하다. */
    val cityZoneIds: List<String> = emptyList(),
    val validationError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReservationEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val reservationRepository: ReservationRepository,
    cityRepository: com.jeongmin.honeymoondoctor.domain.repository.CityRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["reservationId"]

    private val validationError = MutableStateFlow<String?>(null)
    private val _form = MutableStateFlow<ReservationEditForm?>(null)
    val form: StateFlow<ReservationEditForm?> = _form

    private val tripFlow = observeCurrentTrip()

    val uiState: StateFlow<ReservationEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ReservationEditUiState(loading = false))
            } else {
                combine(
                    itineraryRepository.observeItinerary(trip.id),
                    tripRepository.observeMembers(trip.id),
                    cityRepository.observeCities(trip.id),
                    validationError,
                ) { itinerary, members, cities, error ->
                    ReservationEditUiState(
                        loading = false,
                        tripId = trip.id,
                        itinerary = itinerary,
                        members = members,
                        cityZoneIds = cities.map { it.timeZoneId },
                        validationError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationEditUiState())

    init {
        // 초기 데이터 읽기가 실패해도(권한·네트워크) 크래시가 아니라 오류 표시로 끝낸다.
        viewModelScope.launch {
            runCatching { initializeForm() }
                .onFailure { validationError.value = it.toUserMessage("내용을 불러오지 못했습니다.") }
        }
    }

    private suspend fun initializeForm() {
        val trip = tripFlow.first { it != null } ?: return
        val existing = editingId?.let { id ->
            reservationRepository.observeReservations(trip.id).first().firstOrNull { it.id == id }
        }
        val tripStart = runCatching { LocalDate.parse(trip.startDate) }.getOrNull() ?: LocalDate.now()
        _form.value = if (existing == null) {
            ReservationEditForm(reservationId = null, startDate = tripStart, endDate = tripStart)
        } else {
            val startLocal = existing.startAt?.let { LocalTimes.toLocalDateTime(it, existing.timeZone) }
            val endLocal = existing.endAt?.let { LocalTimes.toLocalDateTime(it, existing.effectiveEndTimeZone) }
            ReservationEditForm(
                reservationId = existing.id,
                title = existing.title,
                vendor = existing.vendor,
                type = existing.type,
                status = existing.status,
                confirmationCode = existing.confirmationCode.orEmpty(),
                pin = existing.pin.orEmpty(),
                hasSchedule = startLocal != null,
                allDay = existing.allDay,
                startDate = startLocal?.toLocalDate() ?: tripStart,
                startTime = startLocal?.toLocalTime() ?: LocalTime.of(10, 0),
                endDate = endLocal?.toLocalDate() ?: startLocal?.toLocalDate() ?: tripStart,
                endTime = endLocal?.toLocalTime() ?: LocalTime.of(11, 0),
                timeZone = existing.timeZone,
                endTimeZone = existing.endTimeZone,
                linkedItineraryId = existing.linkedItineraryId,
                estimatedKrwText = existing.estimatedKrw?.toString().orEmpty(),
                notes = existing.notes.orEmpty(),
                assigneeUid = existing.assigneeUid,
            )
        }
    }

    fun updateForm(transform: (ReservationEditForm) -> ReservationEditForm) {
        _form.value = _form.value?.let(transform)
        validationError.value = null
    }

    fun save(onSaved: () -> Unit) {
        val form = _form.value ?: return
        val tripId = uiState.value.tripId ?: return

        if (form.title.isBlank()) {
            validationError.value = "예약명을 입력해 주세요."
            return
        }
        val estimatedKrw = form.estimatedKrwText.trim().let { text ->
            if (text.isEmpty()) {
                null
            } else {
                text.replace(",", "").toLongOrNull() ?: run {
                    validationError.value = "예상 비용은 숫자만 입력해 주세요."
                    return
                }
            }
        }

        val endZoneId = form.endTimeZone ?: form.timeZone
        val startAt = if (!form.hasSchedule) {
            null
        } else if (form.allDay) {
            LocalTimes.startOfDay(form.startDate, form.timeZone)
        } else {
            form.startDate.atTime(form.startTime).atZone(ZoneId.of(form.timeZone)).toInstant()
        }
        val endAt = if (!form.hasSchedule) {
            null
        } else if (form.allDay) {
            LocalTimes.endOfDay(form.endDate, endZoneId)
        } else {
            form.endDate.atTime(form.endTime).atZone(ZoneId.of(endZoneId)).toInstant()
        }
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            validationError.value = "종료가 시작보다 빠릅니다. 시간대까지 감안해 확인해 주세요."
            return
        }

        val reservation = Reservation(
            id = form.reservationId ?: "resv-${UUID.randomUUID()}",
            type = form.type,
            vendor = form.vendor.trim(),
            title = form.title.trim(),
            status = form.status,
            confirmationCode = form.confirmationCode.trim().ifEmpty { null },
            pin = form.pin.trim().ifEmpty { null },
            startAt = startAt,
            endAt = endAt,
            allDay = form.hasSchedule && form.allDay,
            timeZone = form.timeZone,
            endTimeZone = form.endTimeZone?.takeIf { it != form.timeZone },
            linkedItineraryId = form.linkedItineraryId,
            estimatedKrw = estimatedKrw,
            notes = form.notes.trim().ifEmpty { null },
            assigneeUid = form.assigneeUid,
        )
        viewModelScope.launch {
            runCatching {
                if (form.reservationId == null) {
                    reservationRepository.create(tripId, reservation)
                } else {
                    reservationRepository.update(tripId, reservation)
                }
            }
                .onSuccess { onSaved() }
                .onFailure { validationError.value = it.toUserMessage("저장에 실패했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }
}
