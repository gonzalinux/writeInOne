package com.gonzalinux.domain.user

import java.time.OffsetDateTime

data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val emailVerified: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromData(row: Map<String, Any>): User =
            User(
                id = row["id"] as Long,
                email = row["email"] as String,
                displayName = row["display_name"] as String,
                passwordHash = row["password"] as String,
                emailVerified = row["email_verified"] as Boolean,
                createdAt = row["created_at"] as OffsetDateTime,
                updatedAt = row["updated_at"] as OffsetDateTime
            )
    }
}
