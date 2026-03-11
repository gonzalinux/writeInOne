package com.gonzalinux.domain.user

import java.time.LocalDateTime

data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
