package com.jeongmin.honeymoondoctor.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.usecase.ItineraryReminderPlanner
import com.jeongmin.honeymoondoctor.domain.usecase.PlannedReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 중요 일정 알림(스펙 7-8)의 (재)예약을 담당한다. 정확한 알람 권한이 있으면
 * AlarmManager.setExactAndAllowWhileIdle로 정시에 가깝게, 없으면 WorkManager 지연 작업으로
 * 대체한다(지연될 수 있음을 사용자에게도 동기화 상태 화면에서 안내한다).
 */
@Singleton
class ItineraryReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    /**
     * 여행의 최신 일정 목록으로 알림을 다시 계획한다. 더 이상 유효하지 않은
     * (삭제·완료 처리·시간 변경으로 지나간) 알림은 취소하고, 새로 계획된 알림만 (재)예약한다.
     * 일정이 바뀔 때마다 호출해도 안전한 멱등 연산이다.
     */
    suspend fun syncSchedule(items: List<ItineraryItem>) {
        val now = Instant.now()
        val planned = ItineraryReminderPlanner.plan(items, now)
        val newKeys = planned.map { it.key }.toSet()
        val oldKeys = appPreferences.snapshot.first().scheduledReminderKeys

        (oldKeys - newKeys).forEach { staleKey -> cancel(staleKey) }
        planned.forEach { reminder -> schedule(reminder) }

        appPreferences.setScheduledReminderKeys(newKeys)
    }

    private fun schedule(reminder: PlannedReminder) {
        if (canScheduleExactAlarms()) scheduleExact(reminder) else scheduleInexact(reminder)
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun requestCode(key: String): Int = key.hashCode()

    private fun alarmPendingIntent(key: String, title: String?, body: String?): PendingIntent {
        val intent = Intent(context, ItineraryAlarmReceiver::class.java).apply {
            title?.let { putExtra(ItineraryAlarmReceiver.EXTRA_TITLE, it) }
            body?.let { putExtra(ItineraryAlarmReceiver.EXTRA_BODY, it) }
            putExtra(ItineraryAlarmReceiver.EXTRA_NOTIFICATION_ID, requestCode(key))
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleExact(reminder: PlannedReminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(reminder.key, reminder.title, reminder.body)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.fireAt.toEpochMilli(),
            pendingIntent,
        )
    }

    private fun scheduleInexact(reminder: PlannedReminder) {
        val delayMillis = Duration.between(Instant.now(), reminder.fireAt).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ItineraryReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ItineraryReminderWorker.KEY_TITLE to reminder.title,
                    ItineraryReminderWorker.KEY_BODY to reminder.body,
                    ItineraryReminderWorker.KEY_NOTIFICATION_ID to requestCode(reminder.key),
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(reminder.key, ExistingWorkPolicy.REPLACE, request)
    }

    /** 어느 경로로 예약됐었는지 몰라도 안전하도록 두 경로 모두 취소를 시도한다. */
    private fun cancel(key: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(alarmPendingIntent(key, title = null, body = null))
        WorkManager.getInstance(context).cancelUniqueWork(key)
    }
}
