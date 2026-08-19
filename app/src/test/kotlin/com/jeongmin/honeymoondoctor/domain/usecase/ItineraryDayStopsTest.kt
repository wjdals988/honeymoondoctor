package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import com.jeongmin.honeymoondoctor.domain.model.Place
import org.junit.Test

class ItineraryDayStopsTest {

    private val zone = "Asia/Seoul"

    private fun item(id: String, startLocal: String, allDay: Boolean = false, placeId: String? = null) = ItineraryItem(
        id = id,
        title = id,
        type = ItineraryType.SIGHTSEEING,
        startAt = LocalTimes.parseSeedLocal(startLocal, zone),
        endAt = null,
        allDay = allDay,
        timeZone = zone,
        placeId = placeId,
    )

    private fun place(id: String, hasCoordinates: Boolean = true) = Place(
        id = id,
        name = id,
        latitude = if (hasCoordinates) 37.0 else null,
        longitude = if (hasCoordinates) 127.0 else null,
    )

    @Test
    fun `placeId가 없는 일정은 제외한다`() {
        val a = item("a", "2026-09-10T09:00:00", placeId = null)

        assertThat(ItineraryDayStops.resolve(emptyList(), listOf(a), emptyList())).isEmpty()
    }

    @Test
    fun `연결된 장소에 좌표가 없으면 제외한다`() {
        val a = item("a", "2026-09-10T09:00:00", placeId = "p1")
        val p1 = place("p1", hasCoordinates = false)

        assertThat(ItineraryDayStops.resolve(emptyList(), listOf(a), listOf(p1))).isEmpty()
    }

    @Test
    fun `종일 일정이 시간 일정보다 먼저 번호를 받는다`() {
        val allDay = item("allday", "2026-09-10T00:00:00", allDay = true, placeId = "p1")
        val timed1 = item("t1", "2026-09-10T09:00:00", placeId = "p2")
        val timed2 = item("t2", "2026-09-10T14:00:00", placeId = "p3")
        val places = listOf(place("p1"), place("p2"), place("p3"))

        val stops = ItineraryDayStops.resolve(listOf(allDay), listOf(timed1, timed2), places)

        assertThat(stops.map { it.item.id }).containsExactly("allday", "t1", "t2").inOrder()
        assertThat(stops.map { it.sequenceNumber }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `같은 장소를 가리키는 두 일정은 각각 다른 번호를 받는다`() {
        val a = item("a", "2026-09-10T09:00:00", placeId = "p1")
        val b = item("b", "2026-09-10T14:00:00", placeId = "p1")
        val p1 = place("p1")

        val stops = ItineraryDayStops.resolve(emptyList(), listOf(a, b), listOf(p1))

        assertThat(stops).hasSize(2)
        assertThat(stops.map { it.sequenceNumber }).containsExactly(1, 2).inOrder()
        assertThat(stops.map { it.place.id }).containsExactly("p1", "p1")
    }
}
