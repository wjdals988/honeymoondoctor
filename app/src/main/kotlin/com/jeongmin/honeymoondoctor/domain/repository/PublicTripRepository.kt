package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.PublicTripSummary
import com.jeongmin.honeymoondoctor.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface PublicTripRepository {
    /** 다른 계정이 공개한 여행 전체(최근 발행순, 클라이언트 정렬). 로그인만 하면 볼 수 있다. */
    fun observePublicTrips(): Flow<List<PublicTripSummary>>

    fun observePublicTrip(tripId: String): Flow<PublicTripSummary?>

    fun observePublicCities(tripId: String): Flow<List<City>>

    fun observePublicItinerary(tripId: String): Flow<List<ItineraryItem>>

    /** 완료된 여행의 화이트리스트 필드만 사본으로 발행한다. 소유자만 호출 가능(규칙에서 강제). */
    suspend fun publish(trip: Trip, cities: List<City>, itinerary: List<ItineraryItem>)

    suspend fun unpublish(tripId: String)
}
