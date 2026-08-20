package com.jeongmin.honeymoondoctor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val transportLeadMinutes: Int = 60,
    val dailyBriefEnabled: Boolean = true,
    val displayName: String = "",
    val actionError: String? = null,
)

/**
 * 설정은 대부분 기기 로컬(DataStore)이다. Firestore에 두지 않는 이유: 테마·출발 여유는
 * 사람이 아니라 **기기**의 취향이라(어두운 곳에서 쓰는 폰, 태블릿), 두 사람이 여행을
 * 공유해도 설정까지 공유되면 서로의 화면을 바꿔 버린다.
 *
 * 닉네임은 예외다 — 이건 기기가 아니라 **계정**의 정보라 Firebase Auth 프로필에 둔다
 * (AuthRepository). 지금 보고 있는 여행이 있으면 그 여행의 내 구성원 표시 이름도 함께
 * 갱신한다 — 과거 완료된 다른 여행의 기록은 그 시점 이름 그대로 남는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val observeCurrentTrip: ObserveCurrentTrip,
) : ViewModel() {

    private val actionError = ActionErrorState()

    val uiState: StateFlow<SettingsUiState> = combine(
        appPreferences.snapshot,
        authRepository.currentUser,
        actionError.message,
    ) { prefs, user, error ->
        SettingsUiState(
            themeMode = prefs.themeMode,
            transportLeadMinutes = prefs.transportLeadMinutes,
            dailyBriefEnabled = prefs.dailyBriefEnabled,
            displayName = user?.displayName.orEmpty(),
            actionError = error,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun clearActionError() = actionError.clear()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun setTransportLeadMinutes(minutes: Int) {
        viewModelScope.launch { appPreferences.setTransportLeadMinutes(minutes) }
    }

    fun setDailyBriefEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDailyBriefEnabled(enabled) }
    }

    fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            actionError.runReporting("닉네임을 바꾸지 못했습니다.") {
                authRepository.updateDisplayName(trimmed).getOrThrow()
                // 지금 보고 있는 여행이 있으면 그 여행의 내 구성원 표시 이름도 맞춘다.
                val tripId = observeCurrentTrip().first()?.id
                val uid = authRepository.currentUser.value?.uid
                if (tripId != null && uid != null) {
                    tripRepository.updateMyMemberDisplayName(tripId, uid, trimmed)
                }
            }
        }
    }
}
