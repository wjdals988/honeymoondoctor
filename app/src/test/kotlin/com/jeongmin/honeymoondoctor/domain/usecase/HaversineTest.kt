package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HaversineTest {

    @Test
    fun `같은 지점의 거리는 0이다`() {
        assertThat(Haversine.distanceMeters(50.087, 14.421, 50.087, 14.421)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `프라하 구시가지 광장에서 프라하성까지 약 1_6km이다`() {
        // 구시가지 광장(50.0875, 14.4213) → 프라하성(50.0900, 14.4009): 실제 직선 약 1.48km
        val distance = Haversine.distanceMeters(50.0875, 14.4213, 50.0900, 14.4009)
        assertThat(distance).isWithin(150.0).of(1_480.0)
    }

    @Test
    fun `바르셀로나에서 마드리드까지 약 505km이다`() {
        // BCN(41.3874, 2.1686) → MAD(40.4168, -3.7038): 잘 알려진 직선거리 ≈ 505km
        val distance = Haversine.distanceMeters(41.3874, 2.1686, 40.4168, -3.7038)
        assertThat(distance / 1000).isWithin(10.0).of(505.0)
    }

    @Test
    fun `적도에서 경도 1도는 약 111km이다`() {
        val distance = Haversine.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertThat(distance / 1000).isWithin(0.5).of(111.19)
    }
}
