package com.jeongmin.honeymoondoctor.feature.more

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.BuildConfig
import com.jeongmin.honeymoondoctor.core.demo.DemoDataResetter
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.DeleteAccountOutcome
import com.jeongmin.honeymoondoctor.domain.usecase.DeleteAccountUseCase
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import com.jeongmin.honeymoondoctor.domain.usecase.TripBackupBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val appPreferences: AppPreferences,
    private val tripBackupBuilder: TripBackupBuilder,
    @ApplicationContext private val context: Context,
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    private val tripNoteRepository: com.jeongmin.honeymoondoctor.domain.repository.TripNoteRepository,
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

    /** 상대가 보낸 읽지 않은 쪽지 수 — 쪽지함 메뉴 옆 배지. */
    val unreadNoteCount: StateFlow<Int> = currentTrip
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(0)
            } else {
                kotlinx.coroutines.flow.combine(
                    tripNoteRepository.observeNotes(trip.id),
                    authRepository.currentUser,
                ) { notes, user ->
                    val myUid = user?.uid ?: return@combine 0
                    notes.count { it.senderUid != myUid && it.readAt == null }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _deleteAccountState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountUiState> = _deleteAccountState

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage
    fun clearBackupMessage() { _backupMessage.value = null }

    /**
     * 여행 전체를 사용자가 고른 파일(SAF)에 JSON으로 쓴다. 실패해도 크래시가 아니라
     * 메시지로 끝난다 — 백업 실패를 조용히 넘기면 "백업했다고 믿는" 최악의 상태가 된다.
     */
    fun exportBackup(uri: Uri) {
        val trip = currentTrip.value ?: run {
            _backupMessage.value = "내보낼 여행이 없습니다."
            return
        }
        viewModelScope.launch {
            runCatching {
                val json = tripBackupBuilder.build(trip, BuildConfig.VERSION_NAME)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("파일을 열 수 없습니다.")
            }
                .onSuccess { _backupMessage.value = "백업 파일을 저장했습니다." }
                .onFailure { _backupMessage.value = it.message ?: "백업에 실패했습니다." }
        }
    }

    fun resetDemoData() {
        viewModelScope.launch { runCatching { demoDataResetter.resetAll() } }
    }

    fun logout() {
        // 로그아웃 실패(예: Credential Manager 상태 정리 실패)로 앱이 죽지 않게 감싼다.
        // 로그아웃은 되돌릴 필요가 없는 동작이라 별도 오류 표시 없이 조용히 넘긴다.
        viewModelScope.launch {
            // 기기에 남는 설정(마지막 위치·환율·보던 여행)을 먼저 지운다. 다음 사람이
            // 같은 기기로 로그인했을 때 남의 흔적이 보이면 안 된다. signOut보다 먼저
            // 하는 이유: signOut이 실패해도 기기 흔적은 지워져 있어야 한다.
            runCatching { appPreferences.clearAll() }
            runCatching { authRepository.signOut() }
        }
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
            DeleteAccountOutcome.Success -> {
                // 서버 데이터만 지우고 기기 설정을 남겨 두면 개인정보처리방침의
                // "탈퇴 시 삭제"가 반만 사실이 된다.
                runCatching { appPreferences.clearAll() }
                _deleteAccountState.value = DeleteAccountUiState.Idle
            }
            DeleteAccountOutcome.RequiresReauth -> _deleteAccountState.value = DeleteAccountUiState.NeedsReauth
            is DeleteAccountOutcome.Failure ->
                _deleteAccountState.value =
                    DeleteAccountUiState.Failed(outcome.cause.message ?: "회원 탈퇴에 실패했습니다.")
        }
    }
}
