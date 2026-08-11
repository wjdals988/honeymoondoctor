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

sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object NeedsLogin : AuthGateState
    data class NeedsTripSetup(
        val user: AuthUser,
        val pendingJoinTripId: String?,
        val joinRequestStatus: JoinRequestStatus? = null,
    ) : AuthGateState
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
                    combine(tripRepository.observeMyTrip(user.uid), appPreferences.snapshot) { trip, prefs ->
                        trip to prefs.pendingJoinTripId
                    }.flatMapLatest { (trip, pendingJoinTripId) ->
                        when {
                            trip != null -> flowOf(AuthGateState.Ready(user, trip))
                            pendingJoinTripId == null -> flowOf(AuthGateState.NeedsTripSetup(user, null))
                            else -> tripRepository.observeMyJoinRequest(pendingJoinTripId, user.uid)
                                .map { status -> AuthGateState.NeedsTripSetup(user, pendingJoinTripId, status) }
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthGateState.Loading)

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
