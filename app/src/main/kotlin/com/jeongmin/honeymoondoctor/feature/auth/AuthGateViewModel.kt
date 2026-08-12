package com.jeongmin.honeymoondoctor.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.Trip
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 진입 순서: 로그인 → (여행이 없으면) 여행 만들기 → 여행 목록 → 선택한 여행의 5탭.
 *
 * v0.1.6부터 여행 목록 단계가 생겼다. 그전에는 여행이 하나뿐이라 로그인하면 바로 5탭으로
 * 들어갔고, 두 번째 여행을 만들 방법 자체가 없었다.
 */
sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object NeedsLogin : AuthGateState

    /** 여행이 하나도 없을 때. 만들기/참여 폼을 보여준다. */
    data class NeedsTripSetup(
        val user: AuthUser,
        val pendingJoinTripId: String?,
        val joinRequestStatus: JoinRequestStatus? = null,
    ) : AuthGateState

    /** 여행은 있는데 아직 무엇을 볼지 고르지 않았을 때. */
    data class NeedsTripSelection(val user: AuthUser, val trips: List<Trip>) : AuthGateState

    data class Ready(val user: AuthUser, val trip: Trip) : AuthGateState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val appPreferences: AppPreferences,
    val demoModeManager: DemoModeManager,
) : ViewModel() {

    val state: StateFlow<AuthGateState> =
        authRepository.currentUser
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf(AuthGateState.NeedsLogin)
                } else {
                    combine(
                        tripRepository.observeMyTrips(user.uid),
                        appPreferences.snapshot,
                    ) { trips, prefs ->
                        Triple(trips, prefs.selectedTripId, prefs.pendingJoinTripId)
                    }.flatMapLatest { (trips, selectedTripId, pendingJoinTripId) ->
                        // 선택된 id가 목록에 없으면(삭제·나감·다른 기기에서 정리) 목록으로 되돌린다.
                        val selected = trips.firstOrNull { it.id == selectedTripId }
                        when {
                            selected != null -> flowOf(AuthGateState.Ready(user, selected))
                            trips.isNotEmpty() -> flowOf(AuthGateState.NeedsTripSelection(user, trips))
                            pendingJoinTripId == null -> flowOf(AuthGateState.NeedsTripSetup(user, null))
                            else -> tripRepository.observeMyJoinRequest(pendingJoinTripId, user.uid)
                                .map { status -> AuthGateState.NeedsTripSetup(user, pendingJoinTripId, status) }
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthGateState.Loading)

    fun selectTrip(tripId: String) {
        viewModelScope.launch { appPreferences.setSelectedTripId(tripId) }
    }

    /** 5탭에서 여행 목록으로 돌아갈 때. 선택을 비우면 상태 흐름이 알아서 목록으로 되돌린다. */
    fun clearSelectedTrip() {
        viewModelScope.launch { appPreferences.setSelectedTripId(null) }
    }

    init {
        if (demoModeManager.isDemoMode) {
            viewModelScope.launch { runCatching { authRepository.signInAsDemoUser() } }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            authRepository.signInWithGoogleIdToken(idToken).onFailure(onError)
        }
    }

    fun createTrip(user: AuthUser, draft: NewTripDraft, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            runCatching { tripRepository.createTrip(user.uid, user.displayName, draft) }
                // 방금 만든 여행으로 바로 들어간다. 목록에 떨어뜨리면 만들자마자 또 골라야 한다.
                .onSuccess { appPreferences.setSelectedTripId(it.id) }
                .onFailure(onError)
        }
    }

    fun requestToJoin(user: AuthUser, inviteCode: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = tripRepository.requestToJoin(inviteCode, user.uid, user.displayName)
            if (result.isSuccess) {
                com.jeongmin.honeymoondoctor.core.security.InviteCode.extractTripId(inviteCode)?.let {
                    appPreferences.setPendingJoinTripId(it)
                }
            }
            onResult(result)
        }
    }

    fun cancelPendingJoin() {
        viewModelScope.launch { appPreferences.setPendingJoinTripId(null) }
    }
}
