package com.jeongmin.honeymoondoctor.domain.usecase

import java.net.URLDecoder

/**
 * Google Maps 장소 URL(`.../maps/place/<name>/@...`)의 경로에 박힌 장소명을 뽑는다.
 *
 * "저장된 목록(여러 장소)" 링크는 이 형태가 아니고, 안의 장소들은 구글 지도 앱의 JS가
 * 별도 비공개 API로 나중에 불러오는 구조라 이 함수(또는 단순 HTTP 요청) 어디서도 뽑을
 * 수 없다 — 그래서 이 앱은 "장소 하나 = 링크 하나"만 지원한다(여러 줄 붙여넣기로 보완).
 */
object MapsUrlPlaceName {
    private val placeSegment = Regex("""/maps/place/([^/@]+)""")

    fun parse(url: String): String? {
        val raw = placeSegment.find(url)?.groupValues?.get(1) ?: return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.replace('+', ' ').trim().takeIf { it.isNotBlank() }
    }
}
