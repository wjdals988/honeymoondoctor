package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Instant
import org.junit.Test

class TripOverviewBuilderTest {

    private fun item(
        id: String,
        title: String,
        startAt: String,
        zone: String = "Asia/Seoul",
    ) = ItineraryItem(
        id = id,
        title = title,
        type = ItineraryType.SIGHTSEEING,
        startAt = Instant.parse(startAt),
        endAt = null,
        allDay = false,
        timeZone = zone,
        endTimeZone = null,
        cityId = null,
        location = null,
        address = null,
        status = ItineraryStatus.PLANNED,
        assigneeUid = null,
        reservationId = null,
        estimatedKrw = null,
        notes = null,
    )

    @Test
    fun `여행 기간의 모든 날을 D1부터 순서대로 만든다`() {
        val days = TripOverviewBuilder.build("2026-08-10", "2026-08-13", emptyList())

        assertThat(days).hasSize(4)
        assertThat(days.map { it.dayNumber }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(days.first().date.toString()).isEqualTo("2026-08-10")
    }

    @Test
    fun `일정이 없는 날도 빼지 않는다`() {
        // 이 섹션의 존재 이유 — 목록에 있는 것보다 "비어 있는 날"이 계획에 필요한 정보다.
        val days = TripOverviewBuilder.build(
            "2026-08-10",
            "2026-08-12",
            listOf(item("i1", "성 투어", "2026-08-11T01:00:00Z")),
        )

        assertThat(days.map { it.itemCount }).containsExactly(0, 1, 0).inOrder()
        assertThat(days.count { it.itemCount == 0 }).isEqualTo(2)
    }

    @Test
    fun `그날 일정 제목을 시간순으로 모두 준다`() {
        val days = TripOverviewBuilder.build(
            "2026-08-10",
            "2026-08-10",
            listOf(
                item("i2", "저녁 식사", "2026-08-10T10:00:00Z"),
                item("i1", "아침 산책", "2026-08-10T00:00:00Z"),
            ),
        )

        assertThat(days.single().itemCount).isEqualTo(2)
        // 입력 순서가 아니라 시간순이어야 한다 — 화면이 이 순서를 동선으로 이어 붙인다.
        assertThat(days.single().titles).containsExactly("아침 산책", "저녁 식사").inOrder()
    }

    @Test
    fun `일정은 자기 시간대 기준 날짜에 붙는다`() {
        // 2026-08-10T20:00Z는 한국(UTC+9)에서 8월 11일 새벽 5시다.
        val days = TripOverviewBuilder.build(
            "2026-08-10",
            "2026-08-11",
            listOf(item("i1", "새벽 비행", "2026-08-10T20:00:00Z", zone = "Asia/Seoul")),
        )

        assertThat(days.map { it.itemCount }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `여행 기간 밖의 일정은 오버뷰에 나타나지 않는다`() {
        val days = TripOverviewBuilder.build(
            "2026-08-10",
            "2026-08-11",
            listOf(item("i1", "출발 전 미팅", "2026-08-01T01:00:00Z")),
        )

        assertThat(days.sumOf { it.itemCount }).isEqualTo(0)
    }

    @Test
    fun `날짜가 깨졌거나 종료가 시작보다 빠르면 빈 목록을 준다`() {
        assertThat(TripOverviewBuilder.build("", "2026-08-11", emptyList())).isEmpty()
        assertThat(TripOverviewBuilder.build("2026-08-12", "2026-08-11", emptyList())).isEmpty()
    }
}
