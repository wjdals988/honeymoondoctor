package com.jeongmin.honeymoondoctor.data.seed

import kotlinx.serialization.Serializable

/**
 * assets/seed/new_trip_defaults.json 을 그대로 매핑하는 데이터 클래스.
 * 여행명·기간·통화·도시·일정처럼 이번 여행에만 해당하는 값은 사용자가 여행 생성 화면에서
 * 직접 입력하므로 여기 담지 않는다. 이 파일에는 "어떤 여행이든 재사용 가능한" 기본
 * 준비물 체크리스트만 남긴다.
 */
@Serializable
data class NewTripDefaults(
    val seedVersion: String,
    val checklistItems: List<ChecklistItemSeed>,
)

@Serializable
data class ChecklistItemSeed(
    val checklistItemId: String,
    val title: String,
    val category: String,
    val ownerScope: String,
    val required: Boolean,
)
