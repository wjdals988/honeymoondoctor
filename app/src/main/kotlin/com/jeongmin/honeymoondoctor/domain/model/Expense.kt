package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/**
 * 지원 통화. [minorDigits]는 ISO 4217 최소 단위 자릿수로, KRW·JPY·VND는 0(소수점 없음)이다.
 *
 * 처음에는 KRW·EUR·CZK 세 개뿐이었다(프라하 신혼여행 전용 앱이던 시절의 잔재). 도시
 * 프리셋은 64개인데 통화가 셋이면 일본·동남아·미국 여행에서 지출을 아예 기록할 수
 * 없어서, 한국 출발 주요 목적지 통화를 채웠다.
 *
 * 값을 **추가**하는 것은 안전하다 — Firestore에서 읽을 때 `runCatching`으로 감싸
 * 모르는 코드는 KRW로 떨어지고, 기존 문서는 그대로 읽힌다. 반대로 **삭제·개명은
 * 금지**다. 이미 저장된 지출의 통화가 KRW로 조용히 바뀌어 금액이 틀어진다.
 *
 * [autoFetchable]은 유럽중앙은행 고시(frankfurter.dev)에 그 통화가 있는지다. 없으면
 * "오늘 환율 불러오기"가 실패할 수밖에 없어, 버튼을 내밀지 않고 직접 입력만 받는다.
 * VND·TWD가 그렇다(2026-08-12 API `/v1/currencies` 응답으로 확인).
 */
enum class TravelCurrency(
    val code: String,
    val minorDigits: Int,
    val symbol: String,
    val autoFetchable: Boolean = true,
) {
    KRW("KRW", 0, "원"),
    JPY("JPY", 0, "¥"),
    USD("USD", 2, "$"),
    EUR("EUR", 2, "€"),
    CNY("CNY", 2, "¥"),
    TWD("TWD", 2, "NT$", autoFetchable = false),
    HKD("HKD", 2, "HK$"),
    THB("THB", 2, "฿"),
    VND("VND", 0, "₫", autoFetchable = false),
    PHP("PHP", 2, "₱"),
    SGD("SGD", 2, "S$"),
    MYR("MYR", 2, "RM"),
    IDR("IDR", 2, "Rp"),
    GBP("GBP", 2, "£"),
    CHF("CHF", 2, "Fr"),
    AUD("AUD", 2, "A$"),
    TRY("TRY", 2, "₺"),
    CZK("CZK", 2, "Kč"),
}

/**
 * 경비 카테고리. 스펙 7-6의 7종에 항공을 더했다 — 여행 경비에서 항공권은 금액이
 * 가장 크고 성격도 현지 교통과 달라서(출발 전 결제, 1회성) 교통에 섞으면 현지
 * 교통비 파악이 안 된다.
 *
 * [emoji]는 화면 어디서든 라벨과 함께 쓴다([display]). 카테고리는 스캔하는 값이라
 * (목록에서 "식비만 훑기") 글자보다 먼저 눈에 걸리는 그림 하나가 스캔을 줄여 준다.
 *
 * 값 추가는 안전하고(Firestore 파싱이 runCatching + 기본값) 삭제·개명은 금지 —
 * 저장된 지출이 기타/식비로 조용히 바뀐다. TravelCurrency와 같은 규칙.
 */
enum class ExpenseCategory(val labelKo: String, val emoji: String) {
    FOOD("식비", "🍚"),
    CAFE("카페", "☕"),
    TRANSPORT("교통", "🚌"),
    FLIGHT("항공", "✈️"),
    LODGING("숙소", "🏨"),
    SIGHTSEEING("관광", "🗺️"),
    SHOPPING("쇼핑", "🛍️"),
    ETC("기타", "🧾"),
    ;

    /** 칩·목록·합계 어디서든 같은 모양으로 보이게 한 곳에서 만든다. */
    val display: String get() = "$emoji $labelKo"
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
