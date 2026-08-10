package com.jeongmin.honeymoondoctor.feature.checklist

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.domain.model.ChecklistCategory
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.TripMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editorTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChecklistItem?>(null) }

    Scaffold(
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
                    EmptyState(title = "표시할 준비물이 없습니다.")
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = { onToggle() },
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
            Text(
                text = "${item.category.labelKo} · $ownerLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("수정") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "${item.title} 삭제")
        }
    }
}

@Composable
private fun ChecklistEditorDialog(
    original: ChecklistItem?,
    members: List<TripMember>,
    onSave: (ChecklistItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var category by remember { mutableStateOf(original?.category ?: ChecklistCategory.ETC) }
    var ownerUid by remember { mutableStateOf(original?.ownerUid) }
    var required by remember { mutableStateOf(original?.required ?: false) }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            (original ?: ChecklistItem(id = "", title = "", category = category)).copy(
                                title = title.trim(),
                                category = category,
                                ownerUid = ownerUid,
                                required = required,
                            ),
                        )
                    }
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
