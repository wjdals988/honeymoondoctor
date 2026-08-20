package com.jeongmin.honeymoondoctor.core.notification

import android.content.Context
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.TripNote
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripNoteRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 프로세스가 살아 있는 동안 상대의 새 쪽지를 관찰해 알림을 띄운다.
 * ItineraryReminderSyncCoordinator와 같은 이유로 HoneymoonDoctorApp에서 한 번만 시작한다.
 *
 * [startedAt] 이전에 만들어진 쪽지는 알리지 않는다 — 앱을 켜는 순간 밀린 읽지 않은
 * 쪽지가 한꺼번에 알림으로 쏟아지는 것을 막는다(그건 배지가 담당할 몫이다).
 * 프로세스가 죽어 있던 동안의 쪽지는 애초에 알림이 불가능하고, 그게 이 기능의
 * 정직한 한계다(쪽지 = 열 때 확인).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class NoteAlertCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeCurrentTrip: ObserveCurrentTrip,
    private val tripNoteRepository: TripNoteRepository,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
) {
    private val startedAt: Instant = Instant.now()
    private val notifiedNoteIds = mutableSetOf<String>()

    private data class Snapshot(val tripId: String, val notes: List<TripNote>, val user: AuthUser?)

    fun start(scope: CoroutineScope) {
        observeCurrentTrip()
            .flatMapLatest { trip ->
                if (trip == null) {
                    flowOf<Snapshot?>(null)
                } else {
                    // 구성원 목록은 여기서 상시 구독하지 않는다 — 발신자 이름은 알림을 실제로
                    // 띄우는 드문 순간에만 필요해서, 아래 onEach에서 그때그때 1회성으로 읽는다
                    // (그 편이 "쪽지" 리스너를 앱 시작마다 하나 더 얹지 않는다).
                    combine(
                        tripNoteRepository.observeNotes(trip.id),
                        authRepository.currentUser,
                    ) { notes, user -> Snapshot(trip.id, notes, user) }
                }
            }
            .onEach { snapshot ->
                val (tripId, notes, user) = snapshot ?: return@onEach
                val myUid = user?.uid ?: return@onEach
                val toNotify = notes
                    .filter { it.senderUid != myUid && it.readAt == null }
                    .filter { it.createdAt.isAfter(startedAt) }
                    .filter { notifiedNoteIds.add(it.id) } // 같은 쪽지에 중복 알림 방지
                if (toNotify.isEmpty()) return@onEach
                val members = tripRepository.observeMembers(tripId).first()
                toNotify.forEach { note ->
                    val senderName = members.firstOrNull { it.uid == note.senderUid }?.displayName ?: "상대"
                    NoteNotifier.show(context, note.id.hashCode(), senderName, note.text)
                }
            }
            .launchIn(scope)
    }
}
