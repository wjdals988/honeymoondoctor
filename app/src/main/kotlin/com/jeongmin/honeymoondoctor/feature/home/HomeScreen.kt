package com.jeongmin.honeymoondoctor.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SectionHeader
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import com.jeongmin.honeymoondoctor.domain.model.isReadOnly
import com.jeongmin.honeymoondoctor.domain.usecase.NextItineraryUrgency
import com.jeongmin.honeymoondoctor.domain.usecase.TripDaySummary
import java.time.Duration

@Composable
fun HomeScreen(
    onSwitchTrip: () -> Unit,
    onAddItinerary: () -> Unit,
    onOpenItineraryTab: () -> Unit,
    onOpenItineraryDate: (java.time.LocalDate) -> Unit,
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
            HomeHeader(uiState, onSwitchTrip = onSwitchTrip)

            // 출발 전에는 화면의 중심이 다르다. "다음 일정"은 대개 비어 있거나 몇 주 뒤라
            // 계획에 도움이 안 되고, 그때 필요한 건 "어느 날이 아직 비었나"다.
            // 여행 중·완료 후 배치는 종전 그대로 둔다(한 번에 다 바꾸면 어디가 어색한지 못 가린다).
            // 완료 여부를 먼저 본다. 출발일이 아직 미래인 여행을 완료 처리하면(계획을 접은
            // 경우) 출발 전 분기로 빠져 "출발 D-28"과 "비어 있습니다" 경고가 그대로 떴다.
            // 완료는 더 강한 상태다.
            val isCompleted = uiState.trip?.isReadOnly == true
            val isBeforeTrip = !isCompleted && uiState.dDayToStart != null
            if (isBeforeTrip) {
                PreparationSummaryCard(
                    uiState = uiState,
                    onOpenReservations = onOpenReservations,
                    onOpenChecklist = onOpenChecklist,
                )
                TripOverviewSection(
                    uiState = uiState,
                    onOpenItineraryTab = onOpenItineraryTab,
                    onOpenItineraryDate = onOpenItineraryDate,
                )
                if (uiState.conflictCount > 0) {
                    ConflictWarningCard(count = uiState.conflictCount)
                }
                // 출발 전에는 실제로 다음 일정이 있을 때만 보여준다. 일정이 하나도 없는
                // 계획 초기에 "남은 일정이 없습니다" 카드는 알려주는 게 없는 소음이다.
                val hasUpcoming = uiState.next?.let { it.next != null || it.ongoing != null } == true
                if (hasUpcoming) {
                    NextItineraryCard(uiState)
                }
            } else if (isCompleted) {
                // 완료 후에는 기록 열람이 목적이다. "다음 일정"은 없는 개념이라 숨기고
                // 전체 일정을 맨 위로 올린다.
                TripOverviewSection(
                    uiState = uiState,
                    onOpenItineraryTab = onOpenItineraryTab,
                    onOpenItineraryDate = onOpenItineraryDate,
                )
                PreparationSummaryCard(
                    uiState = uiState,
                    onOpenReservations = onOpenReservations,
                    onOpenChecklist = onOpenChecklist,
                )
            } else {
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
                // 여행 중 오버뷰는 오늘 타임라인 아래에 둔다 — 지금 당장은 오늘이 중요하고,
                // 그 다음 궁금한 게 "남은 날엔 뭐가 있나"다.
                TripOverviewSection(
                    uiState = uiState,
                    onOpenItineraryTab = onOpenItineraryTab,
                    onOpenItineraryDate = onOpenItineraryDate,
                )
            }
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
private fun HomeHeader(uiState: HomeUiState, onSwitchTrip: () -> Unit) {
    val trip = uiState.trip ?: return
    val isCompleted = trip.isReadOnly
    Column {
        // 여행 이름을 누르면 여행 목록으로. 전환은 자주 하는 동작이 아니라 별도 버튼 대신
        // 이미 보고 있는 이름 자체를 진입점으로 쓴다("지금 이 여행" → "다른 여행").
        TextButton(
            onClick = onSwitchTrip,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(text = trip.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "  여행 바꾸기 ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // 완료된 여행에 "출발 D-28"은 맞지 않다. 끝난 계획이므로 기간만 보여준다.
        val dDay = uiState.dDayToStart.takeIf { !isCompleted }
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
                // 이동일처럼 체류 기간이 겹치면 시계가 어느 도시 기준인지 알려준다.
                // 알려주지 않으면 시각이 갑자기 바뀐 이유를 알 수 없어 시계를 못 믿는다.
                if (uiState.overlappingCityCount > 1) {
                    Text(
                        text = "체류 기간이 겹치는 도시 ${uiState.overlappingCityCount}곳 중 " +
                            "${city.displayName} 기준입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.Highlight,
        shape = MaterialTheme.shapes.large,
    ) {
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.Warn,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        SectionHeader("오늘 타임라인")

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
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
            // 완료된 여행에서는 "추가" 계열만 내린다. 예약함·준비물·주변·일정 전체는
            // 화면 이동(읽기)이라 그대로 둔다 — 완료된 여행도 열람은 계속 해야 한다.
            if (!LocalTripReadOnly.current) {
                FilledTonalButton(
                    onClick = onAddExpense,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("지출 추가")
                }
            }
            FilledTonalButton(onClick = onOpenReservations) { Text("예약함") }
            FilledTonalButton(onClick = onOpenChecklist) { Text("준비물") }
            FilledTonalButton(onClick = onOpenNearbyTab) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("주변")
            }
            if (!LocalTripReadOnly.current) {
                FilledTonalButton(onClick = onAddItinerary) { Text("일정 추가") }
            }
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

/**
 * 여행 기간 전체를 날짜 한 줄씩 보여주는 오버뷰(출발 전 홈의 중심).
 *
 * 일정 탭을 옮겨 온 것이 아니라 축약판이다 — 시각·유형·충돌 경고·상태 변경은 계속 일정 탭이
 * 맡고, 여기서는 "며칠에 몇 건, 첫 일정은 무엇"만 본다. 비어 있는 날을 감추지 않는 것이
 * 이 섹션의 존재 이유다(계획의 구멍이 드러나야 한다).
 *
 * 기간이 길면 목록이 그만큼 길어지므로 앞의 [PREVIEW_DAYS]일만 펼치고 나머지는 접는다.
 */
@Composable
private fun TripOverviewSection(
    uiState: HomeUiState,
    onOpenItineraryTab: () -> Unit,
    onOpenItineraryDate: (java.time.LocalDate) -> Unit,
) {
    val days = uiState.tripDays
    if (days.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    val isCompleted = uiState.trip?.isReadOnly == true
    val today = remember(uiState.now, uiState.displayZoneId) {
        uiState.now.atZone(java.time.ZoneId.of(uiState.displayZoneId)).toLocalDate()
    }
    // 여행 중에는 오늘부터 펼친다. 이미 지나간 날을 위에 쌓아 두면 정작 남은 일정이
    // 화면 밖으로 밀려난다(출발 전·완료 후에는 처음부터가 맞다).
    val startIndex = if (uiState.isDuringTrip) {
        days.indexOfFirst { !it.date.isBefore(today) }.coerceAtLeast(0)
    } else {
        0
    }
    val shown = if (expanded) days else days.drop(startIndex).take(PREVIEW_DAYS)
    val hiddenCount = days.size - shown.size
    val emptyDayCount = days.count { it.itemCount == 0 }
    val totalItems = days.sumOf { it.itemCount }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "전체 일정",
            trailing = {
                TextButton(onClick = onOpenItineraryTab) {
                    Text(if (isCompleted) "일정 탭에서 보기" else "일정 탭에서 편집")
                }
            },
        )
        Text(
            text = when {
                // 완료된 여행에 "비어 있다"는 지적은 의미가 없다. 남은 건 기록이다.
                isCompleted -> "${days.size}일 동안 일정 ${totalItems}건을 기록했습니다."
                emptyDayCount == 0 -> "${days.size}일 모두 일정이 있습니다."
                else -> "${days.size}일 중 ${emptyDayCount}일이 비어 있습니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (!isCompleted && emptyDayCount > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            shown.forEach { day ->
                TripOverviewRow(
                    day = day,
                    isToday = uiState.isDuringTrip && day.date == today,
                    onClick = { onOpenItineraryDate(day.date) },
                )
            }
        }
        if (hiddenCount > 0 || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "접기" else "전체 ${days.size}일 보기")
            }
        }
    }
}

@Composable
private fun TripOverviewRow(day: TripDaySummary, isToday: Boolean, onClick: () -> Unit) {
    // 하루 동선을 "장소1 → 장소2 → 장소3"으로 잇는다. 건수만 세는 것보다 그날 무엇을
    // 하는지가 바로 보이고, 순서가 있으니 동선이 말이 되는지도 판단할 수 있다.
    val shownTitles = day.titles.take(ROUTE_PREVIEW_ITEMS)
    val restCount = day.itemCount - shownTitles.size

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.width(112.dp)) {
            Text(
                text = day.dayNumber?.let { "Day $it" }.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                // 여행 중에는 오늘 줄을 강조한다. 목록에서 "지금 어디쯤"을 못 찾으면
                // 날짜를 세어 가며 읽어야 한다.
                color = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = day.date.format(overviewDateFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (day.itemCount == 0) {
            Text(
                text = "일정 없음",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = shownTitles.joinToString("  →  "),
                style = MaterialTheme.typography.bodyMedium,
                // 세 줄까지 감싼다. 하루 3~5건이면 대부분 온전히 들어가고, 그보다 많으면
                // 오른쪽 "+N"이 몇 개가 더 있는지 알려준다.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (restCount > 0) {
                // "+N"을 본문 문자열에 넣으면 줄 수를 넘길 때 그것부터 잘려 나가, 정작
                // "더 있다"는 사실이 사라진다. 별도 칸에 두어 항상 보이게 한다.
                Text(
                    text = "+$restCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** 동선에 이어 붙일 최대 일정 수. 넘치면 "+N"으로 접는다(세 줄까지 감싸므로 5개는 들어간다). */
private const val ROUTE_PREVIEW_ITEMS = 5

/** 오버뷰에서 앞쪽 며칠을 펼쳐 둘지. 7일이면 한 주가 한눈에 들어온다. */
private const val PREVIEW_DAYS = 7

private val overviewDateFormatter = java.time.format.DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN)
