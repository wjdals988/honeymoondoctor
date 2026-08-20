package com.jeongmin.honeymoondoctor.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jeongmin.honeymoondoctor.R

const val NOTE_CHANNEL_ID = "trip_notes"

/**
 * 쪽지 도착 알림. 일정 알림(itinerary_reminders)과 채널을 분리한다 — 섞으면
 * "일정 알림만 끄고 쪽지는 받기"가 시스템 설정에서 불가능해진다.
 *
 * 이 알림은 상대 앱 프로세스가 살아 있을 때만 온다(서버 발송 푸시 없음). 죽어 있으면
 * 다음에 앱을 열 때 "읽지 않은 쪽지" 배지로 확인한다 — 쪽지라는 이름이 곧 그 약속이다.
 */
object NoteNotifier {

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTE_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(NOTE_CHANNEL_ID, "쪽지", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "상대가 남긴 쪽지 알림"
            },
        )
    }

    fun show(context: Context, notificationId: Int, senderName: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val openNotesIntent = Intent(Intent.ACTION_VIEW, Uri.parse("honeymoondoctor://notes")).apply {
            setPackage(context.packageName)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openNotesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // "OO 님의 쪽지"(명사형)보다 이모지 하나로 말 건네는 느낌을 준다 — 이 앱의
            // 다른 곳(온보딩 🧳💕)과 같은 톤.
            .setContentTitle("💌 $senderName")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
