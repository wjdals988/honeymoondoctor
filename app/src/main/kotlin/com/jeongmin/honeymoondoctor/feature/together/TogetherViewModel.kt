package com.jeongmin.honeymoondoctor.feature.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeongmin.honeymoondoctor.core.error.ActionErrorState
import com.jeongmin.honeymoondoctor.core.error.runReporting
import com.jeongmin.honeymoondoctor.core.location.LocationProvider
import com.jeongmin.honeymoondoctor.data.local.prefs.AppPreferences
import com.jeongmin.honeymoondoctor.data.local.prefs.LocationShareMode
import com.jeongmin.honeymoondoctor.domain.model.MemberLocation
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.MemberLocationRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import com.jeongmin.honeymoondoctor.domain.usecase.Haversine
import com.jeongmin.honeymoondoctor.domain.usecase.ObserveCurrentTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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

/** 상대(또는 나)의 마지막 공유 위치 + 화면 표시용 파생값. */
data class MemberLocationUi(
    val uid: String,
    val displayName: String,
    val location: MemberLocation,
    /** 내 마지막 위치와의 거리(m). 내 위치가 없으면 null. */
    val distanceMeters: Double?,
    val isMe: Boolean,
)

data class TogetherUiState(
    val loading: Boolean = true,
    val tripId: String? = null,
    val myUid: String? = null,
    val members: List<TripMember> = emptyList(),
    val locations: List<MemberLocationUi> = emptyList(),
    val sharing: Boolean = false,
    val actionError: String? = null,
    /** 위치 권한이 없어 공유 버튼 대신 안내를 보여줘야 하는 상태. */
    val needsPermission: Boolean = false,
    val shareMode: LocationShareMode = LocationShareMode.MANUAL,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TogetherViewModel @Inject constructor(
    observeCurrentTrip: ObserveCurrentTrip,
    tripRepository: TripRepository,
    private val memberLocationRepository: MemberLocationRepository,
    private val authRepository: AuthRepository,
    private val locationProvider: LocationProvider,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val actionError = ActionErrorState()
    private val sharing = MutableStateFlow(false)
    private val needsPermission = MutableStateFlow(!locationProvider.hasLocationPermission())

    val uiState: StateFlow<TogetherUiState> = observeCurrentTrip()
        .flatMapLatest { trip ->
            if (trip == null) {
                flowOf(TogetherUiState(loading = false))
            } else {
                combine(
                    tripRepository.observeMembers(trip.id),
                    memberLocationRepository.observeMemberLocations(trip.id),
                    authRepository.currentUser,
                    appPreferences.snapshot,
                    combine(sharing, needsPermission, actionError.message) { s, p, e -> Triple(s, p, e) },
                ) { members, locations, user, prefs, (sharingNow, permission, error) ->
                    val myUid = user?.uid
                    val mine = locations.firstOrNull { it.uid == myUid }
                    TogetherUiState(
                        loading = false,
                        tripId = trip.id,
                        myUid = myUid,
                        members = members,
                        locations = locations
                            .map { location ->
                                MemberLocationUi(
                                    uid = location.uid,
                                    displayName = members.firstOrNull { it.uid == location.uid }?.displayName
                                        ?: "구성원",
                                    location = location,
                                    distanceMeters = mine?.takeIf { it.uid != location.uid }?.let {
                                        Haversine.distanceMeters(
                                            it.latitude, it.longitude,
                                            location.latitude, location.longitude,
                                        )
                                    },
                                    isMe = location.uid == myUid,
                                )
                            }
                            // 내 카드가 항상 아래, 상대가 위 — 이 화면의 주인공은 상대다.
                            .sortedBy { it.isMe },
                        sharing = sharingNow,
                        actionError = error,
                        needsPermission = permission,
                        shareMode = prefs.locationShareMode,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TogetherUiState())

    fun clearActionError() = actionError.clear()

    /** 자동 공유 모드 변경. 다음 앱 포그라운드 진입부터 적용된다(코디네이터 주석 참고). */
    fun setShareMode(mode: LocationShareMode) {
        viewModelScope.launch { appPreferences.setLocationShareMode(mode) }
    }

    /** 화면 복귀 시 권한 상태 재확인(설정에서 바꾸고 돌아올 수 있다). */
    fun refreshPermission() {
        needsPermission.value = !locationProvider.hasLocationPermission()
    }

    /** 버튼을 눌렀을 때만 위치를 잡아 서버로 보낸다 — 이 함수가 유일한 전송 경로다. */
    fun shareMyLocation() {
        val tripId = uiState.value.tripId ?: return
        val uid = uiState.value.myUid ?: return
        if (sharing.value) return
        sharing.value = true
        viewModelScope.launch {
            actionError.runReporting("위치를 가져오지 못했습니다. 위치 권한과 GPS를 확인해 주세요.") {
                val current = locationProvider.refreshCurrentLocation()
                    ?: error("위치를 가져오지 못했습니다. 위치 권한과 GPS를 확인해 주세요.")
                memberLocationRepository.shareMyLocation(
                    tripId,
                    MemberLocation(
                        uid = uid,
                        latitude = current.latitude,
                        longitude = current.longitude,
                        sharedAt = Instant.now(),
                    ),
                )
            }
            sharing.value = false
        }
    }

    /** 내 위치를 서버에서 지운다. 공유가 부담스러우면 언제든 되돌릴 수 있어야 한다. */
    fun clearMyLocation() {
        val tripId = uiState.value.tripId ?: return
        val uid = uiState.value.myUid ?: return
        viewModelScope.launch {
            actionError.runReporting("위치를 지우지 못했습니다.") {
                memberLocationRepository.clearMyLocation(tripId, uid)
            }
        }
    }
}
