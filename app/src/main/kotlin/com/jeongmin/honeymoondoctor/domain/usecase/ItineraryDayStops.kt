package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.Place

/**
 * 일정 지도 보기(백로그): 하루치 일정 중 좌표를 가진 장소(Place)에 연결된 것만 골라
 * 방문 순서(종일 → 시간순, 목록 화면과 같은 순서)대로 번호를 매긴다. placeId가 없거나
 * 연결된 장소에 좌표가 없으면 지도에는 찍히지 않는다("위치 미확인"으로 화면에서 안내).
 */
object ItineraryDayStops {

    data class Stop(
        val sequenceNumber: Int,
        val item: ItineraryItem,
        val place: Place,
        /**
         * 직전 경유지에서의 **직선** 거리(m). 첫 경유지는 null.
         *
         * 실제 이동 거리·소요 시간이 아니다 — 경로 API를 쓰지 않으므로(월 0원 제약)
         * "대충 얼마나 떨어져 있나"만 알려주고, 화면 문구도 "직선"임을 밝힌다.
         */
        val straightLineFromPreviousMeters: Double? = null,
    )

    /** [allDayItems]/[timedItems]는 ItineraryDay와 같은 순서로 넘겨야 한다(종일 → 시간순). */
    fun resolve(allDayItems: List<ItineraryItem>, timedItems: List<ItineraryItem>, places: List<Place>): List<Stop> {
        val placesById = places.associateBy { it.id }
        return (allDayItems + timedItems)
            .mapNotNull { item ->
                item.placeId?.let(placesById::get)?.takeIf { it.hasCoordinates }?.let { item to it }
            }
            .mapIndexed { index, pair -> Stop(index + 1, pair.first, pair.second) }
            .withStraightLineDistances()
    }

    private fun List<Stop>.withStraightLineDistances(): List<Stop> = mapIndexed { index, stop ->
        if (index == 0) return@mapIndexed stop
        val previous = this[index - 1].place
        stop.copy(
            straightLineFromPreviousMeters = Haversine.distanceMeters(
                previous.latitude!!, previous.longitude!!,
                stop.place.latitude!!, stop.place.longitude!!,
            ),
        )
    }
}
