package com.jeongmin.honeymoondoctor.feature.itinerary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.time.LocalTimes
import com.jeongmin.honeymoondoctor.core.time.koreanZoneLabel
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.CardTone
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SkeletonBar
import com.jeongmin.honeymoondoctor.core.ui.SkeletonBlock
import com.jeongmin.honeymoondoctor.core.ui.TabHeader
import com.jeongmin.honeymoondoctor.core.ui.confirm
import com.jeongmin.honeymoondoctor.core.ui.copyToClipboard
import com.jeongmin.honeymoondoctor.core.ui.openGoogleMapsDirections
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import com.jeongmin.honeymoondoctor.domain.model.ItineraryStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItineraryScreen(
    onOpenEditor: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** 홈 오버뷰에서 날짜 줄을 눌러 들어온 경우 그 날짜(ISO-8601). 없으면 맨 위부터. */
    focusDate: String? = null,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var deleteTarget by remember { mutableStateOf<ItineraryItem?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 날짜별 헤더가 목록 몇 번째인지 세어 그 위치로 보낸다(날짜 칩을 눌렀을 때도 같은
    // 계산을 쓴다 — indexOfDay). 구조를 바꾸면 그 계산도 같이 바뀐다.
    LaunchedEffect(focusDate, uiState.days) {
        val target = focusDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@LaunchedEffect
        indexOfDay(uiState.days, target)?.let { listState.scrollToItem(it) }
    }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)

    // 알림 권한(Android 13+)을 일정 탭 최초 진입에서 딱 한 번 요청한다. 일정 리마인더가
    // 알림의 주 소비자인데, 종전에는 동기화 상태 화면에만 요청이 있어 그 화면을 열지 않은
    // 사용자는 권한이 영영 없었다 — Worker가 정시에 돌아도 알림이 조용히 버려졌다
    // (에뮬레이터 실측: importance=NONE으로 미게시). 거부하면 다시 조르지 않는다 —
    // 동기화 상태 화면의 스위치가 재요청 경로로 남아 있다.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 결과와 무관하게 알림 없이도 앱은 동작한다 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
            uiState.loading -> ItinerarySkeleton(modifier = Modifier.padding(innerPadding))

            uiState.trip == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("여행 정보를 불러올 수 없습니다.") }

            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                TabHeader(
                    Icons.AutoMirrored.Filled.List,
                    "일정",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabSpacing.ContentBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 그냥 스크롤만 되는 목록이라는 피드백("밋밋함") — 여행이 길어지면
                    // "그날이 몇 번째 화면인지" 감으로 스크롤해야 했다. 날짜 칩을
                    // 스크롤 상단에 고정해(stickyHeader) 항상 눌러서 바로 이동할 수 있게
                    // 한다(Wanderlog·TripIt 등 여행 일정 앱의 표준 패턴).
                    stickyHeader {
                        DayChipRow(
                            days = uiState.days,
                            today = LocalDate.now(),
                            onSelectDay = { date ->
                                indexOfDay(uiState.days, date)?.let { index ->
                                    coroutineScope.launch { listState.animateScrollToItem(index) }
                                }
                            },
                        )
                    }
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
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            item = target,
            onConfirm = {
                haptic.confirm()
                viewModel.delete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 백로그 3-1f: 날짜 칩 줄 + 날짜별 그룹 몇 개를 흉내낸 스켈레톤. */
@Composable
private fun ItinerarySkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { SkeletonBlock(Modifier.width(76.dp), height = 32.dp, shape = MaterialTheme.shapes.small) }
        }
        repeat(3) {
            SkeletonBar(Modifier.fillMaxWidth(0.3f), height = 16.dp)
            SkeletonBlock(Modifier.fillMaxWidth(), height = 64.dp)
        }
    }
}

/**
 * [day]가 [ItineraryScreen]의 `LazyColumn`에서 몇 번째 항목인지 센다. 헤더 1 +
 * 종일 일정 + (그 날이 완전히 비었으면 자리채움 1) + 시각 일정 순으로 쌓이는 실제
 * 목록 구조와 반드시 같은 순서여야 한다 — 목록 구조를 바꾸면 이 계산도 같이 바꾼다.
 */
private fun indexOfDay(days: List<ItineraryDay>, target: LocalDate): Int? {
    var index = 0
    for (day in days) {
        if (day.date == target) return index
        index += 1 + day.allDayItems.size + day.timedItems.size +
            if (day.timedItems.isEmpty() && day.allDayItems.isEmpty()) 1 else 0
    }
    return null
}

private val dayChipDateFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 날짜 칩 가로 스크롤러(백로그 피드백: 일정 탭이 "밋밋하다"·상단에 뭔가 있어야 한다).
 * Wanderlog·TripIt류 여행 일정 앱의 표준 패턴을 반영했다 — 여행이 길어질수록
 * "그날이 몇 번째 화면인지" 감으로 스크롤하지 않고 탭 한 번으로 이동할 수 있어야 한다.
 * 오늘(여행 중일 때)은 강조해 "지금 어디쯤"도 함께 알려준다.
 */
@Composable
private fun DayChipRow(
    days: List<ItineraryDay>,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            FilterChip(
                selected = isToday,
                onClick = { onSelectDay(day.date) },
                label = {
                    Text(
                        buildString {
                            day.dayNumber?.let { append("D$it · ") }
                            append(day.date.format(dayChipDateFormatter))
                        },
                    )
                },
            )
        }
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
