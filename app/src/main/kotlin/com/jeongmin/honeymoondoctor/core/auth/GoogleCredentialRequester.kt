package com.jeongmin.honeymoondoctor.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jeongmin.honeymoondoctor.BuildConfig

/**
 * 로그인 화면과 회원 탈퇴 재인증 다이얼로그가 공유하는 Credential Manager 호출.
 *
 * `GetGoogleIdOption`이 아니라 `GetSignInWithGoogleOption`을 쓰는 이유: 전자는 자동
 * 로그인용 바텀시트를 띄우는데, 실기기(Galaxy S948N/One UI)에서 이 바텀시트가 투명한
 * 빈 화면으로 떠 `getCredential`이 영구히 반환되지 않는 문제를 겪었다(호출 14회·완료 0회,
 * `CredentialSelectorActivity`가 resumed인데 아무것도 렌더링되지 않음). 후자는 사용자가
 * 버튼을 명시적으로 눌렀을 때 쓰도록 만들어진 옵션이고 표준 계정 선택 화면을 띄운다.
 */
suspend fun requestGoogleIdToken(context: Context): String {
    check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
        "GOOGLE_WEB_CLIENT_ID가 설정되지 않았습니다. google-services.json을 app/ 폴더에 넣어주세요."
    }
    val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    val credentialManager = CredentialManager.create(context)
    val response = try {
        credentialManager.getCredential(context, request)
    } catch (e: NoCredentialException) {
        // NoCredentialException은 "기기에 계정이 없다"만 뜻하지 않는다. 계정이 멀쩡히 있어도,
        // 이 APK의 서명 인증서 SHA-1이 Firebase에 등록되지 않았으면 Google이 쓸 수 있는 자격
        // 증명을 하나도 돌려주지 않아 같은 예외가 난다(실기기에서 release APK로 처음 겪음 —
        // debug 키스토어 지문만 등록돼 있어서 발생). 계정 문제로만 안내하면 엉뚱한 곳을
        // 보게 되므로 두 원인을 함께 알려준다.
        throw IllegalStateException(
            "사용할 수 있는 Google 계정을 받지 못했습니다.\n" +
                "· 기기에 Google 계정이 추가돼 있는지 확인해주세요.\n" +
                "· 계정이 있는데도 계속 실패하면, 이 앱 서명 키의 SHA-1이 Firebase에 " +
                "등록되지 않은 경우입니다(debug/release 키가 서로 다릅니다).",
            e,
        )
    } catch (e: GetCredentialException) {
        throw IllegalStateException(e.message ?: "Credential Manager 오류", e)
    }
    val credential = response.credential
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "예상하지 못한 자격 증명 유형입니다."
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
