package com.jeongmin.honeymoondoctor.core.error

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 마지막으로 삭제한 항목 1건을 기억해 "되돌리기"를 가능하게 한다.
 *
 * 왜 1건만인가: 스낵바는 한 번에 하나만 뜬다(Material 3 규칙). 연속으로 지우면 앞의
 * 되돌리기 기회는 사라지는데, 이건 Gmail도 같은 방식이다 — 여러 건을 쌓기 시작하면
 * "몇 번째 삭제를 되돌리는 건지"가 오히려 불분명해진다.
 *
 * 왜 삭제를 미루지 않고 즉시 지우는가: 이 앱은 두 사람이 같은 데이터를 본다. "스낵바가
 * 사라질 때 실제 삭제"로 미루면 그 사이 상대 기기에는 항목이 남아 있어 서로 다른 화면을
 * 보게 된다. 즉시 지우고, 되돌리기는 같은 id로 다시 만드는 쪽이 동기화 관점에서 정직하다
 * (Firestore create가 `document(id).set`이라 재생성 = 완전 복원이다).
 */
class UndoDeleteState<T : Any> {

    /**
     * [token]은 offer마다 커지는 일련번호다. 같은 항목을 지우고 → 되돌리고 → 다시 지우면
     * item·message가 완전히 같아, 이것 없이는 화면의 LaunchedEffect가 "값이 안 바뀌었다"고
     * 보고 두 번째 스낵바를 띄우지 않는다.
     */
    data class Pending<T>(val item: T, val message: String, val token: Long)

    private val _pending = MutableStateFlow<Pending<T>?>(null)
    val pending: StateFlow<Pending<T>?> = _pending
    private var counter = 0L

    /** 삭제가 실제로 성공한 뒤에만 부른다 — 실패한 삭제에 되돌리기를 내밀면 안 된다. */
    fun offer(item: T, message: String) {
        _pending.value = Pending(item, message, ++counter)
    }

    /** 되돌리기를 눌렀다. 항목을 돌려주고 상태를 비운다(두 번 복원 방지). */
    fun consume(): T? = _pending.value?.item.also { _pending.value = null }

    /** 스낵바가 눌리지 않고 사라졌다. 참조를 놓아 준다. */
    fun dismiss() {
        _pending.value = null
    }
}
