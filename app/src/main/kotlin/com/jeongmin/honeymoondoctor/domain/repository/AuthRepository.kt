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
}
