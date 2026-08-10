package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val itineraryRepository: ItineraryRepository,
    private val publicTripRepository: PublicTripRepository,
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

    fun setStatus(tripId: String, status: TripStatus) {
        viewModelScope.launch { tripRepository.setStatus(tripId, status) }
    }

    /**
     * 완료된 여행의 도시·일정을 화이트리스트 필드만 골라 공개 사본으로 발행한다. 공개 플래그를
     * 먼저 켜고 사본을 나중에 쓴다 — 사본 쓰기가 실패해도 "공개라고 표시됐는데 실제로는 아무도
     * 못 보는" 상태로 그치게 하고, 반대 순서(사본이 먼저 생겼는데 플래그가 실패)로 인해
     * 소유자 화면엔 "비공개"로 보이지만 둘러보기 목록에는 이미 뜨는 상황을 피한다.
     */
    fun publish(trip: Trip) {
        viewModelScope.launch {
            val cities = cityRepository.observeCities(trip.id).first()
            val itinerary = itineraryRepository.observeItinerary(trip.id).first()
            tripRepository.setPublic(trip.id, true)
            publicTripRepository.publish(trip, cities, itinerary)
        }
    }

    fun unpublish(tripId: String) {
        // 공개 사본을 먼저 지워 둘러보기 목록에서 즉시 사라지게 한 뒤, 원본의 공개 플래그를 내린다.
        viewModelScope.launch {
            publicTripRepository.unpublish(tripId)
            tripRepository.setPublic(tripId, false)
        }
    }
}
