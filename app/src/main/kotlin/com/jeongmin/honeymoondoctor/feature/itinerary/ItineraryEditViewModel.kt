package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.core.location.LocationProvider
import com.jeongmin.honeymoondoctor.data.maps.MapsLinkResolver
import com.jeongmin.honeymoondoctor.data.maps.MapsShortLink
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.Coordinates
import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlCoordinates
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
    val placeId: String? = null,
    val notes: String = "",
    val assigneeUid: String? = null,
    val estimatedKrwText: String = "",
    // 편집 화면에서 바꾸지 않고 보존만 하는 값들
    val status: ItineraryStatus = ItineraryStatus.PLANNED,
    val reservationId: String? = null,
)

/**
 * 일정 편집에서 곧바로 만드는 새 장소. 종전에는 주변 탭으로 나갔다 와야 했고, 그러면
 * 작성 중인 일정 폼이 사라졌다. 좌표 채우는 두 수단(현재 위치·구글 지도 링크)은
 * 장소 화면과 같은 것을 쓴다.
 */
data class NewPlaceForm(
    val name: String = "",
    val category: PlaceCategory = PlaceCategory.ETC,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val mapsUrl: String = "",
    val resolvingLink: Boolean = false,
    val error: String? = null,
) {
    val hasCoordinates: Boolean
        get() = latitudeText.toDoubleOrNull() != null && longitudeText.toDoubleOrNull() != null
    val canSave: Boolean get() = name.isNotBlank() && !resolvingLink
}

data class ItineraryEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val cities: List<City> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val places: List<Place> = emptyList(),
    val validationError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryEditViewModel @Inject constructor(
    private val observeCurrentTrip: ObserveCurrentTrip,
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    private val itineraryRepository: ItineraryRepository,
    private val placeRepository: PlaceRepository,
    private val locationProvider: LocationProvider,
    private val mapsLinkResolver: MapsLinkResolver,
) : ViewModel() {

    /** null이면 "새 장소 만들기" 대화상자가 닫힌 상태다. */
    private val _newPlaceForm = MutableStateFlow<NewPlaceForm?>(null)
    val newPlaceForm: StateFlow<NewPlaceForm?> = _newPlaceForm

    fun openNewPlaceForm() {
        // 일정 이름을 이미 적었으면 장소명 기본값으로 쓴다("센소지" 일정 → "센소지" 장소).
        _newPlaceForm.value = NewPlaceForm(name = _form.value?.title?.trim().orEmpty())
    }

    fun dismissNewPlaceForm() {
        _newPlaceForm.value = null
    }

    fun updateNewPlaceForm(transform: (NewPlaceForm) -> NewPlaceForm) {
        _newPlaceForm.value = _newPlaceForm.value?.let(transform)?.copy(error = null)
    }

    /** 지금 있는 곳의 좌표를 채운다(장소 화면과 같은 동작). */
    fun fillNewPlaceWithCurrentLocation() {
        viewModelScope.launch {
            if (!locationProvider.hasLocationPermission()) {
                _newPlaceForm.value = _newPlaceForm.value?.copy(
                    error = "위치 권한이 없습니다. 주변 탭에서 권한을 허용한 뒤 다시 시도해 주세요.",
                )
                return@launch
            }
            val location = runCatching { locationProvider.refreshCurrentLocation() }.getOrNull()
            _newPlaceForm.value = if (location == null) {
                _newPlaceForm.value?.copy(error = "현재 위치를 가져오지 못했습니다. 실외에서 잠시 뒤 다시 시도해 주세요.")
            } else {
                _newPlaceForm.value?.copy(
                    latitudeText = location.latitude.toString(),
                    longitudeText = location.longitude.toString(),
                    error = null,
                )
            }
        }
    }

    /** 구글 지도 링크(또는 좌표 문자열)에서 좌표를 뽑아 채운다(장소 화면과 같은 동작). */
    fun fillNewPlaceFromMapsUrl() {
        val current = _newPlaceForm.value ?: return
        val url = current.mapsUrl.trim()
        if (url.isBlank()) return

        MapsUrlCoordinates.parse(url)?.let { applyNewPlaceCoordinates(it) ; return }
        if (!MapsShortLink.isShortLink(url)) {
            _newPlaceForm.value = current.copy(
                error = "링크에서 좌표를 찾지 못했습니다. 구글 지도에서 \"공유 → 링크 복사\"한 주소를 넣어 주세요.",
            )
            return
        }
        _newPlaceForm.value = current.copy(resolvingLink = true, error = null)
        viewModelScope.launch {
            mapsLinkResolver.resolve(url)
                .onSuccess { expanded ->
                    val coordinates = MapsUrlCoordinates.parse(expanded)
                    if (coordinates == null) {
                        _newPlaceForm.value = _newPlaceForm.value?.copy(
                            resolvingLink = false,
                            error = "링크를 펼쳤지만 좌표가 없었습니다.",
                        )
                    } else {
                        applyNewPlaceCoordinates(coordinates)
                    }
                }
                .onFailure {
                    _newPlaceForm.value = _newPlaceForm.value?.copy(
                        resolvingLink = false,
                        error = it.toUserMessage("링크를 펼치지 못했습니다. 연결을 확인해 주세요."),
                    )
                }
        }
    }

    private fun applyNewPlaceCoordinates(coordinates: Coordinates) {
        _newPlaceForm.value = _newPlaceForm.value?.copy(
            latitudeText = coordinates.latitude.toString(),
            longitudeText = coordinates.longitude.toString(),
            resolvingLink = false,
            error = null,
        )
    }

    /**
     * 새 장소를 저장하고 곧바로 이 일정에 연결한다. 좌표가 없어도 저장은 되지만 지도에는
     * 안 뜨므로, 화면에서 그 점을 미리 알려 준다.
     */
    fun createAndLinkPlace() {
        val newPlace = _newPlaceForm.value ?: return
        val tripId = uiState.value.tripId ?: return
        if (!newPlace.canSave) return
        val place = Place(
            id = "place-${UUID.randomUUID()}",
            name = newPlace.name.trim(),
            cityId = _form.value?.cityId,
            category = newPlace.category,
            latitude = newPlace.latitudeText.toDoubleOrNull(),
            longitude = newPlace.longitudeText.toDoubleOrNull(),
            mapsUrl = newPlace.mapsUrl.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            runCatching { placeRepository.create(tripId, place) }
                .onSuccess {
                    updateForm { it.copy(placeId = place.id) }
                    _newPlaceForm.value = null
                }
                .onFailure {
                    _newPlaceForm.value = _newPlaceForm.value?.copy(
                        error = it.toUserMessage("장소를 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다."),
                    )
                }
        }
    }

    private val editingItemId: String? = savedStateHandle["itemId"]

    private val validationError = MutableStateFlow<String?>(null)

    private val _form = MutableStateFlow<ItineraryEditForm?>(null)
    val form: StateFlow<ItineraryEditForm?> = _form

    private val tripFlow = observeCurrentTrip()

    val uiState: StateFlow<ItineraryEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ItineraryEditUiState(loading = false))
            } else {
                combine(
                    cityRepository.observeCities(trip.id),
                    tripRepository.observeMembers(trip.id),
                    placeRepository.observePlaces(trip.id),
                    validationError,
                ) { cities, members, places, error ->
                    ItineraryEditUiState(
                        loading = false,
                        tripId = trip.id,
                        cities = cities,
                        members = members,
                        places = places,
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
            placeId = item.placeId,
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
            placeId = form.placeId,
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
