package com.jeongmin.honeymoondoctor.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InviteCodeTest {

    @Test
    fun `생성된 초대코드에서 tripId를 그대로 복원할 수 있다`() {
        val code = InviteCode.generate("trip-abc-123")
        assertThat(InviteCode.extractTripId(code)).isEqualTo("trip-abc-123")
    }

    @Test
    fun `같은 코드는 항상 같은 해시를 만든다`() {
        val code = InviteCode.generate("trip-1")
        assertThat(InviteCode.sha256Hex(code)).isEqualTo(InviteCode.sha256Hex(code))
    }

    @Test
    fun `한 글자만 달라도 해시는 완전히 달라진다`() {
        val hashA = InviteCode.sha256Hex("trip-1:aaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val hashB = InviteCode.sha256Hex("trip-1:aaaaaaaaaaaaaaaaaaaaaaaaaaab")
        assertThat(hashA).isNotEqualTo(hashB)
    }

    @Test
    fun `매번 생성되는 초대코드는 서로 다르다`() {
        val codes = List(50) { InviteCode.generate("trip-1") }
        assertThat(codes.toSet()).hasSize(codes.size)
    }

    @Test
    fun `해시는 원문 초대코드를 담고 있지 않다`() {
        val code = InviteCode.generate("trip-1")
        val hash = InviteCode.sha256Hex(code)
        assertThat(hash).doesNotContain(code)
        assertThat(hash).hasLength(64) // SHA-256 hex = 32바이트 = 64자
    }

    @Test
    fun `형식이 올바르지 않은 코드는 tripId 추출에 실패한다`() {
        assertThat(InviteCode.extractTripId("delimiter-없는-문자열")).isNull()
        assertThat(InviteCode.extractTripId(":secret-only")).isNull()
    }

    @Test
    fun `tripId에 구분자가 포함되면 생성을 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            InviteCode.generate("trip:with:colon")
        }
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            assertThat(t).isInstanceOf(expected)
            return
        }
        throw AssertionError("예외가 발생하지 않았습니다: $expected")
    }
}
