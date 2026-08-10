package com.jeongmin.honeymoondoctor.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppRootViewModel @Inject constructor(
    demoModeManager: DemoModeManager,
    observeCurrentTrip: ObserveCurrentTrip,
) : ViewModel() {
    val isDemoMode: Boolean = demoModeManager.isDemoMode

    /** 완료된 여행은 구성원도 수정할 수 없다. 편집 화면·FAB이 이 값을 참조해 입력을 막는다. */
    val isTripReadOnly: StateFlow<Boolean> = observeCurrentTrip()
        .map { it?.isReadOnly ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
