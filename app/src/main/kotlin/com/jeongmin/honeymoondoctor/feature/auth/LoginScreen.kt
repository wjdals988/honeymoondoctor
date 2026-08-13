package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.R
import com.jeongmin.honeymoondoctor.core.auth.requestGoogleIdToken
import kotlinx.coroutines.launch

/**
 * [signInError]는 Google 계정 선택 이후 단계(Firebase 인증)에서 난 오류다. 계정 선택까지는
 * 성공했는데 Firebase 쪽에서 실패하면 화면에 아무 변화가 없어 "눌러도 반응이 없다"로만
 * 보이므로(실기기에서 실제로 겪음), 그 오류도 반드시 여기 함께 띄운다.
 */
@Composable
fun LoginScreen(
    onGoogleIdToken: (String) -> Unit,
    modifier: Modifier = Modifier,
    signInError: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 진행 중 재탭 방지. 이게 없으면 반응이 늦을 때 사용자가 여러 번 누르게 되고, 새 요청이
    // 앞선 요청의 계정 선택창을 계속 취소시켜 어떤 요청도 완료되지 않는 교착에 빠진다
    // (실기기에서 getCredential이 14번 시작되고 완료가 0번인 상태로 재현됨).
    var isSigningIn by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Button(
            enabled = !isSigningIn,
            onClick = {
                isSigningIn = true
                scope.launch {
                    runCatching { requestGoogleIdToken(context) }
                        .onSuccess { idToken ->
                            errorMessage = null
                            onGoogleIdToken(idToken)
                        }
                        .onFailure { errorMessage = "Google 로그인에 실패했습니다: ${it.message}" }
                    isSigningIn = false
                }
            },
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                // 벤치마킹: 텍스트만 있는 버튼은 신뢰도가 낮아 보인다. Google 로그인 버튼의
                // 관례대로 G 로고를 붙인다.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Google로 로그인", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        (errorMessage ?: signInError)?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
