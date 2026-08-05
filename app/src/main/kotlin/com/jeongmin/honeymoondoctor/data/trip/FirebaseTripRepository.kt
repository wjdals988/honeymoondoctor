package com.jeongmin.honeymoondoctor.data.trip

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.core.security.InviteCode
import com.jeongmin.honeymoondoctor.data.checklist.toFirestoreMap
import com.jeongmin.honeymoondoctor.data.city.toFirestoreMap
import com.jeongmin.honeymoondoctor.data.decision.toFirestoreMap
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.data.itinerary.toFirestoreMap
import com.jeongmin.honeymoondoctor.data.reservation.toFirestoreMap
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

    override suspend fun createTripWithSeed(ownerUid: String, ownerDisplayName: String): Trip {
        val seed = seedAssetLoader.loadHoneymoonTripSeed()
        val tripRef = firestore.collection(TRIPS).document()
        val tripData = mapOf(
            "name" to seed.trip.name,
            "startDate" to seed.trip.startDate,
            "endDate" to seed.trip.endDate,
            "defaultCurrency" to seed.trip.defaultCurrency,
            "ownerId" to ownerUid,
            "memberIds" to listOf(ownerUid),
            "inviteCodeHash" to null,
            "status" to TripStatus.ACTIVE.name,
            "seedVersion" to seed.seedVersion,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val memberRef = tripRef.collection(MEMBERS).document(ownerUid)
        val memberData = mapOf(
            "displayName" to ownerDisplayName,
            "role" to TripRole.OWNER.name,
            "joinedAt" to FieldValue.serverTimestamp(),
        )
        // 여행 문서와 소유자 구성원 문서를 하나의 트랜잭션으로 묶어, 규칙의 isTripOwner()가
        // 두 쓰기 모두에 대해 일관되게(같은 커밋 시점 기준으로) 평가되도록 한다.
        // 시드 데이터(도시·일정)도 같은 트랜잭션에 넣어 "전부 삽입되거나 전혀 안 되거나"를 보장한다.
        // 여행 생성이 시드 삽입의 유일한 진입점이므로 재실행·동기화 시 재삽입되지 않는다(스펙 4장).
        firestore.runTransaction { transaction ->
            transaction.set(tripRef, tripData)
            transaction.set(memberRef, memberData)
            seed.cities.forEach { citySeed ->
                val city = citySeed.toDomainCity()
                transaction.set(
                    tripRef.collection("cities").document(city.id),
                    city.toFirestoreMap() + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            seed.itinerary.forEach { itemSeed ->
                val item = itemSeed.toDomainItem()
                transaction.set(
                    tripRef.collection("itinerary").document(item.id),
                    item.toFirestoreMap() + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            seed.reservations.forEach { reservationSeed ->
                val reservation = reservationSeed.toDomainReservation()
                transaction.set(
                    tripRef.collection("reservations").document(reservation.id),
                    reservation.toFirestoreMap() + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            seed.checklistItems.forEach { checklistSeed ->
                val checklistItem = checklistSeed.toDomainChecklistItem()
                transaction.set(
                    tripRef.collection("checklistItems").document(checklistItem.id),
                    checklistItem.toFirestoreMap() + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            seed.decisions.forEach { decisionSeed ->
                val decision = decisionSeed.toDomainDecision()
                transaction.set(
                    tripRef.collection("decisions").document(decision.id),
                    decision.toFirestoreMap() + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.await()

        return Trip(
            id = tripRef.id,
            name = seed.trip.name,
            startDate = seed.trip.startDate,
            endDate = seed.trip.endDate,
            defaultCurrency = seed.trip.defaultCurrency,
            ownerId = ownerUid,
            memberIds = listOf(ownerUid),
            inviteCodeHash = null,
            status = TripStatus.ACTIVE,
            seedVersion = seed.seedVersion,
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
