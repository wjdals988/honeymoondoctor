package com.jeongmin.honeymoondoctor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.usecase.NextItineraryUrgency
import java.time.Duration

@Composable
fun HomeScreen(
    onAddItinerary: () -> Unit,
    onOpenItineraryTab: () -> Unit,
    onOpenNearbyTab: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenReservations: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        uiState.trip == null -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("여행 정보를 불러올 수 없습니다.")
        }

        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeHeader(uiState)
            NextItineraryCard(uiState)
            if (uiState.conflictCount > 0) {
                ConflictWarningCard(count = uiState.conflictCount)
            }
            PreparationSummaryCard(
                uiState = uiState,
                onOpenReservations = onOpenReservations,
                onOpenChecklist = onOpenChecklist,
            )
            if (uiState.isDuringTrip) {
                SyncStatusFooter(uiState = uiState, onOpenSyncStatus = onOpenSyncStatus)
            }
            TodayTimelineSection(uiState)
            QuickActions(
                onAddItinerary = onAddItinerary,
                onOpenItineraryTab = onOpenItineraryTab,
                onOpenNearbyTab = onOpenNearbyTab,
                onAddExpense = onAddExpense,
                onOpenReservations = onOpenReservations,
                onOpenChecklist = onOpenChecklist,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeHeader(uiState: HomeUiState) {
    val trip = uiState.trip ?: return
    Column {
        Text(text = trip.name, style = MaterialTheme.typography.titleMedium)
        val dDay = uiState.dDayToStart
        if (dDay != null) {
            Text(
                text = if (dDay == 0L) "오늘 출발!" else "출발 D-$dDay",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${trip.startDate} ~ ${trip.endDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            uiState.currentCity?.let { city ->
                Text(
                    text = city.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "${LocalTimes.formatDate(uiState.now, uiState.displayZoneId)} · " +
                    "현지 ${LocalTimes.formatTime(uiState.now, uiState.displayZoneId)}" +
                    if (uiState.displayZoneId != "Asia/Seoul") {
                        " · 한국 ${LocalTimes.formatTime(uiState.now, "Asia/Seoul")}"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 홈에서 가장 큰 요소는 다음 일정이다(스펙 10장). */
@Composable
private fun NextItineraryCard(uiState: HomeUiState) {
    val snapshot = uiState.next ?: return
    val next = snapshot.next
    val ongoing = snapshot.ongoing

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            when {
                ongoing != null && next == null -> {
                    Text("진행 중", style = MaterialTheme.typography.labelLarge)
                    BigItineraryBody(ongoing, uiState)
                    ongoing.endAt?.let { end ->
                        Text(
                            text = "${LocalTimes.formatTime(end, ongoing.effectiveEndTimeZone)} " +
                                "(${koreanZoneLabel(ongoing.effectiveEndTimeZone)}) 종료 예정",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                next != null -> {
                    if (ongoing != null) {
                        Text(
                            text = "진행 중: ${ongoing.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("다음 일정", style = MaterialTheme.typography.labelLarge)
                    BigItineraryBody(next, uiState)
                    snapshot.remaining?.let { remaining ->
                        Text(
                            text = "${formatRemaining(remaining)} 남음",
                            style = MaterialTheme.typography.titleLarge,
                            color = urgencyColor(snapshot.urgency),
                        )
                    }
                }

                else -> {
                    Text("다음 일정", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "남은 일정이 없습니다",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigItineraryBody(item: ItineraryItem, uiState: HomeUiState) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val startDateText = LocalTimes.formatDate(item.startAt, item.timeZone)
    val startTimeText = LocalTimes.formatTime(item.startAt, item.timeZone)
    Text(
        text = "$startDateText $startTimeText (${koreanZoneLabel(item.timeZone)} 시각) · ${item.type.labelKo}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun urgencyColor(urgency: NextItineraryUrgency?): Color = when (urgency) {
    NextItineraryUrgency.WITHIN_1H -> MaterialTheme.colorScheme.error
    NextItineraryUrgency.WITHIN_3H -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun ConflictWarningCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "과로 경고: 일정 ${count}건의 시간이 겹칩니다",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TodayTimelineSection(uiState: HomeUiState) {
    val snapshot = uiState.next ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("오늘 타임라인", style = MaterialTheme.typography.titleMedium)

        if (snapshot.todayAllDay.isEmpty() && snapshot.todayTimed.isEmpty()) {
            Text(
                text = "오늘 등록된 일정이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        snapshot.todayAllDay.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "종일",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            }
        }
        snapshot.todayTimed.forEach { item ->
            val done = item.status != ItineraryStatus.PLANNED
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LocalTimes.formatTime(item.startAt, uiState.displayZoneId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = item.title + if (done) " (${item.status.labelKo})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 출발 전 준비 요약(스펙 7-2): 미완료 필수 준비물, 확인 필요 예약, 예산 현황. */
@Composable
private fun PreparationSummaryCard(
    uiState: HomeUiState,
    onOpenReservations: () -> Unit,
    onOpenChecklist: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("준비 현황", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (uiState.requiredChecklistIncomplete > 0) {
                        "미완료 필수 준비물 ${uiState.requiredChecklistIncomplete}개"
                    } else {
                        "필수 준비물 완료"
                    },
                    modifier = Modifier.weight(1f),
                    color = if (uiState.requiredChecklistIncomplete > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                TextButton(onClick = onOpenChecklist) { Text("준비물") }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (uiState.attentionReservationCount > 0) {
                        "주의 증상: 확인이 필요한 예약 ${uiState.attentionReservationCount}건"
                    } else {
                        "확인 필요 예약 없음"
                    },
                    modifier = Modifier.weight(1f),
                    color = if (uiState.attentionReservationCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                TextButton(onClick = onOpenReservations) { Text("예약함") }
            }
            val budgetText = if (uiState.totalBudgetKrw > 0) {
                val remaining = uiState.totalBudgetKrw - uiState.totalSpentKrw
                "예산 ${formatWon(uiState.totalBudgetKrw)} · 지출 ${formatWon(uiState.totalSpentKrw)} · " +
                    if (remaining >= 0) "잔여 ${formatWon(remaining)}" else "초과 ${formatWon(-remaining)}"
            } else {
                "지출 ${formatWon(uiState.totalSpentKrw)} · 예산 미설정"
            }
            Text(
                text = budgetText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 여행 중에만 표시(스펙 7-2): 오프라인 상태, 마지막 동기화 시각, 동기화 대기 변경 수. */
@Composable
private fun SyncStatusFooter(uiState: HomeUiState, onOpenSyncStatus: () -> Unit) {
    val status = uiState.syncStatus ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = buildString {
                append(if (status.isOnline) "온라인" else "오프라인")
                if (!status.isDemoMode) {
                    append(" · 동기화 대기 ${status.pendingChangeCount}건")
                    status.lastSyncAt?.let {
                        append(" · 마지막 동기화 ${LocalTimes.formatTime(it, "Asia/Seoul")}")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.isOnline) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSyncStatus) { Text("자세히") }
    }
}

@Composable
private fun QuickActions(
    onAddItinerary: () -> Unit,
    onOpenItineraryTab: () -> Unit,
    onOpenNearbyTab: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenReservations: () -> Unit,
    onOpenChecklist: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("빠른 실행", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilledTonalButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("지출 추가")
            }
            FilledTonalButton(onClick = onOpenReservations) { Text("예약함") }
            FilledTonalButton(onClick = onOpenChecklist) { Text("준비물") }
            FilledTonalButton(onClick = onOpenNearbyTab) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("주변")
            }
            FilledTonalButton(onClick = onAddItinerary) { Text("일정 추가") }
            FilledTonalButton(onClick = onOpenItineraryTab) { Text("일정 전체") }
        }
    }
}

private fun formatWon(amount: Long): String =
    "${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(amount)}원"

private fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    if (totalMinutes < 1) return "곧 시작"
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}일 ${hours}시간"
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}
