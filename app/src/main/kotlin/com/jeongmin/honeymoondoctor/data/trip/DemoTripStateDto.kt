package com.jeongmin.honeymoondoctor.data.trip

import kotlinx.serialization.Serializable

/** DataStore에 JSON으로 저장하기 위한 데모 모드 전용 DTO. 도메인 모델(Trip 등)과는 별도로 유지한다. */
@Serializable
data class DemoTripStateDto(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val defaultCurrency: String,
    val ownerId: String,
    val memberIds: List<String>,
    val inviteCodeHash: String?,
    val seedVersion: String?,
    val members: List<DemoMemberDto>,
    val joinRequests: List<DemoJoinRequestDto> = emptyList(),
    val status: String = "ACTIVE",
    val isPublic: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val publishedAtEpochMillis: Long? = null,
)

@Serializable
data class DemoMemberDto(
    val uid: String,
    val displayName: String,
    val role: String,
    val joinedAtEpochMillis: Long,
)

@Serializable
data class DemoJoinRequestDto(
    val id: String,
    val applicantUid: String,
    val applicantDisplayName: String,
    val status: String,
    val createdAtEpochMillis: Long,
)
