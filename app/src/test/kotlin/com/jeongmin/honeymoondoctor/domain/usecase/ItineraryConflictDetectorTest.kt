package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import org.junit.Test

class ItineraryConflictDetectorTest {

    private val prague = "Europe/Prague"
    private val madrid = "Europe/Madrid"

    private fun item(
        id: String,
        startLocal: String,
        endLocal: String?,
        zone: String = prague,
        endZone: String? = null,
        allDay: Boolean = false,
        status: ItineraryStatus = ItineraryStatus.PLANNED,
    ) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.SIGHTSEEING,
        startAt = LocalTimes.parseSeedLocal(startLocal, zone),
        endAt = endLocal?.let { LocalTimes.parseSeedLocal(it, endZone ?: zone) },
        allDay = allDay,
        timeZone = zone,
        endTimeZone = endZone,
        status = status,
    )

    @Test
    fun `겹치는 두 시간 일정을 모두 찾아낸다`() {
        val a = item("a", "2026-09-10T10:00:00", "2026-09-10T12:00:00")
        val b = item("b", "2026-09-10T11:00:00", "2026-09-10T13:00:00")
        val c = item("c", "2026-09-10T14:00:00", "2026-09-10T15:00:00")

        assertThat(ItineraryConflictDetector.findConflictingIds(listOf(a, b, c)))
            .containsExactly("a", "b")
    }

    @Test
    fun `끝과 시작이 정확히 맞닿으면 겹침이 아니다`() {
        val a = item("a", "2026-09-10T10:00:00", "2026-09-10T12:00:00")
        val b = item("b", "2026-09-10T12:00:00", "2026-09-10T13:00:00")

        assertThat(ItineraryConflictDetector.findConflictingIds(listOf(a, b))).isEmpty()
    }

    @Test
    fun `시간대가 달라도 실제 UTC 기준으로 겹침을 판정한다`() {
        // 프라하 11:00~13:00 와 마드리드 12:30~14:00 은 같은 UTC 오프셋(CEST)이라 실제로 겹친다
        val a = item("a", "2026-09-10T11:00:00", "2026-09-10T13:00:00", zone = prague)
        val b = item("b", "2026-09-10T12:30:00", "2026-09-10T14:00:00", zone = madrid)

        assertThat(ItineraryConflictDetector.findConflictingIds(listOf(a, b)))
            .containsExactly("a", "b")
    }

    @Test
    fun `완료·건너뜀·종일·종료없음 일정은 겹침 판정에서 제외한다`() {
        val done = item("done", "2026-09-10T10:00:00", "2026-09-10T12:00:00", status = ItineraryStatus.DONE)
        val skipped = item("skipped", "2026-09-10T10:30:00", "2026-09-10T12:30:00", status = ItineraryStatus.SKIPPED)
        val noEnd = item("noEnd", "2026-09-10T10:00:00", null)
        val allDay = item("allDay", "2026-09-10", "2026-09-10", allDay = true)
        val planned = item("planned", "2026-09-10T10:00:00", "2026-09-10T12:00:00")

        assertThat(ItineraryConflictDetector.findConflictingIds(listOf(done, skipped, noEnd, allDay, planned)))
            .isEmpty()
    }
}
