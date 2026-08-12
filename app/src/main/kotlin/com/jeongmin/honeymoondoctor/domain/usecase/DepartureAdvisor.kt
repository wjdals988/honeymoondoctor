package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Duration
import java.time.Instant

/**
 * 이동 일정의 출발 권장 시각(스펙 7-2 "이동 일정이면 출발 권장 시각").
 *
 * 권장 시각 = 이동 일정 시작 − 여유(분). 여유는 설정에서 사용자가 정한다(기본 60분).
 * 지도 API 없이 숙소→공항 이동시간을 계산할 수 없어서, "계산해 주는 척"보다
 * 사용자가 정한 여유를 정직하게 빼는 쪽을 골랐다. 항공처럼 여유가 더 필요한 날은
 * 설정에서 늘리면 된다.
 *
 * 대상은 시간이 있는 이동(TRANSPORT) 일정뿐이다. 종일 일정은 "몇 시에 나설지"라는
 * 질문 자체가 성립하지 않는다.
 */
object DepartureAdvisor {

    data class Advice(
        /** 이 시각까지는 나서야 한다. */
        val departAt: Instant,
        /** 권장 시각이 이미 지났다 — 홈에서 경고로 표시한다. */
        val overdue: Boolean,
        /** 권장 시각까지 남은 시간. [overdue]면 0. */
        val remaining: Duration,
    )

    fun advise(item: ItineraryItem?, leadMinutes: Int, now: Instant): Advice? {
        if (item == null || item.type != ItineraryType.TRANSPORT || item.allDay) return null
        if (leadMinutes <= 0) return null
        // 이동이 이미 시작됐으면 "언제 나설까"는 끝난 질문이다.
        if (item.startAt <= now) return null

        val departAt = item.startAt.minus(Duration.ofMinutes(leadMinutes.toLong()))
        val overdue = departAt <= now
        return Advice(
            departAt = departAt,
            overdue = overdue,
            remaining = if (overdue) Duration.ZERO else Duration.between(now, departAt),
        )
    }
}
