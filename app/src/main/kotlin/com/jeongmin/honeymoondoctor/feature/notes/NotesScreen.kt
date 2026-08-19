package com.jeongmin.honeymoondoctor.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jeongmin.honeymoondoctor.core.ui.EmptyState
import com.jeongmin.honeymoondoctor.core.ui.LocalTripReadOnly
import com.jeongmin.honeymoondoctor.core.ui.UndoDeleteSnackbarEffect
import com.jeongmin.honeymoondoctor.core.ui.confirm
import com.jeongmin.honeymoondoctor.core.ui.rememberActionErrorSnackbar
import com.jeongmin.honeymoondoctor.domain.model.TripNote
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val noteTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)

/**
 * 쪽지함(삐삐 모델). 2인 대화라 받은함/보낸함으로 나누지 않고 시간순 한 흐름으로 보여준다.
 *
 * 이 화면이 열려 있는 동안 상대 쪽지는 자동으로 "확인함" 처리된다 — 읽음 시각은
 * 어디에도 노출하지 않는다(TripNote 문서 참고).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TripNote?>(null) }
    val snackbarHostState = rememberActionErrorSnackbar(uiState.actionError, viewModel::clearActionError)
    val pendingUndo by viewModel.undoDelete.pending.collectAsState()
    UndoDeleteSnackbarEffect(
        hostState = snackbarHostState,
        pending = pendingUndo,
        onUndo = viewModel::restoreDeleted,
        onDismissed = viewModel.undoDelete::dismiss,
    )

    // 화면에 보이는 상대 쪽지를 확인 처리 + 새 쪽지가 오면 맨 아래로 스크롤.
    LaunchedEffect(uiState.notes) {
        viewModel.markVisibleAsRead()
        if (uiState.notes.isNotEmpty()) {
            listState.animateScrollToItem(uiState.notes.lastIndex)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("쪽지함") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            if (uiState.notes.isEmpty() && !uiState.loading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "아직 주고받은 쪽지가 없습니다",
                        description = "보내 두면 상대가 앱을 열 때 확인합니다. 상대 앱이 켜져 있으면 " +
                            "바로 알림이 가지만, 꺼져 있으면 다음에 열 때 보입니다.",
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.notes.size, key = { i -> uiState.notes[i].id }) { index ->
                        val note = uiState.notes[index]
                        NoteBubble(
                            note = note,
                            isMine = note.senderUid == uiState.myUid,
                            senderName = uiState.members.firstOrNull { it.uid == note.senderUid }?.displayName,
                            onLongPress = { if (note.senderUid == uiState.myUid) deleteTarget = note },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (!LocalTripReadOnly.current) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 500) input = it },
                        placeholder = { Text("쪽지 남기기 (최대 500자)") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = {
                            haptic.confirm()
                            viewModel.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "쪽지 보내기")
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("쪽지 삭제") },
            text = { Text("이 쪽지를 삭제할까요? 상대 화면에서도 사라집니다.") },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteBubble(
    note: TripNote,
    isMine: Boolean,
    senderName: String?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        if (!isMine && senderName != null) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isMine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium,
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            // 읽은 "시각"은 일부러 보여주지 않는다 — 확인 여부만.
            text = buildString {
                append(noteTimeFormatter.format(note.createdAt.atZone(ZoneId.systemDefault())))
                if (isMine) append(if (note.readAt != null) " · 확인함" else " · 아직 안 봄")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
