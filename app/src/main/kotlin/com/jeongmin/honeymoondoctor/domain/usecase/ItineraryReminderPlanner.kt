package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.time.Duration
import java.time.Instant

/** 중요 일정 알림 오프셋(스펙 7-8): 시작 24시간·3시간·1시간 전. */
enum class ReminderOffset(val duration: Duration, val labelKo: String) {
    H24(Duration.ofHours(24), "24시간 전"),
    H3(Duration.ofHours(3), "3시간 전"),
    H1(Duration.ofHours(1), "1시간 전"),
}

data class PlannedReminder(
    val itineraryItemId: String,
    val offset: ReminderOffset,
    val fireAt: Instant,
    val title: String,
    val body: String,
) {
    /** 스케줄/취소를 멱등하게 식별하는 안정적인 키(AlarmManager 요청 코드, WorkManager 고유 작업명으로 재사용). */
    val key: String get() = "$itineraryItemId:${offset.name}"
}

/**
 * 중요 일정(스펙 7-8) 알림 시각을 계산하는 순수 함수. "중요한 일정"은 시각이 있는(allDay 아님)
 * 미완료(PLANNED) 일정으로 정의한다 — 날짜 범위뿐인 숙소 일정은 정확한 알림 시점이 없어 제외한다.
 * 이미 지난 오프셋(fireAt이 now 이전)은 계획에서 제외해 즉시 발사되는 알림을 막는다.
 */
object ItineraryReminderPlanner {
    fun plan(items: List<ItineraryItem>, now: Instant): List<PlannedReminder> =
        items
            .filter { it.status == ItineraryStatus.PLANNED && !it.allDay }
            .flatMap { item ->
                ReminderOffset.entries.mapNotNull { offset ->
                    val fireAt = item.startAt.minus(offset.duration)
                    if (fireAt.isAfter(now)) {
                        PlannedReminder(
                            itineraryItemId = item.id,
                            offset = offset,
                            fireAt = fireAt,
                            title = "곧 일정: ${item.title}",
                            body = "${offset.labelKo} 알림 · ${item.type.labelKo}",
                        )
                    } else {
                        null
                    }
                }
            }
}
