package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 지금 보고 있는 여행(없으면 null).
 *
 * 화면 17곳이 이 유스케이스 하나만 바라보기 때문에, 여행을 여러 개 갖는 구조로 바꿀 때
 * 여기만 고치면 됐다. 각 화면은 여전히 "현재 여행 하나"만 알면 된다.
 *
 * 선택된 id가 내 여행 목록에 없으면(삭제됐거나 나갔거나 다른 기기에서 지워졌을 때) null을
 * 돌려준다 — AuthGate가 그 신호로 여행 목록 화면으로 되돌린다. 여기서 임의로 다른 여행을
 * 골라주면 사용자가 모르는 사이 다른 여행을 편집하게 된다.
 */
class ObserveCurrentTrip @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val appPreferences: AppPreferences,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Trip?> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            combine(
                tripRepository.observeMyTrips(user.uid),
                appPreferences.snapshot.map { it.selectedTripId },
            ) { trips, selectedId ->
                trips.firstOrNull { it.id == selectedId }
            }
        }
    }
}
