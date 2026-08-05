package com.jeongmin.honeymoondoctor.domain.model

/**
 * 여행 도시. startDate/endDate는 ISO-8601 날짜 문자열이며, 결정함에서 이동이 확정되기 전에는
 * null일 수 있다(시드의 바르셀로나 종료일·마드리드 시작일).
 */
data class City(
    val id: String,
    val displayName: String,
    val countryCode: String,
    val timeZoneId: String,
    val startDate: String?,
    val endDate: String?,
    val referenceLatitude: Double? = null,
    val referenceLongitude: Double? = null,
    val notes: String? = null,
)
