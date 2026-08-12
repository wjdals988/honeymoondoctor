package com.jeongmin.honeymoondoctor.core.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** 미니맵에 찍을 핀 하나. [label]은 마커를 탭했을 때 말풍선으로 보인다. */
data class MapPin(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/**
 * 앱 공용 미니맵(오픈스트리트맵/osmdroid — 키·결제 계정 불필요, "월 0원" 유지).
 * 우리 위치·주변 탭이 함께 쓴다. 상세 탐색은 각 화면의 "지도" 버튼이 기기 지도 앱을
 * 여는 이원 구조 — 미니맵은 "한눈에 어디쯤"까지만 책임진다.
 *
 * 기본 줌 규칙:
 * - 핀 1개: [initialZoom](기본 17). OSM에서 가게·장소 명칭이 보이기 시작하는 레벨이
 *   16~17이라, "지도를 열자마자 주변 장소 이름이 읽히는" 상태로 시작한다.
 * - 핀 여러 개: 전부 보이게 BoundingBox로 맞추되, 핀들이 아주 가까우면(같은 골목)
 *   과도하게 확대되므로 [initialZoom]을 상한으로 되민다. 멀면 자연히 줌아웃된다.
 */
@Composable
fun OsmMiniMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    initialZoom: Double = 17.0,
) {
    if (pins.isEmpty()) return
    val shape = MaterialTheme.shapes.medium
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape),
        factory = { context ->
            configureOsmdroid(context)
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                // 미니맵은 훑어보는 용도라 줌 버튼을 그리지 않는다(멀티터치로 충분).
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                overlays.add(CopyrightOverlay(context))
            }
        },
        update = { map ->
            // 핀이 갱신될 때마다 마커만 다시 그린다. CopyrightOverlay는 남긴다.
            map.overlays.removeAll { it is Marker }
            pins.forEach { pin ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(pin.latitude, pin.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = pin.label
                    },
                )
            }
            val points = pins.map { GeoPoint(it.latitude, it.longitude) }
            if (points.size == 1) {
                map.controller.setZoom(initialZoom)
                map.controller.setCenter(points.first())
            } else {
                val box = BoundingBox.fromGeoPoints(points)
                map.post {
                    map.zoomToBoundingBox(box.increaseByScale(1.4f), false)
                    // 핀들이 한 골목 안이면 BoundingBox가 20레벨까지 파고든다 —
                    // 명칭 가독 레벨을 상한으로 되민다.
                    if (map.zoomLevelDouble > initialZoom) {
                        map.controller.setZoom(initialZoom)
                        map.controller.setCenter(box.centerWithDateLine)
                    }
                }
            }
            map.invalidate()
        },
    )
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // OSM 타일 서버 정책: 익명·기본 UA는 차단될 수 있다. 앱 식별자를 UA로 보낸다.
    config.userAgentValue = context.packageName
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
}
