package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/**
 * 공개된 여행의 사본 요약. trips/{id}(원본)와는 별개 컬렉션(publicTrips)에 저장되며,
 * 공개 시점에 화이트리스트 필드만 골라 복사한 값이다 — 예약·경비·준비물·결정함·개인 메모는
 * 여기 포함되지 않는다(발행 시점의 Kotlin 매핑으로 범위를 고정, firestore.rules 참고).
 */
data class PublicTripSummary(
    val tripId: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val cityNames: List<String>,
    val itineraryCount: Int,
    val publishedAt: Instant?,
)
