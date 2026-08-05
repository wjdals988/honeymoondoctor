package com.jeongmin.honeymoondoctor.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 일정·예약의 시간 규칙(스펙 8장): Firestore에는 UTC Timestamp(여기서는 Instant)로 저장하고,
 * 표시할 때만 해당 일정에 저장된 IANA 시간대 문자열로 변환한다. 시드 JSON은
 * `startAtLocal`("2026-09-09T11:05:00" 또는 종일이면 "2026-09-09") + `startTimeZone` 쌍으로
 * 들어오므로, 이 파일이 그 문자열을 UTC Instant로 바꾸는 유일한 경계다.
 */
object LocalTimes {

    /** "2026-09-09T11:05:00" + 시간대 → UTC Instant. 종일 표기("2026-09-09")는 그 날 00:00로 해석한다. */
    fun parseSeedLocal(local: String, zoneId: String): Instant {
        val zone = ZoneId.of(zoneId)
        return if (local.contains('T')) {
            LocalDateTime.parse(local).atZone(zone).toInstant()
        } else {
            LocalDate.parse(local).atStartOfDay(zone).toInstant()
        }
    }

    /** 종일 일정의 종료 경계: 종료일의 그 시간대 23:59:59.999 — "그 날짜가 끝날 때까지 유효"를 뜻한다. */
    fun endOfDay(date: LocalDate, zoneId: String): Instant =
        date.atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(ZoneId.of(zoneId)).toInstant()

    fun startOfDay(date: LocalDate, zoneId: String): Instant =
        date.atStartOfDay(ZoneId.of(zoneId)).toInstant()

    fun toLocalDate(instant: Instant, zoneId: String): LocalDate =
        instant.atZone(ZoneId.of(zoneId)).toLocalDate()

    fun toLocalDateTime(instant: Instant, zoneId: String): LocalDateTime =
        instant.atZone(ZoneId.of(zoneId)).toLocalDateTime()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // UI 언어가 한국어 우선(스펙 1장)이므로 기기 로케일과 무관하게 요일도 한국어로 고정한다.
    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    fun formatTime(instant: Instant, zoneId: String): String =
        instant.atZone(ZoneId.of(zoneId)).format(timeFormatter)

    fun formatDate(instant: Instant, zoneId: String): String =
        instant.atZone(ZoneId.of(zoneId)).format(dateFormatter)
}

/** 이번 여행에서 실제로 쓰는 시간대의 짧은 한국어 라벨. 그 외 시간대는 IANA ID를 그대로 보여준다. */
fun koreanZoneLabel(zoneId: String): String = when (zoneId) {
    "Asia/Seoul" -> "한국"
    "Europe/Prague" -> "프라하"
    "Europe/Madrid" -> "스페인"
    else -> zoneId
}
