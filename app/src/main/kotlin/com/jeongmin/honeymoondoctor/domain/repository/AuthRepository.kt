package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    /** Google ID 토큰(Credential Manager에서 발급)으로 Firebase 인증을 완료한다. 데모 모드에서는 사용하지 않는다. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>

    /** 데모 모드 전용: 실제 Google 계정 없이 로컬 가상 사용자로 진입한다. */
    suspend fun signInAsDemoUser(): AuthUser

    suspend fun signOut()

    /** 계정 자체를 삭제한다(로그아웃과 별개). Firestore 쪽 정리는 호출 전에 끝나 있어야 한다. */
    suspend fun deleteAccount()

    /** 최근 로그인 요구 오류(회원 탈퇴 등 민감한 작업 전)를 해소하기 위한 재인증. */
    suspend fun reauthenticate(idToken: String): Result<Unit>
}
