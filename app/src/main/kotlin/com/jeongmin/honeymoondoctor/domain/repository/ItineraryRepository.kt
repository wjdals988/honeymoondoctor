package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.ItineraryItem
import kotlinx.coroutines.flow.Flow

interface ItineraryRepository {
    /** 여행의 전체 일정. 시작 시각 오름차순으로 정렬해 반환한다. */
    fun observeItinerary(tripId: String): Flow<List<ItineraryItem>>

    /** 새 일정 생성. item.id는 호출자가 미리 발급한다(데모/Firestore 동일 동작을 위해). */
    suspend fun create(tripId: String, item: ItineraryItem)

    suspend fun update(tripId: String, item: ItineraryItem)

    suspend fun delete(tripId: String, itemId: String)
}
