package com.jeongmin.honeymoondoctor.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.demo.DemoDataResetter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val demoDataResetter: DemoDataResetter,
) : ViewModel() {
    fun resetDemoData() {
        viewModelScope.launch { demoDataResetter.resetAll() }
    }
}
