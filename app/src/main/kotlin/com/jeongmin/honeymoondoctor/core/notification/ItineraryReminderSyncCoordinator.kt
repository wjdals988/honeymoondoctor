package com.jeongmin.honeymoondoctor.core.notification

import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 프로세스가 살아있는 동안 현재 여행의 일정 변경을 관찰해 알림을 다시 계획한다.
 * 어느 화면을 보고 있는지와 무관하게 항상 동작해야 하므로, 개별 ViewModel이 아니라
 * HoneymoonDoctorApp.onCreate()에서 프로세스 전역 스코프로 한 번만 시작한다.
 */
@Singleton
class ItineraryReminderSyncCoordinator @Inject constructor(
    private val observeCurrentTrip: ObserveCurrentTrip,
    private val itineraryRepository: ItineraryRepository,
    private val scheduler: ItineraryReminderScheduler,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        observeCurrentTrip()
            .flatMapLatest { trip ->
                if (trip == null) flowOf(emptyList()) else itineraryRepository.observeItinerary(trip.id)
            }
            .onEach { items -> scheduler.syncSchedule(items) }
            .launchIn(scope)
    }
}
