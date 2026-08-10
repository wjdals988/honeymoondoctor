package com.jeongmin.honeymoondoctor.feature.publictrip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.PublicTripSummary
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PublicTripDetailUiState(
    val summary: PublicTripSummary? = null,
    val cities: List<City> = emptyList(),
    val itinerary: List<ItineraryItem> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PublicTripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    publicTripRepository: PublicTripRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    val uiState: StateFlow<PublicTripDetailUiState> = combine(
        publicTripRepository.observePublicTrip(tripId),
        publicTripRepository.observePublicCities(tripId),
        publicTripRepository.observePublicItinerary(tripId),
    ) { summary, cities, itinerary ->
        PublicTripDetailUiState(
            summary = summary,
            cities = cities,
            itinerary = itinerary.sortedBy { it.startAt },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PublicTripDetailUiState())
}
