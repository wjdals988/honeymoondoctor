package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.City
import kotlinx.coroutines.flow.Flow

interface CityRepository {
    fun observeCities(tripId: String): Flow<List<City>>
}
