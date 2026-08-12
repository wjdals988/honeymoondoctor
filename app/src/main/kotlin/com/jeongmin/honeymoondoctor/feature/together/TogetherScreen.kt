package com.jeongmin.honeymoondoctor.feature.together

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.ChipSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.MapPin
import com.jeongmin.honeymoondoctor.core.ui.MyLocationPinColor
import com.jeongmin.honeymoondoctor.core.ui.OsmMiniMap
import com.jeongmin.honeymoondoctor.core.ui.PartnerLocationPinColor
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.data.local.prefs.LocationShareMode
import java.time.Duration
import java.time.Instant

/**
 * 우리 위치 — 구성원끼리 "지금 어디쯤?"에 답하는 화면.
 *
 * 상시 추적이 아니다. "내 위치 공유하기"를 누른 순간의 좌표 1건만 서버에 올라가고,
 * 화면에는 반드시 "언제 공유했는지"를 함께 보여준다 — 3시간 전 위치를 현재 위치처럼
 * 읽는 사고를 막는 것이 시각 표기의 존재 이유다. 지도는 SDK를 넣지 않고 geo: 인텐트로
 * 기기 지도 앱에 넘긴다(월 0원 유지 + 지도는 지도 앱이 더 잘한다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherScreen(
    onNavigateBack: () -> Unit,
    viewModel: TogetherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermission() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("우리 위치") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\"공유하기\"를 누른 순간의 위치만 상대에게 보입니다. 자동으로 따라다니지 " +
                    "않으며, 공유한 위치는 언제든 지울 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.locations.isEmpty()) {
                EmptyState(
                    title = "아직 공유된 위치가 없습니다",
                    description = "아래 버튼으로 내 위치를 공유하면 상대의 화면에 표시됩니다.",
                )
            } else {
                // 앱 내 미니맵(오픈스트리트맵). 상세 탐색은 카드의 "지도"가 지도 앱을 연다.
                // 두 사람이면 둘 다 보이게 맞추고, 가까우면 명칭 가독 레벨(17)로 되민다.
                OsmMiniMap(
                    pins = uiState.locations.map { member ->
                        MapPin(
                            latitude = member.location.latitude,
                            longitude = member.location.longitude,
                            label = if (member.isMe) "나" else member.displayName,
                            pinColor = personPinColor(member.isMe),
                        )
                    },
                )
            }

            uiState.locations.forEach { member ->
                MemberLocationCard(member = member, onClearMine = viewModel::clearMyLocation)
            }

            Spacer(Modifier.height(4.dp))

            // 자동 공유 모드. 셋 다 앱을 보고 있는 동안만 동작한다 — 백그라운드 추적
            // 코드 경로 자체가 없다. 5분 모드도 화면을 끄면 멈춘다.
            ChipSelector(
                label = "자동 공유",
                options = LocationShareMode.entries,
                selected = uiState.shareMode,
                optionLabel = {
                    when (it) {
                        LocationShareMode.MANUAL -> "끔 (버튼으로만)"
                        LocationShareMode.ON_APP_OPEN -> "앱 열 때"
                        LocationShareMode.EVERY_5_MIN_WHILE_USING -> "앱 사용 중 5분마다"
                    }
                },
                onSelect = viewModel::setShareMode,
            )
            if (uiState.shareMode != LocationShareMode.MANUAL) {
                Text(
                    text = "자동 공유도 앱이 화면에 떠 있는 동안만 동작합니다. 화면을 끄거나 다른 앱으로 " +
                        "가면 멈추고, 다음에 열 때 다시 시작됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.needsPermission) {
                AppCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Warn) {
                    Text("위치 권한이 필요합니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "내 위치를 공유하려면 위치 권한을 허용해 주세요. 상대 위치를 보는 데는 " +
                            "권한이 필요 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    FilledTonalButton(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }) { Text("위치 권한 허용") }
                }
            } else {
                Button(
                    onClick = viewModel::shareMyLocation,
                    enabled = !uiState.sharing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.sharing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.sharing) "위치 확인 중…" else "지금 내 위치 공유하기")
                }
            }
        }
    }
}

@Composable
private fun MemberLocationCard(member: MemberLocationUi, onClearMine: () -> Unit) {
    val context = LocalContext.current
    // 지도 핀과 같은 색의 점을 이름 앞에 찍어, 카드와 지도의 어느 마커가 누구인지를
    // 한 번에 연결한다(Life360·Google 지도 위치 공유가 쓰는 방식).
    val dotColor = personPinColor(member.isMe)
    // Life360 사용자들이 실제로 겪는 불만: "핀이 몇 시간째 그 자리인데 경고가 없다"
    // (family-tracker 앱 리뷰에서 반복 지적됨). 오래된 위치는 색+문구로 함께 알린다.
    val stale = Duration.between(member.location.sharedAt, Instant.now()).toHours() >= 3
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (member.isMe) CardTone.Neutral else CardTone.Highlight,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (member.isMe) "나 (${member.displayName})" else member.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = buildString {
                        append(formatAgo(member.location.sharedAt))
                        append(" 공유")
                        member.distanceMeters?.let { append(" · 나와 ${formatDistance(it)}") }
                        if (stale) append(" · 오래된 위치일 수 있어요")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.5f, %.5f".format(member.location.latitude, member.location.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                // geo: 인텐트 — 설치된 지도 앱(구글맵 등)이 연다. q= 라벨은 마커 이름.
                val uri = Uri.parse(
                    "geo:${member.location.latitude},${member.location.longitude}" +
                        "?q=${member.location.latitude},${member.location.longitude}" +
                        "(${Uri.encode(member.displayName)})",
                )
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) { Text("지도") }
            if (member.isMe) {
                TextButton(onClick = onClearMine) { Text("지우기") }
            }
        }
    }
}

/**
 * 나=파랑 / 상대=빨강. "같은 핀, 다른 색"으로 구분하는 관습(Life360·Google 지도
 * 위치 공유)을 따른다 — 이모지(⭐/❤️)보다 지도 위에서 더 작고 또렷하게 읽힌다.
 * 색 자체는 주변 탭의 "내 위치" 핀과 통일해서 쓴다(MyLocationPinColor).
 */
private fun personPinColor(isMe: Boolean): Color =
    if (isMe) MyLocationPinColor else PartnerLocationPinColor

/** "방금 · N분 전 · N시간 전 · 날짜" — 위치의 신선도가 이 화면의 핵심 정보다. */
private fun formatAgo(sharedAt: Instant): String {
    val elapsed = Duration.between(sharedAt, Instant.now())
    return when {
        elapsed.toMinutes() < 1 -> "방금"
        elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()}분 전"
        elapsed.toHours() < 24 -> "${elapsed.toHours()}시간 전"
        else -> LocalTimes.formatDate(sharedAt, "Asia/Seoul")
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${meters.toInt()}m"
    else -> "%.1fkm".format(meters / 1_000)
}
