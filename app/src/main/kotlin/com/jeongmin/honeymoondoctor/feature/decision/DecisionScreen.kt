package com.jeongmin.honeymoondoctor.feature.decision

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.core.ui.AppCard
import com.jeongmin.honeymoondoctor.core.ui.DropdownSelector
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.FabSpacing
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.SkeletonBlock
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.Decision
import com.jeongmin.honeymoondoctor.domain.model.DecisionCategory
import com.jeongmin.honeymoondoctor.domain.model.DecisionOption
import com.jeongmin.honeymoondoctor.domain.model.DecisionStatus
import com.jeongmin.honeymoondoctor.domain.repository.DecisionRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DecisionUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val decisions: List<Decision> = emptyList(),
    val actionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DecisionViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    private val decisionRepository: DecisionRepository,
) : ViewModel() {

    private val actionError = ActionErrorState()

    val uiState: StateFlow<DecisionUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(DecisionUiState(loading = false))
            } else {
                combine(
                    decisionRepository.observeDecisions(trip.id),
                    actionError.message,
                ) { decisions, error ->
                    DecisionUiState(
                        loading = false,
                        tripId = trip.id,
                        decisions = decisions.sortedBy { it.status == DecisionStatus.DECIDED },
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionUiState())

    fun clearActionError() = actionError.clear()

    fun selectOption(decision: Decision, option: DecisionOption) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("결정을 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                decisionRepository.update(
                    tripId,
                    decision.copy(selectedOptionId = option.id, status = DecisionStatus.DECIDED),
                )
            }
        }
    }

    fun setStatus(decision: Decision, status: DecisionStatus) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("결정 상태를 바꾸지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                decisionRepository.update(
                    tripId,
                    decision.copy(
                        status = status,
                        // 결정 취소 시 선택 옵션도 함께 해제한다
                        selectedOptionId = if (status == DecisionStatus.DECIDED) decision.selectedOptionId else null,
                    ),
                )
            }
        }
    }

    fun save(decision: Decision) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("결정 항목을 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                if (decision.id.isEmpty()) {
                    decisionRepository.create(tripId, decision.copy(id = "decision-${UUID.randomUUID()}"))
                } else {
                    decisionRepository.update(tripId, decision)
                }
            }
        }
    }

    fun delete(decision: Decision) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("결정 항목을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                decisionRepository.delete(tripId, decision.id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionScreen(
    onNavigateBack: () -> Unit,
    viewModel: DecisionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var editorTarget by remember { mutableStateOf<Decision?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Decision?>(null) }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("결정함") },
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
                    Icon(Icons.Filled.Add, contentDescription = "결정 항목 추가")
                }
            }
        },
    ) { innerPadding ->
        if (uiState.loading) {
            DecisionSkeleton(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        if (uiState.decisions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "결정할 항목이 없습니다.",
                    description = "숙소·식당처럼 둘이 정해야 할 것을 올리고 투표로 정합니다.",
                    action = if (LocalTripReadOnly.current) {
                        null
                    } else {
                        {
                            FilledTonalButton(onClick = {
                                editorTarget = null
                                showEditor = true
                            }) { Text("결정 항목 추가") }
                        }
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = FabSpacing.ContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.decisions, key = { it.id }) { decision ->
                    DecisionCard(
                        decision = decision,
                        onSelectOption = { option -> viewModel.selectOption(decision, option) },
                        onSetStatus = { status -> viewModel.setStatus(decision, status) },
                        onEdit = {
                            editorTarget = decision
                            showEditor = true
                        },
                        onDelete = { deleteTarget = decision },
                    )
                }
            }
        }
    }

    if (showEditor) {
        DecisionEditorDialog(
            original = editorTarget,
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
            title = { Text("결정 항목 삭제") },
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

/** 백로그 3-1f: 결정 카드 몇 장을 흉내낸 스켈레톤. */
@Composable
private fun DecisionSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth(), height = 100.dp) }
    }
}

@Composable
private fun DecisionCard(
    decision: Decision,
    onSelectOption: (DecisionOption) -> Unit,
    onSetStatus: (DecisionStatus) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = decision.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit) { Text("수정") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "${decision.title} 삭제")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, label = { Text(decision.category.labelKo) })
            AssistChip(onClick = {}, label = { Text(decision.status.labelKo) })
        }
        decision.options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = decision.selectedOptionId == option.id,
                    onClick = { onSelectOption(option) },
                )
                Text(option.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        decision.notes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (decision.status == DecisionStatus.DECIDED) {
            TextButton(onClick = { onSetStatus(DecisionStatus.NEEDS_DECISION) }) { Text("결정 취소") }
        }
    }
}

@Composable
private fun DecisionEditorDialog(
    original: Decision?,
    onSave: (Decision) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var category by remember { mutableStateOf(original?.category ?: DecisionCategory.ETC) }
    var status by remember { mutableStateOf(original?.status ?: DecisionStatus.NEEDS_DECISION) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var options by remember { mutableStateOf(original?.options ?: emptyList()) }
    var newOptionText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "결정 항목 추가" else "결정 항목 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownSelector(
                    label = "카테고리",
                    selectedLabel = category.labelKo,
                    options = DecisionCategory.entries,
                    optionLabel = { it.labelKo },
                    onSelect = { category = it },
                )
                DropdownSelector(
                    label = "상태",
                    selectedLabel = status.labelKo,
                    options = DecisionStatus.entries,
                    optionLabel = { it.labelKo },
                    onSelect = { status = it },
                )
                options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(option.label, modifier = Modifier.weight(1f))
                        TextButton(onClick = { options = options.filterNot { it.id == option.id } }) {
                            Text("제거")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newOptionText,
                        onValueChange = { newOptionText = it },
                        label = { Text("후보 추가") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (newOptionText.isNotBlank()) {
                                options = options + DecisionOption(
                                    id = "opt-${UUID.randomUUID()}",
                                    label = newOptionText.trim(),
                                )
                                newOptionText = ""
                            }
                        },
                    ) { Text("추가") }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    onSave(
                        (original ?: Decision(id = "", title = "", category = category, status = status)).copy(
                            title = title.trim(),
                            category = category,
                            status = status,
                            options = options,
                            notes = notes.trim().ifEmpty { null },
                        ),
                    )
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
