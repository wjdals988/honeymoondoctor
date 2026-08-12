package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class RatingStarsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun 별을_누르면_그_점수가_되고_지우기로_비운다() {
        compose.setContent {
            var rating by remember { mutableStateOf<Double?>(null) }
            RatingStars(rating = rating, onRatingChange = { rating = it })
        }

        compose.onNodeWithText("평점 없음").assertExists()

        compose.onNodeWithContentDescription("별 4점").performClick()
        compose.onNodeWithText("4점").assertExists()

        compose.onNodeWithText("지우기").performClick()
        compose.onNodeWithText("평점 없음").assertExists()
    }

    @Test
    fun 가져오기로_들어온_소수_평점이_그대로_보인다() {
        // TSV/JSON 가져오기의 4.7을 별이 버리면 안 된다(정보 손실 금지).
        compose.setContent {
            RatingStars(rating = 4.7, onRatingChange = {})
        }

        compose.onNodeWithText("4.7점").assertExists()
    }
}
