package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 다음 일정까지 남은 시간에 따른 강조 단계(스펙 7-2: 24시간/3시간/1시간 전 상태를 다르게 표시). */
enum class NextItineraryUrgency {
    ONGOING, // 시작했지만 아직 끝나지 않음
    WITHIN_1H,
    WITHIN_3H,
    WITHIN_24H,
    LATER,
}

data class NextItinerarySnapshot(
    /** 지금 진행 중인 시간 일정(있으면 홈에서 "진행 중"으로 표시). */
    val ongoing: ItineraryItem?,
    /** 현재 시각 이후 가장 빠른 미완료 시간 일정. */
    val next: ItineraryItem?,
    /** next(없으면 ongoing)까지의 남은 시간. ongoing만 있으면 종료까지 남은 시간. */
    val remaining: Duration?,
    val urgency: NextItineraryUrgency?,
    /** 오늘(표시 시간대 기준)의 시간 일정 타임라인 — 상태 무관 전체, 시작 시각 순. */
    val todayTimed: List<ItineraryItem>,
    /** 오늘에 걸쳐 있는 종일 일정 — 시간 일정과 구분해 표시(스펙 7-2). */
    val todayAllDay: List<ItineraryItem>,
)

/**
 * 홈 "다음 일정" 계산. 모든 비교는 UTC Instant로만 수행하므로 기기 시간대 변경·자정 경계·
 * 비행 중 시간대 이동에 영향을 받지 않는다. 호출 시점의 now를 받아 순수 함수로 동작한다
 * (백그라운드 복귀·시간대 변경 시 재계산은 호출자가 now만 다시 넣으면 된다).
 */
object NextItineraryCalculator {

    fun compute(items: List<ItineraryItem>, now: Instant, displayZone: ZoneId): NextItinerarySnapshot {
        val planned = items.filter { it.status == ItineraryStatus.PLANNED }
        val timedPlanned = planned.filter { !it.allDay }

        // 미완료 일정 중 현재 시각 이후 시작이 가장 빠른 것 = 다음 일정
        val next = timedPlanned
            .filter { it.startAt > now }
            .minByOrNull { it.startAt }

        // 이미 시작했지만 종료 전인 미완료 일정 = 진행 중(종료 시각이 없는 일정은 진행 중 취급 안 함)
        val ongoing = timedPlanned
            .filter { it.startAt <= now && (it.endAt ?: it.startAt) > now }
            .minByOrNull { it.startAt }

        val remaining = when {
            next != null -> Duration.between(now, next.startAt)
            ongoing?.endAt != null -> Duration.between(now, ongoing.endAt)
            else -> null
        }
        val urgency = when {
            next == null && ongoing != null -> NextItineraryUrgency.ONGOING
            remaining == null -> null
            remaining <= Duration.ofHours(1) -> NextItineraryUrgency.WITHIN_1H
            remaining <= Duration.ofHours(3) -> NextItineraryUrgency.WITHIN_3H
            remaining <= Duration.ofHours(24) -> NextItineraryUrgency.WITHIN_24H
            else -> NextItineraryUrgency.LATER
        }

        val today = now.atZone(displayZone).toLocalDate()
        // 오늘 타임라인은 "각 일정이 자기 시간대에서 시작하는 날짜"가 아니라, 사용자가 지금 보고 있는
        // 표시 시간대의 오늘 날짜에 걸치는지를 기준으로 한다.
        val todayTimed = items
            .filter { !it.allDay }
            .filter { it.startAt.atZone(displayZone).toLocalDate() == today }
            .sortedBy { it.startAt }
        val todayAllDay = items
            .filter { it.allDay }
            .filter { item ->
                val startDate = LocalTimes.toLocalDate(item.startAt, item.timeZone)
                val endDate = item.endAt?.let { LocalTimes.toLocalDate(it, item.effectiveEndTimeZone) } ?: startDate
                !today.isBefore(startDate) && !today.isAfter(endDate)
            }
            .sortedBy { it.startAt }

        return NextItinerarySnapshot(
            ongoing = ongoing,
            next = next,
            remaining = remaining,
            urgency = urgency,
            todayTimed = todayTimed,
            todayAllDay = todayAllDay,
        )
    }
}
