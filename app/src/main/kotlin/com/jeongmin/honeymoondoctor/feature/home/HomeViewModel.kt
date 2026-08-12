package com.jeongmin.honeymoondoctor.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.SyncStatus
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.BudgetRepository
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.ExpenseRepository
import com.jeongmin.honeymoondoctor.domain.repository.ItineraryRepository
import com.jeongmin.honeymoondoctor.domain.repository.ReservationRepository
import com.jeongmin.honeymoondoctor.domain.repository.SyncStatusRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.CurrentCityResolver
import com.jeongmin.honeymoondoctor.domain.usecase.ItineraryConflictDetector
import com.jeongmin.honeymoondoctor.domain.usecase.NextItineraryCalculator
import com.jeongmin.honeymoondoctor.domain.usecase.NextItinerarySnapshot
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import com.jeongmin.honeymoondoctor.domain.usecase.TripDaySummary
import com.jeongmin.honeymoondoctor.domain.usecase.TripOverviewBuilder
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
    /** 체류 기간이 오늘을 포함하는 도시 수. 2 이상이면 어느 도시를 기준으로 삼았는지 알려준다. */
    val overlappingCityCount: Int = 0,
    /** 홈 표시 기준 시간대(현재 도시 → 다음 일정 → 한국 순으로 결정) */
    val displayZoneId: String = "Asia/Seoul",
    val now: Instant = Instant.EPOCH,
    val next: NextItinerarySnapshot? = null,
    /** 여행 기간 전체 날짜별 요약. 출발 전 오버뷰에서 "어느 날이 비었나"를 보여준다. */
    val tripDays: List<TripDaySummary> = emptyList(),
    val conflictCount: Int = 0,
    /** 출발 전 홈 요약(스펙 7-2): 미완료 필수 준비물, 확인 필요 예약, 예산/지출/잔여 */
    val requiredChecklistIncomplete: Int = 0,
    val attentionReservationCount: Int = 0,
    val totalBudgetKrw: Long = 0,
    val totalSpentKrw: Long = 0,
    /** 여행 중에만 노출(스펙 7-2): 오프라인 상태·마지막 동기화·동기화 대기 변경 수 */
    val syncStatus: SyncStatus? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeCurrentTrip: ObserveCurrentTrip,
    @ApplicationContext private val context: Context,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    itineraryRepository: ItineraryRepository,
    cityRepository: CityRepository,
    checklistRepository: ChecklistRepository,
    reservationRepository: ReservationRepository,
    expenseRepository: ExpenseRepository,
    budgetRepository: BudgetRepository,
    syncStatusRepository: SyncStatusRepository,
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
                observeCurrentTrip().flatMapLatest { trip ->
                    if (trip == null) {
                        flowOf(HomeUiState(loading = false))
                    } else {
                        val preparation = combine(
                            checklistRepository.observeChecklist(trip.id),
                            reservationRepository.observeReservations(trip.id),
                            expenseRepository.observeExpenses(trip.id),
                            budgetRepository.observeBudgets(trip.id),
                        ) { checklist, reservations, expenses, budgets ->
                            PreparationSummary(
                                requiredChecklistIncomplete = checklist.count { it.required && !it.completed },
                                attentionReservationCount = reservations.count {
                                    it.status in setOf(
                                        ReservationStatus.NEEDS_CHECK,
                                        ReservationStatus.NEEDS_BOOKING,
                                        ReservationStatus.NEEDS_PAYMENT,
                                    )
                                },
                                totalBudgetKrw = budgets.sumOf { it.budgetKrw },
                                totalSpentKrw = expenses.sumOf { it.amountKrw },
                            )
                        }
                        combine(
                            itineraryRepository.observeItinerary(trip.id),
                            cityRepository.observeCities(trip.id),
                            preparation,
                            syncStatusRepository.observeSyncStatus(trip.id),
                            clock,
                        ) { items, cities, summary, syncStatus, now ->
                            buildState(trip, items, cities, now, summary, syncStatus)
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
        summary: PreparationSummary,
        syncStatus: SyncStatus,
    ): HomeUiState {
        val tripStart = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
        val tripEnd = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()

        // 현재 도시. 이동일에는 두 도시 기간이 하루 겹치는 게 정상이라 규칙이 필요하다
        // (늦게 시작한 도시 → 기간 짧은 도시 → 도시명 순). CurrentCityResolver 참고.
        val citySelection = CurrentCityResolver.resolve(cities, now)
        val currentCity = citySelection.city

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
            overlappingCityCount = citySelection.overlappingCount,
            displayZoneId = displayZoneId,
            now = now,
            next = snapshot,
            tripDays = TripOverviewBuilder.build(trip.startDate, trip.endDate, items),
            conflictCount = ItineraryConflictDetector.findConflictingIds(items).size,
            requiredChecklistIncomplete = summary.requiredChecklistIncomplete,
            attentionReservationCount = summary.attentionReservationCount,
            totalBudgetKrw = summary.totalBudgetKrw,
            totalSpentKrw = summary.totalSpentKrw,
            syncStatus = syncStatus,
        )
    }
}

private data class PreparationSummary(
    val requiredChecklistIncomplete: Int,
    val attentionReservationCount: Int,
    val totalBudgetKrw: Long,
    val totalSpentKrw: Long,
)
