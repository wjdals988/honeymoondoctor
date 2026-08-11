package com.jeongmin.honeymoondoctor.core.error

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 쓰기 동작 실패를 앱 크래시가 아니라 사용자 메시지로 바꾸는 공용 상태.
 *
 * 왜 모든 ViewModel에 필요한가: Firestore 보안 규칙은 앱 UI와 무관하게 쓰기를 거부한다
 * (완료된 여행 수정, 구성원 아닌 사용자의 쓰기 등). `viewModelScope.launch` 안에서 그
 * 예외를 잡지 않으면 코루틴이 그대로 앱을 죽인다 — 실기기 release 빌드에서 완료된 여행의
 * 일정 상태 변경으로 재현했다(`PERMISSION_DENIED: Missing or insufficient permissions.`).
 *
 * 화면에서 버튼을 감추는 것만으로는 부족하다. 파트너가 먼저 여행을 완료 처리하면 내 화면은
 * 아직 옛 상태여서 버튼이 살아 있고, 그 사이 누르면 같은 예외가 난다. 오프라인 큐가 나중에
 * 거부당하는 경우도 마찬가지다. 그래서 UI 게이팅과 이 예외 처리를 둘 다 둔다.
 */
class ActionErrorState {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clear() {
        _message.value = null
    }

    fun report(cause: Throwable, fallback: String) {
        _message.value = cause.toUserMessage(fallback)
    }
}

/**
 * [block]을 실행하고, 실패하면 [fallback] 문구로 오류를 보고한다(성공하면 이전 오류를 지운다).
 * 반환값은 성공 여부 — 저장 후 화면을 닫는 편집기처럼 "성공했을 때만" 다음 동작을 해야 하는
 * 곳에서 쓴다.
 */
suspend fun ActionErrorState.runReporting(fallback: String, block: suspend () -> Unit): Boolean =
    runCatching { block() }
        .onSuccess { clear() }
        .onFailure { report(it, fallback) }
        .isSuccess

/**
 * Firestore 예외의 `message`는 "PERMISSION_DENIED: Missing or insufficient permissions."처럼
 * 영문 원문이라 사용자에게 그대로 보여주면 안 된다. 그 경우에만 [fallback]으로 갈아치우고,
 * 우리가 직접 던진 예외(한국어 안내를 담고 있음)는 원문을 살린다.
 */
fun Throwable.toUserMessage(fallback: String): String =
    if (this is FirebaseFirestoreException) fallback else message ?: fallback
