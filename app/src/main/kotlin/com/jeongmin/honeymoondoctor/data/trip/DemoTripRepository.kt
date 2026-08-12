package com.jeongmin.honeymoondoctor.data.trip

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.core.security.InviteCode
import com.jeongmin.honeymoondoctor.data.checklist.DemoChecklistRepository
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.data.seed.SeedAssetLoader
import com.jeongmin.honeymoondoctor.data.seed.toDomainChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.TripRole
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val DEMO_TRIP_STATE_KEY = stringPreferencesKey("demo_trip_state_json")

/**
 * 데모 모드에서는 기기 하나로만 동작하므로 "2인 승인" 자체를 실제로 재현할 수는 없다.
 * 그래도 초대코드 생성·해시 검증·참여 요청 CRUD UI는 동일 로직으로 그대로 테스트할 수 있도록
 * DataStore에 JSON 스냅샷 하나를 저장해 실제 Firestore 구조를 최대한 그대로 흉내낸다.
 */
@Singleton
class DemoTripRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seedAssetLoader: SeedAssetLoader,
    private val demoChecklistRepository: DemoChecklistRepository,
) : com.jeongmin.honeymoondoctor.domain.repository.TripRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoTripStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_TRIP_STATE_KEY]?.let { json.decodeFromString(DemoTripStateDto.serializer(), it) }
    }

    // 완료된 여행도 계속 이 값으로 조회된다. FirebaseTripRepository의 observeMyTrip 주석 참고.
    // 데모 모드는 기기 저장소에 여행 하나만 두는 구조라 목록도 0~1개다.
    override fun observeMyTrips(uid: String): Flow<List<Trip>> = stateFlow.map { state ->
        listOfNotNull(state?.takeIf { uid in it.memberIds }?.toDomain())
    }

    override fun observeMembers(tripId: String): Flow<List<TripMember>> = stateFlow.map { state ->
        state?.takeIf { it.id == tripId }?.members.orEmpty().map {
            TripMember(it.uid, it.displayName, TripRole.valueOf(it.role), Instant.ofEpochMilli(it.joinedAtEpochMillis))
        }
    }

    override fun observePendingJoinRequests(tripId: String): Flow<List<JoinRequest>> = stateFlow.map { state ->
        state?.takeIf { it.id == tripId }?.joinRequests.orEmpty()
            .filter { it.status == JoinRequestStatus.PENDING.name }
            .map { it.toDomain() }
    }

    override fun observeMyJoinRequest(tripId: String, uid: String): Flow<JoinRequestStatus?> = stateFlow.map { state ->
        state?.takeIf { it.id == tripId }?.joinRequests.orEmpty()
            .firstOrNull { it.applicantUid == uid }
            ?.let { JoinRequestStatus.valueOf(it.status) }
    }

    override suspend fun createTrip(ownerUid: String, ownerDisplayName: String, draft: NewTripDraft): Trip {
        val defaults = seedAssetLoader.loadNewTripDefaults()
        val tripId = "demo-trip-${UUID.randomUUID()}"
        val now = Instant.now().toEpochMilli()
        val state = DemoTripStateDto(
            id = tripId,
            name = draft.name,
            startDate = draft.startDate,
            endDate = draft.endDate,
            defaultCurrency = draft.defaultCurrency,
            ownerId = ownerUid,
            memberIds = listOf(ownerUid),
            inviteCodeHash = null,
            seedVersion = defaults.seedVersion,
            members = listOf(DemoMemberDto(ownerUid, ownerDisplayName, TripRole.OWNER.name, now)),
        )
        saveState(state)
        // 기본 준비물 체크리스트는 여행 최초 생성 시에만 삽입한다. 여행 생성이 유일한 진입점이므로
        // 재실행·동기화 시 재삽입되지 않는다. 도시·일정·예약·결정함은 사용자가 직접 채운다.
        demoChecklistRepository.seedForNewTrip(tripId, defaults.checklistItems.map { it.toDomainChecklistItem() })
        return state.toDomain()
    }

    override suspend fun regenerateInviteCode(tripId: String): String {
        val state = requireCurrentState(tripId)
        val rawCode = InviteCode.generate(tripId)
        saveState(state.copy(inviteCodeHash = InviteCode.sha256Hex(rawCode)))
        return rawCode
    }

    override suspend fun expireInviteCode(tripId: String) {
        val state = requireCurrentState(tripId)
        saveState(state.copy(inviteCodeHash = null))
    }

    override suspend fun requestToJoin(
        inviteCode: String,
        applicantUid: String,
        applicantDisplayName: String,
    ): Result<Unit> {
        val tripId = InviteCode.extractTripId(inviteCode)
            ?: return Result.failure(IllegalArgumentException("초대코드 형식이 올바르지 않습니다."))
        val state = stateFlow.first() ?: return Result.failure(IllegalStateException("여행을 찾을 수 없습니다."))
        if (state.id != tripId) return Result.failure(IllegalArgumentException("초대코드가 가리키는 여행을 찾을 수 없습니다."))
        if (state.inviteCodeHash != InviteCode.sha256Hex(inviteCode)) {
            return Result.failure(IllegalArgumentException("초대코드가 일치하지 않습니다."))
        }
        val request = DemoJoinRequestDto(
            id = "req-${UUID.randomUUID()}",
            applicantUid = applicantUid,
            applicantDisplayName = applicantDisplayName,
            status = JoinRequestStatus.PENDING.name,
            createdAtEpochMillis = Instant.now().toEpochMilli(),
        )
        saveState(state.copy(joinRequests = state.joinRequests + request))
        return Result.success(Unit)
    }

    override suspend fun approveJoinRequest(tripId: String, requestId: String) {
        val state = requireCurrentState(tripId)
        val request = state.joinRequests.firstOrNull { it.id == requestId } ?: return
        if (state.memberIds.size >= 2) return
        val updatedRequests = state.joinRequests.map {
            if (it.id == requestId) it.copy(status = JoinRequestStatus.APPROVED.name) else it
        }
        val newMember = DemoMemberDto(
            uid = request.applicantUid,
            displayName = request.applicantDisplayName,
            role = TripRole.MEMBER.name,
            joinedAtEpochMillis = Instant.now().toEpochMilli(),
        )
        saveState(
            state.copy(
                memberIds = state.memberIds + request.applicantUid,
                members = state.members + newMember,
                joinRequests = updatedRequests,
            ),
        )
    }

    override suspend fun rejectJoinRequest(tripId: String, requestId: String) {
        val state = requireCurrentState(tripId)
        val updatedRequests = state.joinRequests.map {
            if (it.id == requestId) it.copy(status = JoinRequestStatus.REJECTED.name) else it
        }
        saveState(state.copy(joinRequests = updatedRequests))
    }

    override suspend fun setStatus(tripId: String, status: TripStatus) {
        val state = requireCurrentState(tripId)
        val now = Instant.now().toEpochMilli()
        saveState(
            state.copy(
                status = status.name,
                completedAtEpochMillis = if (status == TripStatus.COMPLETED) now else null,
            ),
        )
    }

    override suspend fun setPublic(tripId: String, isPublic: Boolean) {
        val state = requireCurrentState(tripId)
        saveState(
            state.copy(
                isPublic = isPublic,
                inviteCodeHash = if (isPublic) null else state.inviteCodeHash,
                publishedAtEpochMillis = if (isPublic) Instant.now().toEpochMilli() else null,
            ),
        )
    }

    override suspend fun updateTripInfo(tripId: String, name: String, startDate: String, endDate: String, defaultCurrency: String) {
        val state = requireCurrentState(tripId)
        saveState(state.copy(name = name, startDate = startDate, endDate = endDate, defaultCurrency = defaultCurrency))
    }

    override suspend fun deleteTripCompletely(tripId: String) {
        requireCurrentState(tripId)
        dataStore.edit { it.remove(DEMO_TRIP_STATE_KEY) }
    }

    override suspend fun leaveTrip(tripId: String, uid: String) {
        val state = requireCurrentState(tripId)
        saveState(
            state.copy(
                memberIds = state.memberIds.filterNot { it == uid },
                members = state.members.filterNot { it.uid == uid },
            ),
        )
    }

    override suspend fun transferOwnershipAndLeaveTrip(tripId: String, departingOwnerUid: String, newOwnerUid: String) {
        val state = requireCurrentState(tripId)
        saveState(
            state.copy(
                ownerId = newOwnerUid,
                memberIds = state.memberIds.filterNot { it == departingOwnerUid },
                members = state.members
                    .filterNot { it.uid == departingOwnerUid }
                    .map { if (it.uid == newOwnerUid) it.copy(role = TripRole.OWNER.name) else it },
            ),
        )
    }

    private suspend fun requireCurrentState(tripId: String): DemoTripStateDto {
        val state = stateFlow.first()
        check(state != null && state.id == tripId) { "여행을 찾을 수 없습니다: $tripId" }
        return state
    }

    private suspend fun saveState(state: DemoTripStateDto) {
        dataStore.edit { it[DEMO_TRIP_STATE_KEY] = json.encodeToString(DemoTripStateDto.serializer(), state) }
    }

    private fun DemoTripStateDto.toDomain() = Trip(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        defaultCurrency = defaultCurrency,
        ownerId = ownerId,
        memberIds = memberIds,
        inviteCodeHash = inviteCodeHash,
        status = runCatching { TripStatus.valueOf(status) }.getOrDefault(TripStatus.ACTIVE),
        seedVersion = seedVersion,
        isPublic = isPublic,
        completedAt = completedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        publishedAt = publishedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    )

    private fun DemoJoinRequestDto.toDomain() = JoinRequest(
        id = id,
        applicantUid = applicantUid,
        applicantDisplayName = applicantDisplayName,
        status = JoinRequestStatus.valueOf(status),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )
}
