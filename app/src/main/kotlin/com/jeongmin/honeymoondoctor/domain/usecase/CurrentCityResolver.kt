package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.City
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "지금 어느 도시에 있는가"를 도시별 체류 기간에서 판정한 결과.
 *
 * [overlappingCount]는 오늘을 포함하는 도시가 몇 곳인지다. 2 이상이면 판정이 규칙에 의존한
 * 것이므로 화면에서 사용자에게 알려줘야 한다 — 이동일에는 두 도시 기간이 하루 겹치는 게
 * 정상이고, 그때 어느 시간대로 보이는지 예측할 수 없으면 시계를 신뢰할 수 없다.
 */
data class CurrentCitySelection(
    val city: City?,
    val overlappingCount: Int,
)

/**
 * 체류 기간이 오늘을 포함하는 도시를 고른다.
 *
 * 겹칠 때의 규칙(우선순위 순):
 * 1. **늦게 시작한 도시** — 이동일에는 방금 도착한 도시가 "지금 있는 곳"이다.
 *    (파리 08-10~08-12, 프라하 08-12~08-15 → 08-12에는 프라하)
 * 2. 시작일이 같으면 **기간이 짧은 도시** — 긴 쪽은 대개 전체 구간을 뭉뚱그린 항목이고
 *    짧은 쪽이 더 구체적인 체류다.
 * 3. 그래도 같으면 **도시명 사전순** — 임의 순서로 흔들리지 않게 못 박는다.
 *    (예전에는 목록의 첫 항목을 그냥 집어서, 도시를 수정하기만 해도 기준이 바뀔 수 있었다.)
 *
 * "오늘"은 각 도시 자신의 시간대로 판정한다. 한국 자정 기준으로 자르면 시차가 큰 도시에서
 * 하루가 어긋난다.
 */
object CurrentCityResolver {

    fun resolve(cities: List<City>, now: Instant): CurrentCitySelection {
        val covering = cities.filter { city -> city.covers(now) }
        if (covering.isEmpty()) return CurrentCitySelection(city = null, overlappingCount = 0)

        val selected = covering.minWithOrNull(
            compareByDescending<City> { it.startDateOrNull() }
                .thenBy { it.stayLengthDays() }
                .thenBy { it.displayName },
        )
        return CurrentCitySelection(city = selected, overlappingCount = covering.size)
    }

    private fun City.covers(now: Instant): Boolean {
        val start = startDateOrNull() ?: return false
        val end = endDateOrNull() ?: return false
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrNull() ?: return false
        val todayThere = now.atZone(zone).toLocalDate()
        return !todayThere.isBefore(start) && !todayThere.isAfter(end)
    }

    private fun City.startDateOrNull(): LocalDate? =
        startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun City.endDateOrNull(): LocalDate? =
        endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 정렬용 체류 일수. 날짜가 깨져 있으면 가장 뒤로 밀리게 큰 값을 준다. */
    private fun City.stayLengthDays(): Long {
        val start = startDateOrNull() ?: return Long.MAX_VALUE
        val end = endDateOrNull() ?: return Long.MAX_VALUE
        return end.toEpochDay() - start.toEpochDay()
    }
}
