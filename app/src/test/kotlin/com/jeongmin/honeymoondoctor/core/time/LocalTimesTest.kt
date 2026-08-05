package com.jeongmin.honeymoondoctor.core.time

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class LocalTimesTest {

    @Test
    fun `시각 포함 로컬 문자열을 해당 시간대 기준 UTC로 변환한다`() {
        // 2026-09-09 서울은 UTC+9
        val instant = LocalTimes.parseSeedLocal("2026-09-09T11:05:00", "Asia/Seoul")
        assertThat(instant).isEqualTo(Instant.parse("2026-09-09T02:05:00Z"))

        // 2026-09-09 프라하는 서머타임(CEST, UTC+2)
        val prg = LocalTimes.parseSeedLocal("2026-09-09T17:05:00", "Europe/Prague")
        assertThat(prg).isEqualTo(Instant.parse("2026-09-09T15:05:00Z"))
    }

    @Test
    fun `날짜만 있는 문자열은 그 시간대의 자정으로 해석한다`() {
        val instant = LocalTimes.parseSeedLocal("2026-09-09", "Europe/Prague")
        assertThat(instant).isEqualTo(Instant.parse("2026-09-08T22:00:00Z"))
    }

    @Test
    fun `종일 종료 경계는 그 시간대의 하루 끝이다`() {
        val end = LocalTimes.endOfDay(LocalDate.parse("2026-09-12"), "Europe/Madrid")
        // 마드리드 9/12 23:59:59.999 (CEST, UTC+2)
        assertThat(end).isEqualTo(Instant.parse("2026-09-12T21:59:59.999Z"))
    }

    @Test
    fun `UTC Instant를 표시 시간대의 로컬 날짜로 되돌릴 수 있다`() {
        // 마드리드 20:00 출발 항공편은 서울 기준으로는 이미 다음날 03:00
        val departure = LocalTimes.parseSeedLocal("2026-09-20T20:00:00", "Europe/Madrid")
        assertThat(LocalTimes.toLocalDate(departure, "Europe/Madrid")).isEqualTo(LocalDate.parse("2026-09-20"))
        assertThat(LocalTimes.toLocalDate(departure, "Asia/Seoul")).isEqualTo(LocalDate.parse("2026-09-21"))
    }
}
