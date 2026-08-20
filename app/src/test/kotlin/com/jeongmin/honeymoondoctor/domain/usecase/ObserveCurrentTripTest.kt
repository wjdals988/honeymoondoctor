package com.jeongmin.honeymoondoctor.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPrefsSnapshot
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.LocationShareMode
import com.jeongmin.honeymoondoctor.data.local.prefs.ThemeMode
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 실기기 실측으로 발견한 무한 재구독 루프의 회귀 테스트(2026-08-20).
 *
 * [FirebaseSyncStatusRepository]가 "서버 확인됨" 이벤트마다 `appPreferences.setLastSyncAt()`을
 * 쓰는데, 이 usecase가 selectedTripId 외의 다른 설정 변경(lastSyncAt 포함)에도 재emit하면
 * 홈 화면의 최상위 flatMapLatest가 매번 Firestore 리스너 전체를 재구독한다 — 그 재구독이
 * 다시 "서버 확인됨" 이벤트를 만들어 무한 루프가 된다. 실기기에서 DNS가 잠깐 끊겼다가
 * 여러 리스너가 동시에 재연결되는 순간 이 루프에 갇혀 홈 화면이 무한 로딩됐다.
 */
class ObserveCurrentTripTest {

    private fun snapshot(selectedTripId: String?, lastSyncAtEpochMillis: Long?) = AppPrefsSnapshot(
        selectedCityId = null,
        lastKnownLocation = null,
        pendingJoinTripId = null,
        selectedTripId = selectedTripId,
        lastSyncAtEpochMillis = lastSyncAtEpochMillis,
        scheduledReminderKeys = emptySet(),
        dailyBriefEnabled = true,
        scheduledDailyBriefKeys = emptySet(),
        lastExchangeRates = emptyMap(),
        themeMode = ThemeMode.SYSTEM,
        transportLeadMinutes = 60,
        locationShareMode = LocationShareMode.MANUAL,
        hasSeenOnboarding = true,
    )

    private fun trip(id: String) = Trip(
        id = id,
        name = "테스트 여행",
        startDate = "2026-09-09",
        endDate = "2026-09-20",
        defaultCurrency = "KRW",
        ownerId = "uid-1",
        memberIds = listOf("uid-1"),
        inviteCodeHash = null,
        status = TripStatus.ACTIVE,
        seedVersion = null,
    )

    @Test
    fun `선택된 여행이 안 바뀌면 다른 설정만 바뀌어도 재emit하지 않는다`() = runTest {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.currentUser } returns MutableStateFlow(AuthUser(uid = "uid-1", displayName = "정민", email = "jm@example.com"))

        val tripRepository = mockk<TripRepository>()
        every { tripRepository.observeMyTrips("uid-1") } returns flowOf(listOf(trip("trip-1")))

        // lastSyncAt만 다른 두 snapshot — FirebaseSyncStatusRepository가 "서버 확인됨"마다
        // 쓰는 값과 같은 종류의 변경이다. selectedTripId는 둘 다 "trip-1"로 동일하다.
        val appPreferences = mockk<AppPreferences>()
        every { appPreferences.snapshot } returns flowOf(
            snapshot(selectedTripId = "trip-1", lastSyncAtEpochMillis = 100L),
            snapshot(selectedTripId = "trip-1", lastSyncAtEpochMillis = 200L),
            snapshot(selectedTripId = "trip-1", lastSyncAtEpochMillis = 300L),
        )

        val observeCurrentTrip = ObserveCurrentTrip(authRepository, tripRepository, appPreferences)

        observeCurrentTrip().test {
            val trip = awaitItem()
            assertThat(trip?.id).isEqualTo("trip-1")
            // distinctUntilChanged가 없으면 lastSyncAt이 다른 두 emission이 추가로 들어온다.
            expectNoEvents()
        }
    }

    @Test
    fun `선택된 여행 id 자체가 바뀌면 다시 emit한다`() = runTest {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.currentUser } returns MutableStateFlow(AuthUser(uid = "uid-1", displayName = "정민", email = "jm@example.com"))

        val tripRepository = mockk<TripRepository>()
        every { tripRepository.observeMyTrips("uid-1") } returns
            flowOf(listOf(trip("trip-1"), trip("trip-2")))

        val appPreferences = mockk<AppPreferences>()
        every { appPreferences.snapshot } returns flowOf(
            snapshot(selectedTripId = "trip-1", lastSyncAtEpochMillis = 100L),
            snapshot(selectedTripId = "trip-2", lastSyncAtEpochMillis = 100L),
        )

        val observeCurrentTrip = ObserveCurrentTrip(authRepository, tripRepository, appPreferences)

        observeCurrentTrip().test {
            assertThat(awaitItem()?.id).isEqualTo("trip-1")
            assertThat(awaitItem()?.id).isEqualTo("trip-2")
        }
    }
}
