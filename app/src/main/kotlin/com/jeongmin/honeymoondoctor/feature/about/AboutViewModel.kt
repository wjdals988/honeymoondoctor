package com.jeongmin.honeymoondoctor.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.BuildConfig
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.core.version.AppVersion
import com.jeongmin.honeymoondoctor.data.version.GithubReleaseChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 업데이트 확인 상태. 확인은 화면에 들어올 때 1회 자동으로 시작한다. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    /** 설치된 버전이 최신이다. */
    data object UpToDate : UpdateCheckState
    data class UpdateAvailable(val versionName: String, val releaseUrl: String) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val releaseChecker: GithubReleaseChecker,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_updateState.value == UpdateCheckState.Checking) return
        _updateState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            releaseChecker.fetchLatest()
                .onSuccess { latest ->
                    _updateState.value = if (AppVersion.isNewerThan(latest.tagName, BuildConfig.VERSION_NAME)) {
                        UpdateCheckState.UpdateAvailable(
                            versionName = latest.tagName.removePrefix("v"),
                            releaseUrl = latest.htmlUrl,
                        )
                    } else {
                        UpdateCheckState.UpToDate
                    }
                }
                .onFailure {
                    // 오프라인이 기본 사용 환경(기내·로밍)이므로 실패는 흔한 일이다.
                    // 조용히 실패로 표시하고 재시도 버튼만 준다.
                    _updateState.value = UpdateCheckState.Failed(
                        it.toUserMessage("업데이트를 확인하지 못했습니다. 연결을 확인해 주세요."),
                    )
                }
        }
    }
}
