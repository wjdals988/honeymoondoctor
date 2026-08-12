package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapsUrlCoordinatesTest {

    @Test
    fun `지도 화면 중심 좌표를 읽는다`() {
        val url = "https://www.google.com/maps/place/Kiyomizu-dera/@34.9948,135.7850,17z/data=!4m6"

        val result = MapsUrlCoordinates.parse(url)

        assertThat(result?.latitude).isEqualTo(34.9948)
        assertThat(result?.longitude).isEqualTo(135.7850)
    }

    @Test
    fun `장소 실제 좌표가 있으면 화면 중심보다 우선한다`() {
        // @는 지도 화면 중심이라 장소와 어긋난다. !3d/!4d가 그 장소의 실제 지점이다.
        val url = "https://www.google.com/maps/place/X/@34.9000,135.7000,17z/data=!3d34.9948!4d135.7850"

        val result = MapsUrlCoordinates.parse(url)

        assertThat(result?.latitude).isEqualTo(34.9948)
        assertThat(result?.longitude).isEqualTo(135.7850)
    }

    @Test
    fun `좌표 검색 링크를 읽는다`() {
        val result = MapsUrlCoordinates.parse("https://maps.google.com/?q=37.5665,126.9780")

        assertThat(result?.latitude).isEqualTo(37.5665)
        assertThat(result?.longitude).isEqualTo(126.9780)
    }

    @Test
    fun `geo 스킴을 읽는다`() {
        val result = MapsUrlCoordinates.parse("geo:48.8584,2.2945")

        assertThat(result?.latitude).isEqualTo(48.8584)
        assertThat(result?.longitude).isEqualTo(2.2945)
    }

    @Test
    fun `좌표만 붙여넣어도 읽는다`() {
        val result = MapsUrlCoordinates.parse(" 35.0116, 135.7681 ")

        assertThat(result?.latitude).isEqualTo(35.0116)
        assertThat(result?.longitude).isEqualTo(135.7681)
    }

    @Test
    fun `남반구와 서반구의 음수 좌표를 읽는다`() {
        val result = MapsUrlCoordinates.parse("https://www.google.com/maps/@-33.8688,151.2093,15z")

        assertThat(result?.latitude).isEqualTo(-33.8688)
        assertThat(result?.longitude).isEqualTo(151.2093)
    }

    @Test
    fun `범위를 벗어난 숫자는 좌표로 보지 않는다`() {
        // 위도 200은 존재하지 않는다. 줌 레벨 같은 다른 숫자를 잘못 집는 것을 막는다.
        assertThat(MapsUrlCoordinates.parse("https://example.com/@200.0,300.0,15z")).isNull()
    }

    @Test
    fun `좌표가 없는 링크는 null을 준다`() {
        assertThat(MapsUrlCoordinates.parse("https://maps.app.goo.gl/abcdefg")).isNull()
        assertThat(MapsUrlCoordinates.parse("")).isNull()
        assertThat(MapsUrlCoordinates.parse("그냥 텍스트")).isNull()
    }
}
