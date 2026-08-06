package com.jeongmin.honeymoondoctor.core.notification

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 정확한 알람 권한이 없을 때의 폴백 경로(스펙 7-8). WorkManager 스케줄은 Doze/배터리 최적화의
 * 영향을 받아 지연될 수 있다 — 정시 도착을 보장하지 않으며, 이는 의도된 동작이다.
 * WorkManager는 기기 재부팅 후에도 예약을 자체적으로 복원하므로 별도 재등록이 필요 없다.
 */
class ItineraryReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
        ItineraryNotifier.show(applicationContext, notificationId, title, body)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_NOTIFICATION_ID = "notificationId"
    }
}
