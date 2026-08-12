package com.jeongmin.honeymoondoctor.feature.checklist

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.DateField
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SearchField
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.confirm
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var editorTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val pendingUndo by viewModel.undoDelete.pending.collectAsState()
    UndoDeleteSnackbarEffect(
        hostState = snackbarHostState,
        pending = pendingUndo,
        onUndo = viewModel::restoreDeleted,
        onDismissed = viewModel.undoDelete::dismiss,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("준비물 — 출국 전 처방 체크") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!LocalTripReadOnly.current) {
                FloatingActionButton(onClick = {
                    editorTarget = null
                    showEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "준비물 추가")
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
            val progress = if (uiState.totalCount == 0) 0f else uiState.completedCount.toFloat() / uiState.totalCount
            Text(
                text = "완료율 ${uiState.completedCount}/${uiState.totalCount}" +
                    if (uiState.requiredIncompleteCount > 0) " · 필수 미완료 ${uiState.requiredIncompleteCount}건" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                placeholder = "준비물 검색",
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = uiState.requiredOnly,
                    onClick = { viewModel.setRequiredOnly(!uiState.requiredOnly) },
                    label = { Text("필수만") },
                )
                FilterChip(
                    selected = uiState.ownerFilter == OwnerFilter.All,
                    onClick = { viewModel.setOwnerFilter(OwnerFilter.All) },
                    label = { Text("전체") },
                )
                FilterChip(
                    selected = uiState.ownerFilter == OwnerFilter.Shared,
                    onClick = { viewModel.setOwnerFilter(OwnerFilter.Shared) },
                    label = { Text("공용") },
                )
                uiState.members.forEach { member ->
                    FilterChip(
                        selected = (uiState.ownerFilter as? OwnerFilter.Member)?.uid == member.uid,
                        onClick = { viewModel.setOwnerFilter(OwnerFilter.Member(member.uid)) },
                        label = { Text(member.displayName) },
                    )
                }
            }

            if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "표시할 준비물이 없습니다.",
                        action = if (LocalTripReadOnly.current) {
                            null
                        } else {
                            {
                                FilledTonalButton(onClick = {
                                    editorTarget = null
                                    showEditor = true
                                }) { Text("준비물 추가") }
                            }
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp,
                        bottom = FabSpacing.ContentBottomPadding,
                    ),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        ChecklistRow(
                            item = item,
                            members = uiState.members,
                            onToggle = { viewModel.toggleCompleted(item) },
                            onEdit = {
                                editorTarget = item
                                showEditor = true
                            },
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ChecklistEditorDialog(
            original = editorTarget,
            members = uiState.members,
            tripStartDate = uiState.tripStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onSave = {
                viewModel.save(it)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("준비물 삭제") },
            text = { Text("\"${target.title}\" 항목을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.confirm()
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    members: List<TripMember>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // 체크 순간의 촉각 피드백(백로그 3-3o) — 화면을 보지 않아도 "체크됐다"를 손끝으로 안다.
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = {
                haptic.confirm()
                onToggle()
            },
            enabled = !LocalTripReadOnly.current,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                )
                if (item.required) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "필수",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val ownerLabel = when (item.ownerUid) {
                null -> "공용"
                else -> members.firstOrNull { it.uid == item.ownerUid }?.displayName ?: "구성원"
            }
            val dueDate = item.dueAt?.atZone(ZoneId.of("Asia/Seoul"))?.toLocalDate()
            // 기한이 지났는데 미완료면 색만 바꾸지 않고 "지남"을 글자로도 적는다 —
            // 색약 사용자에게 색은 신호가 아니다(BACKLOG 3-2k).
            val overdue = dueDate != null && !item.completed && dueDate.isBefore(LocalDate.now())
            Text(
                text = buildString {
                    append("${item.category.labelKo} · $ownerLabel")
                    if (dueDate != null) {
                        append(" · 기한 ${dueDate.monthValue}/${dueDate.dayOfMonth}")
                        if (overdue) append(" (지남)")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        // 완료된 여행에서는 수정·삭제 자체를 내린다(체크박스만 비활성으로는 부족했다).
        if (!LocalTripReadOnly.current) {
            TextButton(onClick = onEdit) { Text("수정") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "${item.title} 삭제")
            }
        }
    }
}

@Composable
private fun ChecklistEditorDialog(
    original: ChecklistItem?,
    members: List<TripMember>,
    tripStartDate: LocalDate?,
    onSave: (ChecklistItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var category by remember { mutableStateOf(original?.category ?: ChecklistCategory.ETC) }
    var ownerUid by remember { mutableStateOf(original?.ownerUid) }
    var required by remember { mutableStateOf(original?.required ?: false) }
    // 기한은 날짜만 다룬다. 시각까지 받으면 입력이 번거로운데 "언제까지 준비"에 시각이
    // 의미 있는 경우가 거의 없다. 저장은 지출 날짜와 같은 정오(KST) 규칙(경계 안전).
    var dueDate by remember {
        mutableStateOf(original?.dueAt?.atZone(ZoneId.of("Asia/Seoul"))?.toLocalDate())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "준비물 추가" else "준비물 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("항목 이름 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownSelector(
                    label = "카테고리",
                    selectedLabel = category.labelKo,
                    options = ChecklistCategory.entries,
                    optionLabel = { it.labelKo },
                    onSelect = { category = it },
                )
                DropdownSelector(
                    label = "담당",
                    selectedLabel = when (ownerUid) {
                        null -> "공용"
                        else -> members.firstOrNull { it.uid == ownerUid }?.displayName ?: "구성원"
                    },
                    options = listOf<TripMember?>(null) + members,
                    optionLabel = { it?.displayName ?: "공용" },
                    onSelect = { ownerUid = it?.uid },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("필수 항목", modifier = Modifier.weight(1f))
                    Switch(checked = required, onCheckedChange = { required = it })
                }

                // 기한은 출발일 기준 프리셋으로 채우는 게 대부분이라 칩을 먼저 내민다.
                // (환전·유심·보험처럼 "출발 전 며칠"이 기준인 항목이 많다.)
                Text("기한 (선택)", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = dueDate == null,
                        onClick = { dueDate = null },
                        label = { Text("없음") },
                    )
                    if (tripStartDate != null) {
                        FilterChip(
                            selected = dueDate == tripStartDate.minusWeeks(1),
                            onClick = { dueDate = tripStartDate.minusWeeks(1) },
                            label = { Text("일주일 전") },
                        )
                        FilterChip(
                            selected = dueDate == tripStartDate.minusDays(1),
                            onClick = { dueDate = tripStartDate.minusDays(1) },
                            label = { Text("출발 전날") },
                        )
                    }
                    FilterChip(
                        selected = dueDate != null &&
                            dueDate != tripStartDate?.minusWeeks(1) &&
                            dueDate != tripStartDate?.minusDays(1),
                        onClick = { if (dueDate == null) dueDate = tripStartDate ?: LocalDate.now() },
                        label = { Text("직접 선택") },
                    )
                }
                if (dueDate != null) {
                    DateField(
                        label = "기한 날짜",
                        date = dueDate!!,
                        onDateChange = { dueDate = it },
                    )
                }
            }
        },
        confirmButton = {
            val haptic = LocalHapticFeedback.current
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        haptic.confirm()
                        onSave(
                            (original ?: ChecklistItem(id = "", title = "", category = category)).copy(
                                title = title.trim(),
                                category = category,
                                ownerUid = ownerUid,
                                required = required,
                                dueAt = dueDate?.atTime(12, 0)?.atZone(ZoneId.of("Asia/Seoul"))?.toInstant(),
                            ),
                        )
                    }
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
