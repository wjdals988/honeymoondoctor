package com.jeongmin.honeymoondoctor.feature.together

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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/**
 * 우리 위치 화면의 앱 내 미니맵. 오픈스트리트맵(osmdroid) — 키·결제 계정 없이 쓸 수 있어
 * "월 0원" 제약을 지킨다. 구글지도 SDK는 무료 티어여도 GCP 결제 등록이 필요해서 뺐고,
 * 상세 지도가 필요하면 카드의 "지도" 버튼이 기기 지도 앱(구글지도)을 연다 — 앱 내
 * 지도는 "서로 어디쯤인지 한눈에"까지만 책임진다.
 *
 * OSM 타일 정책 준수: userAgentValue를 앱 패키지로 설정(익명 UA 차단 대상),
 * CopyrightOverlay로 저작권 표기를 지도 위에 그린다.
 */
@Composable
fun TogetherMiniMap(
    locations: List<MemberLocationUi>,
    modifier: Modifier = Modifier,
) {
    if (locations.isEmpty()) return
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
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                )
                overlays.add(CopyrightOverlay(context))
            }
        },
        update = { map ->
            // 위치가 갱신될 때마다 마커를 다시 그린다. CopyrightOverlay(첫 번째)는 남긴다.
            map.overlays.removeAll { it is Marker }
            locations.forEach { member ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(member.location.latitude, member.location.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = if (member.isMe) "나" else member.displayName
                    },
                )
            }
            val points = locations.map { GeoPoint(it.location.latitude, it.location.longitude) }
            if (points.size == 1) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(points.first())
            } else {
                // 두 사람이 모두 보이게 테두리 여유를 두고 맞춘다.
                val box = BoundingBox.fromGeoPoints(points)
                map.post { map.zoomToBoundingBox(box.increaseByScale(1.6f), false) }
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
