package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import java.time.LocalTime
import org.junit.Test

class PlaceRecommendationScorerTest {

    private val noon = LocalTime.of(12, 0)

    private fun place(
        cityId: String? = "prague",
        priority: PlacePriority = PlacePriority.WANT_TO_GO,
        preferredTimes: List<PreferredTime> = emptyList(),
        rating: Double? = null,
        reviews: Long? = null,
    ) = Place(
        id = "p1",
        name = "테스트 장소",
        cityId = cityId,
        category = PlaceCategory.RESTAURANT,
        priority = priority,
        ratingSnapshot = rating,
        reviewCountSnapshot = reviews,
        preferredTimes = preferredTimes,
    )

    @Test
    fun `거리 점수는 300m-1km-2km-5km 경계에서 정확히 바뀐다`() {
        fun distancePointsAt(meters: Double?) =
            PlaceRecommendationScorer.score(place(), meters, null, null, noon).distancePoints

        assertThat(distancePointsAt(300.0)).isEqualTo(45)
        assertThat(distancePointsAt(300.1)).isEqualTo(35)
        assertThat(distancePointsAt(1_000.0)).isEqualTo(35)
        assertThat(distancePointsAt(1_000.1)).isEqualTo(25)
        assertThat(distancePointsAt(2_000.0)).isEqualTo(25)
        assertThat(distancePointsAt(2_000.1)).isEqualTo(10)
        assertThat(distancePointsAt(5_000.0)).isEqualTo(10)
        assertThat(distancePointsAt(5_000.1)).isEqualTo(0)
        // 거리(현재 위치 또는 장소 좌표)가 없으면 임의 점수를 주지 않는다
        assertThat(distancePointsAt(null)).isEqualTo(0)
    }

    @Test
    fun `도시 점수는 현재 도시 20, 선택 도시만 일치 10, 그 외 0이다`() {
        fun cityPoints(currentCityId: String?, selectedCityId: String?) =
            PlaceRecommendationScorer.score(place(cityId = "prague"), null, currentCityId, selectedCityId, noon)
                .cityPoints

        assertThat(cityPoints("prague", "barcelona")).isEqualTo(20)
        assertThat(cityPoints("barcelona", "prague")).isEqualTo(10)
        assertThat(cityPoints("barcelona", "madrid")).isEqualTo(0)
        assertThat(cityPoints(null, null)).isEqualTo(0)
    }

    @Test
    fun `우선순위 점수는 15-9-4다`() {
        fun priorityPoints(priority: PlacePriority) =
            PlaceRecommendationScorer.score(place(priority = priority), null, null, null, noon).priorityPoints

        assertThat(priorityPoints(PlacePriority.MUST_GO)).isEqualTo(15)
        assertThat(priorityPoints(PlacePriority.WANT_TO_GO)).isEqualTo(9)
        assertThat(priorityPoints(PlacePriority.IF_TIME)).isEqualTo(4)
    }

    @Test
    fun `시간대 점수는 일치 10, 언제나-미입력 5, 불일치 0이다`() {
        fun timePoints(times: List<PreferredTime>, at: LocalTime = noon) =
            PlaceRecommendationScorer.score(place(preferredTimes = times), null, null, null, at).timePoints

        assertThat(timePoints(listOf(PreferredTime.LUNCH))).isEqualTo(10) // 정오 = 점심
        assertThat(timePoints(listOf(PreferredTime.ANYTIME))).isEqualTo(5)
        assertThat(timePoints(emptyList())).isEqualTo(5)
        assertThat(timePoints(listOf(PreferredTime.NIGHT))).isEqualTo(0)
        assertThat(timePoints(listOf(PreferredTime.MORNING), at = LocalTime.of(8, 0))).isEqualTo(10)
        assertThat(timePoints(listOf(PreferredTime.NIGHT), at = LocalTime.of(23, 0))).isEqualTo(10)
        assertThat(timePoints(listOf(PreferredTime.NIGHT), at = LocalTime.of(2, 0))).isEqualTo(10)
    }

    @Test
    fun `평점-리뷰 스냅샷이 없으면 0점이고 신뢰도 낮음으로 표시한다`() {
        val score = PlaceRecommendationScorer.score(place(rating = null, reviews = null), null, null, null, noon)
        assertThat(score.ratingPoints).isEqualTo(0)
        assertThat(score.lowConfidence).isTrue()
    }

    @Test
    fun `평점-리뷰 점수는 최대 10점이며 리뷰 수 구간별 가점을 준다`() {
        fun ratingPoints(rating: Double?, reviews: Long?) =
            PlaceRecommendationScorer.score(place(rating = rating, reviews = reviews), null, null, null, noon)

        // 5.0점 + 리뷰 100개 이상 = 7 + 3 = 10 (최대)
        assertThat(ratingPoints(5.0, 500).ratingPoints).isEqualTo(10)
        // 4.5점(6.3→6) + 리뷰 50개(2) = 8
        assertThat(ratingPoints(4.5, 50).ratingPoints).isEqualTo(8)
        // 평점만 있고 리뷰 없음: 3.0 → 4 + 0
        assertThat(ratingPoints(3.0, null).ratingPoints).isEqualTo(4)
        assertThat(ratingPoints(3.0, null).lowConfidence).isFalse()
    }

    @Test
    fun `총점은 항목 합이며 최대 100을 넘지 않는다`() {
        val best = PlaceRecommendationScorer.score(
            place(
                cityId = "prague",
                priority = PlacePriority.MUST_GO,
                preferredTimes = listOf(PreferredTime.LUNCH),
                rating = 5.0,
                reviews = 1000,
            ),
            distanceMeters = 100.0,
            currentCityId = "prague",
            selectedCityId = "prague",
            nowLocalTime = noon,
        )
        assertThat(best.total).isEqualTo(45 + 20 + 15 + 10 + 10)
        assertThat(best.total).isAtMost(100)
    }
}
