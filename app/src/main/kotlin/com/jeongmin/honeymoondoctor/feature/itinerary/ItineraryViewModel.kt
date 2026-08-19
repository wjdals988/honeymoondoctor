package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ItineraryConflictDetector
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 하루치 타임라인. 종일 일정은 시작 날짜에만 붙이고 기간을 라벨로 보여준다. */
data class ItineraryDay(
    val date: LocalDate,
    val dayNumber: Int?, // 여행 몇째 날(D1부터). 여행 기간 밖 일정이면 null.
    val label: String,
    val allDayItems: List<ItineraryItem>,
    val timedItems: List<ItineraryItem>,
)

data class ItineraryUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val days: List<ItineraryDay> = emptyList(),
    val conflictIds: Set<String> = emptySet(),
    val places: List<Place> = emptyList(),
    val actionError: String? = null,
)

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val observeCurrentTrip: ObserveCurrentTrip,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val reservationRepository: ReservationRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val actionError = ActionErrorState()

    /** 삭제 되돌리기. 화면이 pending을 구독해 스낵바를 띄운다. */
    val undoDelete = UndoDeleteState<ItineraryItem>()

    val uiState: StateFlow<ItineraryUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(ItineraryUiState(loading = false))
            } else {
                observeCurrentTrip().flatMapLatest { trip ->
                    if (trip == null) {
                        flowOf(ItineraryUiState(loading = false))
                    } else {
                        combine(
                            itineraryRepository.observeItinerary(trip.id),
                            placeRepository.observePlaces(trip.id),
                            actionError.message,
                        ) { items, places, error ->
                            ItineraryUiState(
                                loading = false,
                                trip = trip,
                                days = buildDays(trip, items),
                                conflictIds = ItineraryConflictDetector.findConflictingIds(items),
                                places = places,
                                actionError = error,
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItineraryUiState())

    fun clearActionError() = actionError.clear()

    fun setStatus(item: ItineraryItem, status: ItineraryStatus) {
        val tripId = uiState.value.trip?.id ?: return
        viewModelScope.launch {
            actionError.runReporting("일정 상태를 바꾸지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                itineraryRepository.update(tripId, item.copy(status = status))
            }
        }
    }

    /**
     * 일정 삭제. 연결 데이터 경고·확인은 화면(다이얼로그)에서 이미 거친 뒤 호출된다.
     * 이 일정을 가리키는 예약의 linkedItineraryId는 참조만 해제한다
     * (스펙 4장 — 자동 연쇄 삭제 금지, 참조 해제만).
     */
    fun delete(item: ItineraryItem) {
        val tripId = uiState.value.trip?.id ?: return
        viewModelScope.launch {
            val deleted = actionError.runReporting("일정을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                reservationRepository.observeReservations(tripId).first()
                    .filter { it.linkedItineraryId == item.id }
                    .forEach { reservationRepository.update(tripId, it.copy(linkedItineraryId = null)) }
                itineraryRepository.delete(tripId, item.id)
            }
            if (deleted) undoDelete.offer(item, "일정을 삭제했습니다.")
        }
    }

    /**
     * 되돌리기: 일정 자체는 같은 id로 완전 복원된다. 삭제 때 해제한 예약의
     * linkedItineraryId는 **복원하지 않는다** — 스낵바가 떠 있는 몇 초 사이 상대가
     * 그 예약을 다른 일정에 연결했을 수 있어, 덮어쓰면 상대의 변경을 지운다.
     * 링크는 예약함에서 다시 연결하면 된다.
     */
    fun restoreDeleted() {
        val tripId = uiState.value.trip?.id ?: return
        val item = undoDelete.consume() ?: return
        viewModelScope.launch {
            actionError.runReporting("일정을 복원하지 못했습니다.") {
                itineraryRepository.create(tripId, item)
            }
        }
    }

    private fun buildDays(trip: Trip, items: List<ItineraryItem>): List<ItineraryDay> {
        val tripStart = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
        val tripEnd = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()

        // 각 일정은 "자기 시간대 기준 시작 날짜"에 붙인다(현지에서 보는 날짜와 일치).
        val itemsByDate = items.groupBy { LocalTimes.toLocalDate(it.startAt, it.timeZone) }

        val tripDates = if (tripStart != null && tripEnd != null && !tripEnd.isBefore(tripStart)) {
            generateSequence(tripStart) { it.plusDays(1) }.takeWhile { !it.isAfter(tripEnd) }.toList()
        } else {
            emptyList()
        }
        val allDates = (tripDates + itemsByDate.keys).distinct().sorted()

        return allDates.map { date ->
            val dayItems = itemsByDate[date].orEmpty()
            val dayNumber = if (tripStart != null && !date.isBefore(tripStart) &&
                (tripEnd == null || !date.isAfter(tripEnd))
            ) {
                ChronoUnit.DAYS.between(tripStart, date).toInt() + 1
            } else {
                null
            }
            ItineraryDay(
                date = date,
                dayNumber = dayNumber,
                label = date.format(dayHeaderFormatter),
                allDayItems = dayItems.filter { it.allDay },
                timedItems = dayItems.filterNot { it.allDay }.sortedBy { it.startAt },
            )
        }
    }
}
