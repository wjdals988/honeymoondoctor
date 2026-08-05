package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/** 지원 통화(스펙 7-6). minorDigits는 최소 단위 자릿수(KRW는 0 = 원 단위 정수). */
enum class TravelCurrency(val code: String, val minorDigits: Int, val symbol: String) {
    KRW("KRW", 0, "원"),
    EUR("EUR", 2, "€"),
    CZK("CZK", 2, "Kč"),
}

/** 스펙 7-6 경비 카테고리: 식비, 교통, 숙소, 관광, 쇼핑, 카페, 기타 */
enum class ExpenseCategory(val labelKo: String) {
    FOOD("식비"),
    TRANSPORT("교통"),
    LODGING("숙소"),
    SIGHTSEEING("관광"),
    SHOPPING("쇼핑"),
    CAFE("카페"),
    ETC("기타"),
}

/**
 * 경비. 금액은 최소 단위 정수 [amountMinor]로 저장한다(EUR 12.34 → 1234).
 * [fxRateToKrw]는 "1 통화 = X KRW"의 X이며 사용자가 입력·수정한다(자동 갱신 금지 — 스펙 7-6).
 * [amountKrw]는 입력 시점 환율로 HALF_UP 반올림해 함께 보존한 스냅샷이다.
 */
data class Expense(
    val id: String,
    val amountMinor: Long,
    val currency: TravelCurrency,
    val fxRateToKrw: Double,
    val amountKrw: Long,
    val category: ExpenseCategory,
    val paidByUid: String? = null,
    val shared: Boolean = true,
    val cityId: String? = null,
    val spentAt: Instant,
    val linkedItineraryId: String? = null,
    val linkedReservationId: String? = null,
    val memo: String? = null,
)

/** 도시별·카테고리별 예산(KRW). cityId/category가 null이면 전체 예산 항목이다. */
data class Budget(
    val id: String,
    val cityId: String? = null,
    val category: ExpenseCategory? = null,
    val budgetKrw: Long,
)
