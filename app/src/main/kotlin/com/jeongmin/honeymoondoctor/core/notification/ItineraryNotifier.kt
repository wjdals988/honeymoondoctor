package com.jeongmin.honeymoondoctor.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jeongmin.honeymoondoctor.R

const val ITINERARY_REMINDER_CHANNEL_ID = "itinerary_reminders"

/**
 * 알림 표시의 단일 창구. AlarmManager 경로(ItineraryAlarmReceiver)와 WorkManager 경로
 * (ItineraryReminderWorker)가 동일한 로직을 공유해, 두 경로 중 무엇으로 예약됐든
 * 사용자에게는 같은 형태의 알림이 뜬다.
 */
object ItineraryNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ITINERARY_REMINDER_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            ITINERARY_REMINDER_CHANNEL_ID,
            "중요 일정 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "일정 시작 24시간·3시간·1시간 전 알림"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Android 13+에서 알림 권한이 없으면 조용히 아무 것도 하지 않는다 — 알림 거절이 앱의
     * 핵심 기능에 영향을 주지 않아야 한다(스펙 7-8). 크래시를 유발하는 대신 안전하게 무시한다.
     */
    fun show(context: Context, notificationId: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ITINERARY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
