package com.jeongmin.honeymoondoctor.domain.model

data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String?,
)
