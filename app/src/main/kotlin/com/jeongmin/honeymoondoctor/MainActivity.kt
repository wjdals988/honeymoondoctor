package com.jeongmin.honeymoondoctor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jeongmin.honeymoondoctor.core.location.LocationShareCoordinator
import com.jeongmin.honeymoondoctor.core.security.InviteCode
import com.jeongmin.honeymoondoctor.core.theme.HoneymoonDoctorTheme
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode
import com.jeongmin.honeymoondoctor.feature.auth.AuthGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var locationShareCoordinator: LocationShareCoordinator

    /** 초대 딥링크(honeymoondoctor://join?code=...)의 code 파라미터. Compose가 읽는 State라
     * onNewIntent에서 갱신하면 recompose로 반영된다. 한 번 소비되면(TripSetupScreen 진입)
     * 값을 지울 필요는 없다 — 참여 버튼을 누르지 않는 한 그냥 입력창에 남아있을 뿐이다. */
    private var pendingInviteCodeFromLink by mutableStateOf<String?>(null)

    /** 쪽지 알림을 탭해서 열렸다는 신호(honeymoondoctor://notes). 로그인 후 5탭 화면이
     * 뜨는 즉시 쪽지함으로 이동하고 나면 [HoneymoonDoctorAppRoot]가 false로 되돌린다. */
    private var pendingOpenNotes by mutableStateOf(false)

    private fun extractInviteCodeFrom(intent: Intent?): String? =
        intent?.data?.let(InviteCode::extractFromLink)

    private fun isOpenNotesIntent(intent: Intent?): Boolean = intent?.data?.host == "notes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInviteCodeFromLink = extractInviteCodeFrom(intent)
        pendingOpenNotes = isOpenNotesIntent(intent)
        enableEdgeToEdge()
        // "우리 위치" 자동 공유. RESUMED 동안만 돌고 백그라운드 전환 시 코루틴이 취소된다 —
        // 어떤 모드든 앱을 보고 있는 동안만 전송된다는 보장이 이 한 줄의 위치에서 나온다.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                locationShareCoordinator.runWhileForeground()
            }
        }
        // 컴포지션 밖에서 한 번만 만든다. setContent 안에서 .map을 부르면 재구성마다
        // 새 Flow가 생겨 collectAsState가 초기화된다(lint FlowOperatorInvokedInComposition).
        val themeModeFlow = appPreferences.snapshot.map { it.themeMode }
        setContent {
            // 저장 전(초기 로딩) 프레임은 SYSTEM으로 그려지는데, 기본값도 SYSTEM이라
            // 설정을 바꾼 적 없는 사용자는 차이를 못 느끼고, 바꾼 사용자는 첫 프레임
            // 직후 바로 전환된다.
            val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HoneymoonDoctorTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate(
                        prefillInviteCode = pendingInviteCodeFromLink,
                        openNotesRequest = pendingOpenNotes,
                        onOpenNotesConsumed = { pendingOpenNotes = false },
                    )
                }
            }
        }
    }

    /** 앱이 이미 떠 있는 상태에서 딥링크로 다시 열릴 때(launchMode 기본값=standard가 아니면
     * onCreate가 재호출되지 않는 경우도 있어 별도로 처리). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractInviteCodeFrom(intent)?.let { pendingInviteCodeFromLink = it }
        if (isOpenNotesIntent(intent)) pendingOpenNotes = true
    }
}
