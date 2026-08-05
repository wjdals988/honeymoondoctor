package com.jeongmin.honeymoondoctor.data.seed

import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Decision
import com.jeongmin.honeymoondoctor.domain.model.DecisionCategory
import com.jeongmin.honeymoondoctor.domain.model.DecisionOption
import com.jeongmin.honeymoondoctor.domain.model.DecisionStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.ItineraryType
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.ReservationType
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

fun ReservationSeed.toDomainReservation(): Reservation = Reservation(
    id = reservationId,
    type = runCatching { ReservationType.valueOf(type) }.getOrDefault(ReservationType.ETC),
    vendor = vendor,
    title = title,
    status = runCatching { ReservationStatus.valueOf(status) }.getOrDefault(ReservationStatus.NEEDS_CHECK),
    confirmationCode = confirmationCode,
    pin = pin,
    startAt = LocalTimes.parseSeedLocal(startAtLocal, startTimeZone),
    endAt = if (allDay) {
        LocalTimes.endOfDay(LocalDate.parse(endAtLocal), endTimeZone)
    } else {
        LocalTimes.parseSeedLocal(endAtLocal, endTimeZone)
    },
    allDay = allDay,
    timeZone = startTimeZone,
    endTimeZone = endTimeZone.takeIf { it != startTimeZone },
    linkedItineraryId = linkedItineraryId,
    estimatedKrw = estimatedKrw,
    notes = notes,
)

/** ownerScope=SHARED는 공용(ownerUid=null). 역할 기반 값(PARTNER_A/B)은 실제 계정 연결 전이라 공용으로 둔다. */
fun ChecklistItemSeed.toDomainChecklistItem(): ChecklistItem = ChecklistItem(
    id = checklistItemId,
    title = title,
    category = runCatching { ChecklistCategory.valueOf(category) }.getOrDefault(ChecklistCategory.ETC),
    ownerUid = null,
    required = required,
)

fun DecisionSeed.toDomainDecision(): Decision = Decision(
    id = decisionId,
    title = title,
    category = runCatching { DecisionCategory.valueOf(category) }.getOrDefault(DecisionCategory.ETC),
    status = runCatching { DecisionStatus.valueOf(status) }.getOrDefault(DecisionStatus.NEEDS_DECISION),
    options = options.mapIndexed { index, label -> DecisionOption(id = "$decisionId-opt-$index", label = label) },
    selectedOptionId = selectedOptionId,
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
