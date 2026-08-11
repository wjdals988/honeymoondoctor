package com.jeongmin.honeymoondoctor.domain.usecase

import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.jeongmin.honeymoondoctor.domain.model.AuthUser
import com.jeongmin.honeymoondoctor.domain.model.Trip
import com.jeongmin.honeymoondoctor.domain.repository.AuthRepository
import com.jeongmin.honeymoondoctor.domain.repository.PublicTripRepository
import com.jeongmin.honeymoondoctor.domain.repository.TripRepository
import javax.inject.Inject

sealed interface DeleteAccountOutcome {
    data object Success : DeleteAccountOutcome
    data object RequiresReauth : DeleteAccountOutcome
    data class Failure(val cause: Throwable) : DeleteAccountOutcome
}

/**
 * 회원 탈퇴. 소유자·동반자 유무에 따라 여행 데이터를 다르게 정리한 뒤 계정을 삭제한다.
 * - 소유자이고 동반자 없음: 여행 전체(+공개 사본) 삭제
 * - 소유자이고 동반자 있음: 남은 동반자에게 소유권을 넘기고 본인만 탈퇴(공동 데이터라 유지)
 * - 소유자가 아님: 본인만 memberIds에서 제거, 소유자의 여행은 그대로 둠
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val publicTripRepository: PublicTripRepository,
) {
    suspend operator fun invoke(user: AuthUser, trip: Trip?): DeleteAccountOutcome = runCatching {
        if (trip != null) {
            when {
                trip.memberIds.size <= 1 -> {
                    if (trip.isPublic) publicTripRepository.unpublish(trip.id)
                    tripRepository.deleteTripCompletely(trip.id)
                }
                trip.ownerId == user.uid ->
                    tripRepository.transferOwnershipAndLeaveTrip(
                        tripId = trip.id,
                        departingOwnerUid = user.uid,
                        newOwnerUid = trip.memberIds.first { it != user.uid },
                    )
                else -> tripRepository.leaveTrip(trip.id, user.uid)
            }
        }
        authRepository.deleteAccount()
    }.fold(
        onSuccess = { DeleteAccountOutcome.Success },
        onFailure = { e ->
            if (e is FirebaseAuthRecentLoginRequiredException) DeleteAccountOutcome.RequiresReauth else DeleteAccountOutcome.Failure(e)
        },
    )
}
