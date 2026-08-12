package com.jeongmin.honeymoondoctor.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
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

/**
 * 미니맵에 찍을 핀 하나. [label]은 마커를 탭했을 때 말풍선으로 보이고, 사람 핀에서는
 * 첫 글자를 핀 안에도 그린다.
 *
 * 마커 모양은 우선순위로 하나만 고른다:
 * - [pinColor]가 있으면: 같은 물방울 핀 모양에 그 색만 다르게 칠한다 — "우리 위치"
 *   에서 나/상대를 구분하는 용도(파랑/빨강). Life360·Google 지도 위치 공유가 쓰는
 *   방식과 같다 — 사람 위치는 "같은 핀, 다른 색"이 표준이고, 장소처럼 종류가 여럿일
 *   때만 아이콘을 바꾼다.
 * - 없고 [emoji]가 있으면: 그 이모지가 핀을 대신한다 — 장소는 카테고리별로 종류가
 *   6개라 색만으로 구분하기 어렵다.
 * - 둘 다 없으면: osmdroid 기본 핀.
 */
data class MapPin(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val emoji: String? = null,
    val pinColor: androidx.compose.ui.graphics.Color? = null,
)

/**
 * "내 위치"를 나타내는 색. 지도가 등장하는 화면(우리 위치·주변 탭) 전체에서 같은 색으로
 * 통일한다 — 화면마다 다른 색이면 "내 위치"라는 의미를 새로 배워야 한다.
 */
val MyLocationPinColor = androidx.compose.ui.graphics.Color(0xFF1E88E5)

/** 상대(파트너) 위치 색. "우리 위치" 화면에서만 쓰인다. */
val PartnerLocationPinColor = androidx.compose.ui.graphics.Color(0xFFE53935)

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
                // 지도가 세로 스크롤 화면 안에 있어, 핀치줌·드래그가 부모 스크롤에
                // 먹히는 문제가 있었다. 지도에 손가락이 닿아 있는 동안만 부모의
                // 터치 가로채기를 끈다 — 지도 밖에서는 화면 스크롤이 평소대로 된다.
                blockParentScrollWhileTouched()
            }
        },
        update = { map ->
            // 핀이 갱신될 때마다 마커만 다시 그린다. CopyrightOverlay는 남긴다.
            map.overlays.removeAll { it is Marker }
            pins.forEach { pin ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(pin.latitude, pin.longitude)
                        title = pin.label
                        when {
                            pin.pinColor != null -> {
                                // 물방울 핀이라 뾰족한 끝이 좌표를 가리킨다 — 이모지와
                                // 달리 ANCHOR_BOTTOM.
                                icon = personPinDrawable(map.context, pin.pinColor, pin.label.firstOrNull())
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            pin.emoji != null -> {
                                // 이모지는 핀 모양이 아니라서 좌표 위에 중앙 정렬로 얹는다.
                                icon = emojiDrawable(map.context, pin.emoji)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            else -> setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
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

@SuppressLint("ClickableViewAccessibility")
private fun MapView.blockParentScrollWhileTouched() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                view.parent.requestDisallowInterceptTouchEvent(true)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.parent.requestDisallowInterceptTouchEvent(false)
        }
        false // 지도 자신의 팬·줌 처리는 그대로 진행한다
    }
}

/**
 * 사람 위치용 물방울 핀. 색만 다르고 모양은 항상 같다 — 요청대로 "같은 핀, 다른 색"
 * (Life360·Google 지도 위치 공유와 같은 관습). 안에는 흰 원 위에 이름 첫 글자를 적어
 * 지도만 봐도 누구인지 알 수 있게 한다(단순 흰 점보다 식별력이 낫다).
 */
private fun personPinDrawable(
    context: Context,
    color: androidx.compose.ui.graphics.Color,
    initial: Char?,
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val widthPx = (34 * density)
    val heightPx = (44 * density)
    val bitmap = Bitmap.createBitmap(widthPx.toInt(), heightPx.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = widthPx / 2f
    val strokeWidth = 1.5f * density
    val headRadius = widthPx / 2f - strokeWidth
    val headCenterY = headRadius + strokeWidth

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }
    // 원(머리) + 삼각형(꼬리)을 하나의 Path로 이어서 물방울 모양을 만든다.
    val path = Path().apply {
        addCircle(cx, headCenterY, headRadius, Path.Direction.CW)
        val tailHalfWidth = headRadius * 0.5f
        val tailStartY = headCenterY + headRadius * 0.8f
        moveTo(cx - tailHalfWidth, tailStartY)
        lineTo(cx, heightPx - strokeWidth)
        lineTo(cx + tailHalfWidth, tailStartY)
        close()
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawCircle(cx, headCenterY, headRadius, strokePaint)

    if (initial != null) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = AndroidColor.WHITE
            textSize = headRadius * 1.1f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = headCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial.uppercase(), cx, baseline, textPaint)
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** 이모지 한 글자를 마커 아이콘 비트맵으로 그린다. */
private fun emojiDrawable(context: Context, emoji: String): BitmapDrawable {
    val sizePx = (36 * context.resources.displayMetrics.density).toInt()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx * 0.8f
        textAlign = Paint.Align.CENTER
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(emoji, sizePx / 2f, baseline, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // OSM 타일 서버 정책: 익명·기본 UA는 차단될 수 있다. 앱 식별자를 UA로 보낸다.
    config.userAgentValue = context.packageName
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
}
