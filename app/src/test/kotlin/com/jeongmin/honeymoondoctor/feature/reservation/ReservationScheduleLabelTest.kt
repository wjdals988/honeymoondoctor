package com.jeongmin.honeymoondoctor.feature.reservation

import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationScheduleLabelTest {

    private fun reservation(
        startAt: Instant?,
        endAt: Instant?,
        timeZone: String = "Asia/Seoul",
        endTimeZone: String? = null,
        allDay: Boolean = false,
    ) = Reservation(
        id = "r1",
        type = ReservationType.FLIGHT,
        vendor = "테스트항공",
        title = "테스트",
        status = ReservationStatus.CONFIRMED,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        timeZone = timeZone,
        endTimeZone = endTimeZone,
    )

    @Test
    fun `같은 시간대·같은 날짜는 종료 날짜를 다시 적지 않는다`() {
        val r = reservation(
            startAt = Instant.parse("2026-09-18T11:13:00Z"),
            endAt = Instant.parse("2026-09-18T14:37:00Z"),
        )
        assertEquals("9월 18일 (금) 20:13 – 23:37", reservationScheduleLabel(r))
    }

    @Test
    fun `왕복 항공권처럼 같은 시간대에서 날짜가 갈리면 종료 날짜를 함께 적는다`() {
        // ICN 왕복: 9/9 11:05 출발 ~ 9/21 16:00 도착, 둘 다 Asia/Seoul.
        val r = reservation(
            startAt = Instant.parse("2026-09-09T02:05:00Z"),
            endAt = Instant.parse("2026-09-21T07:00:00Z"),
        )
        assertEquals("9월 9일 (수) 11:05 – 9월 21일 (월) 16:00", reservationScheduleLabel(r))
    }

    @Test
    fun `시간대가 다르고 같은 날이면 기존처럼 시간만 적는다`() {
        val r = reservation(
            startAt = Instant.parse("2026-09-12T08:30:00Z"), // 10:30 Europe/Prague
            endAt = Instant.parse("2026-09-12T11:05:00Z"), // 13:05 Europe/Madrid
            timeZone = "Europe/Prague",
            endTimeZone = "Europe/Madrid",
        )
        assertEquals("9월 12일 (토) 10:30(체코) → 13:05(스페인)", reservationScheduleLabel(r))
    }
}
