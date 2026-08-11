package com.jeongmin.honeymoondoctor.feature.tripinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly
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
    val actionError: String? = null,
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
    private val actionError = MutableStateFlow<String?>(null)

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
                            actionError,
                        ) { members, joinRequests, generatedCode, cities, error ->
                            TripInfoUiState(user, trip, members, joinRequests, generatedCode, cities, error)
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripInfoUiState())

    fun createCity(tripId: String, city: City) {
        viewModelScope.launch {
            runCatching { cityRepository.create(tripId, city) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("도시를 추가하지 못했습니다.") }
        }
    }

    fun updateCity(tripId: String, city: City) {
        viewModelScope.launch {
            runCatching { cityRepository.update(tripId, city) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("도시를 수정하지 못했습니다.") }
        }
    }

    fun regenerateInviteCode(tripId: String) {
        viewModelScope.launch {
            runCatching { tripRepository.regenerateInviteCode(tripId) }
                .onSuccess {
                    lastGeneratedInviteCode.value = it
                    actionError.value = null
                }
                .onFailure { actionError.value = it.toUserMessage("초대코드 생성에 실패했습니다.") }
        }
    }

    fun expireInviteCode(tripId: String) {
        viewModelScope.launch {
            runCatching { tripRepository.expireInviteCode(tripId) }
                .onSuccess {
                    lastGeneratedInviteCode.value = null
                    actionError.value = null
                }
                .onFailure { actionError.value = it.toUserMessage("초대코드 만료에 실패했습니다.") }
        }
    }

    fun approve(tripId: String, requestId: String) {
        viewModelScope.launch {
            runCatching { tripRepository.approveJoinRequest(tripId, requestId) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("참여 요청 승인에 실패했습니다.") }
        }
    }

    fun reject(tripId: String, requestId: String) {
        viewModelScope.launch {
            runCatching { tripRepository.rejectJoinRequest(tripId, requestId) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("참여 요청 거절에 실패했습니다.") }
        }
    }

    fun updateTripInfo(tripId: String, name: String, startDate: String, endDate: String, defaultCurrency: String) {
        viewModelScope.launch {
            runCatching { tripRepository.updateTripInfo(tripId, name, startDate, endDate, defaultCurrency) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("여행 정보 수정에 실패했습니다.") }
        }
    }

    /**
     * 여행 삭제 또는 나가기. 회원 탈퇴(DeleteAccountUseCase)와 같은 3분기 정책을 쓴다 —
     * 공동 데이터를 한쪽이 일방적으로 없애지 않는다는 원칙이 같기 때문이다.
     *
     * - 나 혼자인 여행: 여행 문서와 하위 데이터를 전부 삭제(공개 중이면 공개 사본 먼저 내림).
     * - 소유자 + 동반자: 소유권을 동반자에게 넘기고 나만 빠진다. 남은 사람의 여행은 유지된다.
     * - 일반 구성원: `memberIds`에서 나만 빠진다.
     *
     * 완료된 여행은 서버 규칙이 하위 컬렉션 쓰기를 막으므로, 삭제 직전에 ACTIVE로 되돌린다.
     * 어차피 문서 전체가 사라지므로 무해하고, "완료된 여행은 못 건드린다"는 규칙을 다른
     * 경로에서 완화하지 않아도 된다(회원 탈퇴에서 쓰는 것과 같은 우회).
     */
    fun deleteOrLeaveTrip(trip: Trip, onDone: () -> Unit) {
        val uid = uiState.value.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val isOwner = trip.ownerId == uid
                val partnerUid = trip.memberIds.firstOrNull { it != uid }
                when {
                    partnerUid == null && isOwner -> {
                        if (trip.isReadOnly) tripRepository.setStatus(trip.id, TripStatus.ACTIVE)
                        if (trip.isPublic) publicTripRepository.unpublish(trip.id)
                        tripRepository.deleteTripCompletely(trip.id)
                    }
                    isOwner && partnerUid != null ->
                        tripRepository.transferOwnershipAndLeaveTrip(trip.id, uid, partnerUid)
                    else -> tripRepository.leaveTrip(trip.id, uid)
                }
            }
                .onSuccess {
                    actionError.value = null
                    onDone()
                }
                .onFailure { actionError.value = it.toUserMessage("여행을 삭제하지 못했습니다.") }
        }
    }

    fun setStatus(tripId: String, status: TripStatus) {
        viewModelScope.launch {
            runCatching { tripRepository.setStatus(tripId, status) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("여행 상태 변경에 실패했습니다.") }
        }
    }

    /**
     * 완료된 여행의 도시·일정을 화이트리스트 필드만 골라 공개 사본으로 발행한다. 공개 플래그를
     * 먼저 켜고 사본을 나중에 쓴다 — 사본 쓰기가 실패해도 "공개라고 표시됐는데 실제로는 아무도
     * 못 보는" 상태로 그치게 하고, 반대 순서(사본이 먼저 생겼는데 플래그가 실패)로 인해
     * 소유자 화면엔 "비공개"로 보이지만 둘러보기 목록에는 이미 뜨는 상황을 피한다.
     */
    fun publish(trip: Trip) {
        viewModelScope.launch {
            runCatching {
                val cities = cityRepository.observeCities(trip.id).first()
                val itinerary = itineraryRepository.observeItinerary(trip.id).first()
                tripRepository.setPublic(trip.id, true)
                publicTripRepository.publish(trip, cities, itinerary)
            }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("공개에 실패했습니다.") }
        }
    }

    fun unpublish(tripId: String) {
        // 공개 사본을 먼저 지워 둘러보기 목록에서 즉시 사라지게 한 뒤, 원본의 공개 플래그를 내린다.
        viewModelScope.launch {
            runCatching {
                publicTripRepository.unpublish(tripId)
                tripRepository.setPublic(tripId, false)
            }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = it.toUserMessage("공개 중단에 실패했습니다.") }
        }
    }

    // Firestore 예외 메시지는 영어·원인코드라 그대로 보여주면 안 되고, 우리 코드가 직접 던진
    // IllegalStateException(예: "여행 구성원은 최대 2명입니다.") 같은 한국어 메시지만 그대로 쓴다.
}
