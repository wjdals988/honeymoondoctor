package com.jeongmin.honeymoondoctor.feature.publictrip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import com.jeongmin.honeymoondoctor.domain.model.PublicTripSummary
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PublicTripListUiState(
    val trips: List<PublicTripSummary> = emptyList(),
    val isDemoMode: Boolean = false,
)

@HiltViewModel
class PublicTripListViewModel @Inject constructor(
    publicTripRepository: PublicTripRepository,
    demoModeManager: DemoModeManager,
) : ViewModel() {

    val uiState: StateFlow<PublicTripListUiState> = publicTripRepository.observePublicTrips()
        .map { trips ->
            PublicTripListUiState(
                trips = trips.sortedByDescending { it.publishedAt },
                isDemoMode = demoModeManager.isDemoMode,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PublicTripListUiState(isDemoMode = demoModeManager.isDemoMode),
        )
}
