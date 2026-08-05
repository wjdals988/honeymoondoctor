package com.jeongmin.honeymoondoctor.feature.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val statusFilter: ReservationStatus? = null, // null = 전체
    val needsAttentionCount: Int = 0, // 확인 필요 + 예약 필요 + 결제 필요
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReservationListViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    reservationRepository: ReservationRepository,
) : ViewModel() {

    private val statusFilter = MutableStateFlow<ReservationStatus?>(null)

    val uiState: StateFlow<ReservationListUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ReservationListUiState(loading = false))
            } else {
                combine(
                    reservationRepository.observeReservations(trip.id),
                    statusFilter,
                ) { reservations, filter ->
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
                            .sortedWith(compareBy(nullsLast()) { it.startAt }),
                        statusFilter = filter,
                        needsAttentionCount = reservations.count { it.status in attention },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationListUiState())

    fun setStatusFilter(status: ReservationStatus?) {
        statusFilter.value = status
    }
}
