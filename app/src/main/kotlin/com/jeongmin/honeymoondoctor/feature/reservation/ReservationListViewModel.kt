package com.jeongmin.honeymoondoctor.feature.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.ui.matchesQuery
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ReservationListUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val reservations: List<Reservation> = emptyList(),
    /** 필터·검색 이전의 전체 건수. 빈 화면이 "아직 없음"인지 "검색 결과 없음"인지 가른다. */
    val totalCount: Int = 0,
    val statusFilter: ReservationStatus? = null, // null = 전체
    val query: String = "",
    val needsAttentionCount: Int = 0, // 확인 필요 + 예약 필요 + 결제 필요
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReservationListViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    reservationRepository: ReservationRepository,
) : ViewModel() {

    private val statusFilter = MutableStateFlow<ReservationStatus?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<ReservationListUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ReservationListUiState(loading = false))
            } else {
                combine(
                    reservationRepository.observeReservations(trip.id),
                    statusFilter,
                    query,
                ) { reservations, filter, queryValue ->
                    val attention = setOf(
                        ReservationStatus.NEEDS_CHECK,
                        ReservationStatus.NEEDS_BOOKING,
                        ReservationStatus.NEEDS_PAYMENT,
                    )
                    ReservationListUiState(
                        loading = false,
                        tripId = trip.id,
                        reservations = reservations
                            .filter { filter == null || it.status == filter }
                            // 예약번호까지 검색 대상 — "체크인 데스크에서 번호로 찾기"가 실사용이다.
                            .filter { matchesQuery(queryValue, it.title, it.vendor, it.confirmationCode) }
                            .sortedWith(compareBy(nullsLast()) { it.startAt }),
                        totalCount = reservations.size,
                        statusFilter = filter,
                        query = queryValue,
                        needsAttentionCount = reservations.count { it.status in attention },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationListUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStatusFilter(status: ReservationStatus?) {
        statusFilter.value = status
    }
}
