package com.jeongmin.honeymoondoctor.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoDataResetter
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.DeleteAccountOutcome
import com.jeongmin.honeymoondoctor.domain.usecase.DeleteAccountUseCase
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DeleteAccountUiState {
    data object Idle : DeleteAccountUiState
    data object InProgress : DeleteAccountUiState
    data object NeedsReauth : DeleteAccountUiState
    data class Failed(val message: String) : DeleteAccountUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val demoDataResetter: DemoDataResetter,
    private val authRepository: AuthRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
) : ViewModel() {

    private val currentTrip: StateFlow<Trip?> =
        observeCurrentTrip().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 소유자가 아니면 규칙에 의해 항상 빈 목록으로 조용히 처리된다(FirestoreFlow 참고). */
    val pendingJoinRequestCount: StateFlow<Int> = currentTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else tripRepository.observePendingJoinRequests(trip.id)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _deleteAccountState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountUiState> = _deleteAccountState

    fun resetDemoData() {
        viewModelScope.launch { runCatching { demoDataResetter.resetAll() } }
    }

    fun logout() {
        // 로그아웃 실패(예: Credential Manager 상태 정리 실패)로 앱이 죽지 않게 감싼다.
        // 로그아웃은 되돌릴 필요가 없는 동작이라 별도 오류 표시 없이 조용히 넘긴다.
        viewModelScope.launch { runCatching { authRepository.signOut() } }
    }

    fun deleteAccount() {
        viewModelScope.launch { performDelete() }
    }

    /** 재인증(idToken) 후 회원 탈퇴를 다시 시도한다. 재인증 자체가 실패하면 탈퇴는 재시도하지 않는다. */
    fun retryDeleteAfterReauth(idToken: String) {
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountUiState.InProgress
            val reauth = authRepository.reauthenticate(idToken)
            if (reauth.isFailure) {
                _deleteAccountState.value =
                    DeleteAccountUiState.Failed(reauth.exceptionOrNull()?.message ?: "재인증에 실패했습니다.")
                return@launch
            }
            performDelete()
        }
    }

    fun dismissDeleteAccountError() {
        _deleteAccountState.value = DeleteAccountUiState.Idle
    }

    private suspend fun performDelete() {
        val user = authRepository.currentUser.value ?: return
        _deleteAccountState.value = DeleteAccountUiState.InProgress
        when (val outcome = deleteAccountUseCase(user, currentTrip.value)) {
            DeleteAccountOutcome.Success -> _deleteAccountState.value = DeleteAccountUiState.Idle
            DeleteAccountOutcome.RequiresReauth -> _deleteAccountState.value = DeleteAccountUiState.NeedsReauth
            is DeleteAccountOutcome.Failure ->
                _deleteAccountState.value =
                    DeleteAccountUiState.Failed(outcome.cause.message ?: "회원 탈퇴에 실패했습니다.")
        }
    }
}
