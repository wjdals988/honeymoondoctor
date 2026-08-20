package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/** 스펙 7-3의 일정 유형: 이동, 관광, 식사, 휴양, 쇼핑, 기타 */
enum class ItineraryType(val labelKo: String, val emoji: String) {
    TRANSPORT("이동", "🚌"),
    SIGHTSEEING("관광", "🗺️"),
    MEAL("식사", "🍽️"),
    REST("휴양", "🌴"),
    SHOPPING("쇼핑", "🛍️"),
    ETC("기타", "📌"),
    ;

    /** ExpenseCategory·ReservationType과 같은 이유(스캔 보조). 칩·목록에서 이 형태로 쓴다. */
    val display: String get() = "$emoji $labelKo"
}

/** 상태: 예정, 완료, 건너뜀. DONE/SKIPPED는 홈의 "다음 일정" 후보에서 제외된다. */
enum class ItineraryStatus(val labelKo: String) {
    PLANNED("예정"),
    DONE("완료"),
    SKIPPED("건너뜀"),
}

/**
 * 시각은 UTC Instant로만 보관하고(Firestore Timestamp와 동일 기준),
 * [timeZone]/[endTimeZone]은 표시용 IANA ID다. 프라하→바르셀로나처럼 출발·도착
 * 시간대가 다른 항공 일정을 위해 종료 시간대를 별도로 둔다(null이면 timeZone과 동일).
 */
data class ItineraryItem(
    val id: String,
    val title: String,
    val type: ItineraryType,
    val startAt: Instant,
    val endAt: Instant?,
    val allDay: Boolean,
    val timeZone: String,
    val endTimeZone: String? = null,
    val cityId: String? = null,
    val location: String? = null,
    val address: String? = null,
    val status: ItineraryStatus = ItineraryStatus.PLANNED,
    val assigneeUid: String? = null,
    val reservationId: String? = null,
    val estimatedKrw: Long? = null,
    val notes: String? = null,
    /** 주변 탭에 저장된 [Place]와의 연결(백로그: 일정 지도 보기). 좌표가 있어야 지도에 찍힌다. */
    val placeId: String? = null,
) {
    val effectiveEndTimeZone: String get() = endTimeZone ?: timeZone
}
