package com.jeongmin.honeymoondoctor.data.trip

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeongmin.honeymoondoctor.core.security.InviteCode
import com.jeongmin.honeymoondoctor.data.checklist.DemoChecklistRepository
import com.jeongmin.honeymoondoctor.data.city.DemoCityRepository
import com.jeongmin.honeymoondoctor.data.decision.DemoDecisionRepository
import com.jeongmin.honeymoondoctor.data.itinerary.DemoItineraryRepository
import com.jeongmin.honeymoondoctor.data.local.prefs.appDataStore
import com.jeongmin.honeymoondoctor.data.reservation.DemoReservationRepository
import com.jeongmin.honeymoondoctor.data.seed.SeedAssetLoader
import com.jeongmin.honeymoondoctor.data.seed.toDomainChecklistItem
import com.jeongmin.honeymoondoctor.data.seed.toDomainCity
import com.jeongmin.honeymoondoctor.data.seed.toDomainDecision
import com.jeongmin.honeymoondoctor.data.seed.toDomainItem
import com.jeongmin.honeymoondoctor.data.seed.toDomainReservation
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
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
    private val demoItineraryRepository: DemoItineraryRepository,
    private val demoCityRepository: DemoCityRepository,
    private val demoReservationRepository: DemoReservationRepository,
    private val demoChecklistRepository: DemoChecklistRepository,
    private val demoDecisionRepository: DemoDecisionRepository,
) : com.jeongmin.honeymoondoctor.domain.repository.TripRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.appDataStore

    private val stateFlow: Flow<DemoTripStateDto?> = dataStore.data.map { prefs ->
        prefs[DEMO_TRIP_STATE_KEY]?.let { json.decodeFromString(DemoTripStateDto.serializer(), it) }
    }

    override fun observeMyTrip(uid: String): Flow<Trip?> = stateFlow.map { state ->
        state?.takeIf { uid in it.memberIds }?.toDomain()
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

    override suspend fun createTripWithSeed(ownerUid: String, ownerDisplayName: String): Trip {
        val seed = seedAssetLoader.loadHoneymoonTripSeed()
        val tripId = "demo-trip-${UUID.randomUUID()}"
        val now = Instant.now().toEpochMilli()
        val state = DemoTripStateDto(
            id = tripId,
            name = seed.trip.name,
            startDate = seed.trip.startDate,
            endDate = seed.trip.endDate,
            defaultCurrency = seed.trip.defaultCurrency,
            ownerId = ownerUid,
            memberIds = listOf(ownerUid),
            inviteCodeHash = null,
            seedVersion = seed.seedVersion,
            members = listOf(DemoMemberDto(ownerUid, ownerDisplayName, TripRole.OWNER.name, now)),
        )
        saveState(state)
        // 시드 데이터는 여행 최초 생성 시에만 삽입한다(스펙 4장). 여행 생성이 유일한 진입점이므로
        // 재실행·동기화 시 재삽입되지 않는다.
        demoCityRepository.seedForNewTrip(tripId, seed.cities.map { it.toDomainCity() })
        demoItineraryRepository.seedForNewTrip(tripId, seed.itinerary.map { it.toDomainItem() })
        demoReservationRepository.seedForNewTrip(tripId, seed.reservations.map { it.toDomainReservation() })
        demoChecklistRepository.seedForNewTrip(tripId, seed.checklistItems.map { it.toDomainChecklistItem() })
        demoDecisionRepository.seedForNewTrip(tripId, seed.decisions.map { it.toDomainDecision() })
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
        status = TripStatus.ACTIVE,
        seedVersion = seedVersion,
    )

    private fun DemoJoinRequestDto.toDomain() = JoinRequest(
        id = id,
        applicantUid = applicantUid,
        applicantDisplayName = applicantDisplayName,
        status = JoinRequestStatus.valueOf(status),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )
}
