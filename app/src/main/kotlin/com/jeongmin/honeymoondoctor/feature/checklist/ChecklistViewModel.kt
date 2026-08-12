package com.jeongmin.honeymoondoctor.feature.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.UndoDeleteState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.domain.model.ChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.ChecklistRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 담당 필터: 전체 / 공용 / 특정 구성원 */
sealed interface OwnerFilter {
    data object All : OwnerFilter
    data object Shared : OwnerFilter
    data class Member(val uid: String) : OwnerFilter
}

data class ChecklistUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    /** 출발일(ISO-8601). 기한 프리셋("출발 전날" 등)의 기준. 파싱 실패 시 null. */
    val tripStartDate: String? = null,
    val members: List<TripMember> = emptyList(),
    val items: List<ChecklistItem> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val requiredIncompleteCount: Int = 0,
    val requiredOnly: Boolean = false,
    val ownerFilter: OwnerFilter = OwnerFilter.All,
    val actionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    private val checklistRepository: ChecklistRepository,
) : ViewModel() {

    private val requiredOnly = MutableStateFlow(false)
    private val ownerFilter = MutableStateFlow<OwnerFilter>(OwnerFilter.All)
    private val actionError = ActionErrorState()

    /** 삭제 되돌리기. 화면이 pending을 구독해 스낵바를 띄운다. */
    val undoDelete = UndoDeleteState<ChecklistItem>()

    val uiState: StateFlow<ChecklistUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(ChecklistUiState(loading = false))
            } else {
                combine(
                    checklistRepository.observeChecklist(trip.id),
                    tripRepository.observeMembers(trip.id),
                    requiredOnly,
                    ownerFilter,
                    actionError.message,
                ) { items, members, requiredOnlyValue, ownerFilterValue, error ->
                    val filtered = items
                        .filter { if (requiredOnlyValue) it.required else true }
                        .filter { item ->
                            when (ownerFilterValue) {
                                OwnerFilter.All -> true
                                OwnerFilter.Shared -> item.ownerUid == null
                                is OwnerFilter.Member -> item.ownerUid == ownerFilterValue.uid
                            }
                        }
                        .sortedWith(compareBy({ it.completed }, { !it.required }, { it.title }))
                    ChecklistUiState(
                        loading = false,
                        tripId = trip.id,
                        tripStartDate = trip.startDate,
                        members = members,
                        items = filtered,
                        completedCount = items.count { it.completed },
                        totalCount = items.size,
                        requiredIncompleteCount = items.count { it.required && !it.completed },
                        requiredOnly = requiredOnlyValue,
                        ownerFilter = ownerFilterValue,
                        actionError = error,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistUiState())

    fun setRequiredOnly(value: Boolean) {
        requiredOnly.value = value
    }

    fun setOwnerFilter(filter: OwnerFilter) {
        ownerFilter.value = filter
    }

    fun clearActionError() = actionError.clear()

    fun toggleCompleted(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        val toggled = if (item.completed) {
            item.copy(completed = false, completedAt = null)
        } else {
            item.copy(completed = true, completedAt = Instant.now())
        }
        viewModelScope.launch {
            actionError.runReporting("준비물 상태를 바꾸지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                checklistRepository.update(tripId, toggled)
            }
        }
    }

    fun save(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            actionError.runReporting("준비물을 저장하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                if (item.id.isEmpty()) {
                    checklistRepository.create(tripId, item.copy(id = "check-${UUID.randomUUID()}"))
                } else {
                    checklistRepository.update(tripId, item)
                }
            }
        }
    }

    fun delete(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            val deleted = actionError.runReporting("준비물을 삭제하지 못했습니다. 완료된 여행은 수정할 수 없습니다.") {
                checklistRepository.delete(tripId, item.id)
            }
            if (deleted) undoDelete.offer(item, "준비물을 삭제했습니다.")
        }
    }

    /** 되돌리기: 같은 id로 다시 만들면 완전 복원이다(문서 id를 클라이언트가 정한다). */
    fun restoreDeleted() {
        val tripId = uiState.value.tripId ?: return
        val item = undoDelete.consume() ?: return
        viewModelScope.launch {
            actionError.runReporting("준비물을 복원하지 못했습니다.") {
                checklistRepository.create(tripId, item)
            }
        }
    }
}
