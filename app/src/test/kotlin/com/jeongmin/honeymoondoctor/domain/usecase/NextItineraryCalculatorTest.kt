package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class NextItineraryCalculatorTest {

    private val seoul = "Asia/Seoul"
    private val prague = "Europe/Prague"
    private val madrid = "Europe/Madrid"

    private fun timed(
        id: String,
        startLocal: String,
        startZone: String,
        endLocal: String? = null,
        endZone: String? = null,
        status: ItineraryStatus = ItineraryStatus.PLANNED,
    ) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.TRANSPORT,
        startAt = LocalTimes.parseSeedLocal(startLocal, startZone),
        endAt = endLocal?.let { LocalTimes.parseSeedLocal(it, endZone ?: startZone) },
        allDay = false,
        timeZone = startZone,
        endTimeZone = endZone,
        status = status,
    )

    private fun allDay(id: String, startDate: String, endDate: String, zone: String) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.REST,
        startAt = LocalTimes.parseSeedLocal(startDate, zone),
        endAt = LocalTimes.endOfDay(java.time.LocalDate.parse(endDate), zone),
        allDay = true,
        timeZone = zone,
    )

    // 실제 시드와 동일한 3개 항공편(모두 출발·도착 시간대가 다름)
    private val icnToPrg = timed("icn_prg", "2026-09-09T11:05:00", seoul, "2026-09-09T17:05:00", prague)
    private val prgToBcn = timed("prg_bcn", "2026-09-12T10:30:00", prague, "2026-09-12T13:05:00", madrid)
    private val madToIcn = timed("mad_icn", "2026-09-20T20:00:00", madrid, "2026-09-21T16:00:00", seoul)
    private val flights = listOf(icnToPrg, prgToBcn, madToIcn)

    @Test
    fun `출발 전에는 첫 항공편이 다음 일정이고 남은 시간이 정확하다`() {
        // 한국 시간 9/9 09:05 = 출발 2시간 전
        val now = LocalTimes.parseSeedLocal("2026-09-09T09:05:00", seoul)
        val snapshot = NextItineraryCalculator.compute(flights, now, ZoneId.of(seoul))

        assertThat(snapshot.next?.id).isEqualTo("icn_prg")
        assertThat(snapshot.remaining).isEqualTo(Duration.ofHours(2))
        assertThat(snapshot.urgency).isEqualTo(NextItineraryUrgency.WITHIN_3H)
    }

    @Test
    fun `비행 중에는 진행 중 일정으로 잡히고 다음 일정은 그 다음 항공편이다`() {
        // ICN→PRG 비행 중간(UTC 기준 이륙 후 4시간)
        val now = icnToPrg.startAt.plus(Duration.ofHours(4))
        val snapshot = NextItineraryCalculator.compute(flights, now, ZoneId.of(prague))

        assertThat(snapshot.ongoing?.id).isEqualTo("icn_prg")
        assertThat(snapshot.next?.id).isEqualTo("prg_bcn")
    }

    @Test
    fun `시간대가 다른 항공편의 실제 소요시간이 보존된다 - 프라하 바르셀로나`() {
        // PRG 10:30 → BCN 13:05, 같은 시간대(CET/CEST)이므로 실제 2시간 35분
        val duration = Duration.between(prgToBcn.startAt, prgToBcn.endAt)
        assertThat(duration).isEqualTo(Duration.ofMinutes(155))
    }

    @Test
    fun `마드리드-인천 야간 항공편은 도착 표기가 다음날이어도 실제 12시간이다`() {
        // MAD 9/20 20:00(UTC+2) → ICN 9/21 16:00(UTC+9): 벽시계는 +20시간이지만 실제는 13시간
        val duration = Duration.between(madToIcn.startAt, madToIcn.endAt)
        assertThat(duration).isEqualTo(Duration.ofHours(13))
    }

    @Test
    fun `완료·건너뜀 일정은 다음 일정 후보에서 제외된다`() {
        val doneFirst = flights.map {
            if (it.id == "icn_prg") it.copy(status = ItineraryStatus.DONE) else it
        }
        val now = LocalTimes.parseSeedLocal("2026-09-09T09:00:00", seoul)
        val snapshot = NextItineraryCalculator.compute(doneFirst, now, ZoneId.of(seoul))

        assertThat(snapshot.next?.id).isEqualTo("prg_bcn")
    }

    @Test
    fun `자정 직전과 직후에도 다음 일정 판정이 흔들리지 않는다`() {
        val beforeMidnight = LocalTimes.parseSeedLocal("2026-09-11T23:59:59", prague)
        val afterMidnight = LocalTimes.parseSeedLocal("2026-09-12T00:00:01", prague)

        val before = NextItineraryCalculator.compute(flights, beforeMidnight, ZoneId.of(prague))
        val after = NextItineraryCalculator.compute(flights, afterMidnight, ZoneId.of(prague))

        assertThat(before.next?.id).isEqualTo("prg_bcn")
        assertThat(after.next?.id).isEqualTo("prg_bcn")
        // 자정을 넘으면 "오늘 타임라인"만 바뀐다
        assertThat(before.todayTimed).isEmpty()
        assertThat(after.todayTimed.map { it.id }).containsExactly("prg_bcn")
    }

    @Test
    fun `기기 시간대가 바뀌어도 다음 일정과 남은 시간은 동일하다`() {
        val now = LocalTimes.parseSeedLocal("2026-09-12T08:00:00", prague)
        val inPrague = NextItineraryCalculator.compute(flights, now, ZoneId.of(prague))
        val inSeoulDevice = NextItineraryCalculator.compute(flights, now, ZoneId.of(seoul))

        assertThat(inPrague.next?.id).isEqualTo(inSeoulDevice.next?.id)
        assertThat(inPrague.remaining).isEqualTo(inSeoulDevice.remaining)
        // 표시 시간대에 따라 "오늘"은 달라질 수 있다(프라하 9/12 08:00 = 서울 9/12 15:00 → 같은 날)
        assertThat(inPrague.todayTimed.map { it.id }).containsExactly("prg_bcn")
        assertThat(inSeoulDevice.todayTimed.map { it.id }).containsExactly("prg_bcn")
    }

    @Test
    fun `종일 일정은 시간 일정과 분리되고 날짜 범위에 걸친 날에만 나타난다`() {
        val hotel = allDay("hotel", "2026-09-09", "2026-09-12", prague)
        val items = flights + hotel

        val during = LocalTimes.parseSeedLocal("2026-09-10T12:00:00", prague)
        val snapshot = NextItineraryCalculator.compute(items, during, ZoneId.of(prague))
        assertThat(snapshot.todayAllDay.map { it.id }).containsExactly("hotel")
        assertThat(snapshot.next?.id).isEqualTo("prg_bcn") // 종일 일정은 다음 일정 후보가 아니다

        val after = LocalTimes.parseSeedLocal("2026-09-13T12:00:00", prague)
        val afterSnapshot = NextItineraryCalculator.compute(items, after, ZoneId.of(prague))
        assertThat(afterSnapshot.todayAllDay).isEmpty()
    }

    @Test
    fun `긴급도 단계가 1시간-3시간-24시간 경계에서 정확히 바뀐다`() {
        val zone = ZoneId.of(seoul)
        fun urgencyAt(minutesBefore: Long) = NextItineraryCalculator.compute(
            listOf(icnToPrg),
            icnToPrg.startAt.minus(Duration.ofMinutes(minutesBefore)),
            zone,
        ).urgency

        assertThat(urgencyAt(30)).isEqualTo(NextItineraryUrgency.WITHIN_1H)
        assertThat(urgencyAt(60)).isEqualTo(NextItineraryUrgency.WITHIN_1H)
        assertThat(urgencyAt(61)).isEqualTo(NextItineraryUrgency.WITHIN_3H)
        assertThat(urgencyAt(180)).isEqualTo(NextItineraryUrgency.WITHIN_3H)
        assertThat(urgencyAt(181)).isEqualTo(NextItineraryUrgency.WITHIN_24H)
        assertThat(urgencyAt(60 * 24)).isEqualTo(NextItineraryUrgency.WITHIN_24H)
        assertThat(urgencyAt(60 * 24 + 1)).isEqualTo(NextItineraryUrgency.LATER)
    }

    @Test
    fun `모든 일정이 끝났으면 다음 일정이 없다`() {
        val now = Instant.parse("2026-09-22T00:00:00Z")
        val snapshot = NextItineraryCalculator.compute(flights, now, ZoneId.of(seoul))

        assertThat(snapshot.next).isNull()
        assertThat(snapshot.ongoing).isNull()
        assertThat(snapshot.remaining).isNull()
        assertThat(snapshot.urgency).isNull()
    }
}
