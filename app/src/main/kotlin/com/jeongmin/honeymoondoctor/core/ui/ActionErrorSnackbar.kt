package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * 목록 화면에서 쓰기 실패 메시지를 스낵바로 띄운다. 목록은 스크롤되기 때문에 인라인 텍스트로
 * 붙이면 화면 밖에 있어 못 볼 수 있어서, 삭제·상태변경 같은 즉시 동작의 실패는 스낵바로 알린다.
 * (편집기 화면은 이미 화면 상단에 검증 오류 자리가 있어 거기에 함께 표시한다.)
 *
 * [message]가 null이 아닐 때 한 번 띄우고 [onShown]으로 상태를 비운다 — 안 비우면 화면
 * 회전이나 재구성 때 같은 메시지가 다시 뜬다.
 */
@Composable
fun rememberActionErrorSnackbar(message: String?, onShown: () -> Unit): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(message)
            onShown()
        }
    }
    return hostState
}
