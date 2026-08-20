package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.jeongmin.honeymoondoctor.feature.triplist.TripListScreen

/**
 * 로그인 → 여행 생성/참여 → 5탭 메인 화면 순서로 진입을 제어하는 최상위 게이트.
 * 데모 모드 배너는 여기서 한 번만 그려 모든 단계(로그인 전 포함)에서 일관되게 보이게 한다.
 *
 * 인셋 처리 주의: `MainActivity`가 `enableEdgeToEdge()`를 쓰고 targetSdk 35+에서는 어차피
 * 강제되므로, 시스템 바 뒤까지 그려진다. 5탭 화면(`HoneymoonDoctorAppRoot`)은 `Scaffold`가
 * 인셋을 자동으로 처리하지만 로그인·여행생성 화면은 Scaffold 밖에 있어 아무도 처리하지
 * 않았고, 실기기에서 제목이 상태표시줄과 겹쳤다. 그래서 Scaffold가 없는 화면에만
 * [safeDrawingInsets]를 적용한다 — 전체에 걸면 Scaffold와 이중으로 적용된다.
 */
@Composable
fun AuthGate(prefillInviteCode: String? = null, viewModel: AuthGateViewModel = hiltViewModel()) {
    Column {
        if (viewModel.demoModeManager.isDemoMode) {
            DemoModeBanner()
        }
        when (viewModel.hasSeenOnboarding.collectAsState().value) {
            null -> LoadingIndicator()
            false -> OnboardingScreen(onDone = viewModel::markOnboardingSeen, modifier = safeDrawingInsets())
            true -> AuthGateContent(viewModel, prefillInviteCode)
        }
    }
}

@Composable
private fun AuthGateContent(viewModel: AuthGateViewModel, prefillInviteCode: String?) {
    when (val state = viewModel.state.collectAsState().value) {
        is AuthGateState.Loading -> LoadingIndicator()
        is AuthGateState.NeedsLogin -> {
            if (viewModel.demoModeManager.isDemoMode) {
                LoadingIndicator() // 데모 모드는 자동으로 로그인되므로 잠깐만 보인다.
            } else {
                // 오류 콜백을 빈 람다로 두면 Firebase 인증 실패가 조용히 사라져
                // "계정을 골랐는데 아무 일도 안 일어난다"로만 보인다(실기기에서 겪은 문제).
                var signInError by remember { mutableStateOf<String?>(null) }
                LoginScreen(
                    onGoogleIdToken = { token ->
                        signInError = null
                        viewModel.signInWithGoogleIdToken(token) {
                            signInError = "Firebase 인증에 실패했습니다: ${it.message}"
                        }
                    },
                    modifier = safeDrawingInsets(),
                    signInError = signInError,
                )
            }
        }
            is AuthGateState.NeedsTripSelection -> {
                // 여행이 여러 개일 수 있으므로 무엇을 볼지 먼저 고른다. "새 여행 만들기"는
                // 만들기 폼(TripSetupScreen)을 그대로 재사용한다 — 참여 코드 입력도 같은 폼에 있다.
                var showCreateForm by remember { mutableStateOf(false) }
                if (showCreateForm) {
                    var createError by remember { mutableStateOf<String?>(null) }
                    TripSetupScreen(
                        user = state.user,
                        pendingJoinTripId = null,
                        joinRequestStatus = null,
                        createError = createError,
                        prefillInviteCode = prefillInviteCode,
                        onCreateTrip = { draft ->
                            createError = null
                            viewModel.createTrip(state.user, draft) {
                                createError = it.message ?: "여행 생성에 실패했습니다."
                            }
                        },
                        onRequestToJoin = { code, onResult -> viewModel.requestToJoin(state.user, code, onResult) },
                        onCancelPendingJoin = viewModel::cancelPendingJoin,
                        onNavigateBack = { showCreateForm = false },
                        modifier = safeDrawingInsets(),
                    )
                } else {
                    TripListScreen(
                        trips = state.trips,
                        userDisplayName = state.user.displayName,
                        onSelectTrip = viewModel::selectTrip,
                        onCreateTrip = { showCreateForm = true },
                        modifier = safeDrawingInsets(),
                    )
                }
            }
            is AuthGateState.NeedsTripSetup -> {
                var createError by remember { mutableStateOf<String?>(null) }
                TripSetupScreen(
                    user = state.user,
                    pendingJoinTripId = state.pendingJoinTripId,
                    joinRequestStatus = state.joinRequestStatus,
                    createError = createError,
                    prefillInviteCode = prefillInviteCode,
                    onCreateTrip = { draft ->
                        createError = null
                        viewModel.createTrip(state.user, draft) { createError = it.message ?: "여행 생성에 실패했습니다." }
                    },
                    onRequestToJoin = { code, onResult -> viewModel.requestToJoin(state.user, code, onResult) },
                    onCancelPendingJoin = viewModel::cancelPendingJoin,
                    modifier = safeDrawingInsets(),
                )
            }
        is AuthGateState.Ready -> HoneymoonDoctorAppRoot()
    }
}

/** Scaffold가 없는 화면이 시스템 바(상태표시줄·내비게이션 바)·컷아웃을 침범하지 않게 하는 여백. */
@Composable
private fun safeDrawingInsets(): Modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
