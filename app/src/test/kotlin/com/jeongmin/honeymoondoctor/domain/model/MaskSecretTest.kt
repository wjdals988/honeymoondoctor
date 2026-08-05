package com.jeongmin.honeymoondoctor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskSecretTest {

    @Test
    fun `4자 이하는 전부 가린다`() {
        assertThat(maskSecret("1234")).isEqualTo("••••")
        assertThat(maskSecret("12")).isEqualTo("••")
    }

    @Test
    fun `5자 이상은 앞 2자만 남긴다`() {
        assertThat(maskSecret("ABC123")).isEqualTo("AB••••")
        assertThat(maskSecret("KE1234567")).isEqualTo("KE•••••••")
    }

    @Test
    fun `null이나 빈 문자열은 null을 돌려준다`() {
        assertThat(maskSecret(null)).isNull()
        assertThat(maskSecret("")).isNull()
    }

    @Test
    fun `마스킹 결과에는 원문 뒷자리가 절대 남지 않는다`() {
        val masked = maskSecret("SECRET-PIN-9876")!!
        assertThat(masked).doesNotContain("9876")
        assertThat(masked.length).isEqualTo("SECRET-PIN-9876".length)
    }
}
