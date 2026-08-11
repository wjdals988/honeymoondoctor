package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
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

/** 편집 폼 상태. 시각은 저장 직전에만 UTC Instant로 변환하고, 폼에서는 로컬 날짜/시각으로 다룬다. */
data class ItineraryEditForm(
    val itemId: String?, // null이면 새 일정
    val title: String = "",
    val type: ItineraryType = ItineraryType.SIGHTSEEING,
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime = LocalTime.of(10, 0),
    val hasEnd: Boolean = true,
    val endDate: LocalDate,
    val endTime: LocalTime = LocalTime.of(11, 0),
    val cityId: String? = null,
    val timeZone: String = "Asia/Seoul",
    val endTimeZone: String? = null, // null = 시작과 동일
    val location: String = "",
    val address: String = "",
    val notes: String = "",
    val assigneeUid: String? = null,
    val estimatedKrwText: String = "",
    // 편집 화면에서 바꾸지 않고 보존만 하는 값들
    val status: ItineraryStatus = ItineraryStatus.PLANNED,
    val reservationId: String? = null,
)

data class ItineraryEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val cities: List<City> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val validationError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val editingItemId: String? = savedStateHandle["itemId"]

    private val validationError = MutableStateFlow<String?>(null)

    private val _form = MutableStateFlow<ItineraryEditForm?>(null)
    val form: StateFlow<ItineraryEditForm?> = _form

    private val tripFlow = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else tripRepository.observeMyTrip(user.uid)
    }

    val uiState: StateFlow<ItineraryEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ItineraryEditUiState(loading = false))
            } else {
                combine(
                    cityRepository.observeCities(trip.id),
                    tripRepository.observeMembers(trip.id),
                    validationError,
                ) { cities, members, error ->
                    ItineraryEditUiState(
                        loading = false,
                        tripId = trip.id,
                        cities = cities,
                        members = members,
                        validationError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItineraryEditUiState())

    init {
        // 초기 데이터 읽기가 실패해도(권한·네트워크) 크래시가 아니라 오류 표시로 끝낸다.
        viewModelScope.launch {
            runCatching { initializeForm() }
                .onFailure { validationError.value = it.toUserMessage("내용을 불러오지 못했습니다.") }
        }
    }

    private suspend fun initializeForm() {
        val trip = tripFlow.first { it != null } ?: return
        val existing = editingItemId?.let { id ->
            itineraryRepository.observeItinerary(trip.id).first().firstOrNull { it.id == id }
        }
        _form.value = if (existing != null) {
            toForm(existing)
        } else {
            val cities = cityRepository.observeCities(trip.id).first()
            val today = LocalDate.now()
            val tripStart = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
            val tripEnd = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()
            // 기본 날짜: 여행 중이면 오늘, 아니면 여행 첫날
            val defaultDate = when {
                tripStart == null -> today
                today.isBefore(tripStart) -> tripStart
                tripEnd != null && today.isAfter(tripEnd) -> tripStart
                else -> today
            }
            val defaultCity = cities.firstOrNull { city ->
                val s = city.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val e = city.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                s != null && e != null && !defaultDate.isBefore(s) && !defaultDate.isAfter(e)
            } ?: cities.firstOrNull()
            ItineraryEditForm(
                itemId = null,
                startDate = defaultDate,
                endDate = defaultDate,
                cityId = defaultCity?.id,
                timeZone = defaultCity?.timeZoneId ?: "Asia/Seoul",
            )
        }
    }

    private fun toForm(item: ItineraryItem): ItineraryEditForm {
        val startLocal = LocalTimes.toLocalDateTime(item.startAt, item.timeZone)
        val endLocal = item.endAt?.let { LocalTimes.toLocalDateTime(it, item.effectiveEndTimeZone) }
        return ItineraryEditForm(
            itemId = item.id,
            title = item.title,
            type = item.type,
            allDay = item.allDay,
            startDate = startLocal.toLocalDate(),
            startTime = startLocal.toLocalTime(),
            hasEnd = endLocal != null,
            endDate = endLocal?.toLocalDate() ?: startLocal.toLocalDate(),
            endTime = endLocal?.toLocalTime() ?: startLocal.toLocalTime().plusHours(1),
            cityId = item.cityId,
            timeZone = item.timeZone,
            endTimeZone = item.endTimeZone,
            location = item.location.orEmpty(),
            address = item.address.orEmpty(),
            notes = item.notes.orEmpty(),
            assigneeUid = item.assigneeUid,
            estimatedKrwText = item.estimatedKrw?.toString().orEmpty(),
            status = item.status,
            reservationId = item.reservationId,
        )
    }

    fun updateForm(transform: (ItineraryEditForm) -> ItineraryEditForm) {
        _form.value = _form.value?.let(transform)
        validationError.value = null
    }

    /** 도시를 고르면 시간대도 그 도시 기준으로 함께 바꾼다(직접 다시 바꿀 수 있음). */
    fun selectCity(city: City?) {
        updateForm { form ->
            form.copy(cityId = city?.id, timeZone = city?.timeZoneId ?: form.timeZone)
        }
    }

    fun createCity(city: City) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            runCatching { cityRepository.create(tripId, city) }
                .onFailure { validationError.value = it.toUserMessage("도시를 추가하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }

    fun save(onSaved: () -> Unit) {
        val form = _form.value ?: return
        val tripId = uiState.value.tripId ?: return

        if (form.title.isBlank()) {
            validationError.value = "일정 이름을 입력해 주세요."
            return
        }
        val estimatedKrw = form.estimatedKrwText.trim().let { text ->
            if (text.isEmpty()) {
                null
            } else {
                text.replace(",", "").toLongOrNull() ?: run {
                    validationError.value = "예상 경비는 숫자만 입력해 주세요."
                    return
                }
            }
        }

        val zone = ZoneId.of(form.timeZone)
        val endZoneId = form.endTimeZone ?: form.timeZone
        val startAt = if (form.allDay) {
            LocalTimes.startOfDay(form.startDate, form.timeZone)
        } else {
            form.startDate.atTime(form.startTime).atZone(zone).toInstant()
        }
        val endAt = when {
            form.allDay -> LocalTimes.endOfDay(form.endDate, endZoneId)
            form.hasEnd -> form.endDate.atTime(form.endTime).atZone(ZoneId.of(endZoneId)).toInstant()
            else -> null
        }
        if (endAt != null && !endAt.isAfter(startAt)) {
            validationError.value = "종료가 시작보다 빠릅니다. 시간대까지 감안해 확인해 주세요."
            return
        }

        val item = ItineraryItem(
            id = form.itemId ?: "itin-${UUID.randomUUID()}",
            title = form.title.trim(),
            type = form.type,
            startAt = startAt,
            endAt = endAt,
            allDay = form.allDay,
            timeZone = form.timeZone,
            endTimeZone = form.endTimeZone?.takeIf { it != form.timeZone },
            cityId = form.cityId,
            location = form.location.trim().ifEmpty { null },
            address = form.address.trim().ifEmpty { null },
            status = form.status,
            assigneeUid = form.assigneeUid,
            reservationId = form.reservationId,
            estimatedKrw = estimatedKrw,
            notes = form.notes.trim().ifEmpty { null },
        )
        // 저장이 실패하면 화면을 닫지 않고 그 자리에 이유를 띄운다(예전에는 예외가 앱을 죽였다).
        viewModelScope.launch {
            runCatching {
                if (form.itemId == null) {
                    itineraryRepository.create(tripId, item)
                } else {
                    itineraryRepository.update(tripId, item)
                }
            }
                .onSuccess { onSaved() }
                .onFailure { validationError.value = it.toUserMessage("저장에 실패했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }
}
