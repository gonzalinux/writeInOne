package com.gonzalinux.domain.user

import com.gonzalinux.common.UnauthorizedException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.OffsetDateTime

data class User(
    val id: Long,
    val email: String,
    val passwordHash: String?,
    val displayName: String,
    val emailVerified: Boolean,
    val ownerId: Long?,
    val serviceAccountTokenHash: String?,
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
                passwordHash = row["password"] as String?,
                emailVerified = row["email_verified"] as Boolean,
                ownerId = row["owner_id"] as Long?,
                serviceAccountTokenHash = row["service_account_token_hash"] as String?,
                createdAt = row["created_at"] as OffsetDateTime,
                updatedAt = row["updated_at"] as OffsetDateTime
            )
    }
}

/** Applied in the service layer, not the repository — see Collaboration.md. */
fun Mono<User>.requireNotServiceAccount(): Mono<User> =
    flatMap {
        if (it.serviceAccountTokenHash == null)
            it.toMono()
        else
            Mono.error { UnauthorizedException() }
    }
