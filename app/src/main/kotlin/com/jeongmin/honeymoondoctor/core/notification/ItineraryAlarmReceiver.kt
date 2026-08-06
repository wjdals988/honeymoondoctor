package com.jeongmin.honeymoondoctor.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 정확한 알람(AlarmManager.setExactAndAllowWhileIdle)의 발사 지점. 예약 시 전달한
 * 제목/본문/알림 ID만 그대로 표시하므로 저장소 의존성이 필요 없다(Hilt 주입 없이 동작).
 */
class ItineraryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        ItineraryNotifier.show(context, notificationId, title, body)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
