package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CollapsibleSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun 접혀_있다가_누르면_펼쳐진다() {
        compose.setContent {
            CollapsibleSection(title = "자세히 입력", initiallyExpanded = false) {
                Text("숨은 내용")
            }
        }

        compose.onNodeWithText("숨은 내용").assertDoesNotExist()
        compose.onNodeWithText("+ 자세히 입력").performClick()
        compose.onNodeWithText("숨은 내용").assertExists()
    }

    @Test
    fun 값이_있으면_펼친_채로_시작한다() {
        // 편집 화면 규칙: 기존 값이 있는 섹션을 접어 두면 값이 있는 줄도 모른다.
        compose.setContent {
            CollapsibleSection(title = "자세히 입력", initiallyExpanded = true) {
                Text("기존 값")
            }
        }

        compose.onNodeWithText("기존 값").assertExists()
    }
}
