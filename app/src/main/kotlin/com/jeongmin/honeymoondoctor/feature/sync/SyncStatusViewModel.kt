package com.jeongmin.honeymoondoctor.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.SyncStatus
import com.jeongmin.honeymoondoctor.domain.repository.SyncStatusRepository
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

data class SyncStatusUiState(
    val loading: Boolean = true,
    val status: SyncStatus? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val uiState: StateFlow<SyncStatusUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(SyncStatusUiState(loading = false))
            } else {
                syncStatusRepository.observeSyncStatus(trip.id)
                    .map { status -> SyncStatusUiState(loading = false, status = status) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatusUiState())
}
