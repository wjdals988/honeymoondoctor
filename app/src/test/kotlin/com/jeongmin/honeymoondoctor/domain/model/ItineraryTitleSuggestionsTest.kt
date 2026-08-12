package com.jeongmin.honeymoondoctor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItineraryTitleSuggestionsTest {

    @Test
    fun `모든 유형에 후보가 있다`() {
        // 한 유형이라도 비어 있으면 그 유형을 고른 사용자에게는 칩이 사라져,
        // "왜 어떤 때는 나오고 어떤 때는 안 나오지?"가 된다.
        ItineraryType.entries.forEach { type ->
            assertThat(ItineraryTitleSuggestions.forType(type)).isNotEmpty()
        }
    }

    @Test
    fun `유형별 후보가 서로 겹치지 않는다`() {
        // 같은 이름이 두 유형에 있으면 유형 선택이 이름 후보를 좁혀 주지 못한다.
        val all = ItineraryType.entries.flatMap { ItineraryTitleSuggestions.forType(it) }

        assertThat(all).containsNoDuplicates()
    }

    @Test
    fun `후보는 한 줄에 담길 만큼 짧다`() {
        // 칩은 가로로 늘어놓기 때문에 긴 이름이 섞이면 나머지가 화면 밖으로 밀린다.
        ItineraryType.entries.flatMap { ItineraryTitleSuggestions.forType(it) }.forEach { title ->
            assertThat(title.length).isAtMost(8)
            assertThat(title.trim()).isEqualTo(title)
        }
    }
}
