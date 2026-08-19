package com.jeongmin.honeymoondoctor.domain.model

/** 새 여행 생성 시 사용자가 직접 입력하는 값. 날짜는 ISO-8601(yyyy-MM-dd) 문자열이다. */
data class NewTripDraft(
    val name: String,
    val startDate: String,
    val endDate: String,
    val defaultCurrency: String,
    /**
     * 첫 목적지. 여행 생성과 함께 도시로 저장한다(체류 기간은 여행 기간과 동일).
     *
     * 종전에는 여행을 만들 때 목적지를 아예 묻지 않아, 도시가 없는 채로 시작했다.
     * 그러면 홈의 현지 시각이 Asia/Seoul로 고정되고, 주변 탭은 거리 계산 기준점이 없어
     * "거리 계산 불가"가 뜨며, 일정의 기본 시간대도 서울이 된다. null이면 종전과 같다.
     */
    val firstCity: City? = null,
)
