package com.jeongmin.honeymoondoctor.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jeongmin.honeymoondoctor.BuildConfig

/** 로그인 화면과 회원 탈퇴 재인증 다이얼로그가 공유하는 Credential Manager 호출. */
suspend fun requestGoogleIdToken(context: Context): String {
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
