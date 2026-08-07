package com.jeongmin.honeymoondoctor.data.seed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NewTripDefaultsTest {

    private fun loadRawSeedJson(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("seed/new_trip_defaults.json")) {
            "seed/new_trip_defaults.json 을 test 리소스 클래스패스에서 찾을 수 없습니다."
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun `시드 JSON은 파싱되고 필수 개수 불변식을 만족한다`() {
        val defaults = parseNewTripDefaults(loadRawSeedJson())

        assertThat(defaults.seedVersion).isNotEmpty()
        assertThat(defaults.checklistItems).hasSize(8)
    }

    @Test
    fun `기본 준비물 목록에는 특정 여행지·국가명이 하드코딩돼 있지 않다`() {
        val defaults = parseNewTripDefaults(loadRawSeedJson())
        val destinationWords = listOf("프라하", "바르셀로나", "마드리드", "유로", "스페인", "체코")

        defaults.checklistItems.forEach { item ->
            destinationWords.forEach { word ->
                assertThat(item.title).doesNotContain(word)
            }
        }
    }
}
