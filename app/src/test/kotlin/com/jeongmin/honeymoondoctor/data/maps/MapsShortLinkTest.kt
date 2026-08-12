package com.jeongmin.honeymoondoctor.data.maps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapsShortLinkTest {

    @Test
    fun `구글 지도 앱이 만드는 단축 링크를 알아본다`() {
        assertThat(MapsShortLink.isShortLink("https://maps.app.goo.gl/AbCdEfGhIjK")).isTrue()
        assertThat(MapsShortLink.isShortLink("https://goo.gl/maps/xyz123")).isTrue()
    }

    @Test
    fun `대소문자와 앞뒤 공백을 무시한다`() {
        assertThat(MapsShortLink.isShortLink("  HTTPS://MAPS.APP.GOO.GL/AbCd  ")).isTrue()
    }

    @Test
    fun `좌표가 이미 들어 있는 일반 링크는 단축 링크가 아니다`() {
        // 이런 주소는 네트워크 없이 바로 파싱된다 — 굳이 펼칠 이유가 없다.
        assertThat(MapsShortLink.isShortLink("https://www.google.com/maps/@35.0,135.0,17z")).isFalse()
        assertThat(MapsShortLink.isShortLink("35.0116, 135.7681")).isFalse()
        assertThat(MapsShortLink.isShortLink("")).isFalse()
    }
}
