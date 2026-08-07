package com.jeongmin.honeymoondoctor.data.auth

import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val DEMO_OWNER = AuthUser(uid = "demo-owner-uid", displayName = "데모 사용자", email = null)

/** 데모 모드에서는 실제 Google 계정 없이 항상 같은 로컬 가상 사용자로 진입한다. */
@Singleton
class DemoAuthRepository @Inject constructor() : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        Result.failure(UnsupportedOperationException("데모 모드에서는 Google 로그인을 사용하지 않습니다."))

    override suspend fun signInAsDemoUser(): AuthUser {
        _currentUser.value = DEMO_OWNER
        return DEMO_OWNER
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }
}
