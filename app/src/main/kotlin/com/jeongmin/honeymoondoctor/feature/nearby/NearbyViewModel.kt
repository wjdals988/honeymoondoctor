package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.location.LocationProvider
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.LastKnownLocation
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.usecase.Haversine
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import com.jeongmin.honeymoondoctor.domain.usecase.PlaceRecommendationScorer
import com.jeongmin.honeymoondoctor.domain.usecase.PlaceScore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NearbySort(val labelKo: String) {
    RECOMMENDED("추천순"),
    DISTANCE("거리순"),
}

data class ScoredPlace(
    val place: Place,
    val distanceMeters: Double?,
    val score: PlaceScore,
)

data class NearbyUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val hasLocationPermission: Boolean = false,
    val refreshing: Boolean = false,
    /** 거리 계산 기준점 설명. null이면 거리 계산 불가 상태를 명시적으로 보여준다. */
    val referenceLabel: String? = null,
    val cities: List<City> = emptyList(),
    val selectedCityId: String? = null,
    val currentCityId: String? = null,
    val categoryFilter: PlaceCategory? = null,
    val unvisitedOnly: Boolean = false,
    val sort: NearbySort = NearbySort.RECOMMENDED,
    /** "지금 가기 좋은 처방" — 추천 점수 상위 3개 */
    val top3: List<ScoredPlace> = emptyList(),
    val others: List<ScoredPlace> = emptyList(),
    val noCoordinates: List<Place> = emptyList(),
    val totalPlaceCount: Int = 0,
)

private data class NearbyFilters(
    val category: PlaceCategory?,
    val unvisitedOnly: Boolean,
    val sort: NearbySort,
    val refreshing: Boolean,
    val permission: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NearbyViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    private val placeRepository: PlaceRepository,
    cityRepository: CityRepository,
    private val appPreferences: AppPreferences,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val categoryFilter = MutableStateFlow<PlaceCategory?>(null)
    private val unvisitedOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(NearbySort.RECOMMENDED)
    private val refreshing = MutableStateFlow(false)
    private val permissionState = MutableStateFlow(locationProvider.hasLocationPermission())

    private val filtersFlow = combine(
        categoryFilter, unvisitedOnly, sort, refreshing, permissionState,
    ) { category, unvisited, sortValue, refreshingValue, permission ->
        NearbyFilters(category, unvisited, sortValue, refreshingValue, permission)
    }

    val uiState: StateFlow<NearbyUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(NearbyUiState(loading = false))
            } else {
                combine(
                    placeRepository.observePlaces(trip.id),
                    cityRepository.observeCities(trip.id),
                    appPreferences.snapshot,
                    filtersFlow,
                ) { places, cities, prefs, filters ->
                    buildState(trip.id, places, cities, prefs.selectedCityId, prefs.lastKnownLocation, filters)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    /** 화면 진입 시(권한이 있으면) 위치를 1회 갱신한다. Pull-to-Refresh도 동일 경로. */
    fun refreshLocation() {
        permissionState.value = locationProvider.hasLocationPermission()
        if (!permissionState.value) return
        viewModelScope.launch {
            refreshing.value = true
            locationProvider.refreshCurrentLocation()
            refreshing.value = false
        }
    }

    fun onPermissionResult() {
        permissionState.value = locationProvider.hasLocationPermission()
        refreshLocation()
    }

    fun setCategoryFilter(category: PlaceCategory?) {
        categoryFilter.value = category
    }

    fun setUnvisitedOnly(value: Boolean) {
        unvisitedOnly.value = value
    }

    fun setSort(value: NearbySort) {
        sort.value = value
    }

    fun selectCity(cityId: String) {
        viewModelScope.launch { appPreferences.setSelectedCity(cityId) }
    }

    fun toggleVisited(place: Place) {
        val tripId = uiState.value.tripId ?: return
        val updated = if (place.visited) place.copy(visitedAt = null) else place.copy(visitedAt = Instant.now())
        viewModelScope.launch { placeRepository.update(tripId, updated) }
    }

    fun delete(place: Place) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch { placeRepository.delete(tripId, place.id) }
    }

    private fun buildState(
        tripId: String,
        places: List<Place>,
        cities: List<City>,
        selectedCityId: String?,
        lastLocation: LastKnownLocation?,
        filters: NearbyFilters,
    ): NearbyUiState {
        val now = Instant.now()
        // 현재 도시: 도시 시간대 기준 오늘이 체류 기간에 포함되는 첫 도시(홈과 동일 규칙)
        val currentCity = cities.firstOrNull { city ->
            val start = city.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val end = city.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (start == null || end == null) return@firstOrNull false
            val todayThere = now.atZone(ZoneId.of(city.timeZoneId)).toLocalDate()
            !todayThere.isBefore(start) && !todayThere.isAfter(end)
        }
        val selectedCity = cities.firstOrNull { it.id == selectedCityId }

        // 거리 기준점: 최근 위치 → 선택(또는 현재) 도시의 기준 좌표 → 없음(거리 계산 불가 명시)
        val referenceCity = selectedCity ?: currentCity
        val (refLat, refLng, referenceLabel) = when {
            filters.permission && lastLocation != null ->
                Triple(lastLocation.latitude, lastLocation.longitude, "현재 위치 기준")

            referenceCity?.referenceLatitude != null && referenceCity.referenceLongitude != null ->
                Triple(
                    referenceCity.referenceLatitude,
                    referenceCity.referenceLongitude,
                    "${referenceCity.displayName} 기준 좌표",
                )

            else -> Triple(null, null, null)
        }

        val zone = ZoneId.of(currentCity?.timeZoneId ?: selectedCity?.timeZoneId ?: "Asia/Seoul")
        val nowLocalTime = now.atZone(zone).toLocalTime()

        val filtered = places
            .filter { filters.category == null || it.category == filters.category }
            .filter { !filters.unvisitedOnly || !it.visited }

        val (withCoords, withoutCoords) = filtered.partition { it.hasCoordinates }
        val scored = withCoords.map { place ->
            val distance = if (refLat != null && refLng != null) {
                Haversine.distanceMeters(refLat, refLng, place.latitude!!, place.longitude!!)
            } else {
                null // 좌표 또는 현재 위치가 없으면 거리값을 임의로 표시하지 않는다(스펙 7-7)
            }
            ScoredPlace(
                place = place,
                distanceMeters = distance,
                score = PlaceRecommendationScorer.score(
                    place = place,
                    distanceMeters = distance,
                    currentCityId = currentCity?.id,
                    selectedCityId = selectedCityId,
                    nowLocalTime = nowLocalTime,
                ),
            )
        }

        val byRecommendation = scored.sortedByDescending { it.score.total }
        val top3 = byRecommendation.take(3)
        val rest = byRecommendation.drop(3)
        val others = when (filters.sort) {
            NearbySort.RECOMMENDED -> rest
            NearbySort.DISTANCE -> rest.sortedWith(compareBy(nullsLast()) { it.distanceMeters })
        }

        return NearbyUiState(
            loading = false,
            tripId = tripId,
            hasLocationPermission = filters.permission,
            refreshing = filters.refreshing,
            referenceLabel = referenceLabel,
            cities = cities,
            selectedCityId = selectedCityId,
            currentCityId = currentCity?.id,
            categoryFilter = filters.category,
            unvisitedOnly = filters.unvisitedOnly,
            sort = filters.sort,
            top3 = top3,
            others = others,
            noCoordinates = withoutCoords,
            totalPlaceCount = places.size,
        )
    }
}
