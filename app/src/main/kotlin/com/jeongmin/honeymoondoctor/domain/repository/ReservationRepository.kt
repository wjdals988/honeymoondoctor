package com.jeongmin.honeymoondoctor.domain.repository

import com.jeongmin.honeymoondoctor.domain.model.Reservation
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    fun observeReservations(tripId: String): Flow<List<Reservation>>

    suspend fun create(tripId: String, reservation: Reservation)

    suspend fun update(tripId: String, reservation: Reservation)

    suspend fun delete(tripId: String, reservationId: String)
}
