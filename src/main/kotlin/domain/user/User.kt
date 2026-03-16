package com.gonzalinux.domain.user

import java.time.OffsetDateTime

data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
