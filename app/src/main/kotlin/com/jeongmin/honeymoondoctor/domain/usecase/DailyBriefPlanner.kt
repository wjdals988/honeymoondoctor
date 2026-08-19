package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "오늘 하루 요약" 아침 알림(백로그: 벤치마킹 — 여행 앱들이 아침에 그날 일정을 한 줄로
 * 밀어주는 패턴).
 *
 * 중요 일정 알림(24/3/1시간 전, [ItineraryReminderPlanner])과 역할이 다르다. 그쪽은
 * "곧 시작하는 일정 하나"를 알리고, 이쪽은 아침에 **그날 전체**를 한 번에 요약한다.
 *
 * 알림 본문을 예약 시점에 미리 만들어 둔다 — 기존 알림 경로가 title/body 문자열을 받아
 * 그대로 띄우는 구조라(ItineraryReminderWorker), 발사 시점에 DB를 다시 읽지 않아도 된다.
 * 대신 일정이 바뀌면 다시 계획해야 하며, 그건 [ItineraryReminderSyncCoordinator]가 한다.
 */
object DailyBriefPlanner {

    /** 아침 몇 시에 보낼지. 출발 준비를 하기엔 이르지 않고, 자는 사람을 깨우지 않는 시각. */
    val BRIEF_TIME: LocalTime = LocalTime.of(8, 0)

    /** 너무 먼 미래까지 잡아두면 일정이 바뀔 때마다 취소·재예약할 양만 늘어난다. */
    private const val HORIZON_DAYS = 7L

    data class DailyBrief(
        val date: LocalDate,
        val fireAt: Instant,
        val title: String,
        val body: String,
    ) {
        /** 예약 취소·중복 방지용 고유 키. */
        val key: String get() = "daily-brief-$date"
    }

    fun plan(
        items: List<ItineraryItem>,
        now: Instant,
        zoneId: ZoneId,
        tripStartDate: LocalDate?,
        tripEndDate: LocalDate?,
    ): List<DailyBrief> {
        val today = now.atZone(zoneId).toLocalDate()
        val from = maxOf(today, tripStartDate ?: today)
        val until = tripEndDate ?: today.plusDays(HORIZON_DAYS)
        if (until.isBefore(from)) return emptyList()

        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(until) && it.isBefore(today.plusDays(HORIZON_DAYS + 1)) }
            .mapNotNull { date -> briefFor(date, items, now, zoneId) }
            .toList()
    }

    private fun briefFor(
        date: LocalDate,
        items: List<ItineraryItem>,
        now: Instant,
        zoneId: ZoneId,
    ): DailyBrief? {
        val fireAt = date.atTime(BRIEF_TIME).atZone(zoneId).toInstant()
        // 이미 지난 시각으로는 예약하지 않는다(오늘 아침 8시가 지났으면 오늘 건은 건너뛴다).
        if (!fireAt.isAfter(now)) return null

        val ofDay = items
            .filter { it.status == ItineraryStatus.PLANNED }
            .filter { LocalTimes.toLocalDate(it.startAt, it.timeZone) == date }
            .sortedWith(compareBy({ !it.allDay }, { it.startAt }))
        // 일정이 없는 날까지 알림을 보내면 금방 꺼버린다.
        if (ofDay.isEmpty()) return null

        val first = ofDay.first()
        val firstLabel = if (first.allDay) {
            "종일 ${first.title}"
        } else {
            "${LocalTimes.formatTime(first.startAt, first.timeZone)} ${first.title}"
        }
        val rest = ofDay.size - 1
        return DailyBrief(
            date = date,
            fireAt = fireAt,
            title = "오늘 일정 ${ofDay.size}건",
            body = if (rest > 0) "$firstLabel 외 ${rest}건" else firstLabel,
        )
    }
}
