package com.jeongmin.honeymoondoctor.data.place

import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.model.PlaceCategory
import com.jeongmin.honeymoondoctor.domain.model.PlacePriority
import com.jeongmin.honeymoondoctor.domain.model.PreferredTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * docs/templates/places_import_template.{tsv,json} 형식의 가져오기 파서(스펙 7-7).
 * 행 단위로 검증해 실패 행과 사유를 그대로 사용자에게 보여준다. 값이 전부 빈 행은 건너뛴다.
 * 카테고리·우선순위·시간대는 영문 enum 이름과 한국어 라벨을 모두 허용한다.
 */
data class PlaceImportRow(
    /** TSV는 파일 행 번호(헤더 포함), JSON은 배열 인덱스+1 */
    val lineNumber: Int,
    val place: Place?,
    val errors: List<String>,
)

data class PlaceImportPreview(
    val rows: List<PlaceImportRow>,
    /** 기존 장소(이름+도시 동일)와 겹쳐 가져오기에서 제외될 행 번호 */
    val duplicateLineNumbers: Set<Int>,
) {
    val validRows: List<PlaceImportRow>
        get() = rows.filter { it.place != null && it.errors.isEmpty() && it.lineNumber !in duplicateLineNumbers }
    val errorRows: List<PlaceImportRow> get() = rows.filter { it.errors.isNotEmpty() }
}

object PlaceImportParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val EXPECTED_COLUMNS = listOf(
        "name", "city", "category", "priority", "latitude", "longitude",
        "mapsUrl", "preferredTime", "ratingSnapshot", "reviewCountSnapshot", "snapshotCheckedDate", "notes",
    )

    fun parseTsv(content: String, cities: List<City>, existingPlaces: List<Place>): PlaceImportPreview {
        val lines = content.split('\n').map { it.trimEnd('\r') }
        if (lines.isEmpty() || lines.first().isBlank()) {
            return PlaceImportPreview(
                rows = listOf(PlaceImportRow(1, null, listOf("헤더 행이 없습니다. 템플릿 형식을 확인해 주세요."))),
                duplicateLineNumbers = emptySet(),
            )
        }
        val header = lines.first().split('\t').map { it.trim() }
        if (header.map { it.lowercase() } != EXPECTED_COLUMNS.map { it.lowercase() }) {
            return PlaceImportPreview(
                rows = listOf(
                    PlaceImportRow(
                        1,
                        null,
                        listOf("헤더가 템플릿과 다릅니다. 기대: ${EXPECTED_COLUMNS.joinToString("\t")}"),
                    ),
                ),
                duplicateLineNumbers = emptySet(),
            )
        }

        val rows = lines.drop(1).mapIndexedNotNull { index, line ->
            val lineNumber = index + 2 // 헤더 다음부터
            if (line.split('\t').all { it.isBlank() }) return@mapIndexedNotNull null // 템플릿의 빈 행
            val columns = line.split('\t')
            val value = { name: String ->
                val i = EXPECTED_COLUMNS.indexOf(name)
                columns.getOrNull(i)?.trim()?.ifEmpty { null }
            }
            buildRow(
                lineNumber = lineNumber,
                cities = cities,
                name = value("name"),
                city = value("city"),
                category = value("category"),
                priority = value("priority"),
                latitude = value("latitude"),
                longitude = value("longitude"),
                mapsUrl = value("mapsUrl"),
                preferredTime = value("preferredTime"),
                rating = value("ratingSnapshot"),
                reviewCount = value("reviewCountSnapshot"),
                snapshotCheckedDate = value("snapshotCheckedDate"),
                notes = value("notes"),
            )
        }
        return withDuplicates(rows, existingPlaces)
    }

    @Serializable
    private data class JsonRowDto(
        val name: String? = null,
        val city: String? = null,
        val category: String? = null,
        val priority: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val mapsUrl: String? = null,
        val preferredTime: String? = null,
        val ratingSnapshot: Double? = null,
        val reviewCountSnapshot: Long? = null,
        val snapshotCheckedDate: String? = null,
        val notes: String? = null,
    )

    fun parseJson(content: String, cities: List<City>, existingPlaces: List<Place>): PlaceImportPreview {
        val dtos = runCatching { json.decodeFromString<List<JsonRowDto>>(content) }.getOrElse {
            return PlaceImportPreview(
                rows = listOf(PlaceImportRow(1, null, listOf("JSON 배열을 읽을 수 없습니다: ${it.message}"))),
                duplicateLineNumbers = emptySet(),
            )
        }
        val rows = dtos.mapIndexedNotNull { index, dto ->
            val allBlank = dto.name.isNullOrBlank() && dto.city.isNullOrBlank() && dto.category.isNullOrBlank() &&
                dto.latitude == null && dto.longitude == null && dto.notes.isNullOrBlank()
            if (allBlank) return@mapIndexedNotNull null // 템플릿의 빈 항목
            buildRow(
                lineNumber = index + 1,
                cities = cities,
                name = dto.name?.trim()?.ifEmpty { null },
                city = dto.city?.trim()?.ifEmpty { null },
                category = dto.category?.trim()?.ifEmpty { null },
                priority = dto.priority?.trim()?.ifEmpty { null },
                latitude = dto.latitude?.toString(),
                longitude = dto.longitude?.toString(),
                mapsUrl = dto.mapsUrl?.trim()?.ifEmpty { null },
                preferredTime = dto.preferredTime?.trim()?.ifEmpty { null },
                rating = dto.ratingSnapshot?.toString(),
                reviewCount = dto.reviewCountSnapshot?.toString(),
                snapshotCheckedDate = dto.snapshotCheckedDate?.trim()?.ifEmpty { null },
                notes = dto.notes?.trim()?.ifEmpty { null },
            )
        }
        return withDuplicates(rows, existingPlaces)
    }

    /** 현재 장소 목록을 템플릿과 같은 열 순서의 TSV로 내보낸다. */
    fun toTsv(places: List<Place>, cities: List<City>): String {
        val header = EXPECTED_COLUMNS.joinToString("\t")
        val body = places.joinToString("\n") { place ->
            val cityName = place.cityId?.let { id -> cities.firstOrNull { it.id == id }?.displayName ?: id }
            listOf(
                place.name,
                cityName.orEmpty(),
                place.category.name,
                place.priority.name,
                place.latitude?.toString().orEmpty(),
                place.longitude?.toString().orEmpty(),
                place.mapsUrl.orEmpty(),
                place.preferredTimes.joinToString(",") { it.name },
                place.ratingSnapshot?.toString().orEmpty(),
                place.reviewCountSnapshot?.toString().orEmpty(),
                place.sourceUpdatedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString().orEmpty(),
                place.notes.orEmpty().replace('\t', ' ').replace('\n', ' '),
            ).joinToString("\t")
        }
        return if (body.isEmpty()) header else "$header\n$body"
    }

    private fun buildRow(
        lineNumber: Int,
        cities: List<City>,
        name: String?,
        city: String?,
        category: String?,
        priority: String?,
        latitude: String?,
        longitude: String?,
        mapsUrl: String?,
        preferredTime: String?,
        rating: String?,
        reviewCount: String?,
        snapshotCheckedDate: String?,
        notes: String?,
    ): PlaceImportRow {
        val errors = mutableListOf<String>()

        if (name.isNullOrBlank()) errors += "장소명(name)이 비어 있습니다."

        val cityId = city?.let { raw ->
            cities.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.displayName == raw }?.id
                ?: run {
                    errors += "알 수 없는 도시입니다: \"$raw\" (등록된 도시의 이름 또는 ID여야 함)"
                    null
                }
        }

        val categoryValue = category?.let { raw ->
            parseEnum<PlaceCategory>(raw, PlaceCategory.entries.map { it to it.labelKo }) ?: run {
                errors += "알 수 없는 카테고리입니다: \"$raw\""
                null
            }
        } ?: PlaceCategory.ETC

        val priorityValue = priority?.let { raw ->
            parseEnum<PlacePriority>(raw, PlacePriority.entries.map { it to it.labelKo }) ?: run {
                errors += "알 수 없는 우선순위입니다: \"$raw\" (꼭 가기/가고 싶음/여유 시)"
                null
            }
        } ?: PlacePriority.WANT_TO_GO

        val latitudeValue = latitude?.let {
            it.toDoubleOrNull()?.takeIf { lat -> lat in -90.0..90.0 }
                ?: run {
                    errors += "위도가 올바르지 않습니다: \"$it\""
                    null
                }
        }
        val longitudeValue = longitude?.let {
            it.toDoubleOrNull()?.takeIf { lng -> lng in -180.0..180.0 }
                ?: run {
                    errors += "경도가 올바르지 않습니다: \"$it\""
                    null
                }
        }
        if ((latitudeValue == null) != (longitudeValue == null)) {
            errors += "위도·경도는 함께 입력하거나 함께 비워야 합니다."
        }

        val preferredTimes = preferredTime?.split(',', ';')?.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            parseEnum<PreferredTime>(trimmed, PreferredTime.entries.map { it to it.labelKo }) ?: run {
                errors += "알 수 없는 추천 시간대입니다: \"$trimmed\""
                null
            }
        }.orEmpty()

        val ratingValue = rating?.let {
            it.toDoubleOrNull()?.takeIf { r -> r in 0.0..5.0 } ?: run {
                errors += "평점이 올바르지 않습니다(0~5): \"$it\""
                null
            }
        }
        val reviewCountValue = reviewCount?.let {
            it.replace(",", "").toLongOrNull()?.takeIf { c -> c >= 0 } ?: run {
                errors += "리뷰 수가 올바르지 않습니다: \"$it\""
                null
            }
        }
        val snapshotDate = snapshotCheckedDate?.let {
            runCatching { LocalDate.parse(it) }.getOrElse {
                errors += "스냅샷 확인일이 올바르지 않습니다(YYYY-MM-DD): \"$snapshotCheckedDate\""
                null
            }
        }

        if (errors.isNotEmpty()) return PlaceImportRow(lineNumber, null, errors)

        return PlaceImportRow(
            lineNumber = lineNumber,
            place = Place(
                id = "place-${UUID.randomUUID()}",
                name = name!!.trim(),
                cityId = cityId,
                category = categoryValue,
                priority = priorityValue,
                latitude = latitudeValue,
                longitude = longitudeValue,
                mapsUrl = mapsUrl,
                notes = notes,
                ratingSnapshot = ratingValue,
                reviewCountSnapshot = reviewCountValue,
                sourceUpdatedAt = snapshotDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
                preferredTimes = preferredTimes,
            ),
            errors = emptyList(),
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, labeled: List<Pair<T, String>>): T? =
        labeled.firstOrNull { (entry, label) -> entry.name.equals(raw, ignoreCase = true) || label == raw }?.first

    /** 기존 장소 및 파일 내부의 (이름+도시) 중복 행을 표시한다. 중복은 오류가 아니라 제외 대상이다. */
    private fun withDuplicates(rows: List<PlaceImportRow>, existingPlaces: List<Place>): PlaceImportPreview {
        val existingKeys = existingPlaces.map { it.name.lowercase() to it.cityId }.toHashSet()
        val seenInFile = HashSet<Pair<String, String?>>()
        val duplicates = mutableSetOf<Int>()
        rows.forEach { row ->
            val place = row.place ?: return@forEach
            val key = place.name.lowercase() to place.cityId
            if (key in existingKeys || !seenInFile.add(key)) {
                duplicates += row.lineNumber
            }
        }
        return PlaceImportPreview(rows = rows, duplicateLineNumbers = duplicates)
    }
}
