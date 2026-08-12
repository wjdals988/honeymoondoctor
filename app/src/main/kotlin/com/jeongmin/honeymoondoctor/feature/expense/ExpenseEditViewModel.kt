package com.jeongmin.honeymoondoctor.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.data.exchange.ExchangeRateFetcher
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Expense
import com.jeongmin.honeymoondoctor.domain.model.ExpenseCategory
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.TravelCurrency
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ExchangeRateDefaults
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
    /**
     * 1 [currency] = ? KRW. KRW일 때만 "1"이 옳다. 외화로 바꾸면 **비워 둔다** —
     * 예전에는 "1"이 그대로 남아 €50이 50원으로 조용히 저장됐다(환율 > 0 검증을 "1"이
     * 통과해 버렸다). 비워 두면 저장이 막히고 이유가 표시된다.
     */
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
    /** 환율 자동 조회 결과 안내(예: "2026-08-11 기준 자동 조회"). 실패 사유도 여기 담는다. */
    val fxRateNotice: String? = null,
    val fxRateLoading: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    private val exchangeRateFetcher: ExchangeRateFetcher,
    private val appPreferences: AppPreferences,
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
    private val fxRateNotice = MutableStateFlow<String?>(null)
    private val fxRateLoading = MutableStateFlow(false)
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
                    combine(validationError, fxRateNotice, fxRateLoading) { error, notice, loading ->
                        Triple(error, notice, loading)
                    },
                ) { members, cities, itinerary, reservations, (error, notice, loading) ->
                    ExpenseEditUiState(
                        loading = false,
                        tripId = trip.id,
                        members = members,
                        cities = cities,
                        itinerary = itinerary,
                        reservations = reservations,
                        validationError = error,
                        fxRateNotice = notice,
                        fxRateLoading = loading,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseEditUiState())

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
        viewModelScope.launch {
            runCatching { cityRepository.create(tripId, city) }
                .onFailure { validationError.value = it.toUserMessage("도시를 추가하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }

    /**
     * 오늘 환율을 받아 환율 칸에 채운다. **제안일 뿐 저장은 여전히 스냅샷**이다 —
     * 사용자가 값을 고치면 고친 값이 이기고, 저장된 지출은 나중에 환율이 바뀌어도 흔들리지
     * 않는다(KrwConverter가 입력 시점 환율을 보존한다).
     *
     * 오프라인이 정상 사용 환경(기내·로밍)이므로 실패는 조용히 안내만 하고 직접 입력을 막지
     * 않는다.
     */
    fun fetchTodayRate() {
        val currency = _form.value?.currency ?: return
        // autoFetchable이 아닌 통화는 호출해도 API가 모른다고 답한다. 화면이 버튼을
        // 숨기지만, 여기서도 막아 두어야 호출 경로가 늘어도 안전하다.
        if (currency == TravelCurrency.KRW || !currency.autoFetchable || fxRateLoading.value) return
        fxRateLoading.value = true
        fxRateNotice.value = null
        viewModelScope.launch {
            exchangeRateFetcher.fetch(currency)
                .onSuccess { fetched ->
                    updateForm { it.copy(fxRateText = ExchangeRateDefaults.formatRate(fetched.krwPerUnit)) }
                    fxRateNotice.value = "${fetched.date} 유럽중앙은행 고시 기준입니다. 필요하면 직접 고치세요."
                }
                .onFailure {
                    fxRateNotice.value = "환율을 받지 못했습니다. 직접 입력해 주세요."
                }
            fxRateLoading.value = false
        }
    }

    fun updateForm(transform: (ExpenseEditForm) -> ExpenseEditForm) {
        val before = _form.value
        val after = before?.let(transform)
        _form.value = after
        validationError.value = null
        // 사용자가 환율을 직접 고치면 "자동 조회" 안내는 더 이상 사실이 아니다.
        if (before?.fxRateText != after?.fxRateText) fxRateNotice.value = null
        if (before != null && after != null && before.currency != after.currency) {
            onCurrencyChanged(after.currency)
        }
    }

    /**
     * 통화를 바꾸면 환율도 그 통화의 것으로 바꿔야 한다. 예전에는 이전 통화의 환율이
     * 그대로 남아, EUR로 1629.64를 받아 둔 뒤 JPY로 바꾸면 엔화가 1엔=1629원으로
     * 저장됐다. 직전에 그 통화로 쓴 값이 있으면 그것을, 없으면 빈 칸을 둔다.
     */
    private fun onCurrencyChanged(currency: TravelCurrency) {
        if (currency == TravelCurrency.KRW) {
            _form.value = _form.value?.copy(
                fxRateText = ExchangeRateDefaults.rateTextFor(currency, emptyMap()),
            )
            fxRateNotice.value = null
            return
        }
        viewModelScope.launch {
            val remembered = appPreferences.snapshot.first().lastExchangeRates
            // 통화를 다시 바꿨으면 늦게 도착한 이 결과는 버린다.
            if (_form.value?.currency != currency) return@launch
            val rateText = ExchangeRateDefaults.rateTextFor(currency, remembered)
            _form.value = _form.value?.copy(fxRateText = rateText)
            // 자동 조회를 못 하는 통화에 "불러오기를 누르세요"라고 하면, 화면에 없는
            // 버튼을 찾게 만든다(그 버튼은 autoFetchable일 때만 그려진다).
            fxRateNotice.value = when {
                rateText.isEmpty() && currency.autoFetchable ->
                    "환율을 입력하거나 \"오늘 환율 불러오기\"를 누르세요."
                rateText.isEmpty() ->
                    "${currency.code}은 자동 조회를 지원하지 않습니다. 환율을 직접 입력해 주세요."
                currency.autoFetchable ->
                    "직전에 쓴 환율입니다. 필요하면 고치거나 다시 불러오세요."
                else ->
                    "직전에 쓴 환율입니다. 필요하면 직접 고쳐 주세요."
            }
        }
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
        // 저장이 실패하면 화면을 닫지 않고 그 자리에 이유를 띄운다. 예전에는 성공을 전제로
        // onSaved()를 호출했고, 서버가 거부하면 예외가 그대로 앱을 죽였다.
        viewModelScope.launch {
            runCatching {
                if (form.expenseId == null) {
                    expenseRepository.create(tripId, expense)
                } else {
                    expenseRepository.update(tripId, expense)
                }
            }
                .onSuccess {
                    // 다음 지출 입력의 기본값으로 쓴다. 저장된 이 지출의 환율은 스냅샷으로
                    // 굳었고 여기 값과 무관하다.
                    if (form.currency != TravelCurrency.KRW) {
                        runCatching { appPreferences.setLastExchangeRate(form.currency.code, fxRate) }
                    }
                    onSaved()
                }
                .onFailure { validationError.value = it.toUserMessage("저장에 실패했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }
}
