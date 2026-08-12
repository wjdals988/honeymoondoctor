package com.jeongmin.honeymoondoctor.data.memberlocation

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.jeongmin.honeymoondoctor.data.firestore.snapshotFlow
import com.jeongmin.honeymoondoctor.domain.model.MemberLocation
import com.jeongmin.honeymoondoctor.domain.repository.MemberLocationRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * trips/{tripId}/memberLocations/{uid} — 문서 id가 uid라서 사람당 1건이 구조적으로
 * 보장된다. 규칙도 같은 모양을 강제한다: 읽기는 구성원, 쓰기·삭제는 **자기 uid 문서만**
 * (상대의 위치를 위조하거나 지울 수 없다). 완료된 여행에서도 삭제는 허용한다 —
 * 위치는 여행 기록이 아니라 실시간 편의 데이터라, 여행이 끝났다고 못 지우게 하면
 * 마지막 위치가 서버에 박제된다.
 */
@Singleton
class FirebaseMemberLocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MemberLocationRepository {

    private fun collection(tripId: String) =
        firestore.collection("trips").document(tripId).collection("memberLocations")

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> =
        collection(tripId).snapshotFlow().map { snapshot ->
            snapshot?.documents.orEmpty().mapNotNull { doc ->
                val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                val sharedAt = doc.getTimestamp("sharedAt") ?: return@mapNotNull null
                MemberLocation(
                    uid = doc.id,
                    latitude = lat,
                    longitude = lng,
                    sharedAt = Instant.ofEpochSecond(sharedAt.seconds, sharedAt.nanoseconds.toLong()),
                )
            }
        }

    override suspend fun shareMyLocation(tripId: String, location: MemberLocation) {
        collection(tripId).document(location.uid)
            .set(
                mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "sharedAt" to Timestamp(location.sharedAt.epochSecond, location.sharedAt.nano),
                ),
            )
            .await()
    }

    override suspend fun clearMyLocation(tripId: String, uid: String) {
        collection(tripId).document(uid).delete().await()
    }
}
