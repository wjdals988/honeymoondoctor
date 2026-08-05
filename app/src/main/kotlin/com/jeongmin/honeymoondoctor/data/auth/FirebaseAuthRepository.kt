package com.jeongmin.honeymoondoctor.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser?.toAuthUser()
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        checkNotNull(result.user) { "Firebase 로그인 응답에 사용자 정보가 없습니다." }.toAuthUser()
    }

    override suspend fun signInAsDemoUser(): AuthUser =
        throw UnsupportedOperationException("실제 Firebase 모드에서는 데모 로그인을 사용하지 않습니다.")

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName ?: email ?: uid,
        email = email,
    )
}
