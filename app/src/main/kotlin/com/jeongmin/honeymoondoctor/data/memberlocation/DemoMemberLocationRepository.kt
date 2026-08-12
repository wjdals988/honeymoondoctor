package com.jeongmin.honeymoondoctor.data.memberlocation

import com.jeongmin.honeymoondoctor.domain.model.MemberLocation
import com.jeongmin.honeymoondoctor.domain.repository.MemberLocationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 데모 모드: 메모리에만 둔다. 데모에는 상대가 없어 내 위치 1건만 오간다. */
@Singleton
class DemoMemberLocationRepository @Inject constructor() : MemberLocationRepository {

    private val store = MutableStateFlow<Map<String, List<MemberLocation>>>(emptyMap())

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> =
        store.map { it[tripId].orEmpty() }

    override suspend fun shareMyLocation(tripId: String, location: MemberLocation) {
        store.value = store.value + (
            tripId to (store.value[tripId].orEmpty().filterNot { it.uid == location.uid } + location)
            )
    }

    override suspend fun clearMyLocation(tripId: String, uid: String) {
        store.value = store.value + (tripId to store.value[tripId].orEmpty().filterNot { it.uid == uid })
    }
}
