package com.jeongmin.honeymoondoctor.core.demo

import com.google.firebase.FirebaseApp
import com.jeongmin.honeymoondoctor.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 데모 모드 여부의 단일 판단 지점.
 *
 * google-services.json이 없으면(BuildConfig.HAS_FIREBASE_CONFIG == false) google-services
 * Gradle 플러그인이 적용되지 않아 Firebase 리소스가 생성되지 않는다. 이 경우 Firebase SDK의
 * 자동 초기화(FirebaseInitProvider)는 예외를 던지지 않고 조용히 실패하므로, 여기서 다시 한 번
 * FirebaseApp.getInstance() 성공 여부로 실제 초기화 상태를 확인해 데모 모드를 확정한다.
 */
@Singleton
class DemoModeManager @Inject constructor() {

    val isDemoMode: Boolean by lazy { !BuildConfig.HAS_FIREBASE_CONFIG || !isDefaultFirebaseAppReady() }

    private fun isDefaultFirebaseAppReady(): Boolean =
        try {
            FirebaseApp.getInstance()
            true
        } catch (_: IllegalStateException) {
            false
        }
}
