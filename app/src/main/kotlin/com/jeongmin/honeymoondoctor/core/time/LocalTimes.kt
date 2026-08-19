package com.jeongmin.honeymoondoctor.core.time

import com.jeongmin.honeymoondoctor.domain.model.CityPresets

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
/**
 * IANA 시간대 id를 사람이 읽는 이름으로. 목록은 [CityPresets]에서 파생한다 —
 * 종전에는 서울·프라하·마드리드 3개만 손으로 매핑해 뒀는데(리브랜딩 전 신혼여행 목적지),
 * 목적지가 63개로 늘어난 뒤에는 나머지 60개가 화면에 `Asia/Tokyo` 같은 원시 id로
 * 노출됐다. 프리셋에서 파생하면 도시를 추가할 때 매핑을 따로 손댈 필요가 없다.
 */
fun koreanZoneLabel(zoneId: String): String = zoneNameByZoneId[zoneId] ?: zoneId

private val zoneNameByZoneId: Map<String, String> by lazy {
    CityPresets.all
        .groupBy { it.timeZoneId }
        // 한 시간대에 여러 나라가 걸리면(예: 같은 UTC 오프셋) 나라명을 모아 보여준다.
        .mapValues { (_, presets) -> presets.map { it.countryName }.distinct().joinToString("·") }
}

/**
 * 시간대 선택 후보. 종전에는 화면마다 `listOf("Asia/Seoul", "Europe/Prague", "Europe/Madrid")`
 * 를 하드코딩해, 도쿄 여행인데 드롭다운에 Asia/Tokyo가 없어 **고를 수 없는** 상태였다
 * (리브랜딩 전 목적지가 그대로 남아 있던 것).
 *
 * 이제 이 여행의 도시들이 쓰는 시간대를 후보로 쓰고, 출발지인 한국을 항상 포함한다
 * ([current]는 이미 선택된 값 — 목록에 없어도 사라지지 않게 함께 넣는다).
 */
fun timeZoneChoices(cityZoneIds: List<String>, current: String? = null): List<String> =
    (listOf("Asia/Seoul") + cityZoneIds + listOfNotNull(current))
        .filter { it.isNotBlank() }
        .distinct()

/** 드롭다운 한 줄 표기. 한국어 이름이 없으면 원시 id를 두 번 적지 않는다. */
fun zoneOptionLabel(zoneId: String): String {
    val name = koreanZoneLabel(zoneId)
    return if (name == zoneId) zoneId else "$name ($zoneId)"
}
