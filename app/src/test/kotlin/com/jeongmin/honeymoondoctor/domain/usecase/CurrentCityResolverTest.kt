package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.City
import java.time.Instant
import org.junit.Test

class CurrentCityResolverTest {

    private fun city(
        id: String,
        name: String,
        zone: String = "Europe/Prague",
        start: String? = null,
        end: String? = null,
    ) = City(
        id = id,
        displayName = name,
        countryCode = "CZ",
        timeZoneId = zone,
        startDate = start,
        endDate = end,
        referenceLatitude = null,
        referenceLongitude = null,
        notes = null,
    )

    /** 2026-08-12 09:00 UTC — 프라하(UTC+2) 11시, 서울(UTC+9) 18시 */
    private val now: Instant = Instant.parse("2026-08-12T09:00:00Z")

    @Test
    fun `체류 기간이 없는 도시는 후보가 아니다`() {
        val result = CurrentCityResolver.resolve(listOf(city("c1", "Prague")), now)

        assertThat(result.city).isNull()
        assertThat(result.overlappingCount).isEqualTo(0)
    }

    @Test
    fun `한쪽 날짜만 있으면 기간 판정이 불가능하므로 제외한다`() {
        val onlyStart = city("c1", "Prague", start = "2026-08-10")

        assertThat(CurrentCityResolver.resolve(listOf(onlyStart), now).city).isNull()
    }

    @Test
    fun `오늘을 포함하는 도시 한 곳이면 그 도시를 고른다`() {
        val prague = city("c1", "Prague", start = "2026-08-10", end = "2026-08-15")

        val result = CurrentCityResolver.resolve(listOf(prague), now)

        assertThat(result.city?.id).isEqualTo("c1")
        assertThat(result.overlappingCount).isEqualTo(1)
    }

    @Test
    fun `이동일에 기간이 겹치면 늦게 시작한 도시를 고른다`() {
        val paris = city("c1", "Paris", zone = "Europe/Paris", start = "2026-08-09", end = "2026-08-12")
        val prague = city("c2", "Prague", start = "2026-08-12", end = "2026-08-16")

        val result = CurrentCityResolver.resolve(listOf(paris, prague), now)

        assertThat(result.city?.displayName).isEqualTo("Prague")
        assertThat(result.overlappingCount).isEqualTo(2)
    }

    @Test
    fun `목록 순서가 바뀌어도 같은 도시를 고른다`() {
        val paris = city("c1", "Paris", zone = "Europe/Paris", start = "2026-08-09", end = "2026-08-12")
        val prague = city("c2", "Prague", start = "2026-08-12", end = "2026-08-16")

        val forward = CurrentCityResolver.resolve(listOf(paris, prague), now).city
        val reversed = CurrentCityResolver.resolve(listOf(prague, paris), now).city

        assertThat(forward?.id).isEqualTo(reversed?.id)
    }

    @Test
    fun `시작일이 같으면 기간이 짧은 도시를 고른다`() {
        val wholeTrip = city("c1", "Europe", start = "2026-08-10", end = "2026-08-20")
        val thisCity = city("c2", "Prague", start = "2026-08-10", end = "2026-08-13")

        val result = CurrentCityResolver.resolve(listOf(wholeTrip, thisCity), now)

        assertThat(result.city?.displayName).isEqualTo("Prague")
    }

    @Test
    fun `시작일과 기간이 모두 같으면 도시명 사전순으로 못 박는다`() {
        val b = city("c1", "Brno", start = "2026-08-10", end = "2026-08-15")
        val a = city("c2", "Ansan", zone = "Asia/Seoul", start = "2026-08-10", end = "2026-08-15")

        assertThat(CurrentCityResolver.resolve(listOf(b, a), now).city?.displayName).isEqualTo("Ansan")
        assertThat(CurrentCityResolver.resolve(listOf(a, b), now).city?.displayName).isEqualTo("Ansan")
    }

    @Test
    fun `오늘 여부는 각 도시 자신의 시간대로 판정한다`() {
        // 프라하(UTC+2)로는 아직 8월 12일이지만 서울(UTC+9)로는 이미 8월 12일 18시다.
        // 8월 12일까지인 도시는 두 시간대 모두 포함하므로, 시간대 처리가 깨지면 여기서 드러난다.
        val untilToday = city("c1", "Prague", start = "2026-08-01", end = "2026-08-12")
        val seoulUntilYesterday = city("c2", "Seoul", zone = "Asia/Seoul", start = "2026-08-01", end = "2026-08-11")

        val result = CurrentCityResolver.resolve(listOf(untilToday, seoulUntilYesterday), now)

        assertThat(result.city?.displayName).isEqualTo("Prague")
        assertThat(result.overlappingCount).isEqualTo(1)
    }

    @Test
    fun `시간대 문자열이 깨져 있으면 크래시 없이 제외한다`() {
        val broken = city("c1", "Broken", zone = "Not/AZone", start = "2026-08-10", end = "2026-08-15")

        assertThat(CurrentCityResolver.resolve(listOf(broken), now).city).isNull()
    }
}
