package com.jeongmin.honeymoondoctor.data.seed

import kotlinx.serialization.Serializable

/**
 * assets/seed/honeymoon_trip_seed.json 을 그대로 매핑하는 데이터 클래스.
 * 이 값들은 "최초 여행 생성 시 1회만 삽입되는 수정 가능한 시드"이며, 실행 중 하드코딩된
 * 읽기 전용 데이터로 취급하지 않는다. seedVersion 은 trips/{tripId}.seedVersion 과 비교해
 * 중복 삽입을 막는 데 사용한다(적용 로직은 여행 생성 플로우에서 구현).
 */
@Serializable
data class TripSeedBundle(
    val seedVersion: String,
    val trip: TripSeed,
    val cities: List<CitySeed>,
    val reservations: List<ReservationSeed>,
    val itinerary: List<ItinerarySeed>,
    val decisions: List<DecisionSeed>,
    val checklistItems: List<ChecklistItemSeed>,
)

@Serializable
data class TripSeed(
    val name: String,
    val startDate: String,
    val endDate: String,
    val nights: Int,
    val memberCount: Int,
    val defaultCurrency: String,
    val memberDisplayNames: Map<String, String>,
)

@Serializable
data class CitySeed(
    val cityId: String,
    val displayName: String,
    val countryCode: String,
    val timeZoneId: String,
    val startDate: String?,
    val endDate: String?,
    val referenceLatitude: Double?,
    val referenceLongitude: Double?,
    val notes: String? = null,
)

@Serializable
data class ReservationSeed(
    val reservationId: String,
    val type: String,
    val vendor: String,
    val title: String,
    val status: String,
    val confirmationCode: String?,
    val pin: String?,
    val startAtLocal: String,
    val startTimeZone: String,
    val endAtLocal: String,
    val endTimeZone: String,
    val allDay: Boolean,
    val linkedItineraryId: String?,
    val estimatedKrw: Long?,
    val notes: String?,
)

@Serializable
data class ItinerarySeed(
    val itineraryId: String,
    val title: String,
    val type: String,
    val cityId: String,
    val startAtLocal: String,
    val startTimeZone: String,
    val endAtLocal: String,
    val endTimeZone: String,
    val allDay: Boolean,
    val status: String,
    val reservationId: String?,
    val estimatedKrw: Long?,
    val notes: String?,
)

@Serializable
data class DecisionSeed(
    val decisionId: String,
    val title: String,
    val category: String,
    val status: String,
    val options: List<String>,
    val selectedOptionId: String?,
    val notes: String?,
)

@Serializable
data class ChecklistItemSeed(
    val checklistItemId: String,
    val title: String,
    val category: String,
    val ownerScope: String,
    val required: Boolean,
)
