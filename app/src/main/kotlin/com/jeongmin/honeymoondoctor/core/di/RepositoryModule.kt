package com.jeongmin.honeymoondoctor.core.di

import com.jeongmin.honeymoondoctor.core.demo.DemoModeManager
import com.jeongmin.honeymoondoctor.data.auth.DemoAuthRepository
import com.jeongmin.honeymoondoctor.data.auth.FirebaseAuthRepository
import com.jeongmin.honeymoondoctor.data.checklist.DemoChecklistRepository
import com.jeongmin.honeymoondoctor.data.checklist.FirebaseChecklistRepository
import com.jeongmin.honeymoondoctor.data.city.DemoCityRepository
import com.jeongmin.honeymoondoctor.data.city.FirebaseCityRepository
import com.jeongmin.honeymoondoctor.data.decision.DemoDecisionRepository
import com.jeongmin.honeymoondoctor.data.decision.FirebaseDecisionRepository
import com.jeongmin.honeymoondoctor.data.expense.DemoBudgetRepository
import com.jeongmin.honeymoondoctor.data.expense.DemoExpenseRepository
import com.jeongmin.honeymoondoctor.data.expense.FirebaseBudgetRepository
import com.jeongmin.honeymoondoctor.data.expense.FirebaseExpenseRepository
import com.jeongmin.honeymoondoctor.data.itinerary.DemoItineraryRepository
import com.jeongmin.honeymoondoctor.data.itinerary.FirebaseItineraryRepository
import com.jeongmin.honeymoondoctor.data.memberlocation.DemoMemberLocationRepository
import com.jeongmin.honeymoondoctor.data.memberlocation.FirebaseMemberLocationRepository
import com.jeongmin.honeymoondoctor.data.place.DemoPlaceRepository
import com.jeongmin.honeymoondoctor.data.place.FirebasePlaceRepository
import com.jeongmin.honeymoondoctor.data.publictrip.DemoPublicTripRepository
import com.jeongmin.honeymoondoctor.data.publictrip.FirebasePublicTripRepository
import com.jeongmin.honeymoondoctor.data.reservation.DemoReservationRepository
import com.jeongmin.honeymoondoctor.data.reservation.FirebaseReservationRepository
import com.jeongmin.honeymoondoctor.data.sync.DemoSyncStatusRepository
import com.jeongmin.honeymoondoctor.data.sync.FirebaseSyncStatusRepository
import com.jeongmin.honeymoondoctor.data.trip.DemoTripRepository
import com.jeongmin.honeymoondoctor.data.trip.FirebaseTripRepository
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.DecisionRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.MemberLocationRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.SyncStatusRepository
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
    fun provideMemberLocationRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoMemberLocationRepository>,
        firebase: Provider<FirebaseMemberLocationRepository>,
    ): MemberLocationRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideTripRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoTripRepository>,
        firebase: Provider<FirebaseTripRepository>,
    ): TripRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideItineraryRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoItineraryRepository>,
        firebase: Provider<FirebaseItineraryRepository>,
    ): ItineraryRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideCityRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoCityRepository>,
        firebase: Provider<FirebaseCityRepository>,
    ): CityRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideChecklistRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoChecklistRepository>,
        firebase: Provider<FirebaseChecklistRepository>,
    ): ChecklistRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideReservationRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoReservationRepository>,
        firebase: Provider<FirebaseReservationRepository>,
    ): ReservationRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideDecisionRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoDecisionRepository>,
        firebase: Provider<FirebaseDecisionRepository>,
    ): DecisionRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideExpenseRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoExpenseRepository>,
        firebase: Provider<FirebaseExpenseRepository>,
    ): ExpenseRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun providePlaceRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoPlaceRepository>,
        firebase: Provider<FirebasePlaceRepository>,
    ): PlaceRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideBudgetRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoBudgetRepository>,
        firebase: Provider<FirebaseBudgetRepository>,
    ): BudgetRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun providePublicTripRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoPublicTripRepository>,
        firebase: Provider<FirebasePublicTripRepository>,
    ): PublicTripRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()

    @Provides
    @Singleton
    fun provideSyncStatusRepository(
        demoModeManager: DemoModeManager,
        demo: Provider<DemoSyncStatusRepository>,
        firebase: Provider<FirebaseSyncStatusRepository>,
    ): SyncStatusRepository = if (demoModeManager.isDemoMode) demo.get() else firebase.get()
}
