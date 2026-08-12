package com.jeongmin.honeymoondoctor.domain.usecase

/**
 * Google Maps 링크에서 좌표를 뽑아낸다.
 *
 * 왜 필요한가: 장소의 위도·경도는 사람이 손으로 넣을 수 있는 값이 아니다. 그런데 앱에는
 * 지도 SDK가 없어(외부 Intent만 사용) "지도에서 찍기"를 만들 수 없다. 대신 구글 지도
 * 앱에서 "공유 → 링크 복사"한 URL을 붙여넣으면 좌표를 대신 채워 준다.
 *
 * 지원하는 형태:
 * - `.../@35.0116,135.7681,15z/...`  — 지도 화면 중심(웹에서 복사할 때 가장 흔함)
 * - `...?q=35.0116,135.7681`         — 좌표 검색 링크
 * - `...!3d35.0116!4d135.7681`       — 장소 상세에 박히는 실제 지점 좌표
 * - `geo:35.0116,135.7681`
 *
 * `!3d/!4d`가 있으면 그것을 먼저 쓴다 — `@`는 화면 중심이라 장소와 조금 어긋난다.
 *
 * 단축 링크(`maps.app.goo.gl/...`)는 리다이렉트를 따라가야 좌표가 나오므로 여기서는
 * 처리하지 않는다(네트워크가 필요하고, 이 함수는 순수 계산으로 둔다).
 */
data class Coordinates(val latitude: Double, val longitude: Double)

object MapsUrlCoordinates {

    private val placeCoordinates = Regex("""!3d(-?\d+\.?\d*)!4d(-?\d+\.?\d*)""")
    private val mapCenter = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")
    private val queryCoordinates = Regex("""[?&](?:q|query|ll|daddr)=(-?\d+\.?\d*),\s*(-?\d+\.?\d*)""")
    private val geoScheme = Regex("""geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""")
    /** URL이 아니라 좌표만 그대로 붙여넣는 경우("35.0116, 135.7681"). */
    private val bareCoordinates = Regex("""^\s*(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)\s*$""")

    fun parse(input: String): Coordinates? {
        if (input.isBlank()) return null
        for (pattern in listOf(placeCoordinates, queryCoordinates, mapCenter, geoScheme, bareCoordinates)) {
            val match = pattern.find(input) ?: continue
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lng = match.groupValues[2].toDoubleOrNull() ?: continue
            if (isValid(lat, lng)) return Coordinates(lat, lng)
        }
        return null
    }

    /** 범위를 벗어난 값은 좌표가 아니다(줌 레벨 등 다른 숫자를 잘못 집는 것을 막는다). */
    private fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
