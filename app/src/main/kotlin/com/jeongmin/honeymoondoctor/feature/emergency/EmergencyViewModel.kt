package com.jeongmin.honeymoondoctor.feature.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.EmergencyContacts
import com.jeongmin.honeymoondoctor.domain.model.EmergencyNumbers
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class EmergencyUiState(
    val loading: Boolean = true,
    /** 이 여행에서 갈 나라들의 긴급번호. 도시가 없으면 비어 있다. */
    val countries: List<EmergencyNumbers> = emptyList(),
    /** 프리셋에 없는 나라라 번호를 못 찾은 도시들 — 조용히 빼지 않고 이유를 보여준다. */
    val unknownCityNames: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    cityRepository: CityRepository,
) : ViewModel() {

    val uiState: StateFlow<EmergencyUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(EmergencyUiState(loading = false))
            } else {
                cityRepository.observeCities(trip.id).map { cities -> buildState(cities) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUiState())

    private fun buildState(cities: List<City>): EmergencyUiState {
        val resolved = cities.map { it to EmergencyContacts.forCountry(it.countryCode) }
        return EmergencyUiState(
            loading = false,
            // 같은 나라 도시가 여럿이면 한 번만 보여준다.
            countries = resolved.mapNotNull { it.second }.distinctBy { it.countryCode },
            unknownCityNames = resolved.filter { it.second == null }.map { it.first.displayName },
        )
    }
}
