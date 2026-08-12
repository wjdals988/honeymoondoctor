package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * 첫 UI 자동화 테스트(백로그 2-7). Hilt·네트워크가 필요 없는 공용 컴포넌트부터
 * 시작한다 — 화면 전체 테스트보다 부서지기 어렵고, 여기 컴포넌트들은 v0.2.x에서
 * 편집 화면 전부가 갈아탄 기반이라 회귀 비용이 가장 크다.
 */
class ChipSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun 칩을_누르면_선택이_바뀐다() {
        compose.setContent {
            var selected by remember { mutableStateOf("식비") }
            ChipSelector(
                label = "카테고리",
                options = listOf("식비", "교통", "숙소"),
                selected = selected,
                optionLabel = { it },
                onSelect = { selected = it },
            )
        }

        compose.onNodeWithText("교통").performClick()

        compose.onNodeWithText("교통").assertIsSelected()
    }

    @Test
    fun 모든_선택지가_한꺼번에_보인다() {
        // 드롭다운을 버린 이유 그 자체가 회귀 대상이다: 펼치는 동작 없이 전부 보여야 한다.
        compose.setContent {
            ChipSelector(
                label = "유형",
                options = listOf("항공", "숙소", "교통", "투어", "식당", "기타"),
                selected = "기타",
                optionLabel = { it },
                onSelect = {},
            )
        }

        listOf("항공", "숙소", "교통", "투어", "식당", "기타").forEach { option ->
            compose.onNodeWithText(option).assertExists()
        }
    }
}
