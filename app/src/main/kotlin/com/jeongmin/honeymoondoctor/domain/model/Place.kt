package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

/** 주변 탭 필터와 1:1로 대응하는 장소 카테고리(스펙 7-7: 맛집, 카페, 관광, 쇼핑, 숙소). */
/** 이모지는 지도 핀·칩·목록에서 스캔 보조로 쓴다(경비 카테고리와 같은 이유). */
enum class PlaceCategory(val labelKo: String, val emoji: String) {
    RESTAURANT("맛집", "🍜"),
    CAFE("카페", "☕"),
    SIGHTSEEING("관광", "🗺️"),
    SHOPPING("쇼핑", "🛍️"),
    LODGING("숙소", "🏨"),
    ETC("기타", "📌"),
    ;

    val display: String get() = "$emoji $labelKo"
}

/** 개인 우선순위(스펙 7-7): 꼭 가기 15점, 가고 싶음 9점, 여유 시 4점. */
enum class PlacePriority(val labelKo: String, val score: Int) {
    MUST_GO("꼭 가기", 15),
    WANT_TO_GO("가고 싶음", 9),
    IF_TIME("여유 시", 4),
}

/** 추천 시간대: 오전, 점심, 오후, 저녁, 밤, 언제나 */
enum class PreferredTime(val labelKo: String) {
    MORNING("오전"),
    LUNCH("점심"),
    AFTERNOON("오후"),
    EVENING("저녁"),
    NIGHT("밤"),
    ANYTIME("언제나"),
}

/**
 * 장소. 좌표가 없으면 거리·거리점수를 계산하지 않고 "위치 미확인" 섹션에 남긴다.
 * 평점·리뷰 수는 실시간 수집이 아니라 사용자가 가져오거나 입력한 스냅샷이며,
 * [sourceUpdatedAt]이 스냅샷 확인일이다(스펙 7-7).
 */
data class Place(
    val id: String,
    val name: String,
    val cityId: String? = null,
    val category: PlaceCategory = PlaceCategory.ETC,
    val priority: PlacePriority = PlacePriority.WANT_TO_GO,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val notes: String? = null,
    val visitedAt: Instant? = null,
    val ratingSnapshot: Double? = null,
    val reviewCountSnapshot: Long? = null,
    val sourceUpdatedAt: Instant? = null,
    val preferredTimes: List<PreferredTime> = emptyList(),
) {
    val visited: Boolean get() = visitedAt != null
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}
