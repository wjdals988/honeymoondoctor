package com.jeongmin.honeymoondoctor.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * FirebaseAuth/FirebaseFirestore는 실제 Firebase 모드(RepositoryModule에서 데모 모드가 아닐 때)에서만
 * Provider<T>를 통해 지연 생성된다. google-services.json이 없는 데모 모드에서는 이 두 Provides 함수가
 * 전혀 호출되지 않으므로, 초기화되지 않은 FirebaseApp에 접근해 예외가 나는 일이 없다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        // Firestore 오프라인 캐시 사용(스펙 3장/8장 요구사항).
        firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        return firestore
    }
}
