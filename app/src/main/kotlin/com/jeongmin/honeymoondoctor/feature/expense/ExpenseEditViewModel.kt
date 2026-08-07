package com.jeongmin.honeymoondoctor.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.KrwConverter
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

data class ExpenseEditForm(
    val expenseId: String?, // null이면 새 지출
    val amountText: String = "",
    val currency: TravelCurrency = TravelCurrency.KRW,
    val fxRateText: String = "1",
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val paidByUid: String? = null,
    val shared: Boolean = true,
    val cityId: String? = null,
    val spentDate: LocalDate,
    val memo: String = "",
    val linkedItineraryId: String? = null,
    val linkedReservationId: String? = null,
) {
    /** 화면에 실시간으로 보여주는 KRW 환산 미리보기(HALF_UP). 입력이 불완전하면 null. */
    val previewKrw: Long?
        get() {
            val amountMinor = KrwConverter.parseAmountMinor(amountText, currency) ?: return null
            val rate = if (currency == TravelCurrency.KRW) 1.0 else fxRateText.replace(",", "").toDoubleOrNull() ?: return null
            return KrwConverter.toKrw(amountMinor, currency, rate)
        }
}

data class ExpenseEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val members: List<TripMember> = emptyList(),
    val cities: List<City> = emptyList(),
    val itinerary: List<ItineraryItem> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val validationError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    itineraryRepository: ItineraryRepository,
    reservationRepository: ReservationRepository,
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["expenseId"]

    private val validationError = MutableStateFlow<String?>(null)
    private val _form = MutableStateFlow<ExpenseEditForm?>(null)
    val form: StateFlow<ExpenseEditForm?> = _form

    private val tripFlow = observeCurrentTrip()

    val uiState: StateFlow<ExpenseEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ExpenseEditUiState(loading = false))
            } else {
                combine(
                    tripRepository.observeMembers(trip.id),
                    cityRepository.observeCities(trip.id),
                    itineraryRepository.observeItinerary(trip.id),
                    reservationRepository.observeReservations(trip.id),
                    validationError,
                ) { members, cities, itinerary, reservations, error ->
                    ExpenseEditUiState(
                        loading = false,
                        tripId = trip.id,
                        members = members,
                        cities = cities,
                        itinerary = itinerary,
                        reservations = reservations,
                        validationError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseEditUiState())

    init {
        viewModelScope.launch { initializeForm() }
    }

    private suspend fun initializeForm() {
        val trip = tripFlow.first { it != null } ?: return
        val existing = editingId?.let { id ->
            expenseRepository.observeExpenses(trip.id).first().firstOrNull { it.id == id }
        }
        _form.value = if (existing == null) {
            ExpenseEditForm(expenseId = null, spentDate = LocalDate.now())
        } else {
            ExpenseEditForm(
                expenseId = existing.id,
                amountText = KrwConverter.formatMajor(existing.amountMinor, existing.currency),
                currency = existing.currency,
                fxRateText = existing.fxRateToKrw.toString(),
                category = existing.category,
                paidByUid = existing.paidByUid,
                shared = existing.shared,
                cityId = existing.cityId,
                spentDate = existing.spentAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                memo = existing.memo.orEmpty(),
                linkedItineraryId = existing.linkedItineraryId,
                linkedReservationId = existing.linkedReservationId,
            )
        }
    }

    fun createCity(city: City) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch { cityRepository.create(tripId, city) }
    }

    fun updateForm(transform: (ExpenseEditForm) -> ExpenseEditForm) {
        _form.value = _form.value?.let(transform)
        validationError.value = null
    }

    fun save(onSaved: () -> Unit) {
        val form = _form.value ?: return
        val tripId = uiState.value.tripId ?: return

        val amountMinor = KrwConverter.parseAmountMinor(form.amountText, form.currency) ?: run {
            validationError.value = when (form.currency) {
                TravelCurrency.KRW -> "금액은 원 단위 정수로 입력해 주세요."
                else -> "금액은 소수 ${form.currency.minorDigits}자리까지의 숫자로 입력해 주세요."
            }
            return
        }
        val fxRate = if (form.currency == TravelCurrency.KRW) {
            1.0
        } else {
            form.fxRateText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: run {
                validationError.value = "환율(1 ${form.currency.code} = ? KRW)을 입력해 주세요."
                return
            }
        }

        val expense = Expense(
            id = form.expenseId ?: "exp-${UUID.randomUUID()}",
            amountMinor = amountMinor,
            currency = form.currency,
            fxRateToKrw = fxRate,
            amountKrw = KrwConverter.toKrw(amountMinor, form.currency, fxRate),
            category = form.category,
            paidByUid = form.paidByUid,
            shared = form.shared,
            cityId = form.cityId,
            // 지출 날짜는 정오(KST)로 저장해 시간대 경계에서 날짜가 밀리지 않게 한다
            spentAt = form.spentDate.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            linkedItineraryId = form.linkedItineraryId,
            linkedReservationId = form.linkedReservationId,
            memo = form.memo.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            if (form.expenseId == null) {
                expenseRepository.create(tripId, expense)
            } else {
                expenseRepository.update(tripId, expense)
            }
            onSaved()
        }
    }
}
