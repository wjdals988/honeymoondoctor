package com.jeongmin.honeymoondoctor.core.di

import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import com.jeongmin.honeymoondoctor.data.auth.DemoAuthRepository
import com.jeongmin.honeymoondoctor.data.auth.FirebaseAuthRepository
import com.jeongmin.honeymoondoctor.data.trip.DemoTripRepository
import com.jeongmin.honeymoondoctor.data.trip.FirebaseTripRepository
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 데모/실서비스 구현을 런타임에 고른다. Provider<T>로 감싸 반대쪽 구현(특히 FirebaseAuth/Firestore를
 * 필요로 하는 쪽)이 실제로 선택되지 않는 한 생성조차 되지 않게 한다 — 데모 모드에서 미초기화된
 * FirebaseApp에 접근해 죽는 일을 원천 차단한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoAuthRepository>,
        firebase: Provider<FirebaseAuthRepository>,
    ): AuthRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideTripRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoTripRepository>,
        firebase: Provider<FirebaseTripRepository>,
    ): TripRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()
}
