package com.jeongmin.honeymoondoctor

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
import com.jeongmin.honeymoondoctor.core.theme.HoneymoonDoctorTheme
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode
import com.jeongmin.honeymoondoctor.feature.auth.AuthGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    AuthGate()
                }
            }
        }
    }
}
