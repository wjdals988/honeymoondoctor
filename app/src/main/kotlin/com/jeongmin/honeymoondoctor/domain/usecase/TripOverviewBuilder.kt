package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 홈 오버뷰의 하루치 요약. 일정 탭이 항목 하나하나를 보여준다면 여기는 "며칠에 몇 건"만 본다.
 *
 * [firstTitle]은 그날의 첫 일정 제목이다. 건수만 있으면 "3건"이 무엇인지 알 수 없어
 * 일정 탭에 들어가 봐야 하고, 그러면 오버뷰가 목적을 잃는다.
 */
data class TripDaySummary(
    val date: LocalDate,
    /** 여행 몇째 날(D1부터). 여행 기간 밖이면 null. */
    val dayNumber: Int?,
    val itemCount: Int,
    val firstTitle: String?,
)

/**
 * 여행 기간 전체를 날짜별로 펼친 요약.
 *
 * 왜 홈에 필요한가: 계획 단계(출발 전)에는 "다음 일정"이 대개 비어 있거나 몇 주 뒤라
 * 화면이 사실상 빈다. 정작 그때 필요한 건 "어느 날이 아직 비었나"인데 그건 일정 탭에
 * 들어가야만 보였다. 빈 날을 [itemCount] 0으로 함께 내려보내 계획의 구멍이 드러나게 한다.
 *
 * 일정이 없는 날을 빼지 않는 것이 핵심이다 — 목록에 있는 것보다 없는 것이 정보다.
 */
object TripOverviewBuilder {

    fun build(startDate: String, endDate: String, items: List<ItineraryItem>): List<TripDaySummary> {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return emptyList()
        val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return emptyList()
        if (end.isBefore(start)) return emptyList()

        // 각 일정은 "자기 시간대 기준 시작 날짜"에 붙인다(일정 탭과 같은 규칙).
        val byDate = items.groupBy { LocalTimes.toLocalDate(it.startAt, it.timeZone) }

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val dayItems = byDate[date].orEmpty().sortedBy { it.startAt }
                TripDaySummary(
                    date = date,
                    dayNumber = (ChronoUnit.DAYS.between(start, date) + 1).toInt(),
                    itemCount = dayItems.size,
                    firstTitle = dayItems.firstOrNull()?.title,
                )
            }
            .toList()
    }
}
