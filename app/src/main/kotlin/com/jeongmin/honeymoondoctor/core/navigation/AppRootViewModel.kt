package com.jeongmin.honeymoondoctor.core.navigation

import androidx.lifecycle.ViewModel
import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(
    demoModeManager: DemoModeManager,
) : ViewModel() {
    val isDemoMode: Boolean = demoModeManager.isDemoMode
}
