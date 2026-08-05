package com.jeongmin.honeymoondoctor.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jeongmin.honeymoondoctor.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onGoogleIdToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("허니문닥터", style = MaterialTheme.typography.headlineMedium)
        Text(
            "정민·찬희 신혼여행 전담 주치의",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Button(onClick = {
            scope.launch {
                runCatching { requestGoogleIdToken(context) }
                    .onSuccess { idToken ->
                        errorMessage = null
                        onGoogleIdToken(idToken)
                    }
                    .onFailure { errorMessage = "Google 로그인에 실패했습니다: ${it.message}" }
            }
        }) {
            Text("Google로 로그인")
        }
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private suspend fun requestGoogleIdToken(context: android.content.Context): String {
    check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
        "GOOGLE_WEB_CLIENT_ID가 설정되지 않았습니다. google-services.json을 app/ 폴더에 넣어주세요."
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
    val credentialManager = CredentialManager.create(context)
    val response = try {
        credentialManager.getCredential(context, request)
    } catch (e: NoCredentialException) {
        throw IllegalStateException("이 기기에 등록된 Google 계정이 없습니다. 설정에서 계정을 추가해주세요.", e)
    } catch (e: GetCredentialException) {
        throw IllegalStateException(e.message ?: "Credential Manager 오류", e)
    }
    val credential = response.credential
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "예상하지 못한 자격 증명 유형입니다."
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
