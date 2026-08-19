package com.jeongmin.honeymoondoctor.core.notification

import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
                if (trip == null) {
                    flowOf(null to emptyList())
                } else {
                    itineraryRepository.observeItinerary(trip.id).map { items -> trip to items }
                }
            }
            .onEach { (trip, items) ->
                scheduler.syncSchedule(items)
                // 아침 요약은 여행 기간 안에서만 보내므로 여행 정보가 함께 필요하다.
                scheduler.syncDailyBrief(
                    items = items,
                    tripStartDate = trip?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    tripEndDate = trip?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    zoneId = ZoneId.systemDefault(),
                )
            }
            .launchIn(scope)
    }
}
