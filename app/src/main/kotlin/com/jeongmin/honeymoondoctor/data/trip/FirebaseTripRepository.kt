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
private val OPERATIONAL_COLLECTIONS = listOf(
    "cities", "itinerary", "reservations", "checklistItems",
    "expenses", "budgets", "places", "decisions",
)

@Singleton
class FirebaseTripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val seedAssetLoader: SeedAssetLoader,
) : TripRepository {

    // 완료된 여행도 계속 이 값으로 조회된다 — 완료 후에도 소유자가 여행 정보 화면에서
    // 공개 토글을 켜거나 다시 활성화할 수 있어야 하기 때문이다(완료 즉시 화면에서 밀려나면
    // 공개 처리를 할 화면 자체가 사라진다). 계정당 여행이 1개인 현재 구조에서 "완료 후 새
    // 여행을 곧바로 또 만드는" 흐름은 이번 범위에 포함하지 않았다(§보류: 여러 여행 전환 UI).
    override fun observeMyTrips(uid: String): Flow<List<Trip>> =
        firestore.collection(TRIPS)
            .whereArrayContains("memberIds", uid)
            .snapshotFlow()
            .map { snapshot ->
                // 정렬을 서버 orderBy로 하지 않는 이유: whereArrayContains와 orderBy를 같이 쓰면
                // 복합 색인이 필요해 배포 절차가 하나 더 늘어난다. 한 사람의 여행은 많아야 수십 개라
                // 클라이언트 정렬로 충분하다. 최근 출발이 위로 오게 한다.
                snapshot?.documents.orEmpty().mapNotNull { it.toTrip() }
                    .sortedByDescending { it.startDate }
            }

    override fun observeMembers(tripId: String): Flow<List<TripMember>> =
        firestore.collection(TRIPS).document(tripId).collection(MEMBERS)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toMember() } }

    override fun observePendingJoinRequests(tripId: String): Flow<List<JoinRequest>> =
        firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS)
            .whereEqualTo("status", JoinRequestStatus.PENDING.name)
            .snapshotFlow()
            .map { snapshot -> snapshot?.documents.orEmpty().mapNotNull { it.toJoinRequest() } }

    override fun observeMyJoinRequest(tripId: String, uid: String): Flow<JoinRequestStatus?> =
        firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS).document(uid)
            .snapshotFlow()
            .map { it?.toJoinRequest()?.status }

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
            "isPublic" to false,
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
        // 문서 ID를 applicantUid로 고정해, 신청자 본인이 나중에 이 문서를 다시 찾아 상태(거절 등)를
        // 확인할 수 있게 한다(auto-ID였다면 list는 소유자만 가능해 본인도 자기 요청을 못 찾았다).
        val requestRef = firestore.collection(TRIPS).document(tripId).collection(JOIN_REQUESTS).document(applicantUid)
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

    override suspend fun setStatus(tripId: String, status: TripStatus) {
        val updates = mutableMapOf<String, Any?>(
            "status" to status.name,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // 완료 처리 시각을 기록하고, 재개(ACTIVE로 되돌림) 시에는 지워 다음 완료 때 다시 채워지게 한다.
        updates["completedAt"] = if (status == TripStatus.COMPLETED) FieldValue.serverTimestamp() else null
        firestore.collection(TRIPS).document(tripId).update(updates).await()
    }

    override suspend fun setPublic(tripId: String, isPublic: Boolean) {
        val updates = mutableMapOf<String, Any?>(
            "isPublic" to isPublic,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isPublic) {
            // 공개 사본을 본 사람이 이 해시로 참여 요청을 위조하지 못하게, 공개하는 순간 지운다.
            updates["inviteCodeHash"] = null
            updates["publishedAt"] = FieldValue.serverTimestamp()
        }
        firestore.collection(TRIPS).document(tripId).update(updates).await()
    }

    override suspend fun deleteTripCompletely(tripId: String) {
        val tripRef = firestore.collection(TRIPS).document(tripId)
        // 완료된 여행의 하위 컬렉션은 isTripActive(tripId) 규칙에 의해 쓰기(삭제 포함)가 막혀
        // 있다. 문서 전체가 곧 사라지므로 completedAt이 잠깐 비는 것은 무해하다 — 삭제만을
        // 위해 잠깐 ACTIVE로 되돌린다.
        setStatus(tripId, TripStatus.ACTIVE)

        val allDocs = buildList {
            OPERATIONAL_COLLECTIONS.forEach { name ->
                addAll(tripRef.collection(name).get().await().documents.map { it.reference })
            }
            addAll(tripRef.collection(MEMBERS).get().await().documents.map { it.reference })
            addAll(tripRef.collection(JOIN_REQUESTS).get().await().documents.map { it.reference })
        }
        // Firestore 배치는 500건 한도 — 2인 여행의 데이터량을 감안해 여유 있게 나눈다.
        allDocs.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
        // 트립 문서는 반드시 마지막에 지운다 — 먼저 지우면 하위 컬렉션 삭제 시 규칙의
        // isTripMember()/isTripOwner()가 존재하지 않는 부모 문서를 get()하게 되어 이후 삭제가
        // 전부 거부되는 고아 데이터가 남을 수 있다.
        tripRef.delete().await()
    }

    override suspend fun leaveTrip(tripId: String, uid: String) {
        val tripRef = firestore.collection(TRIPS).document(tripId)
        firestore.batch().apply {
            update(
                tripRef,
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            delete(tripRef.collection(MEMBERS).document(uid))
        }.commit().await()
    }

    override suspend fun transferOwnershipAndLeaveTrip(tripId: String, departingOwnerUid: String, newOwnerUid: String) {
        val tripRef = firestore.collection(TRIPS).document(tripId)
        firestore.batch().apply {
            update(
                tripRef,
                mapOf(
                    "ownerId" to newOwnerUid,
                    "memberIds" to FieldValue.arrayRemove(departingOwnerUid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            delete(tripRef.collection(MEMBERS).document(departingOwnerUid))
            update(tripRef.collection(MEMBERS).document(newOwnerUid), mapOf("role" to TripRole.OWNER.name))
        }.commit().await()
    }

    override suspend fun updateTripInfo(tripId: String, name: String, startDate: String, endDate: String, defaultCurrency: String) {
        firestore.collection(TRIPS).document(tripId)
            .update(
                mapOf(
                    "name" to name,
                    "startDate" to startDate,
                    "endDate" to endDate,
                    "defaultCurrency" to defaultCurrency,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
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
            isPublic = getBoolean("isPublic") ?: false,
            completedAt = getTimestamp("completedAt")?.toDate()?.toInstant(),
            publishedAt = getTimestamp("publishedAt")?.toDate()?.toInstant(),
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
