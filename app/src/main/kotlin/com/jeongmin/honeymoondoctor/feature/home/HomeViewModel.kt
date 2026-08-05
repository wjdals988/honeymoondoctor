package com.jeongmin.honeymoondoctor.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ItineraryConflictDetector
import com.jeongmin.honeymoondoctor.domain.usecase.NextItineraryCalculator
import com.jeongmin.honeymoondoctor.domain.usecase.NextItinerarySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    /** 여행 시작 전이면 출발까지 남은 일수(당일 0), 여행 중·이후면 null */
    val dDayToStart: Long? = null,
    val isDuringTrip: Boolean = false,
    val currentCity: City? = null,
    /** 홈 표시 기준 시간대(현재 도시 → 다음 일정 → 한국 순으로 결정) */
    val displayZoneId: String = "Asia/Seoul",
    val now: Instant = Instant.EPOCH,
    val next: NextItinerarySnapshot? = null,
    val conflictCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    itineraryRepository: ItineraryRepository,
    cityRepository: CityRepository,
) : ViewModel() {

    /**
     * 1분 단위 재계산 신호(스펙 7-2: 자정 경계·백그라운드 복귀·기기 시간대 변경 시 재계산).
     * - ACTION_TIME_TICK: 시스템이 매 분 정각에 보내는 브로드캐스트 → 자정 경계 포함 주기 갱신
     * - ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED: 수동 시각·시간대 변경 즉시 갱신
     * - 화면 복귀 시에는 stateIn(WhileSubscribed)이 flow를 재구독하며 최초 emit으로 즉시 재계산
     */
    private val clock = callbackFlow {
        trySend(Instant.now())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Instant.now())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    val uiState: StateFlow<HomeUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(HomeUiState(loading = false))
            } else {
                tripRepository.observeMyTrip(user.uid).flatMapLatest { trip ->
                    if (trip == null) {
                        flowOf(HomeUiState(loading = false))
                    } else {
                        combine(
                            itineraryRepository.observeItinerary(trip.id),
                            cityRepository.observeCities(trip.id),
                            clock,
                        ) { items, cities, now ->
                            buildState(trip, items, cities, now)
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(
        trip: Trip,
        items: List<com.jeongmin.honeymoondoctor.domain.model.ItineraryItem>,
        cities: List<City>,
        now: Instant,
    ): HomeUiState {
        val tripStart = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
        val tripEnd = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()

        // 현재 도시: 그 도시 시간대 기준 오늘 날짜가 도시 체류 기간에 들어가는 첫 도시
        val currentCity = cities.firstOrNull { city ->
            val start = city.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val end = city.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (start == null || end == null) return@firstOrNull false
            val todayThere = now.atZone(ZoneId.of(city.timeZoneId)).toLocalDate()
            !todayThere.isBefore(start) && !todayThere.isAfter(end)
        }

        // 표시 시간대: 현재 도시 → (여행 중이면) 다음/진행 중 일정의 시간대 → 한국
        val provisional = NextItineraryCalculator.compute(items, now, ZoneId.of("Asia/Seoul"))
        val seoulToday = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        val isDuringTrip = tripStart != null && tripEnd != null &&
            !seoulToday.isBefore(tripStart) && !seoulToday.isAfter(tripEnd)
        val displayZoneId = currentCity?.timeZoneId
            ?: (if (isDuringTrip) provisional.ongoing?.timeZone ?: provisional.next?.timeZone else null)
            ?: "Asia/Seoul"

        val snapshot = NextItineraryCalculator.compute(items, now, ZoneId.of(displayZoneId))
        val dDayToStart = if (tripStart != null && seoulToday.isBefore(tripStart)) {
            ChronoUnit.DAYS.between(seoulToday, tripStart)
        } else {
            null
        }

        return HomeUiState(
            loading = false,
            trip = trip,
            dDayToStart = dDayToStart,
            isDuringTrip = isDuringTrip,
            currentCity = currentCity,
            displayZoneId = displayZoneId,
            now = now,
            next = snapshot,
            conflictCount = ItineraryConflictDetector.findConflictingIds(items).size,
        )
    }
}
