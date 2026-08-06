package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Duration
import org.junit.Test

class ItineraryReminderPlannerTest {

    private val seoul = "Asia/Seoul"

    private fun item(
        id: String,
        startLocal: String,
        allDay: Boolean = false,
        status: ItineraryStatus = ItineraryStatus.PLANNED,
    ) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.TRANSPORT,
        startAt = LocalTimes.parseSeedLocal(startLocal, seoul),
        endAt = null,
        allDay = allDay,
        timeZone = seoul,
        status = status,
    )

    @Test
    fun `출발 훨씬 전이면 3개 오프셋 모두 계획된다`() {
        val flight = item("f1", "2026-09-09T11:05:00")
        val now = flight.startAt.minus(Duration.ofDays(2))

        val planned = ItineraryReminderPlanner.plan(listOf(flight), now)

        assertThat(planned.map { it.offset }).containsExactly(
            ReminderOffset.H24, ReminderOffset.H3, ReminderOffset.H1,
        )
        assertThat(planned.all { it.itineraryItemId == "f1" }).isTrue()
    }

    @Test
    fun `이미 지난 오프셋은 계획에서 제외된다`() {
        val flight = item("f1", "2026-09-09T11:05:00")
        // 출발 2시간 전: 24h, 3h 전은 이미 지났고 1h 전만 남음
        val now = flight.startAt.minus(Duration.ofHours(2))

        val planned = ItineraryReminderPlanner.plan(listOf(flight), now)

        assertThat(planned).hasSize(1)
        assertThat(planned.single().offset).isEqualTo(ReminderOffset.H1)
    }

    @Test
    fun `출발이 지나면 계획이 없다`() {
        val flight = item("f1", "2026-09-09T11:05:00")
        val now = flight.startAt.plus(Duration.ofMinutes(1))

        assertThat(ItineraryReminderPlanner.plan(listOf(flight), now)).isEmpty()
    }

    @Test
    fun `종일 일정과 완료-건너뜀 일정은 알림 대상이 아니다`() {
        val allDay = item("hotel", "2026-09-09", allDay = true)
        val done = item("done", "2026-09-09T11:05:00", status = ItineraryStatus.DONE)
        val skipped = item("skipped", "2026-09-09T12:05:00", status = ItineraryStatus.SKIPPED)
        val now = LocalTimes.parseSeedLocal("2026-09-01T00:00:00", seoul)

        assertThat(ItineraryReminderPlanner.plan(listOf(allDay, done, skipped), now)).isEmpty()
    }

    @Test
    fun `각 알림의 key는 일정과 오프셋 조합으로 고유하다`() {
        val flight = item("f1", "2026-09-09T11:05:00")
        val now = flight.startAt.minus(Duration.ofDays(2))

        val keys = ItineraryReminderPlanner.plan(listOf(flight), now).map { it.key }

        assertThat(keys).containsExactly("f1:H24", "f1:H3", "f1:H1")
        assertThat(keys.toSet()).hasSize(3)
    }
}
