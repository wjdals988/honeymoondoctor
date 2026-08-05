package com.jeongmin.honeymoondoctor.data.itinerary

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Instant
import kotlinx.serialization.Serializable

/** 데모 모드에서 DataStore에 JSON으로 저장하는 일정 스냅샷. Firestore 문서 구조와 1:1로 맞춘다. */
@Serializable
data class DemoItineraryStateDto(
    val tripId: String,
    val items: List<DemoItineraryItemDto> = emptyList(),
)

@Serializable
data class DemoItineraryItemDto(
    val id: String,
    val title: String,
    val type: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val allDay: Boolean,
    val timeZone: String,
    val endTimeZone: String? = null,
    val cityId: String? = null,
    val location: String? = null,
    val address: String? = null,
    val status: String,
    val assigneeUid: String? = null,
    val reservationId: String? = null,
    val estimatedKrw: Long? = null,
    val notes: String? = null,
)

fun ItineraryItem.toDemoDto() = DemoItineraryItemDto(
    id = id,
    title = title,
    type = type.name,
    startAtEpochMillis = startAt.toEpochMilli(),
    endAtEpochMillis = endAt?.toEpochMilli(),
    allDay = allDay,
    timeZone = timeZone,
    endTimeZone = endTimeZone,
    cityId = cityId,
    location = location,
    address = address,
    status = status.name,
    assigneeUid = assigneeUid,
    reservationId = reservationId,
    estimatedKrw = estimatedKrw,
    notes = notes,
)

fun DemoItineraryItemDto.toDomain() = ItineraryItem(
    id = id,
    title = title,
    type = runCatching { ItineraryType.valueOf(type) }.getOrDefault(ItineraryType.ETC),
    startAt = Instant.ofEpochMilli(startAtEpochMillis),
    endAt = endAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    allDay = allDay,
    timeZone = timeZone,
    endTimeZone = endTimeZone,
    cityId = cityId,
    location = location,
    address = address,
    status = runCatching { ItineraryStatus.valueOf(status) }.getOrDefault(ItineraryStatus.PLANNED),
    assigneeUid = assigneeUid,
    reservationId = reservationId,
    estimatedKrw = estimatedKrw,
    notes = notes,
)
