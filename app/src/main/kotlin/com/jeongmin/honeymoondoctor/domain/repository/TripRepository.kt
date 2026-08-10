package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.JoinRequestStatus
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import com.jeongmin.honeymoondoctor.domain.model.TripStatus
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    /** 현재 로그인한 사용자가 속한 여행. 아직 없으면 null. */
    fun observeMyTrip(uid: String): Flow<Trip?>

    fun observeMembers(tripId: String): Flow<List<TripMember>>

    /** 소유자에게만 유효한 목록. 구성원이 아닌 사용자가 호출하면 규칙에 의해 빈 목록/오류로 처리된다. */
    fun observePendingJoinRequests(tripId: String): Flow<List<JoinRequest>>

    /** 본인이 보낸 참여 요청의 현재 상태(없으면 null). 신청자 본인이 승인/거절 결과를 확인할 수 있게 한다. */
    fun observeMyJoinRequest(tripId: String, uid: String): Flow<JoinRequestStatus?>

    /** 여행 최초 생성 + 기본 준비물 체크리스트 1회 삽입. ownerUid가 유일한 최초 구성원이 된다. */
    suspend fun createTrip(ownerUid: String, ownerDisplayName: String, draft: NewTripDraft): Trip

    /** 새 초대코드를 생성하고 해시만 저장한 뒤, 공유용 원문 코드를 반환한다(재발급 시 이전 코드는 즉시 무효화). */
    suspend fun regenerateInviteCode(tripId: String): String

    /** 초대코드를 무효화한다(원문 없이도 해시를 더 이상 일치시킬 수 없는 값으로 교체). */
    suspend fun expireInviteCode(tripId: String)

    /** 초대코드 원문을 받아 참여 요청을 생성한다. 형식 오류/해시 불일치는 Result.failure로 반환한다. */
    suspend fun requestToJoin(inviteCode: String, applicantUid: String, applicantDisplayName: String): Result<Unit>

    suspend fun approveJoinRequest(tripId: String, requestId: String)

    suspend fun rejectJoinRequest(tripId: String, requestId: String)

    /** 여행을 완료 처리(구성원 쓰기 잠금)하거나 ACTIVE로 되돌린다. 소유자만 호출 가능(규칙에서 강제). */
    suspend fun setStatus(tripId: String, status: TripStatus)

    /**
     * 공개 여부를 바꾼다. 공개(true)는 완료된 여행에서만 가능하고, 규칙이 초대코드 해시가
     * 남아있지 않을 것을 함께 강제한다 — 공개 사본을 본 사람이 참여 요청을 위조하지 못하게.
     */
    suspend fun setPublic(tripId: String, isPublic: Boolean)
}
