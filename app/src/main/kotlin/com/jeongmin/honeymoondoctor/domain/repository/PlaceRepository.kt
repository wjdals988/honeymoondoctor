package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun observePlaces(tripId: String): Flow<List<Place>>

    suspend fun create(tripId: String, place: Place)

    /** 가져오기(TSV/JSON)로 여러 건을 한 번에 추가한다. */
    suspend fun createAll(tripId: String, places: List<Place>)

    suspend fun update(tripId: String, place: Place)

    suspend fun delete(tripId: String, placeId: String)
}
