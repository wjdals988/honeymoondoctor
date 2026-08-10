package com.jeongmin.honeymoondoctor.domain.model

import java.time.Instant

enum class TripRole { OWNER, MEMBER }

enum class TripStatus { ACTIVE, COMPLETED }

data class TripMember(
    val uid: String,
    val displayName: String,
    val role: TripRole,
    val joinedAt: Instant,
)

data class Trip(
    val id: String,
    val name: String,
    val startDate: String, // ISO-8601 date (YYYY-MM-DD), 여행 전체 기간은 시간대와 무관해 문자열로 보관
    val endDate: String,
    val defaultCurrency: String,
    val ownerId: String,
    val memberIds: List<String>,
    val inviteCodeHash: String?,
    val status: TripStatus,
    val seedVersion: String?,
    val isPublic: Boolean = false,
    val completedAt: Instant? = null,
    val publishedAt: Instant? = null,
)

/** 완료된 여행은 구성원도 더 이상 수정할 수 없다(Firestore 규칙에서도 동일하게 서버 측에서 강제). */
val Trip.isReadOnly: Boolean get() = status == TripStatus.COMPLETED

enum class JoinRequestStatus { PENDING, APPROVED, REJECTED }

data class JoinRequest(
    val id: String,
    val applicantUid: String,
    val applicantDisplayName: String,
    val status: JoinRequestStatus,
    val createdAt: Instant,
)
