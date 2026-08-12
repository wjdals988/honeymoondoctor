package com.jeongmin.honeymoondoctor.feature.reservation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.domain.model.Reservation
import com.jeongmin.honeymoondoctor.domain.model.ReservationStatus
import com.jeongmin.honeymoondoctor.domain.model.maskSecret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationListScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: ReservationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("예약함") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "예약 추가")
                }
            }
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            if (uiState.needsAttentionCount > 0) {
                Text(
                    text = "주의 증상: 확인이 필요한 예약 ${uiState.needsAttentionCount}건",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            ) {
                FilterChip(
                    selected = uiState.statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text("전체") },
                )
                ReservationStatus.entries.forEach { status ->
                    FilterChip(
                        selected = uiState.statusFilter == status,
                        onClick = { viewModel.setStatusFilter(status) },
                        label = { Text(status.labelKo) },
                    )
                }
            }

            if (uiState.reservations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "표시할 예약이 없습니다.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = FabSpacing.ContentBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.reservations, key = { it.id }) { reservation ->
                        ReservationCard(reservation = reservation, onClick = { onOpenDetail(reservation.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Neutral, onClick = onClick) {
        Text(
            text = reservationScheduleLabel(reservation) ?: "일시 미정",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = reservation.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (reservation.vendor.isNotBlank() && reservation.vendor != reservation.title) {
            Text(
                text = reservation.vendor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(reservation.type.display) })
            AssistChip(onClick = {}, label = { Text(reservation.status.labelKo) })
        }
        // 목록에서는 예약번호·PIN을 항상 마스킹한다(스펙 7-4). 원문은 상세 화면에서만.
        maskSecret(reservation.confirmationCode)?.let { masked ->
            Text(
                text = "예약번호 $masked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 예약의 일시 요약: 종일이면 날짜 범위, 시간이 있으면 시간대 라벨과 함께. */
internal fun reservationScheduleLabel(reservation: Reservation): String? {
    val startAt = reservation.startAt ?: return null
    if (reservation.allDay) {
        val start = LocalTimes.toLocalDate(startAt, reservation.timeZone)
        val end = reservation.endAt?.let { LocalTimes.toLocalDate(it, reservation.effectiveEndTimeZone) }
        return if (end != null && end != start) {
            "${start.monthValue}/${start.dayOfMonth} ~ ${end.monthValue}/${end.dayOfMonth} (${koreanZoneLabel(reservation.timeZone)})"
        } else {
            "${start.monthValue}/${start.dayOfMonth} 종일"
        }
    }
    val startText = "${LocalTimes.formatDate(startAt, reservation.timeZone)} " +
        LocalTimes.formatTime(startAt, reservation.timeZone)
    val end = reservation.endAt ?: return "$startText (${koreanZoneLabel(reservation.timeZone)})"
    val endText = LocalTimes.formatTime(end, reservation.effectiveEndTimeZone)
    return if (reservation.effectiveEndTimeZone != reservation.timeZone) {
        "$startText(${koreanZoneLabel(reservation.timeZone)}) → $endText(${koreanZoneLabel(reservation.effectiveEndTimeZone)})"
    } else {
        "$startText – $endText"
    }
}
