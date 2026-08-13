package com.jeongmin.honeymoondoctor.feature.nearby

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.toUserMessage
import com.jeongmin.honeymoondoctor.data.maps.MapsLinkResolver
import com.jeongmin.honeymoondoctor.data.maps.MapsShortLink
import com.jeongmin.honeymoondoctor.data.place.PlaceImportParser
import com.jeongmin.honeymoondoctor.data.place.PlaceImportPreview
import com.jeongmin.honeymoondoctor.domain.model.City
import com.jeongmin.honeymoondoctor.domain.model.Place
import com.jeongmin.honeymoondoctor.domain.repository.CityRepository
import com.jeongmin.honeymoondoctor.domain.repository.PlaceRepository
import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlCoordinates
import com.jeongmin.honeymoondoctor.domain.usecase.MapsUrlPlaceName
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [PlaceImportViewModel.importFromUrls] 결과 요약. */
data class UrlImportSummary(val requested: Int, val added: Int)

private data class UrlImportState(val importing: Boolean = false, val summary: UrlImportSummary? = null)

data class PlaceImportUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val cities: List<City> = emptyList(),
    val placeCount: Int = 0,
    val preview: PlaceImportPreview? = null,
    val fileName: String? = null,
    val importedCount: Int? = null,
    val error: String? = null,
    val urlImporting: Boolean = false,
    val urlImportSummary: UrlImportSummary? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeCurrentTrip: ObserveCurrentTrip,
    cityRepository: CityRepository,
    private val placeRepository: PlaceRepository,
    private val mapsLinkResolver: MapsLinkResolver,
) : ViewModel() {

    private val preview = MutableStateFlow<Pair<String, PlaceImportPreview>?>(null) // fileName to preview
    private val importedCount = MutableStateFlow<Int?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val urlImportState = MutableStateFlow(UrlImportState())

    val uiState: StateFlow<PlaceImportUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(PlaceImportUiState(loading = false))
            } else {
                combine(
                    cityRepository.observeCities(trip.id),
                    placeRepository.observePlaces(trip.id).map { it.size },
                    preview,
                    importedCount,
                    combine(error, urlImportState) { errorValue, urlState -> errorValue to urlState },
                ) { cities, placeCount, previewValue, imported, (errorValue, urlState) ->
                    PlaceImportUiState(
                        loading = false,
                        tripId = trip.id,
                        cities = cities,
                        placeCount = placeCount,
                        preview = previewValue?.second,
                        fileName = previewValue?.first,
                        importedCount = imported,
                        error = errorValue,
                        urlImporting = urlState.importing,
                        urlImportSummary = urlState.summary,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceImportUiState())

    /** 선택한 TSV/JSON 파일을 읽어 미리보기를 만든다. 실제 저장은 confirmImport()에서만 일어난다. */
    fun loadFile(uri: Uri, cities: List<City>) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            importedCount.value = null
            error.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    var displayName = uri.lastPathSegment ?: "가져오기 파일"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex) ?: displayName
                        }
                    }
                    val content = resolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("파일을 열 수 없습니다.")
                    val existing = placeRepository.observePlaces(tripId).first()
                    val parsed = if (displayName.endsWith(".json", ignoreCase = true) ||
                        content.trimStart().startsWith("[")
                    ) {
                        PlaceImportParser.parseJson(content, cities, existing)
                    } else {
                        PlaceImportParser.parseTsv(content, cities, existing)
                    }
                    displayName to parsed
                }
            }
            result
                .onSuccess { preview.value = it }
                .onFailure { error.value = it.message ?: "파일을 읽지 못했습니다." }
        }
    }

    fun confirmImport() {
        val tripId = uiState.value.tripId ?: return
        val currentPreview = uiState.value.preview ?: return
        val toImport = currentPreview.validRows.mapNotNull { it.place }
        if (toImport.isEmpty()) return
        viewModelScope.launch {
            runCatching { placeRepository.createAll(tripId, toImport) }
                .onSuccess {
                    importedCount.value = toImport.size
                    preview.value = null
                }
                .onFailure {
                    error.value = it.toUserMessage("장소를 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.")
                }
        }
    }

    fun clearPreview() {
        preview.value = null
        importedCount.value = null
        error.value = null
    }

    /**
     * 개별 장소 링크를 한 줄에 하나씩 붙여넣어 가져온다. 좌표는 [MapsLinkResolver]로
     * (단축 링크면 펼쳐서) 뽑고, 이름은 펼친 URL의 `/maps/place/<이름>/` 경로에서 뽑는다 —
     * "저장된 목록" 링크 안의 장소 여러 개를 한 번에 읽어오는 기능은 아니다. 구글 지도가
     * 목록 내용을 사용자가 앱을 열었을 때 비공개 API로 나중에 불러오는 구조라, 이 앱처럼
     * 단순 HTTP 요청만 하는 쪽에서는 목록 자체의 내용이 응답에 없다.
     */
    fun importFromUrls(rawText: String) {
        val tripId = uiState.value.tripId ?: return
        val urls = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (urls.isEmpty()) return
        urlImportState.value = UrlImportState(importing = true)
        viewModelScope.launch {
            val existingNames = placeRepository.observePlaces(tripId).first()
                .map { it.name.lowercase() }.toHashSet()
            val seenInBatch = HashSet<String>()
            val places = mutableListOf<Place>()
            for (raw in urls) {
                val expanded = if (MapsShortLink.isShortLink(raw)) {
                    mapsLinkResolver.resolve(raw).getOrNull()
                } else {
                    raw
                } ?: continue
                val coordinates = MapsUrlCoordinates.parse(expanded) ?: continue
                val name = MapsUrlPlaceName.parse(expanded) ?: "이름 없는 장소"
                val key = name.lowercase()
                if (key in existingNames || !seenInBatch.add(key)) continue
                places += Place(
                    id = "place-${UUID.randomUUID()}",
                    name = name,
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    mapsUrl = expanded,
                )
            }
            if (places.isNotEmpty()) {
                runCatching { placeRepository.createAll(tripId, places) }
                    .onFailure { error.value = it.toUserMessage("장소를 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") }
            }
            urlImportState.value = UrlImportState(
                importing = false,
                summary = UrlImportSummary(requested = urls.size, added = places.size),
            )
        }
    }

    fun clearUrlImportSummary() {
        urlImportState.value = UrlImportState()
    }

    /** 현재 장소 전체를 TSV로 내보낸다(SAF CreateDocument가 만든 uri에 기록). */
    fun exportTo(uri: Uri) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            error.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val places = placeRepository.observePlaces(tripId).first()
                    val cities = uiState.value.cities
                    val tsv = PlaceImportParser.toTsv(places, cities)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(tsv.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("파일을 쓸 수 없습니다.")
                    places.size
                }
            }
            result
                .onSuccess { importedCount.value = null }
                .onFailure { error.value = it.message ?: "내보내기에 실패했습니다." }
        }
    }
}
