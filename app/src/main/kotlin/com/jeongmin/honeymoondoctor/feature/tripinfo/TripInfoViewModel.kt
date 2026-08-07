package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
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
    val cities: List<City> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripInfoViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val cityRepository: CityRepository,
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
                            cityRepository.observeCities(trip.id),
                        ) { members, joinRequests, generatedCode, cities ->
                            TripInfoUiState(user, trip, members, joinRequests, generatedCode, cities)
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripInfoUiState())

    fun createCity(tripId: String, city: City) {
        viewModelScope.launch { cityRepository.create(tripId, city) }
    }

    fun updateCity(tripId: String, city: City) {
        viewModelScope.launch { cityRepository.update(tripId, city) }
    }

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
