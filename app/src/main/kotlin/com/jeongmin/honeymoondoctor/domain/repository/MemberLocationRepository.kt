package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.MemberLocation
import kotlinx.coroutines.flow.Flow

interface MemberLocationRepository {
    /** 이 여행 구성원들이 공유한 마지막 위치 목록(사람당 최대 1건). */
    fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>>

    /** 내 위치를 공유(덮어쓰기)한다. 버튼을 눌렀을 때만 호출된다. */
    suspend fun shareMyLocation(tripId: String, location: MemberLocation)

    /** 내가 공유한 위치를 서버에서 지운다. 언제든 되돌릴 수 있어야 공유가 부담이 없다. */
    suspend fun clearMyLocation(tripId: String, uid: String)
}
