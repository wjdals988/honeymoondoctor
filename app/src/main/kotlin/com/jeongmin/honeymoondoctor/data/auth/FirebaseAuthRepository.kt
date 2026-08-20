package com.jeongmin.honeymoondoctor.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
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
        // Credential Manager에 "직접 로그아웃했다"고 알려, 다음 로그인 때 마지막 계정이 자동으로
        // 다시 뜨지 않게 한다. 실패해도 로그아웃 자체(위 signOut)는 이미 끝났으니 무시한다.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun deleteAccount() {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("로그인된 사용자가 없습니다.")
        user.delete().await()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun reauthenticate(idToken: String): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("로그인된 사용자가 없습니다.")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("로그인된 사용자가 없습니다.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        // updateProfile은 AuthStateListener를 트리거하지 않는다(ID 토큰이 안 바뀌므로) —
        // 여기서 직접 갱신해야 화면이 새 닉네임을 바로 반영한다.
        _currentUser.value = firebaseAuth.currentUser?.toAuthUser()
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName ?: email ?: uid,
        email = email,
    )
}
