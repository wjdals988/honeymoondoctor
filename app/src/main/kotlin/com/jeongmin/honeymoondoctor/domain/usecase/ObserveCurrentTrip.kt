package com.jeongmin.honeymoondoctor.domain.usecase

import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** 로그인 사용자의 현재 여행(없으면 null). 여러 화면 ViewModel이 반복하던 체인을 한 곳으로 모은다. */
class ObserveCurrentTrip @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Trip?> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else tripRepository.observeMyTrip(user.uid)
    }
}
