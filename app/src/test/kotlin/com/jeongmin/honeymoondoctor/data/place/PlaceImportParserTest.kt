package com.jeongmin.honeymoondoctor.data.place

import com.google.common.truth.Truth.assertThat
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import org.junit.Test

class PlaceImportParserTest {

    private val cities = listOf(
        City(id = "prague", displayName = "프라하", countryCode = "CZ", timeZoneId = "Europe/Prague", startDate = null, endDate = null),
        City(id = "barcelona", displayName = "바르셀로나", countryCode = "ES", timeZoneId = "Europe/Madrid", startDate = null, endDate = null),
    )

    private val header =
        "name\tcity\tcategory\tpriority\tlatitude\tlongitude\tmapsUrl\tpreferredTime\tratingSnapshot\treviewCountSnapshot\tsnapshotCheckedDate\tnotes"

    @Test
    fun `유효한 TSV 행을 장소로 파싱한다 - 한국어 라벨 허용`() {
        val tsv = header + "\n" +
            "카를교\t프라하\t관광\t꼭 가기\t50.0865\t14.4114\thttps://maps.app/abc\t오전,저녁\t4.7\t120000\t2026-08-01\t일출 명소"

        val preview = PlaceImportParser.parseTsv(tsv, cities, existingPlaces = emptyList())

        assertThat(preview.errorRows).isEmpty()
        assertThat(preview.validRows).hasSize(1)
        val place = preview.validRows.first().place!!
        assertThat(place.name).isEqualTo("카를교")
        assertThat(place.cityId).isEqualTo("prague")
        assertThat(place.category).isEqualTo(PlaceCategory.SIGHTSEEING)
        assertThat(place.priority).isEqualTo(PlacePriority.MUST_GO)
        assertThat(place.latitude).isEqualTo(50.0865)
        assertThat(place.preferredTimes).containsExactly(PreferredTime.MORNING, PreferredTime.EVENING)
        assertThat(place.ratingSnapshot).isEqualTo(4.7)
        assertThat(place.reviewCountSnapshot).isEqualTo(120000)
        assertThat(place.sourceUpdatedAt).isNotNull()
    }

    @Test
    fun `빈 행은 건너뛰고 오류 행은 행 번호와 사유를 알려준다`() {
        val tsv = header + "\n" +
            "\t\t\t\t\t\t\t\t\t\t\t\n" + // 빈 행 → 건너뜀
            "\t프라하\t관광\t꼭 가기\t\t\t\t\t\t\t\t\n" + // 이름 없음 → 오류
            "성당\t서울\t관광\t\t\t\t\t\t\t\t\t\n" + // 알 수 없는 도시 → 오류
            "좌표오류\t프라하\t관광\t\t91.0\t14.0\t\t\t\t\t\t" // 위도 범위 밖 → 오류

        val preview = PlaceImportParser.parseTsv(tsv, cities, existingPlaces = emptyList())

        assertThat(preview.rows).hasSize(3)
        assertThat(preview.validRows).isEmpty()
        assertThat(preview.errorRows.map { it.lineNumber }).containsExactly(3, 4, 5)
        assertThat(preview.errorRows[0].errors.first()).contains("장소명")
        assertThat(preview.errorRows[1].errors.first()).contains("도시")
        assertThat(preview.errorRows[2].errors.first()).contains("위도")
    }

    @Test
    fun `헤더가 템플릿과 다르면 전체를 거부한다`() {
        val preview = PlaceImportParser.parseTsv("이름\t도시\n카를교\t프라하", cities, emptyList())
        assertThat(preview.validRows).isEmpty()
        assertThat(preview.errorRows.first().errors.first()).contains("헤더")
    }

    @Test
    fun `기존 장소나 파일 내 중복은 가져오기에서 제외하고 표시한다`() {
        val existing = listOf(
            Place(id = "p1", name = "카를교", cityId = "prague"),
        )
        val tsv = header + "\n" +
            "카를교\t프라하\t관광\t\t\t\t\t\t\t\t\t\n" + // 기존과 중복
            "사그라다\t바르셀로나\t관광\t\t\t\t\t\t\t\t\t\n" +
            "사그라다\t바르셀로나\t관광\t\t\t\t\t\t\t\t\t" // 파일 내 중복

        val preview = PlaceImportParser.parseTsv(tsv, cities, existing)

        assertThat(preview.duplicateLineNumbers).containsExactly(2, 4)
        assertThat(preview.validRows).hasSize(1)
        assertThat(preview.validRows.first().place!!.name).isEqualTo("사그라다")
    }

    @Test
    fun `JSON 배열도 동일한 검증으로 파싱한다`() {
        val json = """
            [
              {"name": "구엘 공원", "city": "barcelona", "category": "SIGHTSEEING", "priority": "WANT_TO_GO",
               "latitude": 41.4145, "longitude": 2.1527, "preferredTime": "AFTERNOON",
               "ratingSnapshot": 4.6, "reviewCountSnapshot": 250000},
              {"name": "", "city": "", "category": "", "priority": "", "latitude": null, "longitude": null,
               "mapsUrl": "", "preferredTime": "", "ratingSnapshot": null, "reviewCountSnapshot": null,
               "snapshotCheckedDate": "", "notes": ""},
              {"name": "이상한곳", "city": "화성"}
            ]
        """.trimIndent()

        val preview = PlaceImportParser.parseJson(json, cities, emptyList())

        assertThat(preview.validRows).hasSize(1)
        assertThat(preview.validRows.first().place!!.name).isEqualTo("구엘 공원")
        assertThat(preview.errorRows).hasSize(1)
        assertThat(preview.errorRows.first().errors.first()).contains("도시")
    }

    @Test
    fun `잘못된 JSON은 전체 오류로 안내한다`() {
        val preview = PlaceImportParser.parseJson("{not json array}", cities, emptyList())
        assertThat(preview.validRows).isEmpty()
        assertThat(preview.errorRows.first().errors.first()).contains("JSON")
    }

    @Test
    fun `내보내기 TSV는 템플릿 헤더와 같은 열 순서다`() {
        val tsv = PlaceImportParser.toTsv(
            listOf(Place(id = "p1", name = "카를교", cityId = "prague", latitude = 50.0865, longitude = 14.4114)),
            cities,
        )
        val lines = tsv.split('\n')
        assertThat(lines.first()).isEqualTo(header)
        assertThat(lines[1]).startsWith("카를교\t프라하\t")
    }
}
