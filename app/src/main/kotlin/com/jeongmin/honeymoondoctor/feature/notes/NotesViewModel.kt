package com.jeongmin.honeymoondoctor.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.domain.model.TripNote
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripNoteRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val myUid: String? = null,
    /** 보낸 시각 오름차순 — 화면에서 위(과거)→아래(최신)로 읽힌다. */
    val notes: List<TripNote> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val actionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    private val tripNoteRepository: TripNoteRepository,
    tripRepository: TripRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val actionError = ActionErrorState()

    /** 삭제 되돌리기(이 앱의 기존 패턴). 같은 id로 다시 만들면 완전 복원이다. */
    val undoDelete = UndoDeleteState<TripNote>()

    val uiState: StateFlow<NotesUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(NotesUiState(loading = false))
            } else {
                combine(
                    tripNoteRepository.observeNotes(trip.id),
                    tripRepository.observeMembers(trip.id),
                    authRepository.currentUser,
                    actionError.message,
                ) { notes, members, user, error ->
                    NotesUiState(
                        loading = false,
                        tripId = trip.id,
                        myUid = user?.uid,
                        notes = notes,
                        members = members,
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun clearActionError() = actionError.clear()

    fun send(text: String) {
        val state = uiState.value
        val tripId = state.tripId ?: return
        val myUid = state.myUid ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val note = TripNote(
            id = "note-${UUID.randomUUID()}",
            senderUid = myUid,
            text = trimmed,
            createdAt = Instant.now(),
        )
        viewModelScope.launch {
            actionError.runReporting("쪽지를 보내지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                tripNoteRepository.send(tripId, note)
            }
        }
    }

    /**
     * 화면에 보이는 "상대가 보낸 읽지 않은 쪽지"를 전부 확인 처리한다.
     * 화면이 열려 있는 동안 새로 도착한 것도 다음 emission에서 처리된다.
     */
    fun markVisibleAsRead() {
        val state = uiState.value
        val tripId = state.tripId ?: return
        val myUid = state.myUid ?: return
        val unread = state.notes.filter { it.senderUid != myUid && it.readAt == null }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            // 하나가 실패해도 나머지는 계속 처리한다(오프라인 큐가 재시도를 맡는다).
            unread.forEach { note ->
                runCatching { tripNoteRepository.markRead(tripId, note.id) }
            }
        }
    }

    fun delete(note: TripNote) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            val deleted = actionError.runReporting("쪽지를 삭제하지 못했습니다.") {
                tripNoteRepository.delete(tripId, note.id)
            }
            if (deleted) undoDelete.offer(note, "쪽지를 삭제했습니다.")
        }
    }

    fun restoreDeleted() {
        val tripId = uiState.value.tripId ?: return
        val note = undoDelete.consume() ?: return
        viewModelScope.launch {
            actionError.runReporting("쪽지를 복원하지 못했습니다.") {
                tripNoteRepository.send(tripId, note.copy(readAt = null))
            }
        }
    }
}
