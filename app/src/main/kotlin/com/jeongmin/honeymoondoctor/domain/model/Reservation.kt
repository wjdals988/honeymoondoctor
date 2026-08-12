package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/** 스펙 7-4 예약 유형: 항공, 숙소, 교통, 투어·입장권, 식당, 기타 */
enum class ReservationType(val labelKo: String, val emoji: String) {
    FLIGHT("항공", "✈️"),
    LODGING("숙소", "🏨"),
    TRANSPORT("교통", "🚌"),
    TOUR("투어·입장권", "🎫"),
    RESTAURANT("식당", "🍽️"),
    ETC("기타", "🧾"),
    ;

    /** 경비 카테고리와 같은 이유(스캔 보조). 칩·목록에서 이 형태로 쓴다. */
    val display: String get() = "$emoji $labelKo"
}

/** 스펙 7-4 예약 상태: 확인 필요, 예약 필요, 예약 완료, 결제 필요, 이용 완료, 취소 */
enum class ReservationStatus(val labelKo: String) {
    NEEDS_CHECK("확인 필요"),
    NEEDS_BOOKING("예약 필요"),
    CONFIRMED("예약 완료"),
    NEEDS_PAYMENT("결제 필요"),
    USED("이용 완료"),
    CANCELED("취소"),
}

/**
 * 예약. 시간 모델은 일정과 동일(UTC Instant + 표시 시간대, 숙소는 allDay 날짜 범위).
 * confirmationCode/pin은 목록·로그에서 항상 마스킹하고 상세 화면에서만 원문을 보여준다(스펙 7-4).
 */
data class Reservation(
    val id: String,
    val type: ReservationType,
    val vendor: String,
    val title: String,
    val status: ReservationStatus,
    val confirmationCode: String? = null,
    val pin: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val allDay: Boolean = false,
    val timeZone: String = "Asia/Seoul",
    val endTimeZone: String? = null,
    val linkedItineraryId: String? = null,
    val estimatedKrw: Long? = null,
    val notes: String? = null,
    val assigneeUid: String? = null,
) {
    val effectiveEndTimeZone: String get() = endTimeZone ?: timeZone
}

/**
 * 예약번호·PIN 마스킹(스펙 7-4/9장): 목록·로그·테스트 출력에서 사용.
 * 4자 이하는 전부 가리고, 그보다 길면 앞 2자만 남긴다 — 남는 정보로 원문을 유추할 수 없게 한다.
 */
fun maskSecret(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    return if (raw.length <= 4) {
        "•".repeat(raw.length)
    } else {
        raw.take(2) + "•".repeat(raw.length - 2)
    }
}
