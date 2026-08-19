package com.jeongmin.honeymoondoctor.data.itinerary

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.Instant

/**
 * trips/{tripId}/itinerary/{itemId} 문서 ↔ 도메인 변환(스펙 8장 스키마).
 * startAt/endAt은 UTC Timestamp, timeZone은 표시용 IANA 문자열이다. 출발·도착 시간대가
 * 다른 항공 일정을 위해 endTimeZone을 추가 필드로 저장한다(없으면 timeZone과 동일).
 * FirebaseTripRepository의 시드 삽입과 FirebaseItineraryRepository가 같은 매핑을 공유한다.
 */
internal fun Instant.toFirestoreTimestamp() = Timestamp(epochSecond, nano)

internal fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())

internal fun ItineraryItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "type" to type.name,
    "startAt" to startAt.toFirestoreTimestamp(),
    "endAt" to endAt?.toFirestoreTimestamp(),
    "allDay" to allDay,
    "timeZone" to timeZone,
    "endTimeZone" to endTimeZone,
    "cityId" to cityId,
    "location" to location,
    "address" to address,
    "status" to status.name,
    "assigneeUid" to assigneeUid,
    "reservationId" to reservationId,
    "estimatedKrw" to estimatedKrw,
    "notes" to notes,
    "placeId" to placeId,
)

internal fun DocumentSnapshot.toItineraryItem(): ItineraryItem? {
    val title = getString("title") ?: return null
    val startAt = getTimestamp("startAt")?.toInstant() ?: return null
    return ItineraryItem(
        id = id,
        title = title,
        type = runCatching { ItineraryType.valueOf(getString("type").orEmpty()) }
            .getOrDefault(ItineraryType.ETC),
        startAt = startAt,
        endAt = getTimestamp("endAt")?.toInstant(),
        allDay = getBoolean("allDay") ?: false,
        timeZone = getString("timeZone") ?: "Asia/Seoul",
        endTimeZone = getString("endTimeZone"),
        cityId = getString("cityId"),
        location = getString("location"),
        address = getString("address"),
        status = runCatching { ItineraryStatus.valueOf(getString("status").orEmpty()) }
            .getOrDefault(ItineraryStatus.PLANNED),
        assigneeUid = getString("assigneeUid"),
        reservationId = getString("reservationId"),
        estimatedKrw = getLong("estimatedKrw"),
        notes = getString("notes"),
        placeId = getString("placeId"),
    )
}
