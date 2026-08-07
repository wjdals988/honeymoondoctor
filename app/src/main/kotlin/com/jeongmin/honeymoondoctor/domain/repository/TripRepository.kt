package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.JoinRequest
import com.jeongmin.honeymoondoctor.domain.model.NewTripDraft
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.model.TripMember
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    /** 현재 로그인한 사용자가 속한 여행. 아직 없으면 null. */
    fun observeMyTrip(uid: String): Flow<Trip?>

    fun observeMembers(tripId: String): Flow<List<TripMember>>

    /** 소유자에게만 유효한 목록. 구성원이 아닌 사용자가 호출하면 규칙에 의해 빈 목록/오류로 처리된다. */
    fun observePendingJoinRequests(tripId: String): Flow<List<JoinRequest>>

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
}
