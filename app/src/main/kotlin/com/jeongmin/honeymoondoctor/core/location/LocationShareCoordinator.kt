package com.jeongmin.honeymoondoctor.core.location

import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.LocationShareMode
import com.jeongmin.honeymoondoctor.domain.model.MemberLocation
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.MemberLocationRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * "우리 위치" 자동 공유의 실행부. MainActivity가 RESUMED 동안만 [runWhileForeground]를
 * 돌리므로, 어떤 모드든 **앱이 화면에 떠 있는 동안만** 전송이 일어난다 — 백그라운드
 * 추적 코드 경로 자체가 존재하지 않는다(방침 6-0항의 근거).
 *
 * 실패는 조용히 삼킨다: 자동 공유는 부가 기능이라, GPS가 안 잡히거나 네트워크가 없다고
 * 사용자를 방해하면 안 된다. 수동 공유(TogetherViewModel)는 반대로 실패를 표시한다 —
 * 버튼을 눌렀는데 아무 일도 없으면 그게 더 혼란스럽다.
 */
@Singleton
class LocationShareCoordinator @Inject constructor(
    private val appPreferences: AppPreferences,
    private val observeCurrentTrip: ObserveCurrentTrip,
    private val authRepository: AuthRepository,
    private val locationProvider: LocationProvider,
    private val memberLocationRepository: MemberLocationRepository,
) {

    /** RESUMED 스코프에서 호출된다. 스코프가 취소되면(백그라운드 전환) 즉시 멈춘다. */
    suspend fun runWhileForeground() {
        // 진입 시점 모드 1회 판정. 모드를 바꾸면 다음 포그라운드 진입부터 적용 —
        // 설정 변경을 실시간 반영하려고 flow를 combine하면 5분 타이머 관리가 복잡해지는데,
        // "앱을 다시 열면 적용된다"로 충분한 기능이다.
        val mode = appPreferences.snapshot.first().locationShareMode
        if (mode == LocationShareMode.MANUAL) return

        shareOnce()
        if (mode == LocationShareMode.EVERY_5_MIN_WHILE_USING) {
            while (true) {
                delay(5.minutes)
                shareOnce()
            }
        }
    }

    private suspend fun shareOnce() {
        runCatching {
            if (!locationProvider.hasLocationPermission()) return
            val uid = authRepository.currentUser.value?.uid ?: return
            val trip = observeCurrentTrip().first() ?: return
            val current = locationProvider.refreshCurrentLocation() ?: return
            memberLocationRepository.shareMyLocation(
                trip.id,
                MemberLocation(
                    uid = uid,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    sharedAt = Instant.now(),
                ),
            )
        }
    }
}
