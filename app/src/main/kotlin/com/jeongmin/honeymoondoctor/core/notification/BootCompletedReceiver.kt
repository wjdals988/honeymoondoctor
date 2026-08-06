package com.jeongmin.honeymoondoctor.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * AlarmManager로 예약한 정확한 알람은 기기 재부팅 시 사라지므로(WorkManager와 달리 자체
 * 복원 기능이 없다), 부팅 완료 시 현재 여행의 일정으로 알림을 다시 계획한다(스펙 7-8).
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var observeCurrentTrip: ObserveCurrentTrip

    @Inject lateinit var itineraryRepository: ItineraryRepository

    @Inject lateinit var scheduler: ItineraryReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val trip = observeCurrentTrip().first()
                if (trip != null) {
                    val items = itineraryRepository.observeItinerary(trip.id).first()
                    scheduler.syncSchedule(items)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
