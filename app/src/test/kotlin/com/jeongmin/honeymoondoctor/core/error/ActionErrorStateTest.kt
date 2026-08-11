package com.jeongmin.honeymoondoctor.core.error

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ActionErrorStateTest {

    @Test
    fun `Firestore 예외는 영문 원문을 노출하지 않고 안내 문구로 바뀐다`() {
        val cause = FirebaseFirestoreException(
            "PERMISSION_DENIED: Missing or insufficient permissions.",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )

        val message = cause.toUserMessage("완료된 여행은 수정할 수 없습니다.")

        assertThat(message).isEqualTo("완료된 여행은 수정할 수 없습니다.")
        assertThat(message).doesNotContain("PERMISSION_DENIED")
    }

    @Test
    fun `우리가 던진 예외는 담고 있는 한국어 안내를 그대로 살린다`() {
        val cause = IllegalStateException("파일이 15MB를 넘습니다.")

        assertThat(cause.toUserMessage("저장에 실패했습니다.")).isEqualTo("파일이 15MB를 넘습니다.")
    }

    @Test
    fun `메시지가 없는 예외는 안내 문구로 대체한다`() {
        assertThat(RuntimeException().toUserMessage("저장에 실패했습니다.")).isEqualTo("저장에 실패했습니다.")
    }

    @Test
    fun `실패한 동작은 예외를 던지지 않고 메시지로 보고한다`() = runTest {
        val state = ActionErrorState()

        val succeeded = state.runReporting("삭제하지 못했습니다.") {
            throw FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        assertThat(succeeded).isFalse()
        assertThat(state.message.value).isEqualTo("삭제하지 못했습니다.")
    }

    @Test
    fun `성공한 동작은 앞서 남은 오류를 지운다`() = runTest {
        val state = ActionErrorState()
        state.report(RuntimeException("이전 오류"), "이전 오류")

        val succeeded = state.runReporting("삭제하지 못했습니다.") { /* 성공 */ }

        assertThat(succeeded).isTrue()
        assertThat(state.message.value).isNull()
    }
}
