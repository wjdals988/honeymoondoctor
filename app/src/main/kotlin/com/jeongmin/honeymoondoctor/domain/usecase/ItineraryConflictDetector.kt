package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.time.Instant

/**
 * "과로 경고"(스펙 7-3): 시간이 겹치는 미완료 시간 일정을 찾는다. 저장을 차단하지 않고
 * 표시만 하므로, 결과는 겹침이 있는 일정 id 집합이다. 종일 일정과 종료 시각 없는 일정은
 * 겹침 판정에서 제외한다(임의로 기간을 추정하지 않는다).
 */
object ItineraryConflictDetector {

    private data class Interval(val id: String, val start: Instant, val end: Instant)

    fun findConflictingIds(items: List<ItineraryItem>): Set<String> {
        val candidates = items.mapNotNull { item ->
            val end = item.endAt
            if (item.status == ItineraryStatus.PLANNED && !item.allDay && end != null && end > item.startAt) {
                Interval(item.id, item.startAt, end)
            } else {
                null
            }
        }
        val conflicting = mutableSetOf<String>()
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val a = candidates[i]
                val b = candidates[j]
                // 경계가 정확히 맞닿는 경우(a.end == b.start)는 겹침이 아니다.
                if (a.start < b.end && b.start < a.end) {
                    conflicting += a.id
                    conflicting += b.id
                }
            }
        }
        return conflicting
    }
}
