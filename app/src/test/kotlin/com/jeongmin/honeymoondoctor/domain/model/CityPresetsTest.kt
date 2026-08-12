package com.jeongmin.honeymoondoctor.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class CityPresetsTest {

    @Test
    fun `모든 프리셋의 시간대 문자열이 실제로 존재한다`() {
        // 이 테스트가 프리셋 표의 존재 이유다. 오타가 하나라도 있으면 그 도시는 앱에서
        // 현지 시각 판정에 조용히 실패한다(CurrentCityResolver가 후보에서 뺀다).
        val broken = CityPresets.all.filter { runCatching { ZoneId.of(it.timeZoneId) }.isFailure }

        assertThat(broken.map { "${it.displayName}=${it.timeZoneId}" }).isEmpty()
    }

    @Test
    fun `국가코드는 두 글자 대문자다`() {
        val wrong = CityPresets.all.filterNot { it.countryCode.matches(Regex("[A-Z]{2}")) }

        assertThat(wrong.map { "${it.displayName}=${it.countryCode}" }).isEmpty()
    }

    @Test
    fun `같은 도시가 중복으로 들어 있지 않다`() {
        val duplicated = CityPresets.all.groupBy { it.displayName }.filter { it.value.size > 1 }

        assertThat(duplicated.keys).isEmpty()
    }

    @Test
    fun `한글 이름으로 찾는다`() {
        assertThat(CityPresets.search("프라하").map { it.timeZoneId }).contains("Europe/Prague")
    }

    @Test
    fun `영문 이름으로도 찾는다`() {
        assertThat(CityPresets.search("prague").map { it.displayName }).contains("프라하")
        assertThat(CityPresets.search("PRAGUE").map { it.displayName }).contains("프라하")
    }

    @Test
    fun `공백을 무시하고 찾는다`() {
        // "Ho Chi Minh City"를 "hochiminh"으로 쳐도 나와야 한다.
        assertThat(CityPresets.search("hochiminh").map { it.displayName }).contains("호치민")
    }

    @Test
    fun `국가명으로 그 나라 도시들을 찾는다`() {
        val japan = CityPresets.search("일본", limit = 20)

        assertThat(japan).isNotEmpty()
        assertThat(japan.map { it.displayName }).contains("도쿄")
        assertThat(japan.all { it.countryCode == "JP" }).isTrue()
    }

    @Test
    fun `빈 검색어는 아무것도 돌려주지 않는다`() {
        assertThat(CityPresets.search("")).isEmpty()
        assertThat(CityPresets.search("   ")).isEmpty()
    }

    @Test
    fun `결과 개수를 제한한다`() {
        assertThat(CityPresets.search("a", limit = 3)).hasSize(3)
    }
}
