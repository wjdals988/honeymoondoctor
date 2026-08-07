package com.jeongmin.honeymoondoctor.feature.nearby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
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
    val ratingText: String = "",
    val reviewCountText: String = "",
    // 편집으로 바꾸지 않고 보존하는 값
    val visitedAt: Instant? = null,
    val sourceUpdatedAt: Instant? = null,
)

data class PlaceEditUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val cities: List<City> = emptyList(),
    val validationError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCurrentTrip: ObserveCurrentTrip,
    private val cityRepository: CityRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["placeId"]

    private val validationError = MutableStateFlow<String?>(null)
    private val _form = MutableStateFlow<PlaceEditForm?>(null)
    val form: StateFlow<PlaceEditForm?> = _form

    private val tripFlow = observeCurrentTrip()

    val uiState: StateFlow<PlaceEditUiState> = tripFlow
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(PlaceEditUiState(loading = false))
            } else {
                combine(cityRepository.observeCities(trip.id), validationError) { cities, error ->
                    PlaceEditUiState(loading = false, tripId = trip.id, cities = cities, validationError = error)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceEditUiState())

    init {
        viewModelScope.launch { initializeForm() }
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
                ratingText = existing.ratingSnapshot?.toString().orEmpty(),
                reviewCountText = existing.reviewCountSnapshot?.toString().orEmpty(),
                visitedAt = existing.visitedAt,
                sourceUpdatedAt = existing.sourceUpdatedAt,
            )
        }
    }

    fun updateForm(transform: (PlaceEditForm) -> PlaceEditForm) {
        _form.value = _form.value?.let(transform)
        validationError.value = null
    }

    fun createCity(city: City) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch { cityRepository.create(tripId, city) }
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
        val rating = form.ratingText.trim().takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull()?.takeIf { r -> r in 0.0..5.0 } ?: run {
                validationError.value = "평점은 0~5 사이 숫자여야 합니다."
                return
            }
        }
        val reviewCount = form.reviewCountText.trim().takeIf { it.isNotEmpty() }?.let {
            it.replace(",", "").toLongOrNull()?.takeIf { c -> c >= 0 } ?: run {
                validationError.value = "리뷰 수는 0 이상 정수여야 합니다."
                return
            }
        }

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
            if (form.placeId == null) {
                placeRepository.create(tripId, place)
            } else {
                placeRepository.update(tripId, place)
            }
            onSaved()
        }
    }
}
