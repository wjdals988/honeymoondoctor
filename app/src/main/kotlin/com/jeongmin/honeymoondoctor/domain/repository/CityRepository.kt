package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.City
import kotlinx.coroutines.flow.Flow

interface CityRepository {
    fun observeCities(tripId: String): Flow<List<City>>
    suspend fun create(tripId: String, city: City)
    suspend fun update(tripId: String, city: City)
    suspend fun delete(tripId: String, cityId: String)
}
