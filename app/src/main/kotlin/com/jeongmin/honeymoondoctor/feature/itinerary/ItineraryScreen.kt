package com.jeongmin.honeymoondoctor.feature.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.copyToClipboard
import com.jeongmin.honeymoondoctor.core.ui.openGoogleMapsDirections
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ItineraryScreen(
    onOpenEditor: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** 홈 오버뷰에서 날짜 줄을 눌러 들어온 경우 그 날짜(ISO-8601). 없으면 맨 위부터. */
    focusDate: String? = null,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<ItineraryItem?>(null) }
    val listState = rememberLazyListState()

    // 날짜별 헤더가 목록 몇 번째인지 세어 그 위치로 보낸다. 헤더 1 + 종일 일정 + (빈 날이면 1)
    // + 시각 일정 순으로 쌓이는 아래 구조와 같은 순서로 계산한다 — 구조를 바꾸면 여기도 바뀐다.
    LaunchedEffect(focusDate, uiState.days) {
        val target = focusDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@LaunchedEffect
        var index = 0
        for (day in uiState.days) {
            if (day.date == target) {
                listState.scrollToItem(index)
                return@LaunchedEffect
            }
            index += 1 + day.allDayItems.size + day.timedItems.size +
                if (day.timedItems.isEmpty() && day.allDayItems.isEmpty()) 1 else 0
        }
    }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val pendingUndo by viewModel.undoDelete.pending.collectAsState()
    UndoDeleteSnackbarEffect(
        hostState = snackbarHostState,
        pending = pendingUndo,
        onUndo = viewModel::restoreDeleted,
        onDismissed = viewModel.undoDelete::dismiss,
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = { onOpenEditor(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "일정 추가")
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.trip == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("여행 정보를 불러올 수 없습니다.") }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabSpacing.ContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.days.forEach { day ->
                    item(key = "header-${day.date}") {
                        DayHeader(day)
                    }
                    items(day.allDayItems.size, key = { i -> "allday-${day.allDayItems[i].id}" }) { i ->
                        ItineraryCard(
                            item = day.allDayItems[i],
                            isConflicting = false,
                            onEdit = onOpenEditor,
                            onSetStatus = viewModel::setStatus,
                            onDeleteRequest = { deleteTarget = it },
                        )
                    }
                    if (day.timedItems.isEmpty() && day.allDayItems.isEmpty()) {
                        item(key = "empty-${day.date}") {
                            EmptyState(title = "일정 없음")
                        }
                    }
                    items(day.timedItems.size, key = { i -> "timed-${day.timedItems[i].id}" }) { i ->
                        val item = day.timedItems[i]
                        ItineraryCard(
                            item = item,
                            isConflicting = item.id in uiState.conflictIds,
                            onEdit = onOpenEditor,
                            onSetStatus = viewModel::setStatus,
                            onDeleteRequest = { deleteTarget = it },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            item = target,
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DayHeader(day: ItineraryDay) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        day.dayNumber?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "D$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItineraryCard(
    item: ItineraryItem,
    isConflicting: Boolean,
    onEdit: (String) -> Unit,
    onSetStatus: (ItineraryItem, ItineraryStatus) -> Unit,
    onDeleteRequest: (ItineraryItem) -> Unit,
) {
    val context = LocalContext.current
    val dimmed = item.status != ItineraryStatus.PLANNED

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        tone = if (dimmed) CardTone.Done else CardTone.Neutral,
        onClick = { onEdit(item.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeLabel(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.status == ItineraryStatus.SKIPPED) TextDecoration.LineThrough else null,
                )
            }
            ItemMenu(
                item = item,
                onEdit = { onEdit(item.id) },
                onSetStatus = onSetStatus,
                onDeleteRequest = onDeleteRequest,
                onDirections = {
                    val destination = item.address?.takeIf { it.isNotBlank() }
                        ?: item.location?.takeIf { it.isNotBlank() }
                    destination?.let { openGoogleMapsDirections(context, it) }
                },
                onCopyAddress = {
                    item.address?.takeIf { it.isNotBlank() }?.let { copyToClipboard(context, "주소", it) }
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(item.type.labelKo) })
            if (item.status != ItineraryStatus.PLANNED) {
                AssistChip(onClick = {}, label = { Text(item.status.labelKo) })
            }
            if (isConflicting) {
                AssistChip(
                    onClick = {},
                    label = { Text("과로 경고: 시간 겹침") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "시간이 겹치는 일정",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }

        val details = buildList {
            item.location?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.estimatedKrw?.let { add("예상 ${NumberFormat.getNumberInstance(Locale.KOREA).format(it)}원") }
        }
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ItemMenu(
    item: ItineraryItem,
    onEdit: () -> Unit,
    onSetStatus: (ItineraryItem, ItineraryStatus) -> Unit,
    onDeleteRequest: (ItineraryItem) -> Unit,
    onDirections: () -> Unit,
    onCopyAddress: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 완료된 여행에서는 수정 계열 항목을 아예 내린다. 길찾기·주소 복사는 읽기 동작이라 남긴다.
    val readOnly = LocalTripReadOnly.current
    // 남는 항목이 하나도 없으면 점 세 개 버튼까지 감춘다(빈 메뉴가 열리는 걸 실기기에서 확인).
    val hasLocationActions = !item.address.isNullOrBlank() || !item.location.isNullOrBlank()
    if (readOnly && !hasLocationActions) return
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "${item.title} 일정 메뉴")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!readOnly && item.status != ItineraryStatus.DONE) {
                DropdownMenuItem(text = { Text("완료로 표시") }, onClick = {
                    expanded = false
                    onSetStatus(item, ItineraryStatus.DONE)
                })
            }
            if (!readOnly && item.status != ItineraryStatus.SKIPPED) {
                DropdownMenuItem(text = { Text("건너뜀으로 표시") }, onClick = {
                    expanded = false
                    onSetStatus(item, ItineraryStatus.SKIPPED)
                })
            }
            if (!readOnly && item.status != ItineraryStatus.PLANNED) {
                DropdownMenuItem(text = { Text("예정으로 되돌리기") }, onClick = {
                    expanded = false
                    onSetStatus(item, ItineraryStatus.PLANNED)
                })
            }
            if (!readOnly) {
                DropdownMenuItem(text = { Text("수정") }, onClick = {
                    expanded = false
                    onEdit()
                })
            }
            if (!item.address.isNullOrBlank() || !item.location.isNullOrBlank()) {
                DropdownMenuItem(text = { Text("Google Maps 길찾기") }, onClick = {
                    expanded = false
                    onDirections()
                })
            }
            if (!item.address.isNullOrBlank()) {
                DropdownMenuItem(text = { Text("주소 복사") }, onClick = {
                    expanded = false
                    onCopyAddress()
                })
            }
            if (!readOnly) {
                DropdownMenuItem(text = { Text("삭제") }, onClick = {
                    expanded = false
                    onDeleteRequest(item)
                })
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    item: ItineraryItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 스펙 4장: 삭제 전 연결 데이터 수를 보여주고, 연쇄 삭제 없이 참조만 해제한다.
    // Phase 4 시점에는 연결 가능한 데이터가 예약뿐이다(경비·장소는 이후 단계에서 추가).
    val linkedCount = if (item.reservationId != null) 1 else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 삭제") },
        text = {
            Text(
                if (linkedCount > 0) {
                    "\"${item.title}\" 일정에 연결된 예약 ${linkedCount}건이 있습니다.\n" +
                        "예약 자체는 삭제되지 않고 일정 연결만 해제됩니다.\n삭제할까요?"
                } else {
                    "\"${item.title}\" 일정을 삭제할까요?"
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun timeLabel(item: ItineraryItem): String {
    if (item.allDay) {
        val start = LocalTimes.toLocalDate(item.startAt, item.timeZone)
        val end = item.endAt?.let { LocalTimes.toLocalDate(it, item.effectiveEndTimeZone) }
        return if (end != null && end != start) {
            "종일 · ${start.monthValue}/${start.dayOfMonth} ~ ${end.monthValue}/${end.dayOfMonth}"
        } else {
            "종일"
        }
    }
    val startText = LocalTimes.formatTime(item.startAt, item.timeZone)
    val end = item.endAt ?: return "$startText (${koreanZoneLabel(item.timeZone)})"
    val endText = LocalTimes.formatTime(end, item.effectiveEndTimeZone)
    return if (item.effectiveEndTimeZone != item.timeZone) {
        // 출발·도착 시간대가 다른 항공 일정: 각 시각이 어느 시간대인지 명시한다
        val endDate = LocalTimes.toLocalDate(end, item.effectiveEndTimeZone)
        val startDate = LocalTimes.toLocalDate(item.startAt, item.timeZone)
        val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
        val plus = if (dayDiff > 0) "+$dayDiff " else ""
        "$startText(${koreanZoneLabel(item.timeZone)}) → $plus$endText(${koreanZoneLabel(item.effectiveEndTimeZone)})"
    } else {
        "$startText – $endText"
    }
}
