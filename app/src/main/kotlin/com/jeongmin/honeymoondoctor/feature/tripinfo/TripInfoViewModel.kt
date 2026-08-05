package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class TripInfoUiState(
    val currentUser: AuthUser? = null,
    val trip: Trip? = null,
    val members: List<TripMember> = emptyList(),
    val pendingJoinRequests: List<JoinRequest> = emptyList(),
    val lastGeneratedInviteCode: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripInfoViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val lastGeneratedInviteCode = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TripInfoUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(TripInfoUiState())
            } else {
                tripRepository.observeMyTrip(user.uid).flatMapLatest { trip ->
                    if (trip == null) {
                        flowOf(TripInfoUiState(currentUser = user))
                    } else {
                        combine(
                            tripRepository.observeMembers(trip.id),
                            tripRepository.observePendingJoinRequests(trip.id),
                            lastGeneratedInviteCode,
                        ) { members, joinRequests, generatedCode ->
                            TripInfoUiState(user, trip, members, joinRequests, generatedCode)
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripInfoUiState())

    fun regenerateInviteCode(tripId: String) {
        viewModelScope.launch {
            lastGeneratedInviteCode.value = tripRepository.regenerateInviteCode(tripId)
        }
    }

    fun expireInviteCode(tripId: String) {
        viewModelScope.launch {
            tripRepository.expireInviteCode(tripId)
            lastGeneratedInviteCode.value = null
        }
    }

    fun approve(tripId: String, requestId: String) {
        viewModelScope.launch { tripRepository.approveJoinRequest(tripId, requestId) }
    }

    fun reject(tripId: String, requestId: String) {
        viewModelScope.launch { tripRepository.rejectJoinRequest(tripId, requestId) }
    }
}
