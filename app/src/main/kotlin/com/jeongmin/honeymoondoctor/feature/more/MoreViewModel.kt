package com.jeongmin.honeymoondoctor.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoDataResetter
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val demoDataResetter: DemoDataResetter,
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
) : ViewModel() {

    /** 소유자가 아니면 규칙에 의해 항상 빈 목록으로 조용히 처리된다(FirestoreFlow 참고). */
    val pendingJoinRequestCount: StateFlow<Int> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else tripRepository.observePendingJoinRequests(trip.id)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun resetDemoData() {
        viewModelScope.launch { demoDataResetter.resetAll() }
    }
}
