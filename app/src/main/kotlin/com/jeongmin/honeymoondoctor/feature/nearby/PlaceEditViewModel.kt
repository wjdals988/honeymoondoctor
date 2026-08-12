package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.core.location.LocationProvider
import com.jeongmin.honeymoondoctor.data.maps.MapsLinkResolver
import com.jeongmin.honeymoondoctor.data.maps.MapsShortLink
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.usecase.Coordinates
import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlCoordinates
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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

data class PlaceEditForm(
    val placeId: String?, // null이면 새 장소
    val name: String = "",
    val cityId: String? = null,
    val category: PlaceCategory = PlaceCategory.ETC,
    val priority: PlacePriority = PlacePriority.WANT_TO_GO,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val mapsUrl: String = "",
    val notes: String = "",
    val preferredTimes: Set<PreferredTime> = emptySet(),
    /** 별 탭으로만 바뀐다. 가져오기로 들어온 4.7 같은 소수도 그대로 담는다. */
    val rating: Double? = null,
    /**
     * 화면에 입력 칸이 없다. 사람이 앉아서 "리뷰 12만개"를 세어 넣을 값이 아니라서
     * 뺐지만, TSV/JSON 가져오기가 채우고 추천 점수(PlaceRecommendationScorer)가
     * 읽으므로 필드는 남긴다. 편집하다가 조용히 지워지면 안 되니 폼에 실어 나른다.
     */
    val reviewCount: Long? = null,
    // 편집으로 바꾸지 않고 보존하는 값
    val visitedAt: Instant? = null,
    val sourceUpdatedAt: Instant? = null,
)

data class PlaceEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val cities: List<City> = emptyList(),
    val validationError: String? = null,
    /** 단축 링크를 펼치는 중. 네트워크를 타므로 버튼을 잠그고 진행 표시를 준다. */
    val resolvingLink: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    private val mapsLinkResolver: MapsLinkResolver,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
    observeCurrentTrip: ObserveCurrentTrip,
    private val cityRepository: CityRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["placeId"]

    private val validationError = MutableStateFlow<String?>(null)
    private val resolvingLink = MutableStateFlow(false)
    private val _form = MutableStateFlow<PlaceEditForm?>(null)
    val form: StateFlow<PlaceEditForm?> = _form

    private val tripFlow = observeCurrentTrip()

    val uiState: StateFlow<PlaceEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(PlaceEditUiState(loading = false))
            } else {
                combine(
                    cityRepository.observeCities(trip.id),
                    validationError,
                    resolvingLink,
                ) { cities, error, resolving ->
                    PlaceEditUiState(
                        loading = false,
                        tripId = trip.id,
                        cities = cities,
                        validationError = error,
                        resolvingLink = resolving,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceEditUiState())

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
            placeRepository.observePlaces(trip.id).first().firstOrNull { it.id == id }
        }
        _form.value = if (existing == null) {
            PlaceEditForm(placeId = null)
        } else {
            PlaceEditForm(
                placeId = existing.id,
                name = existing.name,
                cityId = existing.cityId,
                category = existing.category,
                priority = existing.priority,
                latitudeText = existing.latitude?.toString().orEmpty(),
                longitudeText = existing.longitude?.toString().orEmpty(),
                mapsUrl = existing.mapsUrl.orEmpty(),
                notes = existing.notes.orEmpty(),
                preferredTimes = existing.preferredTimes.toSet(),
                rating = existing.ratingSnapshot,
                reviewCount = existing.reviewCountSnapshot,
                visitedAt = existing.visitedAt,
                sourceUpdatedAt = existing.sourceUpdatedAt,
            )
        }
    }

    /**
     * 지금 있는 곳의 좌표를 위도·경도 칸에 채운다. 사람이 손으로 넣을 수 있는 값이 아니라
     * 버튼으로 대신 넣어 준다. 권한이 없거나 위치를 못 잡으면 이유를 표시한다.
     */
    fun fillWithCurrentLocation() {
        viewModelScope.launch {
            if (!locationProvider.hasLocationPermission()) {
                validationError.value = "위치 권한이 없습니다. 주변 탭에서 권한을 허용한 뒤 다시 시도해 주세요."
                return@launch
            }
            val location = runCatching { locationProvider.refreshCurrentLocation() }.getOrNull()
            if (location == null) {
                validationError.value = "현재 위치를 가져오지 못했습니다. 실외에서 잠시 뒤 다시 시도해 주세요."
                return@launch
            }
            validationError.value = null
            updateForm {
                it.copy(
                    latitudeText = location.latitude.toString(),
                    longitudeText = location.longitude.toString(),
                )
            }
        }
    }

    /**
     * Google Maps 링크(또는 좌표 문자열)에서 좌표를 뽑아 채운다. 지도 SDK가 없어
     * "지도에서 찍기"를 만들 수 없으므로, 구글 지도에서 "공유 → 링크 복사"한 것을
     * 붙여넣는 경로를 대신 제공한다.
     */
    fun fillFromMapsUrl() {
        val url = _form.value?.mapsUrl.orEmpty()
        if (url.isBlank()) return

        // 좌표가 이미 주소에 들어 있으면 네트워크를 쓰지 않는다.
        MapsUrlCoordinates.parse(url)?.let { applyCoordinates(it) ; return }

        // 구글 지도 앱이 공유하는 단축 링크에는 좌표가 없다. 펼쳐야 나온다.
        if (!MapsShortLink.isShortLink(url)) {
            validationError.value = "링크에서 좌표를 찾지 못했습니다. 구글 지도에서 \"공유 → 링크 복사\"한 주소를 넣어 주세요."
            return
        }
        resolvingLink.value = true
        validationError.value = null
        viewModelScope.launch {
            mapsLinkResolver.resolve(url)
                .onSuccess { expanded ->
                    val coordinates = MapsUrlCoordinates.parse(expanded)
                    if (coordinates == null) {
                        validationError.value = "링크를 펼쳤지만 좌표가 없었습니다. 위도·경도를 직접 넣어 주세요."
                    } else {
                        applyCoordinates(coordinates)
                    }
                }
                .onFailure {
                    validationError.value = it.toUserMessage("링크를 펼치지 못했습니다. 연결을 확인해 주세요.")
                }
            resolvingLink.value = false
        }
    }

    private fun applyCoordinates(coordinates: Coordinates) {
        validationError.value = null
        updateForm {
            it.copy(
                latitudeText = coordinates.latitude.toString(),
                longitudeText = coordinates.longitude.toString(),
            )
        }
    }

    fun updateForm(transform: (PlaceEditForm) -> PlaceEditForm) {
        _form.value = _form.value?.let(transform)
        validationError.value = null
    }

    fun createCity(city: City) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            runCatching { cityRepository.create(tripId, city) }
                .onFailure { validationError.value = it.toUserMessage("도시를 추가하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }

    fun togglePreferredTime(time: PreferredTime) {
        updateForm { form ->
            form.copy(
                preferredTimes = if (time in form.preferredTimes) {
                    form.preferredTimes - time
                } else {
                    form.preferredTimes + time
                },
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val form = _form.value ?: return
        val tripId = uiState.value.tripId ?: return

        if (form.name.isBlank()) {
            validationError.value = "장소명을 입력해 주세요."
            return
        }
        val latitude = form.latitudeText.trim().takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull()?.takeIf { lat -> lat in -90.0..90.0 } ?: run {
                validationError.value = "위도는 -90~90 사이 숫자여야 합니다."
                return
            }
        }
        val longitude = form.longitudeText.trim().takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull()?.takeIf { lng -> lng in -180.0..180.0 } ?: run {
                validationError.value = "경도는 -180~180 사이 숫자여야 합니다."
                return
            }
        }
        if ((latitude == null) != (longitude == null)) {
            validationError.value = "위도·경도는 함께 입력하거나 함께 비워야 합니다."
            return
        }
        // 별 탭은 1~5만 만들고 가져오기 값도 파서가 이미 검증한다 — 여기서 다시 막을 것이 없다.
        val rating = form.rating
        val reviewCount = form.reviewCount

        // 평점·리뷰 스냅샷이 새로 입력·변경되면 스냅샷 확인일을 지금으로 기록한다(실시간 수집 아님)
        val sourceUpdatedAt = if (rating != null || reviewCount != null) {
            form.sourceUpdatedAt ?: Instant.now()
        } else {
            null
        }

        val place = Place(
            id = form.placeId ?: "place-${UUID.randomUUID()}",
            name = form.name.trim(),
            cityId = form.cityId,
            category = form.category,
            priority = form.priority,
            latitude = latitude,
            longitude = longitude,
            mapsUrl = form.mapsUrl.trim().ifEmpty { null },
            notes = form.notes.trim().ifEmpty { null },
            visitedAt = form.visitedAt,
            ratingSnapshot = rating,
            reviewCountSnapshot = reviewCount,
            sourceUpdatedAt = sourceUpdatedAt,
            preferredTimes = form.preferredTimes.toList(),
        )
        viewModelScope.launch {
            runCatching {
                if (form.placeId == null) {
                    placeRepository.create(tripId, place)
                } else {
                    placeRepository.update(tripId, place)
                }
            }
                .onSuccess { onSaved() }
                .onFailure { validationError.value = it.toUserMessage("저장에 실패했습니다. 완료된 여행은 수정할 수 없습니다.") }
        }
    }
}
