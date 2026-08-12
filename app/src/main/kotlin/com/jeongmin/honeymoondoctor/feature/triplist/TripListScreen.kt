package com.jeongmin.honeymoondoctor.feature.triplist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 로그인 후 처음 만나는 화면. 내 여행을 고르거나 새로 만든다.
 *
 * v0.1.6에서 추가했다. 그전에는 여행이 계정당 하나뿐이라 로그인하면 곧바로 5탭으로
 * 들어갔고, 지난 여행을 다시 보거나 다음 여행을 미리 계획할 방법이 없었다.
 *
 * "진행 중"과 "지난 여행"을 탭으로 나눈다 — 지난 여행은 참고용으로 가끔 열어보는 것이라
 * 계획 중인 여행과 같은 목록에 섞이면 지금 챙겨야 할 것이 묻힌다.
 */
@Composable
fun TripListScreen(
    trips: List<Trip>,
    userDisplayName: String,
    onSelectTrip: (String) -> Unit,
    onCreateTrip: () -> Unit,
    modifier: Modifier = Modifier,
    /** 5탭에서 들어온 경우에만 뒤로가기를 보여준다(로그인 직후에는 돌아갈 곳이 없다). */
    onNavigateBack: (() -> Unit)? = null,
) {
    val (ongoing, past) = remember(trips) { trips.partition { !it.isReadOnly } }
    var selectedTab by remember { mutableIntStateOf(0) }
    val shown = if (selectedTab == 0) ongoing else past

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = if (onNavigateBack == null) 12.dp else 0.dp)) {
                Text("내 여행", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${userDisplayName}님, 어떤 여행을 볼까요?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(top = 8.dp)) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("진행 중 ${ongoing.size}") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("지난 여행 ${past.size}") },
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        title = if (selectedTab == 0) {
                            "진행 중인 여행이 없습니다."
                        } else {
                            "완료한 여행이 아직 없습니다."
                        },
                        description = if (selectedTab == 0) {
                            "아래 버튼으로 새 여행을 만들어 보세요."
                        } else {
                            "여행을 완료 처리하면 여기에 모입니다."
                        },
                    )
                }
            }
            items(shown.size) { index ->
                val trip = shown[index]
                TripCard(trip = trip, onClick = { onSelectTrip(trip.id) })
            }
        }

        Button(
            onClick = onCreateTrip,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("새 여행 만들기 · 초대코드로 참여") }
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    val status = remember(trip) { tripStatusLabel(trip) }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (trip.isReadOnly) CardTone.Done else CardTone.Neutral,
        onClick = onClick,
    ) {
        Text(trip.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${trip.startDate} ~ ${trip.endDate}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = if (trip.isReadOnly) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "구성원 ${trip.memberIds.size}/2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 목록에서 한눈에 상태를 읽게 한다. 날짜가 깨져 있어도 카드가 비지 않도록 기본 문구를 준다. */
private fun tripStatusLabel(trip: Trip): String {
    if (trip.isReadOnly) return if (trip.isPublic) "완료 · 공개 중" else "완료"
    val start = runCatching { LocalDate.parse(trip.startDate) }.getOrNull() ?: return "진행 중"
    val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull() ?: return "진행 중"
    val today = LocalDate.now()
    return when {
        today.isBefore(start) -> {
            val days = ChronoUnit.DAYS.between(today, start)
            if (days == 0L) "오늘 출발!" else "출발 D-$days"
        }
        today.isAfter(end) -> "기간 종료 — 완료 처리하지 않음"
        else -> {
            val day = ChronoUnit.DAYS.between(start, today) + 1
            "여행 중 · ${day}일차"
        }
    }
}
