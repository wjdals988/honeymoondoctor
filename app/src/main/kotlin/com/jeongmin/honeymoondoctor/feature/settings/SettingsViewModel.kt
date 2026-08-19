package com.jeongmin.honeymoondoctor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val transportLeadMinutes: Int = 60,
    val dailyBriefEnabled: Boolean = true,
)

/**
 * 설정은 전부 기기 로컬(DataStore)이다. Firestore에 두지 않는 이유: 테마·출발 여유는
 * 사람이 아니라 **기기**의 취향이라(어두운 곳에서 쓰는 폰, 태블릿), 두 사람이 여행을
 * 공유해도 설정까지 공유되면 서로의 화면을 바꿔 버린다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appPreferences.snapshot
        .map { prefs ->
            SettingsUiState(
                themeMode = prefs.themeMode,
                transportLeadMinutes = prefs.transportLeadMinutes,
                dailyBriefEnabled = prefs.dailyBriefEnabled,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    fun setTransportLeadMinutes(minutes: Int) {
        viewModelScope.launch { appPreferences.setTransportLeadMinutes(minutes) }
    }

    fun setDailyBriefEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDailyBriefEnabled(enabled) }
    }
}
