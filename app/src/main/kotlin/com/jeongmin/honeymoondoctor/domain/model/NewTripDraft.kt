package com.jeongmin.honeymoondoctor.domain.model

/** 새 여행 생성 시 사용자가 직접 입력하는 값. 날짜는 ISO-8601(yyyy-MM-dd) 문자열이다. */
data class NewTripDraft(
    val name: String,
    val startDate: String,
    val endDate: String,
    val defaultCurrency: String,
)
