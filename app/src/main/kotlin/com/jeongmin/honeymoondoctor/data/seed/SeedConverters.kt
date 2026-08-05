package com.jeongmin.honeymoondoctor.data.seed

import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import java.time.LocalDate

/**
 * 시드 JSON의 로컬시각+시간대 쌍을 도메인 모델(UTC Instant + 표시 시간대)로 변환한다.
 * 여행 생성 시 1회 삽입 경로에서만 사용된다(스펙 4장 — 재삽입 금지는 호출자가 보장).
 */
fun ItinerarySeed.toDomainItem(): ItineraryItem = ItineraryItem(
    id = itineraryId,
    title = title,
    type = runCatching { ItineraryType.valueOf(type) }.getOrDefault(ItineraryType.ETC),
    startAt = LocalTimes.parseSeedLocal(startAtLocal, startTimeZone),
    endAt = if (allDay) {
        LocalTimes.endOfDay(LocalDate.parse(endAtLocal), endTimeZone)
    } else {
        LocalTimes.parseSeedLocal(endAtLocal, endTimeZone)
    },
    allDay = allDay,
    timeZone = startTimeZone,
    endTimeZone = endTimeZone.takeIf { it != startTimeZone },
    cityId = cityId,
    status = runCatching { ItineraryStatus.valueOf(status) }.getOrDefault(ItineraryStatus.PLANNED),
    reservationId = reservationId,
    estimatedKrw = estimatedKrw,
    notes = notes,
)

fun CitySeed.toDomainCity(): City = City(
    id = cityId,
    displayName = displayName,
    countryCode = countryCode,
    timeZoneId = timeZoneId,
    startDate = startDate,
    endDate = endDate,
    referenceLatitude = referenceLatitude,
    referenceLongitude = referenceLongitude,
    notes = notes,
)
