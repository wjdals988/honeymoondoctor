package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 추천 점수(스펙 7-7, 총 100점). 점수 근거를 사용자에게 그대로 보여주기 위해 항목별로 나눠 반환한다.
 * - 거리 적합도 최대 45: ≤300m 45, ≤1km 35, ≤2km 25, ≤5km 10, 그 외 0 (거리 없으면 0)
 * - 도시 적합도 최대 20: 현재 도시 일치 20, 선택 도시와만 일치 10, 그 외 0
 * - 우선순위 최대 15: 꼭 가기 15, 가고 싶음 9, 여유 시 4
 * - 시간대 적합도 최대 10: 일치 10, 언제나/미입력 5, 불일치 0
 * - 평점·리뷰 스냅샷 최대 10: 평점(5점 만점)×7/5 반올림 + 리뷰 수(≥100:3, ≥10:2, ≥1:1).
 *   평점·리뷰가 모두 없으면 0점이며 lowConfidence=true(점수 신뢰도 낮음 표시).
 */
data class PlaceScore(
    val distancePoints: Int,
    val cityPoints: Int,
    val priorityPoints: Int,
    val timePoints: Int,
    val ratingPoints: Int,
    val lowConfidence: Boolean,
) {
    val total: Int get() = distancePoints + cityPoints + priorityPoints + timePoints + ratingPoints
}

object PlaceRecommendationScorer {

    fun score(
        place: Place,
        distanceMeters: Double?,
        currentCityId: String?,
        selectedCityId: String?,
        nowLocalTime: LocalTime,
    ): PlaceScore {
        val distancePoints = when {
            distanceMeters == null -> 0
            distanceMeters <= 300 -> 45
            distanceMeters <= 1_000 -> 35
            distanceMeters <= 2_000 -> 25
            distanceMeters <= 5_000 -> 10
            else -> 0
        }

        val cityPoints = when {
            place.cityId != null && place.cityId == currentCityId -> 20
            place.cityId != null && place.cityId == selectedCityId -> 10
            else -> 0
        }

        val currentSlot = timeSlotOf(nowLocalTime)
        val timePoints = when {
            place.preferredTimes.isEmpty() || PreferredTime.ANYTIME in place.preferredTimes -> 5
            currentSlot in place.preferredTimes -> 10
            else -> 0
        }

        val rating = place.ratingSnapshot
        val reviews = place.reviewCountSnapshot
        val ratingPoints = if (rating == null && reviews == null) {
            0
        } else {
            val ratingPart = rating?.let { (it.coerceIn(0.0, 5.0) / 5.0 * 7).roundToInt() } ?: 0
            val reviewPart = when {
                reviews == null || reviews < 1 -> 0
                reviews >= 100 -> 3
                reviews >= 10 -> 2
                else -> 1
            }
            (ratingPart + reviewPart).coerceAtMost(10)
        }

        return PlaceScore(
            distancePoints = distancePoints,
            cityPoints = cityPoints,
            priorityPoints = place.priority.score,
            timePoints = timePoints,
            ratingPoints = ratingPoints,
            lowConfidence = rating == null && reviews == null,
        )
    }

    /** 현지 시각 → 시간대 구간. 오전 5~10시, 점심 11~13시, 오후 14~16시, 저녁 17~20시, 밤 21~4시. */
    fun timeSlotOf(time: LocalTime): PreferredTime = when (time.hour) {
        in 5..10 -> PreferredTime.MORNING
        in 11..13 -> PreferredTime.LUNCH
        in 14..16 -> PreferredTime.AFTERNOON
        in 17..20 -> PreferredTime.EVENING
        else -> PreferredTime.NIGHT
    }
}
