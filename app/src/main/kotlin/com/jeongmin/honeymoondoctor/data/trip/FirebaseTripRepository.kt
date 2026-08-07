package com.jeongmin.honeymoondoctor.data.trip

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.core.security.InviteCode
import com.jeongmin.honeymoondoctor.data.checklist.toFirestoreMap
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.data.seed.SeedAssetLoader
import com.jeongmin.honeymoondoctor.data.seed.toDomainChecklistItem
import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.TripRole
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TRIPS = "trips"
private const val MEMBERS = "members"
private const val JOIN_REQUESTS = "joinRequests"

@Singleton
class FirebaseTripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val seedAssetLoader: SeedAssetLoader,
) : TripRepository {

    override fun observeMyTrip(uid: String): Flow<Trip?> =
        firestore.collection(TRIPS)
            .whereArrayContains("memberIds", uid)
            .limit(1)
            .snapshotFlow()
            .map { it?.documents?.firstOrNull()?.toTrip() }

    override fun observeMembers(tripId: String): Flow<List<TripMember>> =
        firestore.collection(TRIPS).document(tripId).collection(MEMBERS)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toMember() } }

    override fun observePendingJoinRequests(tripId: String): Flow<List<JoinRequest>> =
        firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS)
            .whereEqualTo("status", JoinRequestStatus.PENDING.name)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toJoinRequest() } }

    override suspend fun createTrip(ownerUid: String, ownerDisplayName: String, draft: NewTripDraft): Trip {
        val defaults = seedAssetLoader.loadNewTripDefaults()
        val tripRef = firestore.collection(TRIPS).document()
        val tripData = mapOf(
            "name" to draft.name,
            "startDate" to draft.startDate,
            "endDate" to draft.endDate,
            "defaultCurrency" to draft.defaultCurrency,
            "ownerId" to ownerUid,
            "memberIds" to listOf(ownerUid),
            "inviteCodeHash" to null,
            "status" to TripStatus.ACTIVE.name,
            "seedVersion" to defaults.seedVersion,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val memberRef = tripRef.collection(MEMBERS).document(ownerUid)
        val memberData = mapOf(
            "displayName" to ownerDisplayName,
            "role" to TripRole.OWNER.name,
            "joinedAt" to FieldValue.serverTimestamp(),
        )
        // firestore.rules의 isTripOwner()/isTripMember()는 get()으로 trips/{tripId}를 다시 읽어 검사한다.
        // Firestore는 트랜잭션 안에서 "같은 트랜잭션이 쓰고 있는 문서"에 대한 get()을 트랜잭션 시작
        // 시점의 상태(즉 아직 존재하지 않음)로 평가하므로, 여행 문서와 구성원·시드 데이터를 한
        // 트랜잭션에 함께 넣으면 프로덕션에서 항상 PERMISSION_DENIED가 난다(Emulator 규칙 테스트는
        // 이 조합을 검증하지 않아 이 문제를 잡지 못했다 — 실제 Firebase 연동 후에야 발견됨).
        // 그래서 여행 문서를 먼저 커밋해 get()이 볼 수 있게 만든 뒤, 구성원+체크리스트를 별도
        // 트랜잭션으로 묶는다. 두 번째 트랜잭션이 실패하면 첫 번째에서 만든 여행 문서를 정리해
        // "구성원도 없는 빈 여행"이 남지 않게 한다.
        tripRef.set(tripData).await()
        runCatching {
            firestore.runTransaction { transaction ->
                transaction.set(memberRef, memberData)
                defaults.checklistItems.forEach { checklistSeed ->
                    val checklistItem = checklistSeed.toDomainChecklistItem()
                    transaction.set(
                        tripRef.collection("checklistItems").document(checklistItem.id),
                        checklistItem.toFirestoreMap() + mapOf(
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }.await()
        }.onFailure { error ->
            runCatching { tripRef.delete().await() }
            throw error
        }

        return Trip(
            id = tripRef.id,
            name = draft.name,
            startDate = draft.startDate,
            endDate = draft.endDate,
            defaultCurrency = draft.defaultCurrency,
            ownerId = ownerUid,
            memberIds = listOf(ownerUid),
            inviteCodeHash = null,
            status = TripStatus.ACTIVE,
            seedVersion = defaults.seedVersion,
        )
    }

    override suspend fun regenerateInviteCode(tripId: String): String {
        val rawCode = InviteCode.generate(tripId)
        firestore.collection(TRIPS).document(tripId)
            .update("inviteCodeHash", InviteCode.sha256Hex(rawCode), "updatedAt", FieldValue.serverTimestamp())
            .await()
        return rawCode
    }

    override suspend fun expireInviteCode(tripId: String) {
        firestore.collection(TRIPS).document(tripId)
            .update("inviteCodeHash", null, "updatedAt", FieldValue.serverTimestamp())
            .await()
    }

    override suspend fun requestToJoin(
        inviteCode: String,
        applicantUid: String,
        applicantDisplayName: String,
    ): Result<Unit> = runCatching {
        val tripId = InviteCode.extractTripId(inviteCode)
            ?: throw IllegalArgumentException("초대코드 형식이 올바르지 않습니다.")
        val requestRef = firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS).document()
        val data = mapOf(
            "applicantUid" to applicantUid,
            "applicantDisplayName" to applicantDisplayName,
            "status" to JoinRequestStatus.PENDING.name,
            "inviteCodeHash" to InviteCode.sha256Hex(inviteCode),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        // 해시가 틀리면 firestore.rules의 create 조건에 의해 여기서 PERMISSION_DENIED로 실패한다.
        requestRef.set(data).await()
    }

    override suspend fun approveJoinRequest(tripId: String, requestId: String) {
        val tripRef = firestore.collection(TRIPS).document(tripId)
        val requestRef = tripRef.collection(JOIN_REQUESTS).document(requestId)
        firestore.runTransaction { transaction ->
            val tripSnapshot = transaction.get(tripRef)
            val requestSnapshot = transaction.get(requestRef)
            val applicantUid = requestSnapshot.getString("applicantUid")
                ?: throw IllegalStateException("참여 요청에 applicantUid가 없습니다.")
            val applicantDisplayName = requestSnapshot.getString("applicantDisplayName") ?: applicantUid
            @Suppress("UNCHECKED_CAST")
            val memberIds = (tripSnapshot.get("memberIds") as? List<String>).orEmpty()
            check(memberIds.size < 2) { "여행 구성원은 최대 2명입니다." }

            transaction.update(
                tripRef,
                "memberIds", memberIds + applicantUid,
                "updatedAt", FieldValue.serverTimestamp(),
            )
            transaction.set(
                tripRef.collection(MEMBERS).document(applicantUid),
                mapOf(
                    "displayName" to applicantDisplayName,
                    "role" to TripRole.MEMBER.name,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            )
            transaction.update(requestRef, "status", JoinRequestStatus.APPROVED.name)
        }.await()
    }

    override suspend fun rejectJoinRequest(tripId: String, requestId: String) {
        firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS).document(requestId)
            .update("status", JoinRequestStatus.REJECTED.name)
            .await()
    }

    private fun DocumentSnapshot.toTrip(): Trip? {
        val ownerId = getString("ownerId") ?: return null
        @Suppress("UNCHECKED_CAST")
        val memberIds = (get("memberIds") as? List<String>).orEmpty()
        return Trip(
            id = id,
            name = getString("name").orEmpty(),
            startDate = getString("startDate").orEmpty(),
            endDate = getString("endDate").orEmpty(),
            defaultCurrency = getString("defaultCurrency").orEmpty(),
            ownerId = ownerId,
            memberIds = memberIds,
            inviteCodeHash = getString("inviteCodeHash"),
            status = runCatching { TripStatus.valueOf(getString("status").orEmpty()) }.getOrDefault(TripStatus.ACTIVE),
            seedVersion = getString("seedVersion"),
        )
    }

    private fun DocumentSnapshot.toMember(): TripMember? {
        val displayName = getString("displayName") ?: return null
        val role = runCatching { TripRole.valueOf(getString("role").orEmpty()) }.getOrDefault(TripRole.MEMBER)
        val joinedAt = getTimestamp("joinedAt")?.toDate()?.toInstant() ?: Instant.EPOCH
        return TripMember(uid = id, displayName = displayName, role = role, joinedAt = joinedAt)
    }

    private fun DocumentSnapshot.toJoinRequest(): JoinRequest? {
        val applicantUid = getString("applicantUid") ?: return null
        val status = runCatching { JoinRequestStatus.valueOf(getString("status").orEmpty()) }
            .getOrDefault(JoinRequestStatus.PENDING)
        val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH
        return JoinRequest(
            id = id,
            applicantUid = applicantUid,
            applicantDisplayName = getString("applicantDisplayName") ?: applicantUid,
            status = status,
            createdAt = createdAt,
        )
    }
}
