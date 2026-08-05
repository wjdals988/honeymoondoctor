package com.jeongmin.honeymoondoctor.feature.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val members: List<TripMember> = emptyList(),
    val items: List<ChecklistItem> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val requiredIncompleteCount: Int = 0,
    val requiredOnly: Boolean = false,
    val ownerFilter: OwnerFilter = OwnerFilter.All,
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
                ) { items, members, requiredOnlyValue, ownerFilterValue ->
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
                        members = members,
                        items = filtered,
                        completedCount = items.count { it.completed },
                        totalCount = items.size,
                        requiredIncompleteCount = items.count { it.required && !it.completed },
                        requiredOnly = requiredOnlyValue,
                        ownerFilter = ownerFilterValue,
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

    fun toggleCompleted(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        val toggled = if (item.completed) {
            item.copy(completed = false, completedAt = null)
        } else {
            item.copy(completed = true, completedAt = Instant.now())
        }
        viewModelScope.launch { checklistRepository.update(tripId, toggled) }
    }

    fun save(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch {
            if (item.id.isEmpty()) {
                checklistRepository.create(tripId, item.copy(id = "check-${UUID.randomUUID()}"))
            } else {
                checklistRepository.update(tripId, item)
            }
        }
    }

    fun delete(item: ChecklistItem) {
        val tripId = uiState.value.tripId ?: return
        viewModelScope.launch { checklistRepository.delete(tripId, item.id) }
    }
}
