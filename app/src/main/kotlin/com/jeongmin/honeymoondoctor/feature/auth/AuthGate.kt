package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.navigation.HoneymoonDoctorAppRoot
import com.jeongmin.honeymoondoctor.core.ui.DemoModeBanner

/**
 * 로그인 → 여행 생성/참여 → 5탭 메인 화면 순서로 진입을 제어하는 최상위 게이트.
 * 데모 모드 배너는 여기서 한 번만 그려 모든 단계(로그인 전 포함)에서 일관되게 보이게 한다.
 */
@Composable
fun AuthGate(viewModel: AuthGateViewModel = hiltViewModel()) {
    Column {
        if (viewModel.demoModeManager.isDemoMode) {
            DemoModeBanner()
        }
        when (val state = viewModel.state.collectAsState().value) {
            is AuthGateState.Loading -> LoadingIndicator()
            is AuthGateState.NeedsLogin -> {
                if (viewModel.demoModeManager.isDemoMode) {
                    LoadingIndicator() // 데모 모드는 자동으로 로그인되므로 잠깐만 보인다.
                } else {
                    LoginScreen(onGoogleIdToken = { token ->
                        viewModel.signInWithGoogleIdToken(token) {}
                    })
                }
            }
            is AuthGateState.NeedsTripSetup -> {
                var createError by remember { mutableStateOf<String?>(null) }
                TripSetupScreen(
                    user = state.user,
                    pendingJoinTripId = state.pendingJoinTripId,
                    joinRequestStatus = state.joinRequestStatus,
                    createError = createError,
                    onCreateTrip = { draft ->
                        createError = null
                        viewModel.createTrip(state.user, draft) { createError = it.message ?: "여행 생성에 실패했습니다." }
                    },
                    onRequestToJoin = { code, onResult -> viewModel.requestToJoin(state.user, code, onResult) },
                    onCancelPendingJoin = viewModel::cancelPendingJoin,
                )
            }
            is AuthGateState.Ready -> HoneymoonDoctorAppRoot()
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
