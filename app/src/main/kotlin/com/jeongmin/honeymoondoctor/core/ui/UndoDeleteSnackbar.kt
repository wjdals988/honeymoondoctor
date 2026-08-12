package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.material3.SnackbarDuration
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 삭제 직후 "되돌리기" 스낵바를 띄운다. [rememberActionErrorSnackbar]가 만든 호스트를
 * 그대로 쓴다 — 호스트가 둘이면 스낵바가 겹쳐 그려질 수 있고, Material 3는 화면당
 * 하나를 전제한다.
 *
 * [pending]이 바뀔 때마다 한 번 띄우고, 결과에 따라 [onUndo](누름) 또는
 * [onDismissed](시간 초과·다른 스낵바에 밀림)를 부른다. 두 콜백 모두 상태를 비워야
 * 화면 회전 때 같은 스낵바가 다시 뜨지 않는다. 문자열이 아니라 Pending을 키로 받는
 * 이유는 그 안의 token 주석 참고 — 같은 항목을 두 번 지워도 다시 떠야 한다.
 */
@Composable
fun UndoDeleteSnackbarEffect(
    hostState: SnackbarHostState,
    pending: UndoDeleteState.Pending<*>?,
    onUndo: () -> Unit,
    onDismissed: () -> Unit,
) {
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = pending.message,
            actionLabel = "되돌리기",
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onDismissed()
        }
    }
}
