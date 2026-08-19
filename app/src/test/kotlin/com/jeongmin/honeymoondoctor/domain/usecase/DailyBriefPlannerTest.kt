package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class DailyBriefPlannerTest {

    private val zone = "Asia/Seoul"
    private val zoneId = ZoneId.of(zone)

    private fun item(
        id: String,
        startLocal: String,
        allDay: Boolean = false,
        status: ItineraryStatus = ItineraryStatus.PLANNED,
    ) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.SIGHTSEEING,
        startAt = LocalTimes.parseSeedLocal(startLocal, zone),
        endAt = null,
        allDay = allDay,
        timeZone = zone,
        status = status,
    )

    private fun now(local: String) = LocalTimes.parseSeedLocal(local, zone)

    @Test
    fun `그날 일정 건수와 첫 일정을 알린다`() {
        val items = listOf(
            item("공항 이동", "2026-09-11T09:00:00"),
            item("점심", "2026-09-11T12:00:00"),
        )

        val briefs = DailyBriefPlanner.plan(
            items, now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        val target = briefs.single { it.date == LocalDate.parse("2026-09-11") }
        assertThat(target.title).isEqualTo("오늘 일정 2건")
        assertThat(target.body).isEqualTo("09:00 공항 이동 외 1건")
        assertThat(target.fireAt).isEqualTo(now("2026-09-11T08:00:00"))
    }

    @Test
    fun `일정이 하나면 외 N건을 붙이지 않는다`() {
        val briefs = DailyBriefPlanner.plan(
            listOf(item("공항 이동", "2026-09-11T09:00:00")),
            now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        assertThat(briefs.single().body).isEqualTo("09:00 공항 이동")
    }

    @Test
    fun `종일 일정이 시간 일정보다 먼저 온다`() {
        val items = listOf(
            item("호텔 체크인", "2026-09-11T15:00:00"),
            item("자유일정", "2026-09-11T00:00:00", allDay = true),
        )

        val briefs = DailyBriefPlanner.plan(
            items, now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        assertThat(briefs.single().body).startsWith("종일 자유일정")
    }

    @Test
    fun `일정이 없는 날은 알리지 않는다`() {
        val briefs = DailyBriefPlanner.plan(
            emptyList(), now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        assertThat(briefs).isEmpty()
    }

    @Test
    fun `이미 지난 아침 시각은 예약하지 않는다`() {
        // 9/11 09:00 현재 → 같은 날 08:00은 이미 지났다
        val briefs = DailyBriefPlanner.plan(
            listOf(item("점심", "2026-09-11T12:00:00")),
            now("2026-09-11T09:00:00"), zoneId,
            LocalDate.parse("2026-09-11"), LocalDate.parse("2026-09-11"),
        )

        assertThat(briefs).isEmpty()
    }

    @Test
    fun `완료 처리한 일정은 세지 않는다`() {
        val items = listOf(
            item("취소된 투어", "2026-09-11T09:00:00", status = ItineraryStatus.SKIPPED),
            item("점심", "2026-09-11T12:00:00"),
        )

        val briefs = DailyBriefPlanner.plan(
            items, now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        assertThat(briefs.single().title).isEqualTo("오늘 일정 1건")
    }

    @Test
    fun `여행 기간 밖 날짜는 계획하지 않는다`() {
        val briefs = DailyBriefPlanner.plan(
            listOf(item("귀국 후 일정", "2026-09-20T09:00:00")),
            now("2026-09-10T20:00:00"), zoneId,
            LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12"),
        )

        assertThat(briefs).isEmpty()
    }
}
