package com.jeongmin.honeymoondoctor

import android.app.Application
import com.jeongmin.honeymoondoctor.core.notification.ItineraryNotifier
import com.jeongmin.honeymoondoctor.core.notification.ItineraryReminderSyncCoordinator
import com.jeongmin.honeymoondoctor.core.notification.NoteAlertCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class HoneymoonDoctorApp : Application() {

    @Inject lateinit var reminderSyncCoordinator: ItineraryReminderSyncCoordinator
    @Inject lateinit var noteAlertCoordinator: NoteAlertCoordinator

    /** 화면 생명주기와 무관하게 프로세스가 살아있는 동안 알림 재계획을 유지하는 스코프. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ItineraryNotifier.ensureChannel(this)
        reminderSyncCoordinator.start(applicationScope)
        noteAlertCoordinator.start(applicationScope)
    }
}
