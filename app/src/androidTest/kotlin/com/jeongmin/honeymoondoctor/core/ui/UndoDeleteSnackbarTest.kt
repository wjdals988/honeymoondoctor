package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import org.junit.Rule
import org.junit.Test

class UndoDeleteSnackbarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun 되돌리기를_누르면_onUndo가_불린다() {
        val state = UndoDeleteState<String>()
        state.offer("지출-1", "지출을 삭제했습니다.")
        var undone = false

        compose.setContent {
            val hostState = SnackbarHostState()
            val pending by state.pending.collectAsState()
            SnackbarHost(hostState)
            UndoDeleteSnackbarEffect(
                hostState = hostState,
                pending = pending,
                onUndo = { undone = true },
                onDismissed = {},
            )
        }

        compose.onNodeWithText("지출을 삭제했습니다.").assertExists()
        compose.onNodeWithText("되돌리기").performClick()
        compose.waitForIdle()

        assert(undone) { "되돌리기 콜백이 불리지 않았다" }
    }

    @Test
    fun 같은_항목을_다시_지워도_스낵바가_또_뜬다() {
        // token이 없던 시절의 회귀: 지우고→되돌리고→다시 지우면 Pending이 값으로
        // 같아져 두 번째 스낵바가 안 떴다.
        val state = UndoDeleteState<String>()
        state.offer("A", "삭제했습니다.")
        val first = state.pending.value!!
        state.consume()
        state.offer("A", "삭제했습니다.")
        val second = state.pending.value!!

        assert(first != second) { "token이 달라 두 Pending은 달라야 한다" }
    }
}
