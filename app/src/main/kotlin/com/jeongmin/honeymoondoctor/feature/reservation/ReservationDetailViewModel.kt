package com.jeongmin.honeymoondoctor.feature.reservation

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.data.local.db.VoucherMetadataEntity
import com.jeongmin.honeymoondoctor.data.voucher.VoucherStore
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class ReservationDetailUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val reservation: Reservation? = null,
    val linkedItinerary: ItineraryItem? = null,
    val vouchers: List<VoucherMetadataEntity> = emptyList(),
    val voucherError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReservationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCurrentTrip: ObserveCurrentTrip,
    private val reservationRepository: ReservationRepository,
    private val itineraryRepository: ItineraryRepository,
    private val voucherStore: VoucherStore,
) : ViewModel() {

    private val reservationId: String = checkNotNull(savedStateHandle["reservationId"])

    private val voucherError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReservationDetailUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ReservationDetailUiState(loading = false))
            } else {
                combine(
                    reservationRepository.observeReservations(trip.id),
                    itineraryRepository.observeItinerary(trip.id),
                    voucherStore.observeForReservation(reservationId),
                    voucherError,
                ) { reservations, itinerary, vouchers, error ->
                    val reservation = reservations.firstOrNull { it.id == reservationId }
                    ReservationDetailUiState(
                        loading = false,
                        tripId = trip.id,
                        reservation = reservation,
                        linkedItinerary = reservation?.linkedItineraryId
                            ?.let { linkedId -> itinerary.firstOrNull { it.id == linkedId } },
                        vouchers = vouchers,
                        voucherError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationDetailUiState())

    fun attachVoucher(uri: Uri) {
        viewModelScope.launch {
            voucherStore.attach(reservationId, uri)
                .onSuccess { voucherError.value = null }
                .onFailure { voucherError.value = it.message ?: "바우처 추가에 실패했습니다." }
        }
    }

    fun removeVoucher(entity: VoucherMetadataEntity) {
        viewModelScope.launch { voucherStore.remove(entity) }
    }

    fun buildOpenIntent(entity: VoucherMetadataEntity): Intent = voucherStore.buildOpenIntent(entity)

    /**
     * 예약 삭제(스펙 4장): 연쇄 삭제 없이 이 예약을 가리키는 일정의 reservationId 참조만 해제하고,
     * 기기 바우처는 사용자가 별도 확인한 경우에만 삭제한다.
     */
    fun delete(deleteVouchers: Boolean, onDeleted: () -> Unit) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            val linkedItems = itineraryRepository.observeItinerary(tripId).first()
                .filter { it.reservationId == reservationId }
            linkedItems.forEach { itineraryRepository.update(tripId, it.copy(reservationId = null)) }
            if (deleteVouchers) {
                voucherStore.removeAllForReservation(reservationId)
            }
            reservationRepository.delete(tripId, reservationId)
            onDeleted()
        }
    }
}
