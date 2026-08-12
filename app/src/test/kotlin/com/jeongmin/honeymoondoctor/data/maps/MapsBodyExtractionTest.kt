package com.jeongmin.honeymoondoctor.data.maps

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlCoordinates
import org.junit.Test

/**
 * 단축 링크가 리다이렉트 대신 HTML 중간 페이지를 줄 때를 대비한 경로를 검증한다.
 * 리졸버의 네트워크 부분은 여기서 다루지 않고, 본문에서 값을 뽑아내는 규칙만 확인한다.
 */
class MapsBodyExtractionTest {

    /** 리졸버가 본문에서 좌표를 찾을 때 쓰는 것과 같은 규칙. */
    private fun findMapsUrl(body: String): String? =
        Regex("""https://www[.]google[.]com/maps[^"' ]*""").find(body)?.value

    @Test
    fun `중간 페이지 HTML에서 지도 주소를 뽑는다`() {
        val html = """
            <html><head><meta http-equiv="refresh" content="0; url=
            https://www.google.com/maps/place/Kiyomizu-dera/@34.9948,135.785,17z"></head></html>
        """.trimIndent()

        val url = findMapsUrl(html)

        assertThat(url).isNotNull()
        assertThat(MapsUrlCoordinates.parse(url!!)?.latitude).isEqualTo(34.9948)
    }

    @Test
    fun `본문에 좌표가 직접 있으면 그대로 읽힌다`() {
        val html = """<script>var center = {lat: 0};</script><a href="/maps/@48.8584,2.2945,17z">Eiffel</a>"""

        assertThat(MapsUrlCoordinates.parse(html)?.longitude).isEqualTo(2.2945)
    }

    @Test
    fun `좌표도 지도 주소도 없으면 아무것도 찾지 못한다`() {
        val html = "<html><body>404 Not Found</body></html>"

        assertThat(findMapsUrl(html)).isNull()
        assertThat(MapsUrlCoordinates.parse(html)).isNull()
    }
}
