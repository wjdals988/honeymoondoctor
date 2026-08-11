package com.jeongmin.honeymoondoctor.core.version

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVersionTest {

    @Test
    fun `태그의 v 접두사를 떼고 마디별 숫자로 읽는다`() {
        assertThat(AppVersion.parse("v0.1.4")).isEqualTo(listOf(0, 1, 4))
        assertThat(AppVersion.parse("0.1.4")).isEqualTo(listOf(0, 1, 4))
    }

    @Test
    fun `새 버전이면 true`() {
        assertThat(AppVersion.isNewerThan("v0.1.5", "0.1.4")).isTrue()
        assertThat(AppVersion.isNewerThan("v0.2.0", "0.1.9")).isTrue()
        assertThat(AppVersion.isNewerThan("v1.0.0", "0.9.9")).isTrue()
    }

    @Test
    fun `같은 버전이면 false`() {
        assertThat(AppVersion.isNewerThan("v0.1.4", "0.1.4")).isFalse()
    }

    @Test
    fun `설치본이 더 새로우면 false`() {
        assertThat(AppVersion.isNewerThan("v0.1.3", "0.1.4")).isFalse()
    }

    @Test
    fun `두 자리 이상 마디를 문자열로 비교하지 않는다`() {
        // 문자열 비교면 "0.1.9" > "0.1.10"으로 잘못 판단한다. 숫자로 끊어야 맞다.
        assertThat(AppVersion.isNewerThan("v0.1.10", "0.1.9")).isTrue()
        assertThat(AppVersion.isNewerThan("v0.1.9", "0.1.10")).isFalse()
    }

    @Test
    fun `마디 수가 달라도 짧은 쪽을 0으로 채워 비교한다`() {
        assertThat(AppVersion.isNewerThan("v0.2", "0.1.9")).isTrue()
        assertThat(AppVersion.isNewerThan("v0.1", "0.1.0")).isFalse()
        assertThat(AppVersion.isNewerThan("v0.1.0.1", "0.1.0")).isTrue()
    }

    @Test
    fun `prerelease 접미사가 붙어도 앞의 숫자로 비교한다`() {
        assertThat(AppVersion.isNewerThan("v0.2.0-beta1", "0.1.4")).isTrue()
    }

    @Test
    fun `읽을 수 없는 값이면 업데이트 있음으로 오판하지 않는다`() {
        assertThat(AppVersion.isNewerThan("", "0.1.4")).isFalse()
        assertThat(AppVersion.isNewerThan("nightly", "0.1.4")).isFalse()
    }
}
