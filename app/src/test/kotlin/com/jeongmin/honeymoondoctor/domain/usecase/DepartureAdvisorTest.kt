package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Duration
import java.time.Instant
import org.junit.Test

class DepartureAdvisorTest {

    private val now: Instant = Instant.parse("2026-09-10T08:00:00Z")

    private fun transport(startAt: Instant, type: ItineraryType = ItineraryType.TRANSPORT, allDay: Boolean = false) =
        ItineraryItem(
            id = "it-1",
            title = "공항 이동",
            type = type,
            startAt = startAt,
            endAt = null,
            allDay = allDay,
            timeZone = "Asia/Tokyo",
        )

    @Test
    fun `이동 일정 시작에서 여유를 뺀 시각을 권장한다`() {
        val item = transport(now.plus(Duration.ofHours(3)))

        val advice = DepartureAdvisor.advise(item, leadMinutes = 60, now = now)!!

        assertThat(advice.departAt).isEqualTo(now.plus(Duration.ofHours(2)))
        assertThat(advice.overdue).isFalse()
        assertThat(advice.remaining).isEqualTo(Duration.ofHours(2))
    }

    @Test
    fun `권장 시각이 지났으면 overdue다`() {
        // 30분 뒤 출발인데 여유가 60분 — 이미 나섰어야 한다.
        val item = transport(now.plus(Duration.ofMinutes(30)))

        val advice = DepartureAdvisor.advise(item, leadMinutes = 60, now = now)!!

        assertThat(advice.overdue).isTrue()
        assertThat(advice.remaining).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `이동이 아니면 권장하지 않는다`() {
        val meal = transport(now.plus(Duration.ofHours(3)), type = ItineraryType.MEAL)

        assertThat(DepartureAdvisor.advise(meal, 60, now)).isNull()
    }

    @Test
    fun `종일 일정과 이미 시작한 이동은 대상이 아니다`() {
        val allDay = transport(now.plus(Duration.ofHours(3)), allDay = true)
        val started = transport(now.minus(Duration.ofMinutes(1)))

        assertThat(DepartureAdvisor.advise(allDay, 60, now)).isNull()
        assertThat(DepartureAdvisor.advise(started, 60, now)).isNull()
        assertThat(DepartureAdvisor.advise(null, 60, now)).isNull()
    }

    @Test
    fun `여유가 0 이하면 계산하지 않는다`() {
        val item = transport(now.plus(Duration.ofHours(3)))

        assertThat(DepartureAdvisor.advise(item, 0, now)).isNull()
        assertThat(DepartureAdvisor.advise(item, -10, now)).isNull()
    }
}
