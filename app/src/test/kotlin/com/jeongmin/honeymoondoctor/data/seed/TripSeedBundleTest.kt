package com.jeongmin.honeymoondoctor.data.seed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripSeedBundleTest {

    private fun loadRawSeedJson(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("seed/honeymoon_trip_seed.json")) {
            "seed/honeymoon_trip_seed.json 을 test 리소스 클래스패스에서 찾을 수 없습니다."
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun `시드 JSON은 파싱되고 필수 개수 불변식을 만족한다`() {
        val bundle = parseTripSeedBundle(loadRawSeedJson())

        assertThat(bundle.seedVersion).isNotEmpty()
        assertThat(bundle.trip.name).isEqualTo("정민·찬희 신혼여행")
        assertThat(bundle.trip.nights).isEqualTo(11)
        assertThat(bundle.trip.defaultCurrency).isEqualTo("KRW")
        assertThat(bundle.cities).hasSize(4)
        assertThat(bundle.reservations).hasSize(5)
        assertThat(bundle.itinerary).hasSize(3)
        assertThat(bundle.decisions).hasSize(5)
        assertThat(bundle.checklistItems).hasSize(8)
    }

    @Test
    fun `일정의 reservationId는 모두 존재하는 예약을 가리킨다`() {
        val bundle = parseTripSeedBundle(loadRawSeedJson())
        val reservationIds = bundle.reservations.map { it.reservationId }.toSet()

        bundle.itinerary.forEach { item ->
            assertThat(item.reservationId).isNotNull()
            assertThat(reservationIds).contains(item.reservationId)
        }
    }

    @Test
    fun `예약의 linkedItineraryId는 존재하지 않거나 실제 일정을 가리킨다`() {
        val bundle = parseTripSeedBundle(loadRawSeedJson())
        val itineraryIds = bundle.itinerary.map { it.itineraryId }.toSet()

        bundle.reservations.mapNotNull { it.linkedItineraryId }.forEach { linkedId ->
            assertThat(itineraryIds).contains(linkedId)
        }
    }

    @Test
    fun `확인되지 않은 예약번호·PIN은 임의로 채워지지 않았다`() {
        val bundle = parseTripSeedBundle(loadRawSeedJson())

        bundle.reservations.forEach { reservation ->
            assertThat(reservation.confirmationCode).isNull()
            assertThat(reservation.pin).isNull()
        }
    }

    @Test
    fun `후보만 있는 결정 항목은 임의의 좌표나 확정 상태를 갖지 않는다`() {
        val bundle = parseTripSeedBundle(loadRawSeedJson())

        bundle.decisions.forEach { decision ->
            assertThat(decision.selectedOptionId).isNull()
            assertThat(decision.status).isAnyOf("NEEDS_DECISION", "NEEDS_BOOKING")
        }
    }
}
